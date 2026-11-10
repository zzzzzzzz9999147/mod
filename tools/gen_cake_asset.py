"""生成「魔法蛋糕」物品贴图（16×16）。

用法：
    python3 tools/gen_cake_asset.py

设计取向
--------
玩家已经认识 Create 的**烈焰蛋糕**（`create:item/blaze_cake`）：一张
"俯视的圆饼、边缘一圈纸托、顶面有淋面高光、底沿一圈更暗的焦底"的图标。
魔法蛋糕是"同一个蛋糕注了魔法原液"，所以必须**沿用同一套构图和明暗结构**，
只把色相从橙金换成紫——这样放在物品栏里并排时，一眼就能认出关系。

具体沿用（对照 blaze_cake 的 16×16 像素表，逐行对齐）：

| 层 | Create 的橙金 | 本 mod 的紫 | 作用 |
|---|---|---|---|
| 外描边 | `#CC4628` | `#7B3ECE` | 勾出剪影，全图**最饱和**的一圈 |
| 顶面斜坡 | `#D67824` | `#6436AA` | 顶面外沿，比描边暗一档形成倒角 |
| 顶面主体 | `#ECC543` | `#844EC8` | 顶面 |
| 顶面受光 | `#FFE385` | `#9E6ADC` | 顶面偏上一侧 |
| 高光点 | 成簇 `#FFE385` | `#D6B4FA` | 顶部两三格亮点，模拟油光 |
| 饼身 | `#EF8D36` → `#D67824` | `#58348C` → `#442278` | 正对镜头那一圈 |
| 焦底 | `#854242` | `#602C9C` | 饼身与纸托之间 |
| 纸托 | `#723232` `#501B1B` `#411616` | `#4A2279` `#32164E` `#26103A` | 最下两三行，压暗让饼"坐得住" |

**为什么不直接给 blaze_cake.png 调色相**：那是 Create（MIT）的美术资源，
逐像素搬运再改色属于衍生复制。这里按同样的构图自己画一张 —— 形状与结构一致、
像素独立，既有辨识度也不越界。

**为什么纯手写 16×16 矩阵**：这个尺寸下每个像素都是一个设计决策，
算法缩图/羽化只会得到糊掉的一团。矩阵改一行就能看效果，比调参快。
而且只写左半边、右半边镜像 —— 圆饼左右对称，写一半既少出错，也保证不歪。

色相统一取原液 `PURPLE = (88,48,145)` 一带，
和流体、火焰、桶保持同一个紫。
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEM_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/item"

# 调色板。键名按"层"取，方便和 blaze_cake 的像素表逐行对照。
PALETTE = {
    ".": (0, 0, 0, 0),            # 透明
    # 描边是全图**最饱和**的一圈：橙金版靠亮橙红 #CC4628 把形状"撑"起来，
    # 换成紫色如果直接用最暗的紫，整张图会塌成一块色饼。
    "o": (123, 62, 206, 255),     # 外描边
    "s": (100, 54, 170, 255),     # 顶面斜坡（比描边暗一档，形成倒角）
    "S": (132, 78, 200, 255),     # 顶面主体
    "l": (158, 106, 220, 255),    # 顶面受光
    "L": (214, 180, 250, 255),    # 顶面高光点
    "n": (88, 46, 152, 255),      # 饼身
    "m": (68, 34, 120, 255),      # 饼身暗部
    "K": (96, 44, 156, 255),      # 焦底（对应 blaze_cake 的 #854242）
    "j": (74, 34, 121, 255),      # 纸托（#723232）
    "J": (50, 22, 78, 255),       # 纸托最暗（#501B1B）
    "u": (38, 16, 58, 255),       # 底部阴影（#411616）
}

# 左半边手写、右半边镜像 —— 16×16 的圆饼左右对称，
# 写一半既少出错，也保证左右不歪（blaze_cake 本身也是左右对称的）。
# 自上而下：2 行空气 → 顶面（受光在上、往下渐暗）→ 饼身 → 焦底 → 纸托 → 收口。
_HALF = [
    "........",
    "........",
    "....oooo",
    "...oossl",
    "..oosSSl",
    "..osSSSl",
    ".osSSSlL",
    ".osSSllL",
    ".ossSSSl",
    ".oossSSS",
    ".oonnSSS",
    "..onnSss",
    "..ojjJuu",
    "...oJJJJ",
    "....ooJJ",
    "........",
]

SPRITE = [row + row[::-1] for row in _HALF]


def build() -> None:
    if len(SPRITE) != 16 or any(len(row) != 16 for row in SPRITE):
        bad = [(i, len(r), r) for i, r in enumerate(SPRITE) if len(r) != 16]
        raise SystemExit(f"像素矩阵必须是 16×16，问题行：{bad}")

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(SPRITE):
        for x, ch in enumerate(row):
            if ch not in PALETTE:
                raise SystemExit(f"未知颜色键 {ch!r} @ ({x},{y})")
            px[x, y] = PALETTE[ch]

    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    out = ITEM_DIR / "magic_cake.png"
    img.save(out)

    used = sorted({c for row in SPRITE for c in row if c != "."})
    print(f"魔法蛋糕贴图：16×16，{len(used)} 种颜色 -> {out}")


if __name__ == "__main__":
    build()
