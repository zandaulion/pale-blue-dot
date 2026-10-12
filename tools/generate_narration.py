#!/usr/bin/env python3
"""Generate bundled Talia clips. Reads a local key; never logs or packages it.

Use --prepare-only to inspect scripts without making API calls. Existing clips
are reused only when their text, voice, model, and settings still match.
"""
from concurrent.futures import ThreadPoolExecutor
from threading import Event
from pathlib import Path
import argparse
import hashlib
import json
import re
import unicodedata
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/narration'
VOICE = 'OZ0L6eISlOejga3XjDFt'
MODEL = 'eleven_multilingual_v2'
SETTINGS = {'stability': .65, 'similarity_boost': .8, 'style': .1, 'use_speaker_boost': True, 'speed': .94}
CHECKPOINTS = ROOT.parent / 'voice-samples/narration-checkpoints'
LOCALES = {'en-US': 'values', 'de-DE': 'values-de-rDE', 'es-ES': 'values-es-rES',
           'fr-FR': 'values-fr-rFR', 'ar': 'values-ar', 'hi-IN': 'values-hi-rIN',
           'ja-JP': 'values-ja-rJP', 'ko-KR': 'values-ko-rKR', 'pt-BR': 'values-pt-rBR',
           'ro': 'values-ro', 'uk': 'values-uk', 'zh-CN': 'values-zh-rCN'}
CATALOG = ASSETS / 'catalog.json'

def android_text(value):
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    escapes = {'n': '\n', 't': '\t', 'r': '\r', '\\': '\\', "'": "'", '"': '"'}
    return re.sub(r'''\\([ntr\\'"])''', lambda match: escapes[match[1]], value)

def clean(value):
    value = value.replace('\\n', '\n').replace("\\'", "'").replace('\\"', '"')
    value = value.replace('\ufe0f', '').replace('\ufe0e', '')
    value = ''.join(c if unicodedata.category(c)[0] in 'LMNP' or c.isspace() else ' ' for c in value)
    return ' '.join(value.split())

def resolve(parts, strings, locale):
    def resource(part):
        if isinstance(part, str):
            return strings[part]
        value = strings[part['resource']]
        for position, argument in enumerate(part.get('arguments', []), 1):
            if isinstance(argument, dict):
                argument = strings[argument['resource']]
            def formatted(match):
                text = str(int(argument)) if match[1] == 'd' else str(argument)
                # Java Formatter localizes decimal integers for the app's Arabic locale.
                if match[1] == 'd' and locale == 'ar':
                    text = text.translate(str.maketrans('0123456789', '٠١٢٣٤٥٦٧٨٩'))
                return text
            value = re.sub(r'%' + str(position) + r'\$([sd])', formatted, value)
        assert not re.search(r'%\d+\$', value), f'Unresolved format argument in {locale}'
        return value
    return clean('\n'.join(resource(part) for part in parts))

def scripts():
    jobs = []
    catalog = json.loads(CATALOG.read_text())
    for tag, directory in LOCALES.items():
        elements = ET.parse(ROOT / 'app/src/main/res' / directory / 'strings.xml').getroot()
        strings = {element.attrib['name']: android_text(element.text or '') for element in elements}
        for clip, parts in catalog.items():
            text = resolve(parts, strings, tag)
            assert text and not re.search(r'%\d+\$', text), (tag, clip)
            payload = {'text': text, 'model_id': MODEL, 'voice_settings': SETTINGS, 'seed': 42}
            digest = hashlib.sha256(json.dumps({'voice': VOICE, **payload}, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
            jobs.append({'locale': tag, 'clip': clip, 'text': text, 'generation_sha256': digest, 'payload': payload})
    return jobs

def generate(job, key, previous):
    directory = ASSETS / job['locale']
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / (job['clip'] + '.mp3')
    identity = (job['locale'], job['clip'])
    if previous.get(identity) == job['generation_sha256'] and target.exists() and target.stat().st_size > 1000:
        return f"Cached {job['locale']}/{job['clip']}"
    request = urllib.request.Request(
        f'https://api.elevenlabs.io/v1/text-to-speech/{VOICE}?output_format=mp3_44100_128',
        data=json.dumps(job['payload'], ensure_ascii=False).encode(),
        headers={'xi-api-key': key, 'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            audio = response.read()
            if response.headers.get_content_type() != 'audio/mpeg' or len(audio) < 1000:
                raise RuntimeError('Expected nonempty MP3 response')
    except urllib.error.HTTPError as error:
        # Log an error category only; API responses can include private account/key names.
        try:
            detail = json.loads(error.read()).get('detail', {})
            status = detail.get('status', 'request_failed') if isinstance(detail, dict) else 'request_failed'
        except (ValueError, UnicodeDecodeError):
            status = 'request_failed'
        raise RuntimeError(f"{job['locale']}/{job['clip']}: HTTP {error.code}, {status}") from None
    temporary = target.with_suffix('.partial')
    temporary.write_bytes(audio)
    temporary.replace(target)
    # Save each completed generation so an interrupted run does not spend twice.
    checkpoint = CHECKPOINTS / job['locale'] / (job['clip']+'.json')
    checkpoint.parent.mkdir(parents=True, exist_ok=True)
    checkpoint.write_text(json.dumps({'generation_sha256': job['generation_sha256']})+'\n')
    return f"Generated {job['locale']}/{job['clip']} ({len(audio)} bytes)"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--key-file', type=Path, default=ROOT.parent / '.secrets/elevenlabs.env')
    args = parser.parse_args()
    jobs = scripts()
    # Inspection material and per-request checkpoints stay outside the Git repo.
    review = ROOT.parent / 'voice-samples/app-narration-scripts.json'
    review.parent.mkdir(parents=True, exist_ok=True)
    review.write_text(json.dumps(jobs, ensure_ascii=False, indent=2)+'\n')
    print(f"Prepared {len(jobs)} clips, {sum(len(j['text']) for j in jobs)} text characters.", flush=True)
    if args.prepare_only:
        return
    key = next(line.split('=', 1)[1].strip().strip('\"\x27') for line in args.key_file.read_text().splitlines() if line.strip().startswith('ELEVENLABS_API_KEY='))
    previous = {}
    for job in jobs:
        receipt = CHECKPOINTS / job['locale'] / (job['clip']+'.json')
        if receipt.exists():
            previous[(job['locale'], job['clip'])] = json.loads(receipt.read_text())['generation_sha256']
    stopped = Event()
    def run(job):
        if stopped.is_set():
            return 'Skipped after generation failure'
        try:
            return generate(job, key, previous)
        except Exception:
            stopped.set()
            raise
    with ThreadPoolExecutor(max_workers=2) as pool:
        for result in pool.map(run, jobs):
            print(result, flush=True)
    for tag in LOCALES:
        index = {job['text']: job['clip']+'.mp3' for job in jobs if job['locale'] == tag}
        (ASSETS / tag / 'index.json').write_text(json.dumps(index, ensure_ascii=False, indent=2)+'\n')

if __name__ == '__main__':
    main()
