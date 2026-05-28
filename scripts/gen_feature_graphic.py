"""Generate the 1024x500 Play Store feature graphic.

Pure-black backdrop with the abstract hourglass on the left and the
``H O U R G L A S S`` wordmark + tagline on the right. Mirrors the
in-app visual: thin Roboto-like sans, wide letter spacing, no extra
chrome.
"""

from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
OUT = r"D:\Project\flux-hourglass-android\play_store\graphics\feature-graphic.png"

FONT_LIGHT = r"C:\Windows\Fonts\segoeuil.ttf"
FONT_REGULAR = r"C:\Windows\Fonts\segoeui.ttf"


def spaced(text: str, gap: str = " ") -> str:
    out = []
    for ch in text:
        out.append(ch)
        out.append(gap if ch != " " else "  ")
    return "".join(out).rstrip()


def draw_hourglass(draw: ImageDraw.ImageDraw, cx: int, cy: int, size: int) -> None:
    """Draw the abstract hourglass centered at (cx, cy) at given pixel size."""
    s = size / 108.0

    def p(x: float, y: float) -> tuple[float, float]:
        return (cx - size / 2 + x * s, cy - size / 2 + y * s)

    outline = [p(34, 30), p(74, 30), p(59, 54), p(74, 78), p(34, 78), p(49, 54)]
    draw.line(outline + [outline[0]], fill=(255, 255, 255, 255),
              width=int(round(3.5 * s)), joint="curve")

    upper = [p(40, 38), p(68, 38), p(54, 54)]
    draw.polygon(upper, fill=(255, 255, 255, 0xB3))

    lower = [p(36, 74), p(72, 74), p(64, 61), p(54, 66), p(44, 61)]
    draw.polygon(lower, fill=(255, 255, 255, 0xE6))


def main() -> None:
    img = Image.new("RGB", (W, H), (0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")

    draw_hourglass(draw, cx=200, cy=H // 2, size=240)

    eyebrow_font = ImageFont.truetype(FONT_REGULAR, 22)
    eyebrow = spaced("FLUX", gap="  ")
    eyebrow_b = draw.textbbox((0, 0), eyebrow, font=eyebrow_font)
    eyebrow_w = eyebrow_b[2] - eyebrow_b[0]

    title_font = ImageFont.truetype(FONT_LIGHT, 80)
    title = spaced("HOURGLASS", gap=" ")
    tb = draw.textbbox((0, 0), title, font=title_font)
    title_w = tb[2] - tb[0]
    title_h = tb[3] - tb[1]

    right_zone_x = 380
    right_zone_w = W - right_zone_x - 60
    title_x = right_zone_x + (right_zone_w - title_w) // 2
    title_y = H // 2 - title_h // 2 - 8
    draw.text((title_x, title_y), title, font=title_font,
              fill=(255, 255, 255, 255))

    eyebrow_x = title_x + (title_w - eyebrow_w) // 2
    eyebrow_y = title_y - 50
    draw.text((eyebrow_x, eyebrow_y), eyebrow, font=eyebrow_font,
              fill=(255, 255, 255, 0xC0))

    line_y = title_y + title_h + 12
    line_w = min(title_w, right_zone_w - 40)
    line_x = right_zone_x + (right_zone_w - line_w) // 2
    draw.line([(line_x, line_y), (line_x + line_w, line_y)],
              fill=(255, 255, 255, 0x55), width=1)

    tag_font = ImageFont.truetype(FONT_REGULAR, 20)
    tagline = spaced("PARTICLE PHYSICS TIMER", gap=" ")
    tagb = draw.textbbox((0, 0), tagline, font=tag_font)
    tag_w = tagb[2] - tagb[0]
    tag_x = right_zone_x + (right_zone_w - tag_w) // 2
    draw.text((tag_x, line_y + 18), tagline, font=tag_font,
              fill=(255, 255, 255, 0xB0))

    img.save(OUT, "PNG")
    print(f"wrote {OUT} ({W}x{H})")


if __name__ == "__main__":
    main()
