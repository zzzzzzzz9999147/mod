"""从原版客户端 jar 里取橡木木板 / 木棍 / 五件木制工具的**亮度结构**，重新着色成魔法木。

只取亮度、不取像素：每张图先算 luma，再用 `TINT x (luma / BASE)` 重新上色。
这和 gen_textures.py 里 magic_log / magic_leaves 是同一套做法（那几张 VANILLA_* 亮度表
也是这么来的），所以成品里没有任何一像素来自原版贴图。

为什么不自己画：
  · 16x16 的镐 / 斧 / 锹是剪影占主导的图形，手画的辨识度远不如原版；
  · 五件工具 + 木棍 + 木板还要"看起来是同一块木头"，共用一套亮度→配色映射最省事；
  · 用户要的就是"像原版一样的木头"，那原版的明暗结构本身就是需求。

配色为什么所有图共用一个 BASE：
  原版木制工具（最亮 luma≈105）本来就比木板（最亮 161）暗一档，共用 BASE 才保得住
  这个相对关系——各图各自归一化的话，深色的剑会亮得像在发光。

用法：
    python tools/gen_wood_textures.py [client.jar 路径]
不给路径时依次尝试：
    %MC_CLIENT_JAR%  ->  ~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar
"""

import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from gen_textures import ROOT, write_png   # 复用同一个 PNG 写出函数

# ---- 魔法木配色 ------------------------------------------------------------
# 最亮一档：比魔法原木侧面（LOG_TINT 150,100,196）亮一点，木板的观感本来就比树皮浅。
WOOD_BRIGHT = (172, 122, 214)
# 原版橡木木板最亮像素的 luma。五张图共用，保住"工具比木板暗"这个原版关系。
BASE_LUMA = 161.0
# 最暗一档的底噪：纯线性映射会把描边压成近黑（24/161 ≈ 0.15），
# 抬到 0.12 之后描边是"带紫的深色"而不是黑边，和 BUTTERFLY_OUTLINE 的设计一致
# （那个描边也刻意不用纯黑）。再高就会把原版的明暗对比抹平。
SHADOW_FLOOR = 0.12

# 原版贴图 -> 我们的文件名
SOURCES = {
    "block/oak_planks": "block/magic_planks",
    "item/stick": "item/magic_stick",
    "item/wooden_sword": "item/magic_sword",
    "item/wooden_pickaxe": "item/magic_pickaxe",
    "item/wooden_axe": "item/magic_axe",
    "item/wooden_shovel": "item/magic_shovel",
    "item/wooden_hoe": "item/magic_hoe",
}

DEFAULT_JAR = os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar")


# ------------------------------------------------------------------ png 读取
def read_png(data):
    """解 8bit 的灰度 / 调色板 / RGB / RGBA（原版贴图这两种都有，pickaxe 和 hoe 就是调色板）。"""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("不是 PNG")
    pos, idat, palette, trns = 8, b"", [], b""
    width = height = depth = color_type = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, color_type = (*struct.unpack(">II", chunk[:8]), chunk[8], chunk[9])
        elif tag == b"PLTE":
            palette = [(chunk[i], chunk[i + 1], chunk[i + 2]) for i in range(0, len(chunk), 3)]
        elif tag == b"tRNS":
            trns = chunk
        elif tag == b"IDAT":
            idat += chunk
    if depth != 8:
        raise ValueError(f"只支持 8bit，遇到 {depth}bit")

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    stride = width * channels
    raw = zlib.decompress(idat)
    prev = bytearray(stride)
    rows = []
    for y in range(height):
        off = y * (stride + 1)
        filt = raw[off]
        line = bytearray(raw[off + 1:off + 1 + stride])
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filt == 1:
                line[i] = (line[i] + a) & 0xFF
            elif filt == 2:
                line[i] = (line[i] + b) & 0xFF
            elif filt == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if (pa <= pb and pa <= pc) else (b if pb <= pc else c))) & 0xFF
        prev = line

        row = []
        for x in range(width):
            if color_type == 6:
                r, g, b, a = line[x * 4:x * 4 + 4]
            elif color_type == 2:
                r, g, b = line[x * 3:x * 3 + 3]
                a = 255
            elif color_type == 3:
                index = line[x]
                r, g, b = palette[index]
                a = trns[index] if index < len(trns) else 255
            elif color_type == 0:
                r = g = b = line[x]
                a = 255
            else:                                   # 灰度 + alpha
                r = g = b = line[x * 2]
                a = line[x * 2 + 1]
            row.append((r, g, b, a))
        rows.append(row)
    return width, height, rows


# ------------------------------------------------------------------ 重新上色
def luma(r, g, b):
    return (299 * r + 587 * g + 114 * b) // 1000


def retint(rows):
    """alpha < 128 判透明（cutout），其余按亮度乘以 WOOD_BRIGHT。"""
    art = []
    for row in rows:
        out = []
        for r, g, b, a in row:
            if a < 128:
                out.append([0, 0, 0, 0])
                continue
            f = SHADOW_FLOOR + (1.0 - SHADOW_FLOOR) * min(1.0, luma(r, g, b) / BASE_LUMA)
            out.append([min(255, int(c * f + 0.5)) for c in WOOD_BRIGHT] + [255])
        art.append(out)
    return art


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("MC_CLIENT_JAR", DEFAULT_JAR)
    if not os.path.isfile(jar):
        raise SystemExit(
            "找不到原版客户端 jar，请在参数里给路径：\n"
            "    python tools/gen_wood_textures.py <client.jar>\n"
            f"尝试过: {jar}")

    archive = zipfile.ZipFile(jar)
    for source, target in SOURCES.items():
        data = archive.read(f"assets/minecraft/textures/{source}.png")
        width, height, rows = read_png(data)
        art = retint(rows)
        path = os.path.join(ROOT, *target.split("/")) + ".png"
        write_png(path, width, height, art)
        print("wrote", path, f"{width}x{height}")


if __name__ == "__main__":
    main()
