# Live data integration

## Sources and limits

The runtime fetches public NASA/USGS data without accounts or API keys. These
are observations, not a historical archive, forecast, or emergency service.

| Data | Source and request | Display |
|---|---|---|
| Clouds | NASA Worldview Snapshot, `VIIRS_SNPP_CorrectedReflectance_TrueColor`, previous UTC date, 2048×1024 JPEG | Approximate cloud alpha extracted from brightness/saturation |
| Earthquakes | USGS `4.5_week.geojson` | M4.5+ from past seven days, reported magnitude/depth/time |
| Volcanoes | EONET v3 `status=open&category=volcanoes` | Open reported events; no day filter |
| Wildfires | EONET v3 `status=open&category=wildfires&days=10` | Open events filtered to ten days |
| Severe storms | EONET v3 `status=open&category=severeStorms&days=7` | Open events filtered to seven days; latest dated point |

Base URLs: [Worldview Snapshot](https://wvs.earthdata.nasa.gov/),
[USGS feed](https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson),
[NASA EONET](https://eonet.gsfc.nasa.gov/api/v3/events).

## Scheduling and cache ownership

`data/EarthRepository.kt` is application-owned and uses two background workers.
Consumers share refreshes and immutable feed snapshots. The repository loads
versioned disk caches before servicing queued refresh requests; GL surface
creation does not fetch data. App, wallpaper, and widget can reuse CPU results.

- Event refreshes reuse successful fetches younger than 30 minutes and coalesce
  concurrent requests per feed. Manual refresh has a 15-second cooldown.
- Event caches use `AtomicFile`, schema 1, raw payload, and fetch timestamp.
  Corrupt, oversized, future-dated, or unknown-schema caches are rejected.
- Processed cloud imagery has a six-hour disk cache and separate imagery-date
  metadata. A refresh failure can reuse stale imagery. No usable image leaves
  the renderer's generated cloud fallback available.
- Refresh is requested by consumers; the cache interval is not a guarantee of
  a perpetual background polling timer.

| State | Meaning |
|---|---|
| `NOT_LOADED` | No result loaded yet |
| `LOADING` | Refresh in progress; previous observations may still be present |
| `READY` | Successful result, including a valid empty feed |
| `STALE` | Prior successful data retained after expiry/failure |
| `UNAVAILABLE` | No usable result |

Failures are independent across feeds and do not clear other successful feeds.
UI exposes freshness, check time, and unavailable/stale explanations. It does
not silently present retained observations as current.

## Parsing and rendering

`EarthEventsProvider.fetchRaw(type)` uses HTTPS with 10-second connect and
15-second read timeouts and an 8 MiB response bound. `parse(type, json)` skips
malformed individual records while rejecting an unusable whole response.

Events preserve a stable USGS/EONET ID, category, title, coordinates,
observation/update times, source and optional URL, optional magnitude/depth,
all valid dated point geometries, and a separate visual marker size. Unsupported
shapes are skipped; EONET uses its latest valid dated point rather than assuming
the first geometry is current. Unknown values remain unknown.

Native cards show date/source and facts, can expand the explanation, persist
until dismissed/replaced, and can save an observation to the local notebook.
Layer filtering agrees across markers, picking, event lists, and challenges.

Cloud extraction produces an alpha bitmap, not a meteorological cloud product.
The chosen mode is Off, Generated, or Satellite. Generated clouds may drift;
the satellite map stays fixed and shows its imagery date when known. During
Explore time, latest event/cloud observations are hidden by default; explicitly
showing them does not turn them into historical/future data.

## Language and read aloud

Remote titles and attribution retain their original source text. Surrounding
labels, category explanations, dates, and freshness messages use the app locale.
Dynamic event facts use device TTS when Read aloud is enabled and a suitable
voice is available. They are not sent to ElevenLabs at runtime. Talia's static
recordings and their development API key are separate from these public-data
integrations. See [localization](localization.md), [voiceover](voiceover.md), and
[privacy](../PRIVACY.md).

Parser coverage includes malformed records, optional values, point selection,
and stable IDs. Remaining cache/network acceptance checks are recorded in
[progress](earth-companion-progress.md); passing parser checks alone does not
verify every failure mode on a device.
