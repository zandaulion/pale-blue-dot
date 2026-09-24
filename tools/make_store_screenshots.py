"""Compose Play Store phone screenshots (1080x1920, 9:16) from raw device captures.

Usage:
    python tools/make_store_screenshots.py <raw_dir>

<raw_dir> holds full-resolution captures from the Galaxy Z Fold4 cover screen
(904x2316): s1..s7.png, raw8p.png (system live-wallpaper preview) and
widget_crop.png (the Earth Today widget cropped from a home screen). The status bar
is cropped away; each screen sits under a caption on the feature-graphic starfield.
Output: store/screenshots/01-*.png ... 08-*.png.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from make_feature_graphic import DEFAULT_FONTS, starfield  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT_DIR = os.path.join(ROOT, "store", "screenshots")
W, H = 1080, 1920
SCREEN_TOP, SCREEN_BOTTOM = 92, 2268      # device rows kept: below status bar, above gesture pill
SHOT_H = 1480                             # on-canvas height of the device screen
SHOT_Y = 400
RADIUS = 44

# (output name, raw file, caption, subtitle) in store order.
SHOTS = [
    ("01-real-time-earth", "s1", "A real-time Earth", "Day and night, lit by the real Sun"),
    ("02-earth-events", "s2", "Tap to explore", "Volcanoes, earthquakes, wildfires and storms, with dates and sources"),
    ("03-day-or-night", "s3", "Day or night?", "Tap anywhere to find out why, then save your favourite places"),
    ("04-plate-boundaries", "s5", "Where plates meet", "See why earthquakes and volcanoes line up"),
    ("05-seasons", "s6", "Travel through the year", "Pick a date and watch the seasons change"),
    ("06-night-sky", "s4", "A pocket night sky", "The Sun, Moon, stars and Space Station"),
    ("07-home-screen", None, "Earth on your home screen", "A widget and a live wallpaper that follow the real Sun"),
    ("08-today", "s7", "Your planet today", "Tonight's Moon, sunrise at your place and the latest events"),
]


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def device_screen(raw_dir, name):
    im = Image.open(os.path.join(raw_dir, name + ".png")).convert("RGB")
    return im.crop((0, SCREEN_TOP, im.width, SCREEN_BOTTOM))


def home_screen(raw_dir):
    """Live wallpaper (system preview, chrome cropped) with the Earth Today widget on top."""
    size = (904, SCREEN_BOTTOM - SCREEN_TOP)
    wall = Image.open(os.path.join(raw_dir, "raw8p.png")).convert("RGB").crop((0, 200, 904, 1900))
    scale = max(size[0] / wall.width, size[1] / wall.height)
    wall = wall.resize((round(wall.width * scale), round(wall.height * scale)), Image.LANCZOS)
    left, top = (wall.width - size[0]) // 2, (wall.height - size[1]) // 2
    screen = wall.crop((left, top, left + size[0], top + size[1])).convert("RGBA")
    widget = Image.open(os.path.join(raw_dir, "widget_crop.png")).convert("RGB")
    widget = widget.resize((round(widget.width * 0.74), round(widget.height * 0.74)), Image.LANCZOS)
    widget = rounded(widget, 36)
    shadow = Image.new("RGBA", screen.size, (0, 0, 0, 0))
    wx, wy = 48, size[1] - widget.height - 120      # bottom-left, leaving the globe visible
    ImageDraw.Draw(shadow).rounded_rectangle([wx, wy + 10, wx + widget.width, wy + widget.height + 10],
                                             radius=36, fill=(0, 0, 0, 150))
    screen = Image.alpha_composite(screen, shadow.filter(ImageFilter.GaussianBlur(18)))
    screen.alpha_composite(widget, (wx, wy))
    return screen.convert("RGB")


def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


def compose(screen, caption, subtitle, fonts):
    canvas = starfield(W, H, quiet_left=0, glow_center=(W // 2, SHOT_Y + SHOT_H // 2)).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    title_font = ImageFont.truetype(fonts[0], 60)
    while draw.textlength(caption, font=title_font) > W - 100:
        title_font = ImageFont.truetype(fonts[0], title_font.size - 2)
    sub_font = ImageFont.truetype(fonts[1], 32)
    draw.text((W / 2, 120), caption, font=title_font, fill=(255, 255, 255), anchor="mm")
    for i, line in enumerate(wrap(draw, subtitle, sub_font, W - 140)):
        draw.text((W / 2, 205 + i * 46), line, font=sub_font, fill=(190, 218, 255), anchor="mm")

    shot_w = round(screen.width * SHOT_H / screen.height)
    screen = rounded(screen.resize((shot_w, SHOT_H), Image.LANCZOS), RADIUS)
    x = (W - shot_w) // 2
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([x - 6, SHOT_Y - 6, x + shot_w + 6, SHOT_Y + SHOT_H + 6],
                                           radius=RADIUS + 6, fill=(80, 140, 230, 90))
    canvas = Image.alpha_composite(canvas, glow.filter(ImageFilter.GaussianBlur(22)))
    canvas.alpha_composite(screen, (x, SHOT_Y))
    ImageDraw.Draw(canvas).rounded_rectangle([x, SHOT_Y, x + shot_w - 1, SHOT_Y + SHOT_H - 1],
                                             radius=RADIUS, outline=(110, 150, 210), width=2)
    return canvas.convert("RGB")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raw_dir = sys.argv[1]
    fonts = [os.path.join(DEFAULT_FONTS, "GoogleSansCode-Bold.ttf"),
             os.path.join(DEFAULT_FONTS, "GoogleSansCode-Regular.ttf")]
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, raw, caption, subtitle in SHOTS:
        screen = home_screen(raw_dir) if raw is None else device_screen(raw_dir, raw)
        path = os.path.join(OUT_DIR, name + ".png")
        compose(screen, caption, subtitle, fonts).save(path, optimize=True)
        print("Wrote %s (%d KB)" % (os.path.normpath(path), os.path.getsize(path) // 1024))


if __name__ == "__main__":
    main()
