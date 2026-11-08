"""Generate 16x16 / 32x32 pixel-art PNGs for the Magic Induction mod without Pillow.

Pure stdlib: zlib + struct + manual PNG chunk assembly.
Palette is deliberately narrow (violet ramp + neutral stone) so the mod reads as one set.
"""

import os
import struct
import zlib

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(PROJECT_ROOT, "src", "main", "resources", "assets", "magicinduction", "textures")


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


def blank(size, color=(0, 0, 0, 0)):
    return [[list(color) for _ in range(size)] for _ in range(size)]


def noise(seed):
    """Deterministic pseudo random in [0,1) so regenerating never changes the art."""
    state = (seed * 1103515245 + 12345) & 0x7FFFFFFF
    return (state >> 8) % 1000 / 1000.0


def clamp(value):
    return max(0, min(255, int(value)))


# ---- palettes -------------------------------------------------------------
STONE_DARK = (104, 102, 110)
STONE_MID = (126, 124, 132)
STONE_LIGHT = (146, 144, 152)
VIOLET_DEEP = (46, 32, 74)
VIOLET_MID = (106, 62, 168)
VIOLET_BRIGHT = (168, 108, 232)
VIOLET_GLOW = (216, 176, 255)

# 紫罗兰配色：实体贴图、罐子图标、罐内蝴蝶共用一套，免得物品栏里和世界里两种颜色。
# 绿边和黑描边保留（那是翅形的骨架），只把翅面从靛蓝换成紫，斑点从橄榄黄换成淡紫白
# ——黄点配紫色对比太凶，三色撞在一起像贴纸；淡紫白点才和翅面是同一个色系。
BUTTERFLY_OUTLINE = (26, 20, 38)     # 不用纯黑：带一点紫的描边和紫翅面才是一家人
BUTTERFLY_PURPLE = (126, 84, 198)    # 翅面主色
BUTTERFLY_PURPLE_DARK = (100, 62, 168)   # 靠绿边一侧，收一档
BUTTERFLY_PURPLE_DEEP = (74, 44, 128)    # 翅根
BUTTERFLY_GREEN = (75, 161, 53)
BUTTERFLY_GREEN_DARK = (38, 107, 20)
BUTTERFLY_SPOT = (226, 208, 255)
BUTTERFLY_BODY_LIGHT = (121, 129, 132)
BUTTERFLY_BODY_GREY = (109, 114, 116)
BUTTERFLY_BODY_DARK = (52, 57, 66)
BUTTERFLY_HEAD = (122, 105, 61)
METAL_BASE = (44, 40, 58)
METAL_EDGE = (28, 25, 38)
METAL_HILITE = (78, 72, 98)
GLASS_LIGHT = (214, 232, 244)
GLASS_MID = (176, 200, 218)
GLASS_EDGE = (128, 156, 176)
GLASS_SHINE = (248, 253, 255)
CORK_DARK = (98, 68, 40)
CORK_MID = (140, 100, 60)
CORK_LIGHT = (172, 130, 84)
# 魔法树：形状、纹理方向、年轮结构、树叶孔洞分布全部照抄原版橡木，
# 只把色相换成紫罗兰。
#
# 取色方式是两个乘数：最终色 = TINT × (原版亮度 / BASE_LUMA)。
#   · 原版亮度是 0~35 的档位（等价于 0~255 的灰度），乘数天生 ≤1，不会把高光撑爆成粉色。
#   · BASE_LUMA 决定"这张图整体多亮"：数值越小越亮。
#   · 关键在于**同一张图内所有像素共用一个 BASE_LUMA**，于是原版的明暗比例被一比一保留。
#     不能改成"最暗拉到黑、最亮拉到白"的拉伸写法——原版树叶本身只有 14~26 的微弱
#     明暗差，一拉伸就会从"一整片、微微有层次"变成"一地碎宝石"。
BASE_LUMA_LOG = 26.0
BASE_LUMA_LEAF = 30.0
LOG_TINT = (150, 100, 196)
LEAF_TINT = (200, 136, 252)

