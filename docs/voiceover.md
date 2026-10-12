# ElevenLabs voiceover

Talia — Warm Soft Guide is the selected warm female voice for Pale Blue Dot's
fixed read-aloud cards in all 12 supported languages. The delivery is calm and
curious, suitable for children and teens. Recordings are generated during
development and bundled; the app never calls ElevenLabs.

## User behavior and coverage

First launch offers **Explore with Talia** or **Explore quietly**. No speech starts
before a choice. Talia plays an enabling confirmation after the first option;
quiet exploration starts silently. **Explore with Talia** enables only Talia;
Android speech stays off. **Explore quietly** disables both voices. Settings has
independent **Talia voice** and **Android voice** switches. Talia covers recorded
lessons; Android speech covers changing event facts and scores only. Dismissing
onboarding does not enable either voice. Existing users retain their Talia
preference; Android speech defaults off and must be explicitly enabled. Users
are not prompted again.
Music has its own switch and volume slider. Wallpaper and widgets stay silent.

| Surface | Voice and behavior |
|---|---|
| Read-aloud enabling confirmation | Talia |
| Sky, daytime, night, ISS daytime/night cards | Talia when Talia voice is enabled |
| First Time Traveler discovery | Talia |
| Newly discovered fixed card | Separate Talia notebook acknowledgment followed by the complete card |
| Why does this happen? | Talia automatically reads earthquake, volcano, wildfire and storm explanations; Read aloud replays them |
| Guided journeys | Compact journey cards automatically read all 18 step titles/instructions/options with Talia, eight correct/incorrect feedback passages, time-advance and boundary-reveal explanations, and three completions |
| Fixed challenge feedback | Talia reads all six prompts, tap Earth, correct, try again, stopped, and a fixed congratulation after a positive score |
| Guide, seasons, plate explanation | Read aloud actions play Talia on request |
| Today and notebook | Read aloud actions play the daily prompt/fact and unlocked discovery facts with Talia; no automatic panel-wide reading |
| Place-saving help | Talia reads the fixed touch-and-hold instructions |
| Event dialog facts, including Replay | Device TTS only when Android voice is enabled and a suitable voice is available |
| Exact positive challenge score | Remains on screen; Android reads it only when Android voice is enabled and Talia is off |
| Recording playback failure | Silent; never substitutes another voice |
| Settings/forms, changing place/time/Moon values, source links, status/error notices | Use Android accessibility; these are not recorded educational passages |

This feature is optional narration, separate from Android TalkBack. It does not
provide a screen reader for every control or panel. Remote NASA/USGS titles keep
the source language even when the surrounding interface is translated.

Closing a card, event dialog or narrated panel, or leaving the app, stops both voices. Event dialogs clear the previous asynchronous dismiss listener before starting an explanation. Journey feedback and continuation use the compact inline card, keeping the globe visible. Refreshing an already open Today panel preserves its daily-fact playback. Turning a
voice switch off stops that voice independently. The event dialog's Read aloud
replay action appears only when Android voice is enabled. Talia also stops on
audio-focus loss. It does not resume automatically. Talia uses
transient audio focus with speech attributes; if focus is refused, playback stays
silent instead of bypassing the refusal through device TTS. Device TTS depends
on the selected engine and available locale; a missing voice can leave changing
facts silent. Offline playback is guaranteed by the bundled Talia clips, not by
all possible device TTS engines.

Day/night cards introduce each discovery once. Later ordinary Earth taps do not
reopen or restart speech; touch and hold deliberately revisits the lesson. See
[globe interactions](globe-interactions.md).

## Recordings and runtime

`app/src/main/assets/narration/<locale>/` contains an MP3 for each of 101
fixed passages and `index.json` for each of `en-US`, `de-DE`, `es-ES`, `fr-FR`,
`ar`, `hi-IN`, `ja-JP`, `ko-KR`, `pt-BR`, `ro`, `uk`, and `zh-CN`: 1,212 clips, approximately 124 MiB of audio.
The original 144 recordings are reused unchanged.

`assets/narration/catalog.json` declares the Android resources and fixed format
arguments for every passage. The generator and `kids/NarrationCatalog.kt` use
this shared manifest, including complete journey titles/questions/options,
feedback, discoveries, 24 daily facts and nine Guide entries. Notebook descriptions use the existing reported-observation wording rather than implying a witnessed eruption or fire. Long sections have
individual reading actions rather than one uninterrupted panel recording.
The index maps cleaned spoken text to the filename. `kids/TaliaNarrator.kt` resolves the current app locale and
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

The tool derives scripts from the shared catalog and actual Android translations, including escape handling, composed passages and fixed format arguments. It writes review material to
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
prepared scripts before running generation. A generation error stops further requests; completed checkpoints remain reusable. Indexes are written only after the whole job completes. Checkpoints and audition material do not belong in Git.

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

The October 12, 2026 independent-switch update was also checked on the Motorola:

- Four `TaliaNarrationTest` cases passed, covering the same 144 recordings,
  Talia-only first-launch opt-in, quiet mode, legacy preference handling,
  independent Settings switches and persistence, all four routing combinations,
  and a deliberately missing recording staying silent even with Android enabled.
- All 11 combined voice, globe-interaction and localization cases passed with
  native gesture tests protected from system navigation interruptions.
- The updated switches were captured in all 12 languages at 100% and 130% text
  size. Korean normal-size and Spanish large-text captures were repeated with
  foreground checks and navigation protection after Recents interrupted them.
  The reviewed app captures show no voice-control text clipping or overlap.
- Android app/test builds and lint passed; resource validation now covers 795
  translated strings per locale. Existing spoken scripts and recordings did not
  change, so no ElevenLabs regeneration or API request was needed.

The later fixed-educational coverage update adds 89 passages per locale. All
1,212 MP3s decode to nonempty audio and match the shared resource-script catalog;
the original 144 clips remain byte-for-byte unchanged. Integer format arguments
are resolved before script cleanup, including Arabic digits, and unresolved
placeholders reject generation. Six Talia tests plus three globe-interaction
and four localization tests passed on the Motorola (13 total), including real
event explanation playback/replay/close, reference reading actions, scored
challenge feedback, and a dialog continuation that keeps the next lesson playing.
App/test assembly and lint passed. Native event-explanation, journey-choice and feedback layout audits passed in all 12 languages at 100% and 130% font size (96 captures). Visual review found no clipping or overlap; long labels wrap or native dialog buttons stack. Original app preferences, locale, font and display settings were restored and verified. Captures and failure/retry logs stay outside Git.

Use [build/test instructions](build-release.md) and the
[focused layout audit](localization.md) to repeat these checks. Captures and local
results stay outside the repository. These checks establish playback and layout,
not a native-speaker pronunciation review or coverage of other device sizes.

The integration is present in the current checkout, now version 3.11/code 13.
An earlier candidate was tested on a phone. It has not been released through Play. Live ElevenLabs
synthesis would require a separate service holding the key; no such backend is
part of this integration.

References: [API authentication](https://elevenlabs.io/docs/api-reference/authentication),
[models and languages](https://elevenlabs.io/docs/overview/models),
[Voice Library](https://elevenlabs.io/docs/eleven-creative/voices/voice-library),
[Android audio focus](https://developer.android.com/media/optimize/audio-focus).
