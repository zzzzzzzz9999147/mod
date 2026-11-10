"""生成「魔法蛋糕」物品贴图（16×16）。

用法：
    python3 tools/gen_cake_asset.py

做法
----
**直接取 Create 烈焰蛋糕 `create:item/blaze_cake` 的像素图，把色相换成紫色。**
形状、层次、高光位置、纸托纹理、轮廓台阶 —— 全部原样保留，
只有颜色的"色相"变了，亮度和饱和度逐像素不动。

之所以这么做：
- 魔法蛋糕的玩法就是"烈焰蛋糕注了魔法原液"，剪影必须和烈焰蛋糕**完全一致**，
  玩家在物品栏里一眼就能看出是同一样东西换了色；
- 手画 16×16 很难在 11 个色阶上复刻出这种"顶面高光成簇、底沿压暗"的层次，
  换色反而更忠实。

依赖 Create 的 jar
------------------
本脚本从 `mods-external/create-1.20.1-6.0.8.jar` 里读 blaze_cake，
**只在生成贴图时用一次**，产物是独立的 png，运行时不需要 Create。

找不到 jar 时会跳过生成并打印提示 —— 已经生成的 `magic_cake.png` 留在仓库里，
不影响构建。换 Create 版本后重跑一次即可。

色相映射
--------
原图是橙金（色相 11°–46°）+ 一圈暗红纸托（色相 0°），共 11 色。
按亮度从暗到亮依次映射到紫（色相 253°–274°），形成从深紫到浅紫的顺色阶，
和原液的 `PURPLE = (88,48,145)` 同一色系。
"""

from __future__ import annotations

import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEM_DIR = ROOT / "src/main/resources/assets/magicinduction/textures/item"
OUT = ITEM_DIR / "magic_cake.png"

# 候选 jar：先找工程里放依赖的目录，再退回 Gradle 缓存里的 modrinth 缓存。
JAR_CANDIDATES = [
    ROOT / "mods-external" / "create-1.20.1-6.0.8.jar",
    Path("/workspace/mods-external/create-1.20.1-6.0.8.jar"),
]
SOURCE_ENTRY = "assets/create/textures/item/blaze_cake.png"

# 原色 -> (目标色相, 注记)。亮度 L、饱和 S 一律沿用原像素。
# 亮度顺序：暗红纸托 4 色 → 焦底 → 描边 → 饼身 → 顶面 → 高光。
HUE_MAP = {
    (0x41, 0x16, 0x16): (253, "纸托最暗"),
    (0x50, 0x1B, 0x1B): (255, "纸托"),
    (0x65, 0x28, 0x28): (258, "纸托受光"),
    (0x72, 0x32, 0x32): (260, "饼身暗部"),
    (0x85, 0x42, 0x42): (262, "焦底"),
    (0xA2, 0x2E, 0x14): (264, "饼身暗红"),
    (0xCC, 0x46, 0x28): (266, "外描边"),
    (0xD6, 0x78, 0x24): (268, "顶面外沿"),
    (0xEF, 0x8D, 0x36): (270, "饼身受光"),
    (0xEC, 0xC5, 0x43): (272, "顶面主体"),
    (0xFF, 0xE3, 0x85): (274, "顶面高光"),
}


def find_source() -> Image.Image | None:
    for jar in JAR_CANDIDATES:
        if not jar.is_file():
            continue
        with zipfile.ZipFile(jar) as zf:
            if SOURCE_ENTRY not in zf.namelist():
                continue
            with zf.open(SOURCE_ENTRY) as f:
                return Image.open(io.BytesIO(f.read())).convert("RGBA")
    return None


def recolor(src: Image.Image) -> Image.Image:
    w, h = src.size
    out = Image.new("RGBA", (w, h))
    sp, op = src.load(), out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a == 0:
                op[x, y] = (0, 0, 0, 0)
                continue
            target = HUE_MAP.get((r, g, b))
            if target is None:
                raise SystemExit(f"烈焰蛋糕出现了未映射的颜色 #{r:02X}{g:02X}{b:02X} @ ({x},{y})，"
                                 f"请把新颜色加进 HUE_MAP")
            hue = target[0]
            _, light, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hls_to_rgb(hue / 360.0, light, sat)
            op[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def build() -> None:
    src = find_source()
    if src is None:
        print("找不到 Create 的 blaze_cake，跳过（已生成的 magic_cake.png 保留）")
        sys.exit(0)
    if src.size != (16, 16):
        raise SystemExit(f"烈焰蛋糕贴图尺寸异常：{src.size}，预期 16×16")

    img = recolor(src)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    img.save(OUT)

    colors = {c[:3] for c in img.getcolors(65536) or [] if c[1][3] > 0}
    print(f"魔法蛋糕贴图：16×16，{len(colors)} 种颜色（烈焰蛋糕换了色相）-> {OUT}")


if __name__ == "__main__":
    build()
