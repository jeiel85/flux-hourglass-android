"""Generate 512x512 Play Store icon from the launcher vector drawable.

Recreates the adaptive-icon foreground at high resolution on a pure black
canvas. Mirrors the path commands in
``app/src/main/res/drawable/ic_launcher_foreground.xml`` scaled from the
108x108 vector viewport to 512x512.
"""

from PIL import Image, ImageDraw

OUT = r"D:\Project\flux-hourglass-android\play_store\graphics\icon-512.png"
SIZE = 512
SCALE = SIZE / 108.0


def s(v: float) -> float:
    return v * SCALE


def main() -> None:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    draw = ImageDraw.Draw(img, "RGBA")

    # Outline: M 34,30 L 74,30 L 59,54 L 74,78 L 34,78 L 49,54 Z (stroke 3.5, white)
    outline = [(s(34), s(30)), (s(74), s(30)), (s(59), s(54)),
               (s(74), s(78)), (s(34), s(78)), (s(49), s(54))]
    draw.line(outline + [outline[0]], fill=(255, 255, 255, 255),
              width=int(round(s(3.5))), joint="curve")

    # Upper sand: M 40,38 L 68,38 L 54,54 Z (fill #B3FFFFFF ~ 70% white)
    upper = [(s(40), s(38)), (s(68), s(38)), (s(54), s(54))]
    draw.polygon(upper, fill=(255, 255, 255, 0xB3))

    # Lower pile: M 36,74 L 72,74 L 64,61 L 54,66 L 44,61 Z (fill #E6FFFFFF ~ 90% white)
    lower = [(s(36), s(74)), (s(72), s(74)), (s(64), s(61)),
             (s(54), s(66)), (s(44), s(61))]
    draw.polygon(lower, fill=(255, 255, 255, 0xE6))

    img.save(OUT, "PNG")
    print(f"wrote {OUT} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
