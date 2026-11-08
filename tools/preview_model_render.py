"""软件光栅器小工具：给"模型长什么样"这类问题出一张图，代替重启游戏试错。

真正的入口是 preview_ref.py（按参考 mod 的几何出三视图）。
这个文件只提供读图 / 写图 / 矩阵 / 三角光栅化，被它 import。

把 ManaButterflyModel 的几何按 ModelPart 的变换链真实投影出来，看它在游戏里长什么样。

为什么需要：模型只有进游戏才看得到，而"pivot 上那个 -90° 的 yRot 到底把
哪根轴转到了哪"这类问题，光看 addBox 的数字推不出来（推错一次就要重启游戏试错）。
这个脚本按 ModelPart.render 的真实顺序做变换：

    world = T(pivot) · R(pivot) · T(body) · R(body) · T(wing) · R(wing) · v

注意 **子部件的旋转先作用于顶点，父部件的旋转后作用**——顺序反过来，
扇翅就会变成跷跷板。这里用和 MC 一样的嵌套顺序，所以能拍到真实姿态。

输出四张图：俯视、正视、侧视、45° 斜视，各在扇翅的两个相位上。
表面色取自实体贴图的对应 UV，alpha < 128 的像素直接丢弃（cutout）。

用法（作为库）：
    import preview_model_render as R
    R.TEX_W, R.TEX_H, R.TEX = R.read_png(path)
    img = R.render(faces, "top", 260, 150.0, bg)
    R.write_png(out, 260, 260, img)
"""

import math
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TEX_PATH = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "magicinduction", "textures", "entity", "mana_butterfly.png")
OUT_DIR = os.path.join(ROOT, "preview_model")


# ---------------------------------------------------------------- png
def read_png(path):
    data = open(path, "rb").read()
    pos, width, height, color_type, idat = 8, None, None, 6, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height = struct.unpack(">II", chunk[:8])
            color_type = chunk[9]
        elif tag == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    channels = 4 if color_type == 6 else 3
    stride = width * channels
    prev = bytearray(stride)
    rows = []
    for y in range(height):
        off = y * (stride + 1)
        filt = raw[off]
        line = bytearray(raw[off + 1:off + 1 + stride])
        if filt == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        prev = line
        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            row.append((px[0], px[1], px[2], px[3]) if channels == 4
                       else (px[0], px[1], px[2], 255))
        rows.append(row)
    return width, height, rows


def write_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for px in row:
            raw.extend(bytes(px))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(blob)


TEX_W, TEX_H, TEX = read_png(TEX_PATH)


# ---------------------------------------------------------------- math
def mat_identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_apply(m, v):
    x, y, z = v
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def mat_translate(x, y, z):
    m = mat_identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def mat_rot(axis, deg):
    """与 MC PoseStack.mulPose 同向。"""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = mat_identity()
    if axis == "x":
        m[1][1], m[1][2], m[2][1], m[2][2] = c, -s, s, c
    elif axis == "y":
        m[0][0], m[0][2], m[2][0], m[2][2] = c, s, -s, c
    else:
        m[0][0], m[0][1], m[1][0], m[1][1] = c, -s, s, c
    return m


# ---------------------------------------------------------------- 模型
# 每个部件的变换严格照 ModelPart.translateAndRotate：
#   translate(x/16, y/16, z/16) → mulPose(Z) → mulPose(Y) → mulPose(X)
def part_matrix(pos, rot_deg, scale=(1.0, 1.0, 1.0)):
    """与 ModelPart.translateAndRotate 同序：先平移，再 Z→Y→X，最后乘缩放。"""
    m = mat_translate(pos[0] / 16.0, pos[1] / 16.0, pos[2] / 16.0)
    if rot_deg[2]:
        m = mat_mul(m, mat_rot("z", rot_deg[2]))
    if rot_deg[1]:
        m = mat_mul(m, mat_rot("y", rot_deg[1]))
    if rot_deg[0]:
        m = mat_mul(m, mat_rot("x", rot_deg[0]))
    if scale != (1.0, 1.0, 1.0):
        m = mat_mul(m, mat_scale(*scale))
    return m