# ── 原版贴图的亮度结构 ──
# 字符 0-9A-Z 表示 36 档亮度（0 最暗、Z 最亮），'.' 表示完全透明。
# 这几张表只记录"哪里暗、哪里亮、哪里是洞"，不含任何原版颜色，
# 生成时会被重新着色，所以成品里没有一像素来自原版贴图。
VANILLA_LOG_SIDE = """
D8GDD6GDD6DG8GDA
D8HAD6HAD6DG8DDA
D6AAD6HAD8D88DDA
G6DAD8DADAD68DGA
G8DAD6DAHADAD8GD
GD8DG8D8HAG8D8GD
AD8DGAADGDGDD8GD
AD6DGDAGDDGADAGD
D86HDDAGDAGAGAGD
DD8HDDADDAG8GD6D
DDDGDAD6DAD8GD6D
D8DGDAD6HDD6HD8D
H8DDGAG6HAD6DDAD
HD8DGDD8GAD8DDDD
GD8GDDG6D8D88GDD
GD8G8DG8D8DD8GDA
"""

VANILLA_LOG_TOP = """
A8ADA8AAD8AA8AAA
8LLLLKLKMKKLLLL8
ALEEGGIGIIIIGGMA
ALEMLLLLKLLLGLA
AKGLKKKKKKKKLIKA
8LILKGGIIGGKLGK8
AKGKKEKKKKGKKGLA
AKILKGKIIKIKLIKA
AKILKGKIGKGKLGKA
8KILKGKKKKIKKGK8
AKGLKGGEGIIKLIKA
AKILKKKKKKKKLGKA
8LILLKLLLLLMLGK8
ALIGIGGGGGGGGGLA
ALLLKKLKKMKKLLLA
A8AAA8AAD8AA8DAA
"""

VANILLA_LEAVES = """
EE.Q.LL.GGEL..QG
L...EEGL.LGL.QLQ
..GG.LLGQ.L..QQQ
.LGEE.QLQE....Q.
LGLG..LQ...ELL..
LLG..QGEEE..EGL.
GG.EQLQL.GG.LLGQ
GEE.QQQ.EEGL.QGL
QL.EEQLL.GLGL.LG
Q...EEEGL.LLL.QL
.LL.E.LLGQ.L...Q
QGEE...QLQ.GL...
GLL.LL..Q.EEGQ.Q
LQ.QGEE..E.LQGLQ
Q.QLQL..GG..QLQ.
L.QQQ..EEEG..Q.L
"""

VANILLA_SAPLING = """
................
...........F....
.......I..99....
....7.FI..7EC7..
....CC9F77EC99..
..IFI9C.7CC99I..
...IFCE9.CC.IFI.
......CC7C...II.
.....97CIF.79...
...9C99IFI9C97..
..F99C9CE7C979F.
.FFF.9ICC797.FF.
.....IF9CFI...F.
.....FI7CIF.....
.......9C7I.....
.......997......
"""

_LUMA_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def _parse_vanilla(pattern):
    """把亮度字符图解析成二维数组，透明格为 None。"""
    grid = []
    for line in pattern.strip().splitlines():
        line = line.strip()
        if line:
            grid.append([None if c == "." else _LUMA_CHARS.index(c) for c in line])
    return grid


