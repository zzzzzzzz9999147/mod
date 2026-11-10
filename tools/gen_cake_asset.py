"""生成「魔法蛋糕」物品贴图（16×16）。

用法：
    python3 tools/gen_cake_asset.py

设计取向：
- 形状是**俯视的三层同心椭圆**，和 Create 的 `blaze_cake` 完全一致：
  外圈是烘焙纸托（深棕紫）、中圈是淋面（本 mod 的魔法紫）、中心是凹槽里的微光。
  用一模一样的剪影，玩家一眼就知道"这是那个蛋糕浇了原液"，而不是一件新物品。
- 只有 16×16，纯手写像素矩阵，不做任何降采样/滤镜：这个尺寸下每格都是设计决策，
  算法缩图只会得到糊掉的一团。
- 暗/亮层次照原版来：蛋糕顶面（朝上）最亮，侧面比顶面暗一档，纸托最暗。
  纯色块在这个尺寸会显得像"一块色纸"，有层次才像烘焙出来的东西。

为什么不把贴图直接叠到 Create 的 blaze_cake.png 上：
    那是 Create 的 MIT 资源，逐像素搬运别人的贴图再改色属于衍生复制。
    自己按同样的构图重画 16×16，形状一致、像素独立，既保留辨识度也不越界。
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEM_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/item"

# 调色板。与原液 PURPLE = (88,48,145) 同一色相，往两头拉出层次。
PALETTE = {
    ".": (0, 0, 0, 0),            # 透明
    "o": (34, 20, 50, 255),       # 纸托外描边（最深，勾出剪影）
    "p": (86, 55, 112, 255),      # 纸托主体
    "q": (112, 76, 142, 255),     # 纸托受光面（比主体亮一档）
    "d": (120, 72, 178, 255),     # 淋面外沿（侧壁，暗）
    "D": (145, 96, 200, 255),     # 淋面
    "M": (170, 124, 222, 255),    # 顶面受光
    "L": (198, 158, 240, 255),    # 顶面高光
    "g": (226, 196, 252, 255),    # 中心微光（最亮）
}

# 16×16。行 0/15 与列 0/15 留空，外轮廓做 45° 阶梯（Minecraft 圆形物件的标准画法）。
SPRITE = [
    "................",
    "................",
    "....oooooooo....",
    "..oopqqqqqqpoo..",
    ".opqqqppppqqqpo.",
    ".opqqppppppqqpo.",
    "opqqddddddddqqpo",
    "opqqdDDDDDDdqqpo",
    "opqqdDMMMMDdqqpo",
    "opqqdDMggMDdqqpo",
    "opqqdDMMMMDdqqpo",
    "opqqdDDDDDDdqqpo",
    "opqqddddddddqqpo",
    ".opqqppppppqqpo.",
    ".opqqqppppqqqpo.",
    "..oopqqqqqqpoo..",
]


def build() -> None:
    if len(SPRITE) != 16 or any(len(row) != 16 for row in SPRITE):
        bad = [(i, len(r)) for i, r in enumerate(SPRITE) if len(r) != 16]
        raise SystemExit(f"像素矩阵必须是 16×16，问题行：{bad}")

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(SPRITE):
        for x, ch in enumerate(row):
            if ch not in PALETTE:
                raise SystemExit(f"未知颜色键 {ch!r} @ ({x},{y})")
            px[x, y] = PALETTE[ch]

    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    img.save(ITEM_DIR / "magic_cake.png")

    used = sorted({c for row in SPRITE for c in row if c != "."})
    print(f"魔法蛋糕贴图：16×16，{len(used)} 种颜色 -> {ITEM_DIR / 'magic_cake.png'}")


if __name__ == "__main__":
    build()
