#!/usr/bin/env python3
"""
生成 Android 的启动图标和启动图。

默认使用「第一次打包」那套美术（白底圆角卡 + 蓝色「心」，见
scripts/assets/legacy/），脚本只负责把它按各密度拷贝 / 缩放到位；
加 --style gradient 则生成后改的青绿渐变版本（需 scripts/assets/xin-glyph.png）。

用法（在 frontend/ 下）：
    python3 scripts/gen-android-assets.py                # 复古白底蓝心（默认）
    python3 scripts/gen-android-assets.py --style gradient

写入 android/app/src/main/res/：
    mipmap-*/ic_launcher.png             传统方形图标（API 22~25 用）
    mipmap-*/ic_launcher_round.png       圆形图标
    mipmap-*/ic_launcher_foreground.png  自适应图标前景（API 26+）
    drawable*/splash.png                 启动图
以及 public/icons/ 下的网页 / PWA 图标。
"""

import os
import shutil
import struct
import subprocess
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GLYPH_PNG = os.path.join(ROOT, "scripts/assets/xin-glyph.png")
LEGACY = os.path.join(ROOT, "scripts/assets/legacy")
RES = os.path.join(ROOT, "android/app/src/main/res")
WEB_ICONS = os.path.join(ROOT, "public/icons")

ICON_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SPLASH_SIZES = {"mdpi": (320, 480), "hdpi": (480, 800), "xhdpi": (720, 1280),
                "xxhdpi": (960, 1600), "xxxhdpi": (1280, 1920)}
WEB_ICON_FILES = ("icon-512.png", "icon-192.png", "icon-maskable-512.png",
                  "apple-touch-icon.png")

NIGHT_TOP = (7, 28, 46)      # --xinyu-night-1
NIGHT_BOTTOM = (4, 14, 21)   # --xinyu-night-3

# 图标底色：左上偏亮青 → 中间品牌色 → 右下深青，和 App 里的极光渐变同色系
GRAD_START = (56, 220, 200)
GRAD_MID = (13, 148, 136)    # --xinyu-accent
GRAD_END = (8, 88, 82)
SHADOW_RGB = (4, 52, 48)


# ---------- PNG 读 / 写（借 sips 转 BMP，绕开 Pillow 依赖） ----------