def _vanilla_remap(pattern, tint, base_luma):
    """按原版亮度等比缩放 tint，形状与明暗对比完全照搬原版。"""
    grid = _parse_vanilla(pattern)
    art = blank(len(grid[0]), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, v in enumerate(row):
            if v is None:
                continue
            f = v / base_luma
            art[y][x] = [min(255, int(tint[k] * f + 0.5)) for k in range(3)] + [255]
    return art


def magic_ore():
    art = blank(16)
    for y in range(16):
        for x in range(16):
            roll = noise(x * 31 + y * 17 + 7)
            base = STONE_DARK if roll < 0.35 else (STONE_MID if roll < 0.8 else STONE_LIGHT)
            shift = clamp((noise(x * 7 + y * 13) - 0.5) * 10)
            art[y][x] = [clamp(base[0] + shift), clamp(base[1] + shift), clamp(base[2] + shift), 255]

    clusters = [(2, 3), (10, 2), (4, 10), (12, 9)]
    for cx, cy in clusters:
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            if 0 <= cx + dx < 16 and 0 <= cy + dy < 16:
                art[cy + dy][cx + dx] = list(VIOLET_DEEP) + [255]
        art[cy][cx] = list(VIOLET_BRIGHT) + [255]
        if cy + 1 < 16:
            art[cy + 1][cx] = list(VIOLET_MID) + [255]
        if cx + 1 < 16 and cy + 1 < 16:
            art[cy + 1][cx + 1] = list(VIOLET_MID) + [255]
        if cx + 1 < 16:
            art[cy][cx + 1] = list(VIOLET_GLOW) + [255]
    return art


# 侧面四个指示点的顺时针顺序：左上 → 右上 → 右下 → 左下。
# 前 4 只蝴蝶逐一点亮它们，第 5 只才点亮中间竖条——所以机器"满罐"是可读的。
INDICATOR_DOTS = ((4, 4), (11, 4), (11, 11), (4, 11))
DOT_OFF = (64, 50, 94)
DOT_HALO = (88, 54, 146)
# 中央核心：直径 8px 的圆环 + 圆心。半径取 2.7..4.0 是为了让环有厚度，
# 1px 细环在这个尺寸下会被像素网格拉成方块；环形有质量才读得出"圆"。
CORE_R_OUT = 4.0
CORE_R_IN = 2.7
CORE_R_CENTER = 1.5
CORE_OFF_RING = (56, 42, 90)
CORE_OFF_CENTER = (40, 30, 64)


def inducer_side(lit_dots=0, core_lit=False):
    """lit_dots 0..4 决定几个点亮着，core_lit 决定中央圆环核心是否点亮。"""
    art = blank(16, METAL_BASE + (255,))
    for y in range(16):
        for x in range(16):
            edge = y in (0, 15) or x in (0, 15)
            inner = y in (1, 14) or x in (1, 14)
            if edge:
                art[y][x] = list(METAL_EDGE) + [255]
            elif inner:
                art[y][x] = list(METAL_HILITE) + [255]
            elif noise(x * 11 + y * 5) < 0.18:
                art[y][x] = [clamp(METAL_BASE[0] + 8), clamp(METAL_BASE[1] + 8), clamp(METAL_BASE[2] + 10), 255]

    # 中央圆形核心：熄灭时是一圈凹陷的暗环 + 暗圆心，点亮时整环发光、圆心最亮。
    for y in range(4, 12):
        for x in range(4, 12):
            dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if dist > CORE_R_OUT:
                continue
            if dist >= CORE_R_IN:
                art[y][x] = list(VIOLET_BRIGHT if core_lit else CORE_OFF_RING) + [255]
            elif dist < CORE_R_CENTER:
                art[y][x] = list(VIOLET_GLOW if core_lit else CORE_OFF_CENTER) + [255]

    # 指示点：熄灭是 1px 暗槽，点亮是"中心发光 + 四向暗紫晕"，远看也能数清几个。
    for index, (cx, cy) in enumerate(INDICATOR_DOTS):
        if index < lit_dots:
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                art[cy + dy][cx + dx] = list(DOT_HALO) + [255]
            art[cy][cx] = list(VIOLET_GLOW) + [255]
        else:
            art[cy][cx] = list(DOT_OFF) + [255]
    return art


def inducer_top():
    art = blank(16, METAL_BASE + (255,))
    for y in range(16):
        for x in range(16):
            dist = max(abs(x - 7.5), abs(y - 7.5))
            if dist > 7.0:
                art[y][x] = list(METAL_EDGE) + [255]
            elif dist > 6.0:
                art[y][x] = list(METAL_HILITE) + [255]
    for y in range(16):
        for x in range(16):
            dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 2.0 <= dist <= 3.2:
                art[y][x] = list(VIOLET_BRIGHT) + [255]
            elif dist < 1.6:
                art[y][x] = list(VIOLET_GLOW) + [255]
    return art


def inducer_front():
    art = inducer_side()
    for y in range(4, 13):
        for x in range(4, 12):
            art[y][x] = list(METAL_EDGE) + [255]
    for y in range(5, 12):
        for x in range(5, 11):
            art[y][x] = list(VIOLET_DEEP) + [255]
    for y in range(6, 11):
        for x in range(6, 10):
            art[y][x] = list(VIOLET_MID) + [255]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (9, 9), (6, 9)):
        art[y][x] = list(VIOLET_BRIGHT) + [255]
    art[7][7] = list(VIOLET_GLOW) + [255]
    art[8][8] = list(VIOLET_GLOW) + [255]
    return art


