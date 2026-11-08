#!/usr/bin/env python3
"""从实体贴图拼出 mod 图标（icon.png，256x256）。

素材解析（对应 ManaButterflyModel.createBodyLayer 的 UV）：
  - 左翅 texOffs(0,0)   : 贴图 x 0..17, y 0..11
  - 右翅 texOffs(0,10)  : 贴图 x 0..17, y 10..19
  - 躯干 texOffs(0,20)  : 贴图 x 0..25, y 20..23（10x2x2 横杆的一条面）
  - 原版蝴蝶模板里 y0..3 的左上角小块是触角（3x2x0 薄片）

拼法：俯视展开图 —— 上翅在上、下翅在下、躯干竖着夹在中间，触角从头顶伸出。
画布按 1 像素 = 1 单位先排好，再整体 NEAREST 放大，保持像素颗粒。
"""
from PIL import Image

SRC = "src/main/resources/assets/magicinduction/textures/entity/mana_butterfly.png"
OUT = "src/main/resources/icon.png"
SIZE = 256

tex = Image.open(SRC).convert("RGBA")

# 抹掉贴图左上角 y0..3 / x0..3 的杂散触角像素：它们在本图层里被单独建成
# antennae 薄片，这里不需要，留着会污染包围盒。
clean = tex.copy()
for y in range(0, 4):
    for x in range(0, 4):
        clean.putpixel((x, y), (0, 0, 0, 0))

upper_wing = clean.crop((0, 0, 18, 12))    # 前翅（上）
lower_wing = clean.crop((0, 10, 18, 20))   # 后翅（下）
torso_src = clean.crop((0, 20, 24, 24))    # 躯干条（实际非透明范围 x 0..23）

# --- 躯干：把 26x4 的横条竖过来，取中间一段当身体 ---
torso = torso_src.transpose(Image.ROTATE_90)   # 旋转后 4 宽 x 24 高
bw, bh = torso.size
body = torso.crop((0, 7, bw, bh - 7))          # 去掉头尾，剩 4 x 10
body_w, body_h = body.size

# --- 画布布局 ---
GAP = 0          # 翅膀与身体之间不留空，靠自身的 alpha 轮廓分开
CANVAS_W = body_w + upper_wing.width * 2 + GAP * 2
CANVAS_H = upper_wing.height + lower_wing.height + body_h + 4   # 上方给触角留 4

canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))

cx = CANVAS_W // 2
body_x = cx - body_w // 2
body_y = 4 + upper_wing.height - 2         # 身体与上翅略重叠，视觉上连成一体

# 身体先画，翅膀压在上面
canvas.alpha_composite(body, (body_x, body_y))

# 上翅：左片原样，右片镜像。裁切时避开贴图左上角那 2x2 的杂散像素，
# 否则包围盒被撑大、图标整体缩水（第一版就是这样，触角点被甩到边缘）。
canvas.alpha_composite(upper_wing, (body_x - GAP - upper_wing.width, 4))
canvas.alpha_composite(upper_wing.transpose(Image.FLIP_LEFT_RIGHT), (body_x + body_w + GAP, 4))

# 下翅：贴着身体下半段
lower_y = body_y + body_h - lower_wing.height
canvas.alpha_composite(lower_wing, (body_x - GAP - lower_wing.width, lower_y))
canvas.alpha_composite(lower_wing.transpose(Image.FLIP_LEFT_RIGHT), (body_x + body_w + GAP, lower_y))

# --- 触角：从头顶各伸一根 2 像素的小折线，紧贴身体不脱离 ---
accent = (168, 110, 232, 255)


def put(x, y, color):
    if 0 <= x < CANVAS_W and 0 <= y < CANVAS_H:
        canvas.putpixel((x, y), color)


top = body_y
put(body_x, top - 1, accent)
put(body_x - 1, top - 2, accent)
put(body_x + body_w - 1, top - 1, accent)
put(body_x + body_w, top - 2, accent)

# --- 整数倍放大并居中 ---
bbox = canvas.getbbox()
canvas = canvas.crop(bbox)
scale = SIZE // max(canvas.width, canvas.height)
big = canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)

icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
icon.alpha_composite(big, ((SIZE - big.width) // 2, (SIZE - big.height) // 2))
icon.save(OUT, "PNG", optimize=True)
print(f"{OUT}: {icon.size}，素材 {canvas.size} 放大 {scale}x")
