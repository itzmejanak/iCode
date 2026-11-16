#!/usr/bin/env python3
"""Build two labeled review sheets from the captured route screenshots."""

from pathlib import Path
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as error:
    raise SystemExit("Pillow is required to build screenshot contact sheets.") from error


def load_font(size: int):
    font_path = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default()


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("artifacts/screens")
    destination = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("artifacts/review")
    screenshots = sorted(source.glob("*.png"))
    # 40 design frames, 39 routes: frame 3:55 "Runtime error" is the failed state
    # of 3:27 and has no route of its own (docs/design-and-navigation.md).
    expected = 39
    if len(screenshots) != expected:
        raise SystemExit(f"Expected {expected} screenshots in {source}, found {len(screenshots)}.")

    destination.mkdir(parents=True, exist_ok=True)
    with Image.open(screenshots[0]) as first:
        thumb_width = 248
        thumb_height = round(thumb_width * first.height / first.width)

    columns = 4
    rows = 5
    label_height = 32
    gutter = 12
    canvas_size = (
        columns * thumb_width + (columns + 1) * gutter,
        rows * (thumb_height + label_height) + (rows + 1) * gutter,
    )
    font = load_font(17)

    for sheet_index in range(2):
        canvas = Image.new("RGB", canvas_size, "#0a0d0b")
        draw = ImageDraw.Draw(canvas)
        subset = screenshots[sheet_index * 20 : (sheet_index + 1) * 20]
        for index, screenshot in enumerate(subset):
            with Image.open(screenshot) as original:
                image = original.convert("RGB")
            image.thumbnail((thumb_width, thumb_height), Image.Resampling.LANCZOS)
            column = index % columns
            row = index // columns
            x = gutter + column * (thumb_width + gutter)
            y = gutter + row * (thumb_height + label_height + gutter)
            canvas.paste(image, (x, y))
            draw.text((x, y + thumb_height + 6), screenshot.stem, fill="#e9efe7", font=font)

        target = destination / f"contact-sheet-{sheet_index + 1}.png"
        canvas.save(target, optimize=True)
        print(target)


if __name__ == "__main__":
    main()
