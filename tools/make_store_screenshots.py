"""Compose the current English Play Store screenshots from native device captures.

Usage: python tools/make_store_screenshots.py RAW_ROOT [--output OUTPUT_DIRECTORY]
RAW_ROOT contains phone/, tablet-7/, tablet-10/, each with the eight names in SHOTS.
Capture them with the opt-in StoreScreenshotTest (see store/README.md).
Phone: captioned 1080x1920; tablets: unframed native 1920x1080 and 2560x1440.
The captures contain no system bars, so no device-specific crop is needed.
"""
import argparse
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

DEFAULT_FONTS = r"C:\Program Files\Android\Android Studio\jbr\lib\fonts"

ROOT = Path(__file__).resolve().parent.parent
PROFILES = {"phone": (1080, 1920), "tablet-7": (1920, 1080), "tablet-10": (2560, 1440)}
RAW_SIZES = {"phone": (1080, 2400), "tablet-7": (1920, 1080), "tablet-10": (2560, 1440)}
SHOTS = [
    ("01-real-time-earth", "A real-time Earth", "Day and night, lit by the real Sun"),
    ("02-earth-events", "Explore Earth's events", "Open an observation to see its date, source and details"),
    ("03-day-or-night", "Discover day and night", "Meet each lesson once. Touch and hold to revisit it"),
    ("04-seasons", "Travel through the year", "Choose a date and explore Earth's changing seasons"),
    ("05-night-sky", "A pocket night sky", "Find the Moon, even when its surface is unlit"),
    ("06-languages-and-narration", "Your language, your pace", "12 languages and independent Talia and Android voice controls"),
    ("07-live-wallpaper", "Earth on your home screen", "Preview a live wallpaper that follows the real Sun"),
    ("08-today", "Your planet today", "The Moon, daylight at a saved place and recent observations"),
]


def starfield(width, height):
    """A quiet, reproducible brand background; only Pillow is required."""
    canvas = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(canvas)
    for y in range(height):
        glow = max(0, 1 - abs(y - height * 0.6) / height)
        color = tuple(round(base + amount * glow) for base, amount in [(4, 3), (8, 7), (18, 13)])
        draw.line((0, y, width, y), fill=color)
    rng = random.Random(7)
    for _ in range(240):
        x, y = rng.uniform(0, width), rng.uniform(0, height)
        brightness = rng.randint(35, 100)
        radius = rng.uniform(0.4, 1.3)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius),
                     fill=(brightness, brightness, min(255, brightness + 12)))
    return canvas


def default_fonts():
    candidates = [
        (Path(DEFAULT_FONTS) / "GoogleSansCode-Bold.ttf", Path(DEFAULT_FONTS) / "GoogleSansCode-Regular.ttf"),
        (Path("/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf"), Path("/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf")),
        (Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")),
    ]
    for pair in candidates:
        if all(path.is_file() for path in pair):
            return pair
    raise SystemExit("Pass --font-bold and --font-regular pointing to installed TrueType fonts")


def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) <= width:
            line = trial
        else:
            if line:
                lines.append(line)
            line = word
    return lines + [line]


def compose_phone(screen, caption, subtitle, fonts):
    width, height = PROFILES["phone"]
    top, shot_height, radius = 320, 1560, 40
    canvas = starfield(width, height).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    title_font = ImageFont.truetype(str(fonts[0]), 54)
    while draw.textlength(caption, font=title_font) > width - 100:
        title_font = ImageFont.truetype(str(fonts[0]), title_font.size - 2)
    sub_font = ImageFont.truetype(str(fonts[1]), 30)
    lines = wrap(draw, subtitle, sub_font, width - 140)
    assert len(lines) <= 3, "Caption exceeds the reserved header"
    draw.text((width / 2, 90), caption, font=title_font, fill=(255, 255, 255), anchor="mm")
    for index, line in enumerate(lines):
        draw.text((width / 2, 164 + index * 43), line, font=sub_font, fill=(190, 218, 255), anchor="mm")
    shot_width = round(screen.width * shot_height / screen.height)
    assert shot_width < width - 80
    resized = screen.resize((shot_width, shot_height), Image.Resampling.LANCZOS).convert("RGBA")
    mask = Image.new("L", resized.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot_width - 1, shot_height - 1), radius=radius, fill=255)
    resized.putalpha(mask)
    x = (width - shot_width) // 2
    glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle((x - 5, top - 5, x + shot_width + 5, top + shot_height + 5),
                                         radius=radius + 5, fill=(70, 130, 220, 85))
    canvas = Image.alpha_composite(canvas, glow.filter(ImageFilter.GaussianBlur(20)))
    canvas.alpha_composite(resized, (x, top))
    ImageDraw.Draw(canvas).rounded_rectangle((x, top, x + shot_width - 1, top + shot_height - 1),
                                            radius=radius, outline=(100, 145, 205), width=2)
    return canvas.convert("RGB")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw_root", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "store/screenshots")
    parser.add_argument("--font-bold", type=Path)
    parser.add_argument("--font-regular", type=Path)
    args = parser.parse_args()
    if bool(args.font_bold) != bool(args.font_regular):
        parser.error("Provide both font paths")
    fonts = (args.font_bold, args.font_regular) if args.font_bold else default_fonts()
    for profile, size in PROFILES.items():
        directory = args.output / profile
        directory.mkdir(parents=True, exist_ok=True)
        for name, caption, subtitle in SHOTS:
            with Image.open(args.raw_root / profile / (name + ".png")) as raw:
                assert raw.size == RAW_SIZES[profile], f"Unexpected capture dimensions: {profile}/{name}: {raw.size}"
                screen = raw.convert("RGB")
            output = compose_phone(screen, caption, subtitle, fonts) if profile == "phone" else screen
            assert output.size == size
            # Save a fresh RGB image; never carry device metadata or alpha into store assets.
            path = directory / (name + ".png")
            clean = Image.new("RGB", output.size)
            clean.paste(output)
            clean.save(path, optimize=True)
            print(f"{profile}/{path.name}: {size[0]}x{size[1]}, RGB PNG")


if __name__ == "__main__":
    main()
