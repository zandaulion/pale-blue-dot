# Moon visibility guide

The Moon keeps its existing sunlight and texture shading. A neutral, 1dp outline
marks its silhouette only when the Earth-facing side is nearly dark. The outline has
no glow and does not brighten the interior.

- At 0–3% illumination the guide is fully visible.
- Between 3% and 12% it fades smoothly; at 12% it disappears.
- At up to 1% the label reads “New Moon” and “Unlit side · outline guide”.
- Above 1% the label reads “Thin crescent”, the approximate illuminated percentage,
  and “Outline guide”. Both waxing and waning crescents use the same treatment.

Illumination comes from the same Sun and Moon directions and scene clock as the
renderer, including Explore time. These are the app's existing approximate
astronomical models. The label is native, supports system text scaling and Arabic
direction, and is translated into all 12 app languages.

The outline is depth tested, so Earth covers it. The label hides when the Moon's
centre is behind Earth, behind the camera, or off-screen. It chooses space below
or above the Moon and avoids Earth, the time/Settings header, bottom controls, and
event cards. Opening another panel hides the label. The existing Moon direction
arrow remains available at every phase; tapping it lifts the whole lunar disk
above Earth instead of leaving it partly hidden at the globe's edge.

`MoonGuideTest` covers phase geometry, fade thresholds, and projection/occlusion.
`MoonGuideDeviceTest` is an opt-in physical-device screenshot audit, enabled with
the instrumentation argument `moonGuideAudit=true`. It checks six phase scenes
and both labels in all 12 languages, saving captures outside the repository.
The audit ignores touch input within its simulated scenes. On a dedicated test
phone, `moonGuidePinTask=true` temporarily pins the app task to prevent Recents
gestures from interrupting captures; the test removes the pin in `finally`.

Verified on the Motorola Edge 30 Neo: six phase scenes and new-Moon/crescent
labels in all 12 languages at both 100% and 130% system text size. Native text
bounds, wrapping, ellipsis, scene-clock agreement, and Earth occlusion checks
passed. Captures were visually reviewed; Arabic physical positioning and the
Moon-arrow camera framing were corrected during the audit. Build, Android lint,
and the localization validator passed (788 resources at the Moon audit; the current
catalog has 792 after the narration update). Test navigation pinning, text
size, app language, and the previous scene time were restored afterward.
