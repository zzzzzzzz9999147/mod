#!/usr/bin/env python3
"""生成「吃过魔法蛋糕的烈焰人燃烧室」用的紫色火焰贴图。

## 为什么不是直接给 Create 的 blaze_burner_flame 换个色

Create 的火焰贴图只有 16×16、单帧、5 色，火苗轮廓是一个「底宽顶窄」的梯形色块。
换色调之后仍然是个紫色梯形——玩家会觉得「火焰被染了色」，而不是「变成了另一种火」。

用户要的是「参照原版火焰」。原版 fire_0.png 是 16×16×32 帧的动画，火苗有：
  - 顶部三簇高低不齐的尖角（16 帧一轮回地独立跳动）
  - 底部铺满整行、往上收成一条细颈
  - 内部有 4 级明度（暗红 → 褐 → 橙 → 亮黄白），不是平涂

所以这里直接从原版 fire_0 / fire_1 取像素，把整条「色带」映射到紫色。
映射方式：先按亮度把原版像素排成一条从暗到亮的序列，再把这条序列
按同样的相对位置铺到紫色渐变上。这样明度层次、高光位置都保留下来，
只换了色相。

产出：
  textures/block/magic_flame_0.png   (16×512, 32 帧, 带 mcmeta)
  textures/block/magic_flame_1.png   (16×512, 32 帧, 带 mcmeta)

## 关于 .mcmeta

原版 fire 的动画是 fire.json（方块模型）里 inline 描述的，不是 png.mcmeta。
但这里贴图会被 Create 的 OBJ 模型当普通 sprite 用，走的是粒子图集，
所以必须用独立的 png.mcmeta 来声明动画。
"""
from __future__ import annotations

import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/magicinduction/textures/block"

# 原版客户端 jar —— 只用来取 fire_0/fire_1 的像素形态
MC_JAR = Path("/root/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar")
MC_PREFIX = "assets/minecraft/textures/block/"

# 紫色渐变：从深紫到接近白的淡紫。
#
# 第一版按「亮度排名」均匀铺色，结果整簇火糊成一片紫罗兰色块：
# 原版火焰的像素绝大多数落在「橙→黄」这一窄段亮度区间里，
# 按排名摊开就把这段区间撑满了整条渐变色带，火心的高光被摊薄了。
#
# 这一版按「原版色带」分段映射：原版火焰是 4 档明度
# （暗红边缘 → 焦褐 → 橙 → 亮黄白），这里给每一档指定一个紫色，
# 保证暗端仍然是"暗紫"、高光仍然是"接近白"，层次才留得住。
# 键是原版像素的亮度区间（luminance 0..255）。
PURPLE_BANDS = [
    (0.0, (34, 8, 62)),        # 最暗：火的边缘，接近黑紫
    (60.0, (74, 24, 128)),     # 暗紫
    (110.0, (126, 52, 190)),   # 中紫
    (160.0, (168, 100, 236)),  # 亮紫
    (200.0, (208, 160, 255)),  # 接近白的淡紫
    (240.0, (250, 236, 255)),  # 火心高光
]


def luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = rgb
    return 0.299 * r + 0.587 * g + 0.114 * b


