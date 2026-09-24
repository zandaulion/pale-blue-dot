"""Simplify attributed PB2002 GeoJSON into the app's offline line asset.

Input: https://raw.githubusercontent.com/fraxen/tectonicplates/master/GeoJSON/PB2002_boundaries.json
The source collection is ODbL 1.0; see docs/data-credits.md. Run:
  python tools/preprocess_plate_boundaries.py docs/plate-source.json app/src/main/assets/plate_boundaries_v1.json
"""

import json
import math
import pathlib
import sys


def unwrap(points):
    result = [tuple(points[0])]
    for lon, lat in points[1:]:
        while lon - result[-1][0] > 180:
            lon -= 360
        while lon - result[-1][0] < -180:
            lon += 360
        result.append((lon, lat))
    return result


def simplify(points, tolerance=0.08):
    if len(points) <= 2:
        return points
    first, last = points[0], points[-1]
    dx, dy = last[0] - first[0], last[1] - first[1]
    length_sq = dx * dx + dy * dy
    best, index = -1.0, 0
    for i, (x, y) in enumerate(points[1:-1], 1):
        t = max(0.0, min(1.0, ((x-first[0])*dx + (y-first[1])*dy) / length_sq)) if length_sq else 0
        d = math.hypot(x - first[0] - t * dx, y - first[1] - t * dy)
        if d > best:
            best, index = d, i
    if best <= tolerance:
        return [first, last]
    return simplify(points[:index+1], tolerance)[:-1] + simplify(points[index:], tolerance)


source, target = (pathlib.Path(s) for s in sys.argv[1:3])
features = json.loads(source.read_text(encoding="utf-8"))["features"]
lines = []
for feature in features:
    if feature["geometry"]["type"] != "LineString":
        continue
    points = simplify(unwrap(feature["geometry"]["coordinates"]))
    if len(points) >= 2:
        lines.append([[round(lon, 4), round(lat, 4)] for lon, lat in points])
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps({"version": "PB2002-2014-ODbL", "lines": lines}, separators=(",", ":")),
                  encoding="utf-8")
print(f"{len(lines)} lines, {sum(map(len, lines))} points, {target.stat().st_size} bytes")