def magic_crystal():
    art = blank(16)
    shape = {
        4: (7, 8),
        5: (6, 9),
        6: (6, 9),
        7: (5, 10),
        8: (5, 10),
        9: (5, 10),
        10: (6, 9),
        11: (7, 8),
    }
    for y, (x0, x1) in shape.items():
        for x in range(x0, x1 + 1):
            art[y][x] = list(VIOLET_MID) + [255]
    for y in range(4, 12):
        x0, x1 = shape[y]
        art[y][x0] = list(VIOLET_DEEP) + [255]
        art[y][x1] = list(VIOLET_DEEP) + [255]
    for y in range(6, 10):
        art[y][6] = list(VIOLET_BRIGHT) + [255]
    art[5][7] = list(VIOLET_GLOW) + [255]
    art[6][7] = list(VIOLET_GLOW) + [255]
    return art


def mana_dust():
    art = blank(16)
    mound = {
        6: (6, 9),
        7: (5, 10),
        8: (4, 11),
        9: (4, 11),
        10: (5, 10),
        11: (6, 9),
    }
    for y, (x0, x1) in mound.items():
        for x in range(x0, x1 + 1):
            roll = noise(x * 19 + y * 23 + 5)
            tone = VIOLET_MID if roll < 0.45 else (VIOLET_BRIGHT if roll < 0.85 else VIOLET_GLOW)
            art[y][x] = list(tone) + [255]
    for x in range(7, 9):
        art[5][x] = list(VIOLET_GLOW) + [255]
    for x, y in ((3, 12), (12, 12), (5, 13), (10, 13), (8, 14)):
        art[y][x] = list(VIOLET_BRIGHT) + [255]
    return art


def butterfly_jar():
    art = blank(16)
    glass = (208, 226, 240, 96)
    rim = (168, 196, 214, 220)
    for y in range(4, 13):
        for x in range(4, 12):
            art[y][x] = list(glass)
    for x in range(4, 12):
        art[3][x] = list(rim)
        art[13][x] = list(rim)
    for y in range(3, 14):
        art[y][4] = list(rim)
        art[y][11] = list(rim)
    art[3][6] = [248, 252, 255, 255]
    art[3][7] = [248, 252, 255, 255]
    art[14][7] = [200, 216, 226, 200]
    art[14][8] = [200, 216, 226, 200]

    # 罐里那只小蝴蝶：紫翅 + 绿边 + 深色身体，和实体贴图同一套配色，
    # 否则物品栏里和世界里会是两种颜色的生物。
    for x in (6, 9):
        art[6][x] = list(BUTTERFLY_GREEN) + [255]
        art[7][x] = list(BUTTERFLY_PURPLE) + [255]
        art[8][x] = list(BUTTERFLY_PURPLE_DARK) + [255]
    art[7][7] = list(BUTTERFLY_PURPLE) + [255]
    art[7][8] = list(BUTTERFLY_PURPLE_DARK) + [255]
    art[8][7] = list(BUTTERFLY_BODY_DARK) + [255]
    art[9][7] = list(BUTTERFLY_BODY_GREY) + [255]
    return art


