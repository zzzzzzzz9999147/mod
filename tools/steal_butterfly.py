"""把参考 mod 里某只蝴蝶的贴图换成紫色，另存成我们的实体贴图。

为什么要"偷"而不是照着画：参考 mod 的贴图是按它自己的模型 UV 排的——
左上 6x2 是触角，接着两块 17x10 是左右两片翅膀（各含前翅后翅），
底下 24x4 是身体。照着画很容易对不上 UV 分块，直接改色则一个像素都不会错位。

做法：只动翅膀区（第 0~19 行），把亮度映射到一条紫色渐变上，
暗处的翅脉、翅缘、白斑全部按原来的明暗关系保留；
身体区（第 20~23 行）原样保留，灰褐色的身体配紫翅膀才分得清头尾。

用法：
    python tools/steal_butterfly.py <物种名> [输出路径]
物种名对应参考 mod 里的 butterfly_<物种名>.png。
"""

import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import preview_model_render as R      # 复用 read_png / write_png

REF_JAR = os.path.join(
    os.path.expanduser("~"), "Desktop", ".minecraft", "versions",
    "1.20.1-Forge_47.4.20 www", "mods", "butterflies-7.10.12.jar")
REF_ENTRY = "assets/butterflies/textures/entity/butterfly/butterfly_%s.png"

OUT = os.path.join(ROOT, "src", "main", "resources", "assets",
                   "magicinduction", "textures", "entity", "mana_butterfly.png")

# 紫色渐变：亮度 0 -> 255 依次经过这几个色标（线性插值）。
# 起点带一点蓝紫而不是纯黑，暗部在游戏里才不会糊成一块。
GRADIENT = (
    (0.00, (24, 16, 38)),
    (0.18, (56, 32, 96)),
    (0.40, (104, 64, 172)),
    (0.62, (140, 96, 214)),
    (0.82, (176, 142, 238)),
    (1.00, (228, 212, 255)),
)

WING_ROWS = 20      # 第 0~19 行是翅膀（含触角），第 20 行起是身体


def luma(px):
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) / 255000.0


def ramp(t):
    """在 GRADIENT 上按 t(0..1) 取色。"""
    for i in range(len(GRADIENT) - 1):
        t0, c0 = GRADIENT[i]
        t1, c1 = GRADIENT[i + 1]
        if t <= t1:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            k = max(0.0, min(1.0, k))
            return tuple(round(c0[j] + (c1[j] - c0[j]) * k) for j in range(3))
    return GRADIENT[-1][1]


def generate(species="admiral", out=OUT):
    """把参考 mod 的某只蝴蝶改色后写成我们的实体贴图，返回输出路径。"""
    with zipfile.ZipFile(REF_JAR) as z:
        blob = z.read(REF_ENTRY % species)
    tmp = os.path.join(HERE, "_ref_tmp.png")
    open(tmp, "wb").write(blob)
    w, h, px = R.read_png(tmp)
    os.remove(tmp)

    changed = 0
    for y in range(min(WING_ROWS, h)):
        for x in range(w):
            c = px[y][x]
            if c[3] < 8:
                continue
            r, g, b = ramp(luma(c))
            px[y][x] = (r, g, b, c[3])
            changed += 1

    R.write_png(out, w, h, px)
    print(f"{species}: {w}x{h}, recoloured {changed} px -> {out}")
    return out


def main():
    species = sys.argv[1] if len(sys.argv) > 1 else "admiral"
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    generate(species, out)


if __name__ == "__main__":
    main()
