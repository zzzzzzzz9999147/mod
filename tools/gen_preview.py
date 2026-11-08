"""Stitch every texture into one upscaled contact sheet so the art can be eyeballed."""

import os
import struct
import zlib

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_ROOT = os.path.join(PROJECT_ROOT, "src", "main", "resources", "assets", "magicinduction", "textures")

FILES = [
    ("block", "magic_ore.png"),
    ("block", "mana_inducer_side.png"),
    ("block", "mana_inducer_top.png"),
    ("block", "mana_inducer_front.png"),
    ("item", "magic_crystal.png"),
    ("item", "mana_dust.png"),
    ("item", "butterfly_jar.png"),
    ("entity", "mana_butterfly.png"),
]

SCALE = 6
PAD = 8
COLS = 4


def read_png(path):
    data = open(path, "rb").read()
    pos, width, height, idat = 8, 0, 0, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height = struct.unpack(">II", payload[:8])
        elif tag == b"IDAT":
            idat += payload
    raw = zlib.decompress(idat)
    stride = width * 4
    rows = []
    for y in range(height):
        offset = y * (stride + 1) + 1
        rows.append([tuple(raw[offset + x * 4:offset + x * 4 + 4]) for x in range(width)])
    return width, height, rows


def write_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for color in row:
            raw.extend(bytes(color))

    def chunk(tag, payload):
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    open(path, "wb").write(blob)


def main():
    images = [read_png(os.path.join(TEX_ROOT, folder, name)) for folder, name in FILES]
    cell_w = max(image[0] for image in images) * SCALE
    cell_h = max(image[1] for image in images) * SCALE
    rows = (len(images) + COLS - 1) // COLS
    width = COLS * (cell_w + PAD) + PAD
    height = rows * (cell_h + PAD) + PAD

    canvas = [[(235, 233, 228, 255) for _ in range(width)] for _ in range(height)]
    for index, (img_w, img_h, pixels) in enumerate(images):
        origin_x = PAD + (index % COLS) * (cell_w + PAD)
        origin_y = PAD + (index // COLS) * (cell_h + PAD)
        for y in range(img_h * SCALE):
            for x in range(img_w * SCALE):
                r, g, b, a = pixels[y // SCALE][x // SCALE]
                if a < 8:
                    continue
                canvas[origin_y + y][origin_x + x] = (r, g, b, 255)

    out = os.path.join(PROJECT_ROOT, "preview_sheet.png")
    write_png(out, width, height, canvas)
    print("sheet", width, "x", height, "->", out)


if __name__ == "__main__":
    main()
