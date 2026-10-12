#!/usr/bin/env python3
"""Check all bundled narration against the shared scripts, without API calls or credentials."""
import json
from generate_narration import ASSETS, LOCALES, scripts


def main():
    jobs = scripts()
    for locale in LOCALES:
        passages = [job for job in jobs if job['locale'] == locale]
        expected = {job['text']: job['clip'] + '.mp3' for job in passages}
        assert len(expected) == len(passages), f'Duplicate cleaned text in {locale}'
        directory = ASSETS / locale
        actual = json.loads((directory / 'index.json').read_text())
        assert actual == expected, f'Outdated narration index: {locale}'
        assert {path.name for path in directory.glob('*.mp3')} == set(expected.values()), f'Missing or extra audio: {locale}'
        for filename in expected.values():
            assert (directory / filename).stat().st_size > 1000, f'Empty audio: {locale}/{filename}'
    print(f'Validated {len(jobs)} recordings in {len(LOCALES)} languages; scripts, indexes and bundled files match.')


if __name__ == '__main__':
    main()
