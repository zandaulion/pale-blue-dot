# ElevenLabs voiceover

Talia — Warm Soft Guide is the selected warm female voice for Pale Blue Dot's
fixed read-aloud cards in all 12 supported languages. The delivery is calm and
curious, suitable for children and teens. Recordings are generated during
development and bundled; the app never calls ElevenLabs.

## User behavior and coverage

First launch offers **Explore with Talia** or **Explore quietly**. No speech starts
before a choice. Talia plays an enabling confirmation after the first option;
quiet exploration starts silently. The choice persists, and **Settings → Read
aloud** can change it later. Dismissing onboarding does not enable narration.
Existing onboarded users keep their stored preference and are not prompted again.
Music has its own switch and volume slider. Wallpaper and widgets stay silent.

| Surface | Voice and behavior |
|---|---|
| Read-aloud enabling confirmation | Talia |
| Sky, daytime, night, ISS daytime/night cards | Talia when Read aloud is enabled |
| First Time Traveler discovery | Talia |
| Newly discovered fixed card | Separate Talia notebook acknowledgment followed by the complete card |
| Fixed challenge feedback | Talia: tap Earth, correct, try again, stopped with no score |
| Event dialog facts, including Replay | Device TTS when enabled and a suitable voice is available |
| Challenge completion with a positive score | Device TTS |
| Recording playback failure | Device TTS when ready and available |
| Today, daily facts, journeys, places, Guide, notebook panels, Moon label | No automatic read-aloud narration added |

This feature is optional narration, separate from Android TalkBack. It does not
provide a screen reader for every control or panel. Remote NASA/USGS titles keep
the source language even when the surrounding interface is translated.

Closing a card or event dialog, turning Read aloud off, or leaving the app stops
speech. Talia also stops on audio-focus loss. It does not resume automatically. Talia uses
transient audio focus with speech attributes; if focus is refused, playback stays
silent instead of bypassing the refusal through device TTS. Device TTS depends
on the selected engine and available locale; a missing voice can leave changing
facts silent. Offline playback is guaranteed by the bundled Talia clips, not by
all possible device TTS engines.

## Recordings and runtime

`app/src/main/assets/narration/<locale>/` holds 12 MP3s and `index.json` for each
of `en-US`, `de-DE`, `es-ES`, `fr-FR`, `ar`, `hi-IN`, `ja-JP`, `ko-KR`, `pt-BR`,
`ro`, `uk`, and `zh-CN`: 144 clips, about 15 MB of audio in total.

The clip names are `enabled`, `sky`, `day`, `night`, `iss_day`, `iss_night`,
`time_traveler`, `added`, `tap_earth`, `correct`, `try_again`, and
`challenge_stopped`, each with an `.mp3` extension. The index maps cleaned spoken
text to the filename. `kids/TaliaNarrator.kt` resolves the current app locale and
plays recordings with Android `MediaPlayer`; `kids/NarrationText.kt` removes
emoji and markup-like symbols while preserving letters, combining marks,
numbers, punctuation, and normalized whitespace. Notebook acknowledgment and
card are played as separate complete clips, without splicing words. Generation
tokens prevent stale asynchronous callbacks from restarting or stopping a newer
recording. MainActivity owns narration and releases it with its lifecycle.

## Local credentials

The generator defaults to `../.secrets/elevenlabs.env`, outside the repository:

```dotenv
ELEVENLABS_API_KEY=
```

Put the key after `=`; use directory permissions 700 and file permissions 600.
The generator also accepts `--key-file /path/to/elevenlabs.env`. It reads the key
locally without printing or packaging it. Keep it out of chat, Git, Android
assets, and BuildConfig. The repository ignores `.secrets/`, `.env`, and `.env.*`
(except a synthetic `.env.example`). Building or running bundled clips needs no key.

Create a restricted API key in the ElevenLabs dashboard with **Text-to-Speech**
access. Browsing or auditioning catalog voices also needs **Voices → Read**
(`voices_read`); voice-writing permissions are not needed to list/search.
Regenerating with the already selected public voice ID only requires the
text-to-speech operation. Use a credit quota appropriate to the generation job.

## Generating recordings

Run from the repository root with Python 3; only the standard library is used:

```bash
# Prepare scripts for review; makes no API calls and needs no key.
python3 tools/generate_narration.py --prepare-only

# Generate or reuse the recordings, using the local key file.
python3 tools/generate_narration.py
```

The tool derives scripts from the actual Android translations, including escape
handling and the combined Time Traveler title/fact. It writes review material to
`../voice-samples/app-narration-scripts.json` and generated app clips/indexes to
`app/src/main/assets/narration/`. Editing spoken text requires regenerating and
checking indexes against the updated resources before shipping.

Generation uses public voice ID `OZ0L6eISlOejga3XjDFt`, model
`eleven_multilingual_v2`, stability 0.65, similarity boost 0.8, style 0.1,
speaker boost enabled, speed 0.94, seed 42, and `mp3_44100_128` output. The script
language guides synthesis; the tool does not send a language-code override.

Two workers generate clips and atomically replace each completed file.
Per-clip generation hashes/checkpoints live in
`../voice-samples/narration-checkpoints/`. A completed clip is reused only when
its checkpoint matches the text, voice, model, and settings and the file is
present with usable size. Interrupted runs can resume locally. A fresh checkout
without these checkpoints will generate again and spend credits; inspect the
prepared scripts before running generation. Indexes are written after the job
completes. Checkpoints and audition material do not belong in Git.

## Auditions and validation

Talia, Maisie (Friendly Casual Neighbor), and Florence (Atmospheric Storyteller)
were auditioned in all 12 app languages: 36 clips and a local listening page
outside the repository in `../voice-samples/`. The audition passage is adapted
from the daytime/night-sky cards; bundled recordings use the actual app strings.
Talia was chosen after listening. English-origin voices may retain an accent in
other languages; multilingual support does not establish native pronunciation.

The October 11, 2026 Motorola Edge 30 Neo checks passed:

- `TaliaNarrationTest`: two tests covering all 12 locale catalogs, all 144 playable
  clips, composed cards, dynamic-text fallback, first-launch choices and
  persistence, actual confirmation playback, and stopping on background/off.
- `LocalizationTest`: all four tests passed; the source validator covers 792
  resources in each of the 12 languages.
- Focused onboarding/Settings layout captures at 100% and 130% text size in all
  12 languages, with no reported clipping or overlap. Three languages were
  retried after a foreground-focus interruption, then passed.
- App and instrumentation builds and Android lint passed. All MP3s decoded to
  nonempty audio locally. The reviewed APK contained all clips and no API key.

Use [build/test instructions](build-release.md) and the
[focused layout audit](localization.md) to repeat these checks. Captures and local
results stay outside the repository. These checks establish playback and layout,
not a native-speaker pronunciation review or coverage of other device sizes.

The integration is present in the current checkout and installed test APK,
still version 3.10/code 12. It has not been released through Play. Live ElevenLabs
synthesis would require a separate service holding the key; no such backend is
part of this integration.

References: [API authentication](https://elevenlabs.io/docs/api-reference/authentication),
[models and languages](https://elevenlabs.io/docs/overview/models),
[Voice Library](https://elevenlabs.io/docs/eleven-creative/voices/voice-library),
[Android audio focus](https://developer.android.com/media/optimize/audio-focus).