def read_png_rgb(path):
    bmp = "/tmp/xinyu-icon.bmp"
    subprocess.run(["sips", "-s", "format", "bmp", path, "--out", bmp],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    data = open(bmp, "rb").read()
    offset = struct.unpack("<I", data[10:14])[0]
    width, height = struct.unpack("<ii", data[18:26])
    bpp = struct.unpack("<H", data[28:30])[0]
    stride = (width * bpp // 8 + 3) // 4 * 4
    bottom_up = height > 0
    height = abs(height)
    rows = []
    for y in range(height):
        base = offset + (height - 1 - y if bottom_up else y) * stride
        rows.append([(data[base + x * (bpp // 8) + 2],
                      data[base + x * (bpp // 8) + 1],
                      data[base + x * (bpp // 8)]) for x in range(width)])
    return width, height, rows


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(tag, payload):
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def shrink(src, dst_w, dst_h, dst):
    """统一走 sips，缩放质量比手写双线性好，也不用在这里折腾插值"""
    subprocess.run(["sips", "-z", str(dst_h), str(dst_w), src, "--out", dst],
                   check=True, stdout=subprocess.DEVNULL)


# ---------- 字形提取 ----------

def read_mask(path):
    """白字黑底的 png → 0~1 的蒙版矩阵；白色是不透明，黑是透明"""
    width, height, rows = read_png_rgb(path)
    return width, height, [[(0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                            for (r, g, b) in line] for line in rows]


def blur(mask, radius):
    """两次盒式模糊近似高斯，够用来做投影，也不慢"""
    r = int(radius)
    if r < 1:
        return mask
    out = mask
    for _ in range(2):
        out = _box_blur(out, r)
    return out


def _box_blur(mask, r):
    h, w = len(mask), len(mask[0])
    win = 2 * r + 1
    tmp = []
    for line in mask:
        row = [0.0] * w
        acc = sum(line[:r + 1])
        for x in range(w):
            if x + r < w:
                acc += line[x + r]
            if x - r - 1 >= 0:
                acc -= line[x - r - 1]
            row[x] = acc / win
        tmp.append(row)
    cols = []
    for x in range(w):
        col = [tmp[y][x] for y in range(h)]
        res = [0.0] * h
        acc = sum(col[:r + 1])
        for y in range(h):
            if y + r < h:
                acc += col[y + r]
            if y - r - 1 >= 0:
                acc -= col[y - r - 1]
            res[y] = acc / win
        cols.append(res)
    return [[cols[x][y] for x in range(w)] for y in range(h)]


def bbox(mask):
    ys = [y for y, line in enumerate(mask) if any(v > 0.2 for v in line)]
    xs = [x for x in range(len(mask[0])) if any(mask[y][x] > 0.2 for y in ys)]
    return xs[0], ys[0], xs[-1], ys[-1]


def sample(mask, sx, sy):
    h, w = len(mask), len(mask[0])
    x0 = max(0, min(w - 1, int(sx)))
    y0 = max(0, min(h - 1, int(sy)))
    x1, y1 = min(w - 1, x0 + 1), min(h - 1, y0 + 1)
    fx = max(0.0, min(1.0, sx - x0))
    fy = max(0.0, min(1.0, sy - y0))
    top = mask[y0][x0] * (1 - fx) + mask[y0][x1] * fx
    bot = mask[y1][x0] * (1 - fx) + mask[y1][x1] * fx
    return top * (1 - fy) + bot * fy


class Glyph:
    """把字形摆到目标画布里，按需采样"""

    def __init__(self, mask, box_w, box_h, center_x, center_y, radius=0):
        self.mask = mask
        self.x0, self.y0, x1, y1 = bbox(mask)
        gw, gh = x1 - self.x0 + 1, y1 - self.y0 + 1
        self.scale = min(box_w / gw, box_h / gh)
        self.off_x = center_x - gw * self.scale / 2
        self.off_y = center_y - gh * self.scale / 2
        self.box_w, self.box_h = box_w, box_h
        self.cx, self.cy = center_x, center_y
        self.radius = radius

    def alpha_at(self, x, y):
        if abs(x - self.cx) > self.box_w / 2 + 1 or abs(y - self.cy) > self.box_h / 2 + 1:
            return 0.0
        return sample(self.mask, self.x0 + (x - self.off_x) / self.scale,
                      self.y0 + (y - self.off_y) / self.scale)


def gradient_at(x, y, size):
    """45° 渐变 + 左上柔光 + 右下压暗，让纯色块看起来有厚度"""
    t = (x + y) / (2.0 * (size - 1))
    if t < 0.55:
        u = t / 0.55
        c0, c1 = GRAD_START, GRAD_MID
    else:
        u = (t - 0.55) / 0.45
        c0, c1 = GRAD_MID, GRAD_END
    r = c0[0] + (c1[0] - c0[0]) * u
    g = c0[1] + (c1[1] - c0[1]) * u
    b = c0[2] + (c1[2] - c0[2]) * u

    # 左上 26% 处的柔光
    dx = (x - size * 0.26) / (size * 0.62)
    dy = (y - size * 0.16) / (size * 0.62)
    glow = max(0.0, 1.0 - (dx * dx + dy * dy)) ** 2 * 0.22
    r += (255 - r) * glow
    g += (255 - g) * glow
    b += (255 - b) * glow

    # 右下角轻微压暗，避免整体发灰
    shade = max(0.0, ((x + y) / (2.0 * (size - 1)) - 0.62) / 0.38) ** 2 * 0.18
    r *= 1 - shade
    g *= 1 - shade
    b *= 1 - shade
    return r, g, b


def inside_round_rect(x, y, size, radius):
    if not radius:
        return True
    if not (x < radius or x > size - radius) or not (y < radius or y > size - radius):
        return True
    cx = min(max(x, radius), size - radius)
    cy = min(max(y, radius), size - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2


def icon_rows(size, glyph, bg=True, corner=0.0, shadow=0.30):
    """渐变底 + 白字 + 柔和投影；bg=False 时输出透明底的纯字形（自适应图标前景）"""
    alpha = [[glyph.alpha_at(x, y) for x in range(size)] for y in range(size)]
    drop = blur([[glyph.alpha_at(x, y - size * 0.012) for x in range(size)]
                 for y in range(size)], max(1, size * 0.022))

    rows = []
    radius = size * corner
    for y in range(size):
        line = bytearray()
        for x in range(size):
            a = alpha[y][x]
            if not bg:
                line += bytes((255, 255, 255, round(255 * a)))
                continue
            if not inside_round_rect(x, y, size, radius):
                line += b"\x00\x00\x00\x00"
                continue
            r, g, b = gradient_at(x, y, size)
            s = drop[y][x] * shadow
            if s:
                r += (SHADOW_RGB[0] - r) * s
                g += (SHADOW_RGB[1] - g) * s
                b += (SHADOW_RGB[2] - b) * s
            if a:
                r += (255 - r) * a
                g += (255 - g) * a
                b += (255 - b) * a
            line += bytes((round(r), round(g), round(b), 255))
        rows.append(line)
    return rows


def splash_rows(width, height, logo, logo_w, logo_h):
    """深色竖向渐变 + 居中图标（仿 Apple 启动图：只有底色和 logo，没有文字）"""
    left, top = (width - logo_w) // 2, (height - logo_h) // 2
    rows = []
    for y in range(height):
        t = y / max(1, height - 1)
        # 统一 RGBA：logo 行是 4 字节/像素，底色也必须是 4 字节，否则整行错位
        base = bytes(round(NIGHT_TOP[i] * (1 - t) + NIGHT_BOTTOM[i] * t)
                     for i in range(3)) + b"\xff"
        if top <= y < top + logo_h:
            logo_y = y - top
            line = bytearray(base * left)
            line += logo[logo_y]
            line += base * (width - left - logo_w)
        else:
            line = bytearray(base * width)
        rows.append(line)
    return rows


def apply_legacy():
    """第一次打包那套：白底圆角卡 + 蓝色「心」。素材原样来自当年的 APK，别再画一遍"""
    splash_port = os.path.join(LEGACY, "splash_port_1280x1920.png")
    splash_land = os.path.join(LEGACY, "splash_land_1920x1280.png")
    # 圆角的 mdpi 版当年就没生成对（res 里是 162×162），统一从 192 缩下来
    round_master = os.path.join(LEGACY, "ic_launcher_round_192.png")

    for dpi, size in ICON_SIZES.items():
        shutil.copyfile(os.path.join(LEGACY, f"ic_launcher_{size}.png"),
                        os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.png"))
        shrink(round_master, size, size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_round.png"))
        shutil.copyfile(os.path.join(LEGACY, f"ic_launcher_foreground_{FG_SIZES[dpi]}.png"),
                        os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_foreground.png"))

    for dpi, (w, h) in SPLASH_SIZES.items():
        shrink(splash_port, w, h, os.path.join(RES, f"drawable-port-{dpi}", "splash.png"))
        shrink(splash_land, h, w, os.path.join(RES, f"drawable-land-{dpi}", "splash.png"))
    shrink(splash_port, 320, 480, os.path.join(RES, "drawable/splash.png"))

    for name in WEB_ICON_FILES:
        shutil.copyfile(os.path.join(LEGACY, "web", name), os.path.join(WEB_ICONS, name))

    print("图标与启动图已恢复为第一次打包的样式（白底 + 蓝色「心」）")


def apply_gradient():
    width, height, mask = read_mask(GLYPH_PNG)
    print("字形蒙版", width, "×", height, "字形框", bbox(mask))

    # ---- 启动图 ----
    logo_size = 320
    logo_glyph = Glyph(mask, logo_size * 0.56, logo_size * 0.56, logo_size / 2, logo_size / 2)
    logo = icon_rows(logo_size, logo_glyph, corner=0.24, shadow=0.22)

    port = splash_rows(1280, 1920, logo, logo_size, logo_size)
    land = splash_rows(1920, 1280, logo, logo_size, logo_size)
    tmp_port, tmp_land = "/tmp/xinyu-splash-port.png", "/tmp/xinyu-splash-land.png"
    write_png(tmp_port, 1280, 1920, port)
    write_png(tmp_land, 1920, 1280, land)

    densities = {"mdpi": (320, 480), "hdpi": (480, 800), "xhdpi": (720, 1280),
                 "xxhdpi": (960, 1600), "xxxhdpi": (1280, 1920)}
    for dpi, (w, h) in densities.items():
        shrink(tmp_port, w, h, os.path.join(RES, f"drawable-port-{dpi}", "splash.png"))
        shrink(tmp_land, h, w, os.path.join(RES, f"drawable-land-{dpi}", "splash.png"))
    shrink(tmp_port, 320, 480, os.path.join(RES, "drawable/splash.png"))

    # ---- 传统图标（方形 / 圆形） ----
    legacy_glyph = Glyph(mask, 192 * 0.56, 192 * 0.56, 96, 96)
    legacy = icon_rows(192, legacy_glyph, corner=0.22)
    tmp_legacy = "/tmp/xinyu-legacy.png"
    write_png(tmp_legacy, 192, 192, legacy)

    round_rows = []
    for y in range(192):
        line = bytearray()
        for x in range(192):
            if (x - 96) ** 2 + (y - 96) ** 2 > 92 ** 2:
                line += b"\x00\x00\x00\x00"
            else:
                line += legacy[y][x * 4:x * 4 + 4]
        round_rows.append(line)
    tmp_round = "/tmp/xinyu-round.png"
    write_png(tmp_round, 192, 192, round_rows)

    icon_sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, size in icon_sizes.items():
        shrink(tmp_legacy, size, size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.png"))
        shrink(tmp_round, size, size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_round.png"))

    # ---- 自适应图标前景：透明底纯字形，落在 66% 安全区内 ----
    # 背景层是 drawable/ic_launcher_background.xml 里的同款渐变
    fg_glyph = Glyph(mask, 432 * 0.46, 432 * 0.46, 216, 216)
    fg = icon_rows(432, fg_glyph, bg=False)
    tmp_fg = "/tmp/xinyu-fg.png"
    write_png(tmp_fg, 432, 432, fg)

    fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for dpi, size in fg_sizes.items():
        shrink(tmp_fg, size, size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_foreground.png"))

    # ---- 网页 / PWA 图标：同一套视觉，浏览器标签页和「添加到主屏幕」才对得上 ----
    web = icon_rows(512, Glyph(mask, 512 * 0.56, 512 * 0.56, 256, 256), corner=0.22)
    write_png(os.path.join(WEB_ICONS, "icon-512.png"), 512, 512, web)
    shrink(os.path.join(WEB_ICONS, "icon-512.png"), 192, 192,
           os.path.join(WEB_ICONS, "icon-192.png"))

    # iOS 自己会裁圆角，所以给满幅方图
    apple = icon_rows(180, Glyph(mask, 180 * 0.56, 180 * 0.56, 90, 90))
    write_png(os.path.join(WEB_ICONS, "apple-touch-icon.png"), 180, 180, apple)

    # maskable：字形缩到 80% 安全圆里，四角不留透明
    maskable = icon_rows(512, Glyph(mask, 512 * 0.40, 512 * 0.40, 256, 256))
    write_png(os.path.join(WEB_ICONS, "icon-maskable-512.png"), 512, 512, maskable)

    print("图标与启动图已生成（青绿渐变）")


def main():
    style = "legacy"
    if "--style" in sys.argv:
        style = sys.argv[sys.argv.index("--style") + 1]
    if style == "legacy":
        apply_legacy()
    elif style == "gradient":
        apply_gradient()
    else:
        raise SystemExit(f"未知样式：{style}（可选 legacy / gradient）")


if __name__ == "__main__":
    main()
