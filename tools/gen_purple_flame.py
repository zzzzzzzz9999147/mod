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
# 刻意保留一点蓝，避免落到「洋红」那一片，和 mod 的魔法原液同一色系。
#
# 第一版把最暗端设成 (26,8,48)，结果整张图糊在深紫里——因为原版火焰的
# 亮度分布本身偏暗（大量像素是焦褐色），按排名铺开后一大半都落在 ramp 前段。
# 这里把前两档整体提亮，并缩小暗端占比，让画面中心真的"亮起来"。
PURPLE_RAMP = [
    (44, 16, 78),     # 最暗：火苗边缘的暗紫
    (78, 34, 126),
    (114, 56, 176),
    (150, 88, 220),
    (184, 126, 245),
    (216, 172, 255),
    (246, 228, 255),  # 最亮：火心高光
]


def luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = rgb
    return 0.299 * r + 0.587 * g + 0.114 * b


def ramp_lookup(t: float) -> tuple[int, int, int]:
    """把 0..1 映射到 PURPLE_RAMP 上的颜色。"""
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    n = len(PURPLE_RAMP) - 1
    pos = t * n
    i = int(pos)
    if i >= n:
        return PURPLE_RAMP[-1]
    frac = pos - i
    a, b = PURPLE_RAMP[i], PURPLE_RAMP[i + 1]
    return tuple(round(a[k] + (b[k] - a[k]) * frac) for k in range(3))


def build_lut(src: Image.Image) -> dict[tuple[int, int, int], tuple[int, int, int]]:
    """统计原图所有不透明像素的亮度，按排名均匀铺到紫色渐变上。

    用「排名」而不是「线性亮度」：原版火焰的亮度分布很不均匀，
    大量像素挤在暗红那一段，直接线性映射的话紫色会糊成一坨深紫，
    看不出火的层次。按排名铺能保证每一档紫色都用得上。
    """
    px = src.convert("RGBA")
    w, h = px.size
    seen: set[tuple[int, int, int]] = set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px.getpixel((x, y))
            if a > 0:
                seen.add((r, g, b))
    ordered = sorted(seen, key=luminance)
    lut = {}
    total = max(len(ordered) - 1, 1)
    for idx, rgb in enumerate(ordered):
        lut[rgb] = ramp_lookup(idx / total)
    return lut


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

            src = Image.open(tmp)
            lut = build_lut(src)
            out = recolor(src, lut)
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