def ramp_lookup(t: float) -> tuple[int, int, int]:
    """把 0..255 的亮度映射到紫色渐变上（分段线性插值）。"""
    t = 0.0 if t < 0.0 else (255.0 if t > 255.0 else t)
    for i in range(len(PURPLE_BANDS) - 1):
        x0, c0 = PURPLE_BANDS[i]
        x1, c1 = PURPLE_BANDS[i + 1]
        if t <= x1:
            f = 0.0 if x1 == x0 else (t - x0) / (x1 - x0)
            return tuple(round(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
    return PURPLE_BANDS[-1][1]


def build_lut(src: Image.Image) -> dict[tuple[int, int, int], tuple[int, int, int]]:
    """按每个原版像素的亮度直接取紫（不再按排名摊平，见 PURPLE_BANDS 注释）。"""
    px = src.convert("RGBA")
    w, h = px.size
    lut: dict[tuple[int, int, int], tuple[int, int, int]] = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px.getpixel((x, y))
            if a > 0:
                lut[(r, g, b)] = ramp_lookup(luminance((r, g, b)))
    return lut


def narrow_to_pyramid(
    frames: list[Image.Image],
    *,
    half_bottom: int = 6,
    half_top: int = 1,
    tip_rows: int = 2,
) -> list[Image.Image]:
    """把原版「横向铺满整格的火墙」收成「一簇竖直的火苗」。

    原版 fire 的每一帧都<b>横向铺满 16 像素</b>：它要盖住整个方块面，
    所以底部一整行都是实的。直接贴到燃烧室上，屏幕观感是「一条横向的紫色色带」，
    完全不像火苗——这是实测截图里的样子。原版火看起来像火，靠的是帧间尖角跳动，
    单帧轮廓本身并不像火苗。

    这里按行做水平收口：底部保留原宽度，往上线性收窄到 half_top。
    <b>只改轮廓，不改颜色</b>——颜色仍由 :func:`recolor` 按亮度映射。
    收口窄于原帧时，把源行里「最靠近中心」的那几个不透明像素搬过去，
    这样火心的高光（原版在中心偏下）会留在中心，不会把边缘的暗紫当高光搬进来。
    """
    out: list[Image.Image] = []
    for fr in frames:
        w, h = fr.size
        new = Image.new("RGBA", (w, h))
        px_in = fr.load()
        px_out = new.load()
        for y in range(h):
            # y=0 是贴图顶端，y=h-1 是底端
            t = y / max(1, h - 1)
            if y >= h - tip_rows:
                half = 1.0
            elif y < tip_rows:
                # 原版帧的顶部 2~3 行是空的，尖角从第 3 行才开始；
                # 这里让贴图顶端也收窄，否则 v≈1 那一端会在方块顶上摊成一个宽口。
                half = max(1.0, half_top * 0.5 + (half_bottom - half_top) * (tip_rows / max(1, h - 1)) * 0.5)
            else:
                half = half_top + (half_bottom - half_top) * t
                half = max(1.0, half)
            cols = [x for x in range(w) if px_in[x, y][3] > 0]
            if not cols:
                continue
            # 目标区间：以中心（7.5）为轴、宽 2*half 的一段
            width = max(1, int(round(half * 2)))
            x0 = w // 2 - width // 2
            slots = list(range(x0, min(w, x0 + width)))
            if y >= h - tip_rows:
                # 底端 2 行是原版的「炉口燃面」：铺满整行并贴到边缘，
                # 留着它，火焰才像是从炉口里长出来的，而不是悬空的一根紫条。
                slots = list(range(w))
            srcs = sorted(cols, key=lambda x: x * (len(slots) - 1) / max(1, w - 1))
            for i, dst_x in enumerate(slots):
                if not srcs:
                    break
                j = min(len(srcs) - 1, int(i * len(srcs) / len(slots)))
                px_out[dst_x, y] = px_in[srcs[j], y]
        out.append(new)
    return out


def recolor(src: Image.Image, lut: dict) -> Image.Image:
    out = Image.new("RGBA", src.size)
    px_src = src.convert("RGBA")
    px_out = out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = px_src.getpixel((x, y))
            if a == 0:
                px_out[x, y] = (0, 0, 0, 0)
                continue
            nr, ng, nb = lut.get((r, g, b), (128, 68, 196))
            px_out[x, y] = (nr, ng, nb, a)
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    if not MC_JAR.exists():
        raise SystemExit(f"找不到原版客户端 jar：{MC_JAR}\n先跑一次 gradlew，让 ForgeGradle 把 1.20.1 下载下来。")

    with zipfile.ZipFile(MC_JAR) as zf:
        for i in (0, 1):
            name = f"fire_{i}.png"
            data = zf.read(MC_PREFIX + name)
            tmp = OUT / f"_src_{name}"
            tmp.write_bytes(data)

            src = Image.open(tmp).convert("RGBA")
            lut = build_lut(src)
            recolored = recolor(src, lut)

            # 逐帧收口：原版是横向火墙，这里收成一簇竖直火苗
            frames = [recolored.crop((0, f * 16, 16, f * 16 + 16)) for f in range(src.size[1] // 16)]
            frames = narrow_to_pyramid(frames, half_bottom=6 if i == 0 else 5, half_top=1 if i == 0 else 1)
            out = Image.new("RGBA", src.size)
            for f, fr in enumerate(frames):
                out.paste(fr, (0, f * 16))

            target = OUT / f"magic_flame_{i}.png"
            out.save(target)
            tmp.unlink()

            # 刻意不生成 png.mcmeta。
            #
            # 如果给了 mcmeta，Forge 会把这张 16×512 当成"32 帧动画"交给图集管理，
            # sprite.getV() 拿到的就是**当前帧**的 UV——而渲染器里我又手动按帧号做了
            # 一次 UV 偏移，两者叠加，火焰会在两帧之间高频抖动。
            #
            # 当作静态长条图更可控：整张 16×512 作为一个 sprite 进图集，
            # 帧号完全由渲染器决定，还能让每个燃烧室用不同相位（见 animTicks）。
            mcmeta_file = OUT / f"magic_flame_{i}.png.mcmeta"
            if mcmeta_file.exists():
                mcmeta_file.unlink()

            colors = len({p[:3] for p in out.convert("RGBA").get_flattened_data() if p[3] > 0})
            print(f"magic_flame_{i}.png  {out.size}  不透明色数={colors}")


if __name__ == "__main__":
    main()
