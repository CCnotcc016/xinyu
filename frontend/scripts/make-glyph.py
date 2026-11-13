#!/usr/bin/env python3
"""
把「心」字渲染成 scripts/assets/xin-glyph.png（白字黑底蒙版）。

只在想换字形时跑一次，需要 Pillow：
    python3 -m venv /tmp/iconvenv && /tmp/iconvenv/bin/pip install pillow
    /tmp/iconvenv/bin/python scripts/make-glyph.py

之后 gen-android-assets.py 只读这张蒙版，不需要 Pillow。
"""

from PIL import Image, ImageDraw, ImageFont

FONT = "/System/Library/Fonts/PingFang.ttc"   # 换机器时改成系统里的中文字体
INDEX = 8        # 0/1/2 = Regular，3~5 = Medium，6~8 = Semibold（SC 是 2/5/8）
CHAR = "心"
SIZE = 860
STROKE = 9       # 稍微加粗，48px 的图标上笔画才不会糊成一根
OUT = "scripts/assets/xin-glyph.png"
CANVAS = 1024
PAD = 6


def main():
    font = ImageFont.truetype(FONT, SIZE, index=INDEX)
    img = Image.new("L", (1400, 1400), 0)
    ImageDraw.Draw(img).text((700, 700), CHAR, font=font, fill=255, anchor="mm",
                             stroke_width=STROKE, stroke_fill=255)

    x0, y0, x1, y1 = img.getbbox()
    glyph = img.crop((x0 - PAD, y0 - PAD, x1 + PAD, y1 + PAD))
    scale = CANVAS / max(glyph.size)
    glyph = glyph.resize((round(glyph.width * scale), round(glyph.height * scale)),
                         Image.LANCZOS)

    canvas = Image.new("RGB", (CANVAS, CANVAS), (0, 0, 0))
    canvas.paste(Image.merge("RGB", (glyph, glyph, glyph)),
                 ((CANVAS - glyph.width) // 2, (CANVAS - glyph.height) // 2))
    canvas.save(OUT)
    print("已写入", OUT, canvas.size)


if __name__ == "__main__":
    main()
