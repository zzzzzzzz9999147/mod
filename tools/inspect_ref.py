"""放大参考 mod 的实体贴图，供人眼/视觉模型审阅。

纯标准库：复用 gen_textures.write_png，自行解码 PNG（取样参考 mod 的 64x64 贴图）。
输出：把每张 64x64 放大 SCALE 倍，并叠加一层棋盘格表示透明区域，
      否则透明区在黑底上看不出边界。
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REF_DIR = os.path.join(ROOT, "tools", "ref_butterfly")
OUT_DIR = os.path.join(ROOT, "tools", "ref_preview")

SCALE = 8


def write_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for px in row:
            raw.extend(bytes(px))
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", compressed)
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(blob)


def read_png(path):
    data = open(path, "rb").read()
    pos, width, height, color_type, idat = 8, None, None, 6, b""
    palette = None
    trns = None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height = struct.unpack(">II", chunk[:8])
            bit_depth = chunk[8]
            color_type = chunk[9]
        elif tag == b"PLTE":
            palette = chunk
        elif tag == b"tRNS":
            trns = chunk
        elif tag == b"IDAT":
            idat += chunk

    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    stride = width * channels
    prev = bytearray(stride)
    out = []

    for y in range(height):
        offset = y * (stride + 1)
        filt = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        if filt == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        prev = line

        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if color_type == 6:
                row.append((px[0], px[1], px[2], px[3]))
            elif color_type == 2:
                row.append((px[0], px[1], px[2], 255))
            elif color_type == 3:
                idx = px[0]
                r, g, b = palette[idx * 3:idx * 3 + 3]
                a = trns[idx] if trns and idx < len(trns) else 255
                row.append((r, g, b, a))
            else:
                row.append((px[0], px[0], px[0], 255))
        out.append(row)

    return width, height, out


def checker(x, y):
    return (58, 58, 64, 255) if ((x // 4 + y // 4) % 2 == 0) else (40, 40, 46, 255)


def preview(name):
    src = os.path.join(REF_DIR, name)
    if not os.path.exists(src):
        print("skip missing", src)
        return
    w, h, px = read_png(src)
    ow, oh = w * SCALE, h * SCALE
    canvas = []
    for y in range(oh):
        row = []
        for x in range(ow):
            r, g, b, a = px[y // SCALE][x // SCALE]
            if a < 8:
                row.append(checker(x, y))
            else:
                # 预乘黑底，半透明也能看出层次
                alpha = a / 255.0
                k = alpha
                row.append((int(r * k), int(g * k), int(b * k), 255))
        canvas.append(row)
    out = os.path.join(OUT_DIR, os.path.splitext(name)[0] + f"_{SCALE}x.png")
    write_png(out, ow, oh, canvas)
    opaque = sum(1 for row in px for p in row if p[3] > 8)
    print(f"{name}: {w}x{h}  不透明像素 {opaque} ({opaque * 100 // (w * h)}%)  -> {out}")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in (
        "butterfly.png",
        "butterfly_bluemoon.png",
        "butterfly_monarch.png",
        "butterfly_swallowtail.png",
        "butterfly_admiral.png",
        "butterfly_cabbage.png",
    ):
        preview(name)


if __name__ == "__main__":
    main()
