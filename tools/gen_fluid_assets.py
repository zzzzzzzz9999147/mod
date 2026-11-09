"""生成魔法原液的流体贴图与原液桶贴图。

用法：
    python3 tools/gen_fluid_assets.py

设计说明：
- 流体 still / flow 都做成纯紫色（没有明暗花纹）。玩家反馈原来的噪点纹理太花，
  而且魔法原液本身就是"一滩纯粹的紫"，不需要再模拟波纹。
- 原液桶直接复用原版 water_bucket.png 的像素布局，只把"水"的蓝色像素转到紫色。
  这样桶的轮廓、提手、高光、桶沿都和原版水桶一模一样，玩家一眼就能认出来。
"""

from __future__ import annotations

import colorsys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/item"

# 深紫：取蝴蝶翅膀暗部的色相，整体压到 88,48,145。
# 之前的 155,107,224 在游戏里偏亮，看着像染料而不是"原液"。
PURPLE = (88, 48, 145)
PURPLE_ALPHA = 220  # 略微半透明，看得见底下的方块，但不是一片透明

# 原版资源 jar（ForgeGradle 缓存）。不同环境路径可能不同，按顺序探测。
VANILLA_JARS = [
    Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar",
]


def find_vanilla_jar() -> Path:
    for p in VANILLA_JARS:
        if p.exists():
            return p
    raise SystemExit(
        "找不到原版资源 jar（client-extra.jar）。\n"
        "请先跑一次 ./gradlew build 让 ForgeGradle 下载 1.20.1 资源，\n"
        "或在脚本顶部把 VANILLA_JARS 改成实际路径。"
    )


def make_fluid_textures() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    solid = Image.new("RGBA", (16, 16), PURPLE + (PURPLE_ALPHA,))
    solid.save(BLOCK_DIR / "mana_essence_still.png")
    # 流动贴图与静止同色：纯色时两者没有区别，但游戏仍然要两张文件
    solid.save(BLOCK_DIR / "mana_essence_flow.png")
    print("流体贴图：still / flow 均为纯紫色", PURPLE)


# 原版水桶里最亮的水面像素，用来把亮部压到 PURPLE、暗部按比例跟随
REF_WATER_VALUE = 0.90


def blue_to_purple(rgb: tuple[int, int, int]) -> tuple[int, int, int]:
    """把一个偏蓝的水像素转到深紫，保留原本的明暗层次。

    <p>不能只改色相：原版水面最亮处接近纯白蓝（v≈0.9），只换 hue 会让桶里的水
    比液面亮一大截，两个紫色对不上。这里按"相对最亮水的比例"整体缩放到 PURPLE，
    亮部正好落在 PURPLE 上，暗部自然更深。
    """
    r, g, b = (c / 255.0 for c in rgb)
    _, _, v = colorsys.rgb_to_hsv(r, g, b)
    k = v / REF_WATER_VALUE
    return tuple(min(255, max(0, int(c * k))) for c in PURPLE)


def make_bucket_texture() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    jar = find_vanilla_jar()
    with zipfile.ZipFile(jar) as z:
        data = z.read("assets/minecraft/textures/item/water_bucket.png")

    src = Image.open(__import__("io").BytesIO(data)).convert("RGBA")
    out = Image.new("RGBA", src.size)
    changed = 0
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            # 判定"水"：蓝色明显压过红绿，且不是桶身的灰（灰的 r≈g≈b）
            if a > 0 and b > 120 and b > r + 40 and b > g + 20:
                out.putpixel((x, y), blue_to_purple((r, g, b)) + (a,))
                changed += 1
            else:
                out.putpixel((x, y), (r, g, b, a))

    out.save(ITEM_DIR / "mana_essence_bucket.png")
    print(f"原液桶：基于原版 water_bucket.png，替换 {changed} 个水面像素")


if __name__ == "__main__":
    make_fluid_textures()
    make_bucket_texture()
    print("完成")