def mat_scale(sx, sy, sz):
    m = mat_identity()
    m[0][0], m[1][1], m[2][2] = sx, sy, sz
    return m


def quad(x0, y0, z0, w, h, d, tex_u, tex_v):
    """返回 6 个面：[(4 个顶点(局部, 已除 16), 4 个 uv, 名字)]。

    ModelPart.Cube 在构造顶点时就把坐标除了 16（1 单位 = 1/16 格），
    部件位置也一样。两处都除，比例才对。
    """
    x0, y0, z0, w, h, d = x0 / 16.0, y0 / 16.0, z0 / 16.0, w / 16.0, h / 16.0, d / 16.0
    x1, y1, z1 = x0 + w, y0 + h, z0 + d

    # UV 用的是元数，必须在除以 16 之前算好
    uw, uh, ud = round(w * 16), round(h * 16), round(d * 16)

    def q(pts, ux, uy, uw, uh, name):
        uvs = [(ux, uy), (ux + uw, uy), (ux + uw, uy + uh), (ux, uy + uh)]
        return (pts, uvs, name)

    return [
        q([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], tex_u + ud, tex_v + ud, uw, uh, "N"),
        q([(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)], tex_u + 2 * ud + uw, tex_v + ud, uw, uh, "S"),
        q([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], tex_u, tex_v + ud, ud, uh, "E"),
        q([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], tex_u + ud + uw, tex_v + ud, ud, uh, "W"),
        q([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], tex_u + ud, tex_v, uw, ud, "U"),
        q([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], tex_u + ud + uw, tex_v, uw, ud, "D"),
    ]


# ---------------------------------------------------------------- 光栅化
# 模型整体挂在 y≈1.44 格（脚底 24 元），正/侧视要把这个高度减掉再投影，
# 否则整只蝴蝶会被推到画布外，出图全黑。
Y_CENTER = 1.44
VIEWS = {
    "top": lambda p: (p[0], p[2], p[1]),                       # 屏幕 (x, z)，深度 y
    "front": lambda p: (p[0], -(p[1] - Y_CENTER), p[2]),       # 屏幕 (x, -y)，深度 z
    "side": lambda p: (p[2], -(p[1] - Y_CENTER), p[0]),        # 屏幕 (z, -y)，深度 x
}


def render(faces, view, size, scale, bg):
    proj = VIEWS[view]
    canvas = [[bg for _ in range(size)] for _ in range(size)]
    depth = [[-1e9 for _ in range(size)] for _ in range(size)]
    cx = cy = size / 2.0

    for pts, uvs, _name in faces:
        # 两个三角形
        for tri in ((0, 1, 2), (0, 2, 3)):
            sp, uv, dp = [], [], []
            for k in tri:
                x, y, z = pts[k]
                sx, sy, sz = proj((x, y, z))
                sp.append((cx + sx * scale, cy + sy * scale))
                uv.append(uvs[k])
                dp.append(sz)
            x0, y0 = sp[0]
            x1, y1 = sp[1]
            x2, y2 = sp[2]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            minx = max(0, int(math.floor(min(x0, x1, x2))))
            maxx = min(size - 1, int(math.ceil(max(x0, x1, x2))))
            miny = max(0, int(math.floor(min(y0, y1, y2))))
            maxy = min(size - 1, int(math.ceil(max(y0, y1, y2))))
            for py in range(miny, maxy + 1):
                for px in range(minx, maxx + 1):
                    sx, sy = px + 0.5, py + 0.5
                    w0 = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) / area
                    w1 = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) / area
                    w2 = 1.0 - w0 - w1
                    if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                        continue
                    u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
                    v = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
                    tx = int(math.floor(u)) % TEX_W
                    ty = int(math.floor(v)) % TEX_H
                    col = TEX[ty][tx]
                    if col[3] < 128:
                        continue
                    zz = w0 * dp[0] + w1 * dp[1] + w2 * dp[2]
                    if zz <= depth[py][px]:
                        continue
                    depth[py][px] = zz
                    canvas[py][px] = (col[0], col[1], col[2], 255)
    return canvas
