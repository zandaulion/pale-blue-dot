# Credits

## Texture Assets

The following texture images are sourced from NASA and are in the public domain.
NASA imagery is not copyrighted unless otherwise noted.
See: https://www.nasa.gov/multimedia/guidelines/index.html

- `app/src/main/res/drawable-nodpi/earth_day.jpg` — NASA Blue Marble / Visible Earth
- `app/src/main/res/drawable-nodpi/earth_night.jpg` — NASA Earth at Night / Black Marble
- `app/src/main/res/drawable-nodpi/moon.jpg` — NASA lunar surface imagery

## Live Data

- Cloud cover overlay — extracted from NASA VIIRS SNPP Corrected Reflectance imagery via the [Worldview Snapshot API](https://wvs.earthdata.nasa.gov/)
- Earthquakes — [USGS Earthquake Hazards Program](https://earthquake.usgs.gov/earthquakes/feed/v1.0/geojson.php).
- Volcano, wildfire, and severe-storm observations — [NASA EONET](https://eonet.gsfc.nasa.gov/).

## Offline data and translations

City names, coordinates, and time zones are derived from GeoNames (CC BY 4.0).
Plate boundaries derive from Bird's PB2002 model via the tectonicplates collection
(Open Data Commons Attribution License 1.0). See [dataset provenance](docs/data-credits.md)
for sources, preprocessing, and checksums, and [localization review status](docs/localization.md)
for the translated interface and lessons.

## Narration

- `app/src/main/assets/narration/` — 144 generated recordings using ElevenLabs'
  **Talia — Warm Soft Guide** voice and `eleven_multilingual_v2`, covering 12 app
  languages. These are synthetic recordings of the app's public learning text.
  See [voiceover](docs/voiceover.md) for generation settings and coverage.
- Changing facts and scores use the user's selected Android text-to-speech engine.
  No speech engine or ElevenLabs SDK is bundled with the app.

## Music

- `app/src/main/res/raw/ambient_space.mp3` — "Wonders of the Earth"
  from [Pixabay](https://pixabay.com/music/adventure-wonders-of-the-earth-550792/).
  Used under the Pixabay Content License (free for commercial use, no
  attribution required).
