"""Render the Play Store feature graphic (1024x500, no alpha) to store/feature-graphic.png.

Usage:
    python tools/make_feature_graphic.py [path/to/font-bold.ttf] [path/to/font-regular.ttf]

The globe is drawn from the app's own NASA textures (res/drawable-nodpi) with an
orthographic projection, sunlight, city lights on the night side and an atmosphere
rim, so the image matches what the app shows. Rendered at 2x and downsampled.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
TEX = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "store", "feature-graphic.png")
DEFAULT_FONTS = r"C:\Program Files\Android\Android Studio\jbr\lib\fonts"

W, H, SS = 1024, 500, 2               # output size and supersampling factor
GLOBE_C = (784, 250)                  # globe centre, output pixels
GLOBE_R = 212                         # globe radius, output pixels
VIEW_LAT, VIEW_LON = 18.0, 22.0       # point facing the viewer (Africa / Europe)
SUN_LAT, SUN_LON = 8.0, -18.0         # subsolar point: terminator over East Africa / Arabia
MOON_C, MOON_R = (962, 78), 20
SEED = 7


def unit(lat, lon):
    lat, lon = np.radians(lat), np.radians(lon)
    return np.stack([np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)], axis=-1)


def sample(tex, lat, lon):
    """Bilinear lookup in an equirectangular texture (float array HxWx3)."""
    th, tw = tex.shape[:2]
    u = ((lon + 180.0) / 360.0 * tw) % tw
    v = np.clip((90.0 - lat) / 180.0 * th, 0, th - 1.001)
    x0, y0 = np.floor(u).astype(int), np.floor(v).astype(int)
    x1, y1 = (x0 + 1) % tw, np.minimum(y0 + 1, th - 1)
    fx, fy = (u - x0)[..., None], (v - y0)[..., None]
    top = tex[y0, x0] * (1 - fx) + tex[y0, x1] * fx
    bottom = tex[y1, x0] * (1 - fx) + tex[y1, x1] * fx
    return top * (1 - fy) + bottom * fy


def sphere(size, radius, view_lat, view_lon):
    """Per-pixel lat/lon, lambert mask and limb factor for an orthographic sphere."""
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    x = (xs + 0.5 - size / 2) / radius
    y = -(ys + 0.5 - size / 2) / radius
    rr = x * x + y * y
    inside = rr <= 1.0
    z = np.sqrt(np.clip(1.0 - rr, 0, 1))
    # View frame -> world: rotate so +z faces (view_lat, view_lon).
    phi, lam = np.radians(view_lat), np.radians(view_lon)
    east = np.array([-np.sin(lam), np.cos(lam), 0.0])
    north = np.array([-np.sin(phi) * np.cos(lam), -np.sin(phi) * np.sin(lam), np.cos(phi)])
    out = np.array([np.cos(phi) * np.cos(lam), np.cos(phi) * np.sin(lam), np.sin(phi)])
    world = x[..., None] * east + y[..., None] * north + z[..., None] * out
    lat = np.degrees(np.arcsin(np.clip(world[..., 2], -1, 1)))
    lon = np.degrees(np.arctan2(world[..., 1], world[..., 0]))
    return world, lat, lon, inside, z, np.sqrt(rr)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def render_earth(size):
    radius = size / 2 - 1
    day = np.asarray(Image.open(os.path.join(TEX, "earth_day.jpg")).convert("RGB"), dtype=np.float32) / 255
    night = np.asarray(Image.open(os.path.join(TEX, "earth_night.jpg")).convert("RGB"), dtype=np.float32) / 255
    world, lat, lon, inside, z, r = sphere(size, radius, VIEW_LAT, VIEW_LON)
    sun = unit(SUN_LAT, SUN_LON)
    ndl = (world @ sun)
    d = sample(day, lat, lon)
    n = sample(night, lat, lon)
    light = np.clip(ndl, 0, 1)[..., None]
    daylit = d * (0.08 + 1.05 * light ** 0.8)
    night_mix = (1 - smoothstep(-0.12, 0.08, ndl))[..., None]
    city = n ** 1.4 * np.array([1.25, 1.0, 0.7]) * 1.6
    color = daylit * (1 - night_mix) + (d * 0.05 + city) * night_mix
    # Atmosphere: blue haze toward the limb, brighter on the sunlit side.
    limb = smoothstep(0.82, 1.0, r)[..., None] ** 1.5
    lit_rim = np.clip(ndl + 0.2, 0, 1)[..., None]
    color = color * (1 - 0.45 * limb) + np.array([0.36, 0.62, 1.0]) * limb * (0.08 + 0.6 * lit_rim)
    rgba = np.zeros((size, size, 4), np.float32)
    rgba[..., :3] = np.clip(color, 0, 1)
    edge = np.clip((1 - r) * radius, 0, 1)          # 1px anti-aliased edge
    rgba[..., 3] = np.where(inside, edge, 0)
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def render_moon(size):
    moon = np.asarray(Image.open(os.path.join(TEX, "moon.jpg")).convert("RGB").reduce(8), dtype=np.float32) / 255
    world, lat, lon, inside, z, r = sphere(size, size / 2 - 1, 0.0, SUN_LON + 50.0)
    ndl = world @ unit(0.0, SUN_LON)
    color = sample(moon, lat, lon) * (0.04 + np.clip(ndl, 0, 1)[..., None] * 1.1)
    rgba = np.zeros((size, size, 4), np.float32)
    rgba[..., :3] = np.clip(color, 0, 1)
    rgba[..., 3] = np.where(inside, np.clip((1 - r) * size / 2, 0, 1), 0)
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def starfield(w, h, quiet_left=0.56, glow_center=None):
    """Night sky; stars dimmer left of quiet_left * w (behind text), glow around glow_center."""
    rng = np.random.default_rng(SEED)
    sky = np.zeros((h, w, 3), np.float32)
    yy, xx = np.mgrid[0:h, 0:w]
    # Deep blue gradient, lighter behind the globe.
    gx, gy = glow_center or (GLOBE_C[0] * SS, GLOBE_C[1] * SS)
    dist = np.sqrt((xx - gx) ** 2 + (yy - gy) ** 2) / (w * 0.6)
    base = np.array([0.012, 0.022, 0.055])
    glow = np.array([0.03, 0.07, 0.16])
    sky[:] = base + glow * np.clip(1 - dist, 0, 1)[..., None] ** 2
    img = Image.fromarray((np.clip(sky, 0, 1) * 255).astype(np.uint8))
    draw = ImageDraw.Draw(img)
    for _ in range(520):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        b = rng.power(3.5) * (0.45 if x < w * quiet_left else 1.0)   # quieter behind the text
        size = 0.5 + 1.8 * b ** 3
        tint = rng.choice([(255, 244, 230), (220, 232, 255), (255, 255, 255)])
        a = int(35 + 185 * b)
        col = tuple(int(c * a / 255) for c in tint)
        draw.ellipse([x - size, y - size, x + size, y + size], fill=col)
    return img


def main():
    fonts = sys.argv[1:3] or [os.path.join(DEFAULT_FONTS, "GoogleSansCode-Bold.ttf"),
                              os.path.join(DEFAULT_FONTS, "GoogleSansCode-Regular.ttf")]
    cw, ch = W * SS, H * SS
    canvas = starfield(cw, ch).convert("RGBA")

    globe_px = GLOBE_R * 2 * SS
    earth = render_earth(globe_px)
    gx, gy = GLOBE_C[0] * SS - globe_px // 2, GLOBE_C[1] * SS - globe_px // 2
    # Soft blue glow behind the planet.
    glow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([gx - 30, gy - 30, gx + globe_px + 30, gy + globe_px + 30],
                                 fill=(70, 140, 255, 70))
    canvas = Image.alpha_composite(canvas, glow.filter(ImageFilter.GaussianBlur(38)))
    canvas.alpha_composite(earth, (gx, gy))

    moon_px = MOON_R * 2 * SS
    canvas.alpha_composite(render_moon(moon_px), (MOON_C[0] * SS - moon_px // 2, MOON_C[1] * SS - moon_px // 2))

    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(fonts[0], 60 * SS)
    tagline = ImageFont.truetype(fonts[1], 25 * SS)
    small = ImageFont.truetype(fonts[1], 19 * SS)
    x0 = 56 * SS
    draw.text((x0, 150 * SS), "Pale Blue Dot", font=title, fill=(255, 255, 255))
    draw.text((x0 + 3 * SS, 232 * SS), "Explore our planet in real time", font=tagline, fill=(190, 218, 255))
    chips = ["Day & night", "Earth events", "Night sky"]
    cx = x0
    for chip in chips:
        tw = draw.textlength(chip, font=small)
        box = [cx, 292 * SS, cx + tw + 32 * SS, 330 * SS]
        draw.rounded_rectangle(box, radius=19 * SS, fill=(18, 36, 66), outline=(90, 140, 210), width=SS)
        draw.text((cx + 16 * SS, 299 * SS), chip, font=small, fill=(225, 238, 255))
        cx = box[2] + 10 * SS

    out = canvas.convert("RGB").resize((W, H), Image.LANCZOS)
    out.save(OUT, optimize=True)
    print("Wrote", os.path.normpath(OUT), out.size)


if __name__ == "__main__":
    main()