def jar_glass():
    """3D 罐身的玻璃材质。

    刻意做成不透明（alpha 255）而不是半透明：物品模型的 alpha 只支持二值 cutout，
    真正的半透明需要单独的 translucent render layer，容易在物品栏里出现排序闪烁。
    不透明的"磨砂玻璃"在视觉上同样成立，而且绝不翻车。
    """
    art = blank(16)
    for y in range(16):
        for x in range(16):
            n = noise(x * 29 + y * 43 + 11)
            if y < 2 or y > 13:
                base = GLASS_EDGE
            elif n < 0.55:
                base = GLASS_MID
            else:
                base = GLASS_LIGHT
            art[y][x] = list(base) + [255]

    # 斜向高光带：玻璃最有辨识度的特征
    for step in range(14):
        x = 2 + step
        y = 13 - step
        if 0 <= x < 16 and 0 <= y < 16:
            art[y][x] = list(GLASS_SHINE) + [255]
        if 0 <= x + 1 < 16 and 0 <= y < 16:
            art[y][x + 1] = list(GLASS_SHINE) + [255]

    # 竖向细高光，避免大面积纯色
    for y in range(3, 12):
        art[y][3] = list(GLASS_SHINE) + [255]

    # 四条边压暗，让立方体轮廓在物品栏里能看清
    for i in range(16):
        art[0][i] = list(GLASS_EDGE) + [255]
        art[15][i] = list(GLASS_EDGE) + [255]
        art[i][0] = list(GLASS_EDGE) + [255]
        art[i][15] = list(GLASS_EDGE) + [255]
    return art


def jar_cork():
    """软木塞。横向的木纹纹理，两端压暗做出圆柱感。"""
    art = blank(16)
    for y in range(16):
        for x in range(16):
            n = noise(x * 17 + y * 61 + 5)
            base = CORK_MID if n < 0.5 else CORK_LIGHT
            if y % 4 == 0:
                base = CORK_DARK
            art[y][x] = list(base) + [255]
    for i in range(16):
        art[i][0] = list(CORK_DARK) + [255]
        art[i][15] = list(CORK_DARK) + [255]
        art[0][i] = list(CORK_DARK) + [255]
        art[15][i] = list(CORK_DARK) + [255]
    return art


def jar_contents(count):
    """罐内蝴蝶：0 只全透明，1-5 只逐个叠加。

    图案只有 3x2 像素——罐子在物品栏里本身就不大，再细的翅膀在屏幕上会糊成一团，
    所以这里用"两只翅膀 + 一条深色身体"的极简符号，只求数量可数。
    """
    art = blank(16)
    if count <= 0:
        return art

    # 位置必须落在薄片 UV 取样的区间 [4,12] x [1,9] 内，否则会画到贴图外面白丢
    spots = [(6, 2), (10, 3), (6, 5), (10, 6), (8, 8)]
    for index in range(min(count, len(spots))):
        cx, cy = spots[index]
        tone = BUTTERFLY_PURPLE if index % 2 == 0 else BUTTERFLY_PURPLE_DARK
        if 0 <= cy < 16 and 0 <= cy + 1 < 16:
            art[cy][cx - 1] = list(tone) + [255]
            art[cy][cx + 1] = list(tone) + [255]
            art[cy][cx] = list(BUTTERFLY_BODY_DARK) + [255]
            art[cy + 1][cx] = list(BUTTERFLY_BODY_GREY) + [255]
    return art


