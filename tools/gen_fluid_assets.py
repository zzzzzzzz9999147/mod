"""生成魔法原液的流体贴图、流体方块模型与原液桶贴图。

用法：
    python3 tools/gen_fluid_assets.py

设计说明：
- 流体 still / flow 直接复用**原版 water_still.png / water_flow.png 的像素结构**
  （同样的 16×512 / 32×1024 帧布局、同样的 32 帧、同样的明暗分布），
  只把每个像素的灰度映射到紫色区间。玩家要的就是"水那样的波纹"，而不是一块均匀色块。
  保持原版帧尺寸还有两个硬性好处：帧计数、动画循环周期、mipmap 边界与水完全一致。
- 原液桶直接复用原版 water_bucket.png 的像素布局，只把"水"的蓝色像素转到紫色。
  这样桶的轮廓、提手、高光、桶沿都和原版水桶一模一样，玩家一眼就能认出来。
- 额外生成模型 `models/block/mana_essence.json`：从原版 `minecraft:block/water` 派生，
  top/bottom 用 still、side 用 flow，补上原版水方块偏暗的四个侧面。

注意颜色链路：
    FluidType#getTintColor 必须是白色（0xFFFFFFFF），并在 mcmeta 里关掉
    `gui`/`blur` 之外的任何自动染色，否则贴图的紫色会被二次相乘变暗。
    颜色只由贴图决定，改色只需换 DARK / LIGHT 两个端点。
"""

from __future__ import annotations

import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/item"
MODEL_DIR = ROOT / "src/main/resources/assets/magicinduction/models/block"

# 深紫：取蝴蝶翅膀暗部的色相，整体压到 88,48,145。
# 之前的 155,107,224 在游戏里偏亮，看着像染料而不是"原液"。
PURPLE = (88, 48, 145)

# 原版水贴图是灰度图 + 客户端 tint 染色。这里把灰度的明暗直接映射到紫色区间：
# 暗端比 PURPLE 略深，亮端略带紫粉，形成"深紫 → 浅紫"的波纹。
DARK = (62, 30, 108)
LIGHT = (146, 100, 208)

# 原版水贴图的灰度范围（实测 water_still.png 是 165~255）。归一化时留余量，
# 避免个别像素被裁到端点后失去层次。
GRAY_MIN = 150.0
GRAY_MAX = 255.0

PURPLE_ALPHA = 210  # 与原版 water_still 的 alpha=180 同量级，看得见底下的方块


# 原版资源 jar（ForgeGradle 缓存）。不同环境路径可能不同，按顺序探测。
VANILLA_JARS = [
    Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar",
    Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar",
]

WATER_STILL = "assets/minecraft/textures/block/water_still.png"
WATER_FLOW = "assets/minecraft/textures/block/water_flow.png"
WATER_BUCKET = "assets/minecraft/textures/item/water_bucket.png"


def find_vanilla_jar() -> Path:
    for p in VANILLA_JARS:
        if p.exists():
            with zipfile.ZipFile(p) as z:
                names = set(z.namelist())
            if WATER_STILL in names:
                return p
    raise SystemExit(
        "找不到含 water_still.png 的原版资源 jar。\n"
        "请先跑一次 ./gradlew build 让 ForgeGradle 下载 1.20.1 资源，\n"
        "或在脚本顶部把 VANILLA_JARS 改成实际路径。"
    )


def read_vanilla(name: str) -> bytes:
    with zipfile.ZipFile(find_vanilla_jar()) as z:
        return z.read(name)


def gray_to_purple(v: int) -> tuple[int, int, int]:
    """把一个灰度值映射到紫→浅紫的渐变上。

    原版水是"灰度贴图 × tint 颜色"，明暗层次全在灰度里。这里保留同样的层次，
    只把两端换成紫色系，波纹就跟着明暗一起变成紫色的深浅。
    """
    t = (v - GRAY_MIN) / (GRAY_MAX - GRAY_MIN)
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return tuple(round(DARK[i] + (LIGHT[i] - DARK[i]) * t) for i in range(3))


def recolor(src: Image.Image) -> Image.Image:
    """逐像素把灰度水贴图换成紫色，保留帧尺寸与 alpha 结构。"""
    src = src.convert("RGBA")
    out = Image.new("RGBA", src.size)
    px = src.load()
    op = out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            # 原版水面是纯灰度（r=g=b）；若换了资源包混进彩色像素，按亮度处理即可
            lum = (r * 299 + g * 587 + b * 114) // 1000
            pr, pg, pb = gray_to_purple(lum)
            # 原版 alpha 是 180；统一压到 PURPLE_ALPHA，同时保留 a==0 的像素完全透明
            op[x, y] = (pr, pg, pb, 0 if a == 0 else min(a, PURPLE_ALPHA))
    return out


def make_fluid_textures() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)

    still = recolor(Image.open(io.BytesIO(read_vanilla(WATER_STILL))))
    still.save(BLOCK_DIR / "mana_essence_still.png")
    (BLOCK_DIR / "mana_essence_still.png.mcmeta").write_text(
        '{\n  "animation": {\n    "frametime": 2\n  }\n}\n', encoding="utf-8")

    flow = recolor(Image.open(io.BytesIO(read_vanilla(WATER_FLOW))))
    flow.save(BLOCK_DIR / "mana_essence_flow.png")
    (BLOCK_DIR / "mana_essence_flow.png.mcmeta").write_text(
        '{\n  "animation": {}\n}\n', encoding="utf-8")

    print(f"流体贴图：still {still.size} / flow {flow.size}，原版水帧结构 + 紫色映射")


def make_block_model() -> None:
    """从原版 minecraft:block/water 派生模型，补上侧面。

    只写 particle 的时候方块走的是 forge 默认流体模型（六面全用 still），
    俯视看到的就是一整块平涂。照原版 water 的做法：top/bottom 用 still，side 用 flow。
    """
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    model = """{
  "parent": "minecraft:block/water",
  "textures": {
    "particle": "magicinduction:block/mana_essence_still"
  }
}
"""
    (MODEL_DIR / "mana_essence.json").write_text(model, encoding="utf-8")
    print("方块模型：models/block/mana_essence.json（parent = minecraft:block/water）")


# 原版水桶里最亮的水面像素，用来把亮部压到 PURPLE、暗部按比例跟随
REF_WATER_VALUE = 0.90


def blue_to_purple(rgb: tuple[int, int, int]) -> tuple[int, int, int]:
    """把一个偏蓝的水像素转到深紫，保留原本的明暗层次。

    <p>不能只改色相：原版水面最亮处接近纯白蓝（v≈0.9），只换 hue 会让桶里的水
    比液面亮一大截，两个紫色对不上。这里按"相对最亮水的比例"整体缩放到 PURPLE，
    亮部正好落在 PURPLE 上，暗部自然更深。
    """
    from colorsys import rgb_to_hsv

    r, g, b = (c / 255.0 for c in rgb)
    _, _, v = rgb_to_hsv(r, g, b)
    k = v / REF_WATER_VALUE
    return tuple(min(255, max(0, int(c * k))) for c in PURPLE)


def make_bucket_texture() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    src = Image.open(io.BytesIO(read_vanilla(WATER_BUCKET))).convert("RGBA")
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
    make_block_model()
    make_bucket_texture()
    print("完成")