def _removed_butterfly_entity():
    """实体蝴蝶贴图以前在这里手画，现在改为抽取参考 mod 的贴图改色。

    原因：参考 mod 的贴图是按它自己的模型 UV 分块排的（左上 6x2 触角、
    两块 17x10 翅膀、底下 24x4 身体），手画的坐标必须和那套分块逐个像素对齐，
    改一次模型就要重画一次。改色则一个像素都不会错位。

    真正的实现在 tools/steal_butterfly.py，main() 末尾会调用它。
    """
    raise NotImplementedError("use tools/steal_butterfly.py")


def magic_log_side():
    """魔法原木侧面：完全沿用原版橡木的竖向木纹与两处树结，只换成紫罗兰色。

    上一版是自绘噪声木纹，问题在于噪声再怎么调都不像"木头的纹理逻辑"——
    原版木纹是每列一个稳定基调、列内几乎不变，所以远看是平行竖条。
    """
    return _vanilla_remap(VANILLA_LOG_SIDE, LOG_TINT, BASE_LUMA_LOG)


def magic_log_top():
    """魔法原木断面：沿用原版橡木的年轮结构与外圈树皮。"""
    return _vanilla_remap(VANILLA_LOG_TOP, LOG_TINT, BASE_LUMA_LOG)


def magic_leaves():
    """魔法树叶：沿用原版橡木树叶的团块分布与孔洞位置（约 1/4 是完全透明的）。

    孔洞不是缺陷——原版树叶靠它才能看到树冠内部，形成"密林"的层次感。
    自绘版本为了好看看着密实，反而丢掉了这个特性。
    """
    return _vanilla_remap(VANILLA_LEAVES, LEAF_TINT, BASE_LUMA_LEAF)


def magic_sapling():
    """魔法树苗：沿用原版橡木树苗的剪影（茎 + 左右两簇叶 + 深色描边）。"""
    return _vanilla_remap(VANILLA_SAPLING, LEAF_TINT, BASE_LUMA_LEAF)


def main():
    outputs = {
        os.path.join(ROOT, "block", "magic_ore.png"): magic_ore(),
        os.path.join(ROOT, "block", "magic_log.png"): magic_log_side(),
        os.path.join(ROOT, "block", "magic_log_top.png"): magic_log_top(),
        os.path.join(ROOT, "block", "magic_leaves.png"): magic_leaves(),
        os.path.join(ROOT, "block", "magic_sapling.png"): magic_sapling(),
        os.path.join(ROOT, "block", "mana_inducer_top.png"): inducer_top(),
        os.path.join(ROOT, "block", "mana_inducer_front.png"): inducer_front(),
        os.path.join(ROOT, "item", "magic_crystal.png"): magic_crystal(),
        os.path.join(ROOT, "item", "mana_dust.png"): mana_dust(),
        os.path.join(ROOT, "item", "jar_glass.png"): jar_glass(),
        os.path.join(ROOT, "item", "jar_cork.png"): jar_cork(),
    }
    # 诱导机器侧面的 6 档指示：0 = 全灭，1..4 = 顺时针点亮对应数量的点，5 = 四点全亮 + 竖条点亮。
    # 档位 0 沿用原文件名 mana_inducer_side.png，其余加 _N 后缀。
    for level in range(0, 6):
        name = "mana_inducer_side.png" if level == 0 else f"mana_inducer_side_{level}.png"
        outputs[os.path.join(ROOT, "block", name)] = inducer_side(min(level, 4), level >= 5)

    # 3D 罐子的 6 档内容贴图（0 = 空罐）
    for count in range(0, 6):
        outputs[os.path.join(ROOT, "item", f"jar_contents_{count}.png")] = jar_contents(count)

    for path, pixels in outputs.items():
        write_png(path, len(pixels[0]), len(pixels), pixels)
        print("wrote", path, len(pixels[0]), "x", len(pixels))

    # 实体蝴蝶贴图不在这里画：直接取参考 mod 的某只蝴蝶改色（tools/steal_butterfly.py）。
    # 它按参考 mod 的模型 UV 分块，手画容易错位，所以留成单独一步。
    import steal_butterfly
    steal_butterfly.generate()


if __name__ == "__main__":
    main()
