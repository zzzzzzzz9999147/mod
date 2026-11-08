"""把物品模型 JSON 渲染成等轴测预览图，用于在不开游戏的情况下检查几何是否成立。

为什么需要它：物品模型只有进游戏才能看到效果，而"罐子看起来像不像罐子"
（比例、木塞会不会比罐身还宽、内容薄片有没有被罐体挡住）这类问题
光看 JSON 数字判断不出来。这个脚本按 display.gui 的旋转角度做正交投影，
用各面贴图的平均色填充，输出一张近似物品栏视角的图。

它不是精确渲染器：没有光照、没有贴图细节、没有 mipmap。
它的唯一用途是回答"形状对不对"。
"""

import json
import math
import os
import struct
import zlib

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(PROJECT_ROOT, "src", "main", "resources", "assets", "magicinduction")
TEXTURES = os.path.join(ASSETS, "textures")
MODELS = os.path.join(ASSETS, "models", "item")
MODELS_ROOT = os.path.join(ASSETS, "models")


def read_png(path):
    data = open(path, "rb").read()
    pos, width, height, idat = 8, None, None, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height = struct.unpack(">II", chunk[:8])
        elif tag == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    stride = width * 4
    rows = []
    for y in range(height):
        offset = y * (stride + 1) + 1
        rows.append([tuple(raw[offset + x * 4:offset + x * 4 + 4]) for x in range(width)])
    return width, height, rows


def write_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for color in row:
            raw.extend(bytes(color))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                           + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def average_color(texture_path, uv):
    """取贴图指定 UV 区域的平均色，跳过透明像素。"""
    width, height, rows = read_png(texture_path)
    x0, y0, x1, y1 = [int(round(v)) for v in uv]
    total = [0, 0, 0]
    count = 0
    for y in range(max(0, y0), min(height, y1)):
        for x in range(max(0, x0), min(width, x1)):
            r, g, b, a = rows[y][x]
            if a < 16:
                continue
            total[0] += r
            total[1] += g
            total[2] += b
            count += 1
    if count == 0:
        return (120, 120, 140, 0)
    return (total[0] // count, total[1] // count, total[2] // count, 255)


def rotation_matrix(pitch_deg, yaw_deg):
    """先绕 Y 轴转 yaw，再绕 X 轴转 pitch（与 MC 的 display rotation 顺序一致）。"""
    ya = math.radians(yaw_deg)
    pa = math.radians(pitch_deg)
    cy, sy = math.cos(ya), math.sin(ya)
    cp, sp = math.cos(pa), math.sin(pa)

    def apply(v):
        x, y, z = v
        # 绕 Y
        x1 = x * cy - z * sy
        z1 = x * sy + z * cy
        # 绕 X
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        return x1, y2, z2

    return apply


FACE_CORNERS = {
    "north": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],
    "south": [(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)],
    "west": [(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)],
    "east": [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
    "up": [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],
    "down": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
}


def load_model(path, depth=0):
    """读取模型并沿 magicinduction: 的 parent 链合并。

    罐子的 6 档变体都是"只覆盖贴图"的子模型，elements 全在 base 里，
    不解析 parent 就会拿到一个空模型（0 个面）。
    """
    model = json.load(open(path, encoding="utf-8"))
    parent = model.get("parent")
    if depth > 8 or not parent or not parent.startswith("magicinduction:"):
        return model
    # parent 写的是完整资源路径（如 item/butterfly_jar_base），要从 models 根拼起
    parent_path = os.path.join(MODELS_ROOT, parent.split(":", 1)[1] + ".json")
    if not os.path.exists(parent_path):
        return model
    base = load_model(parent_path, depth + 1)
    merged = dict(base)
    merged["textures"] = {**base.get("textures", {}), **model.get("textures", {})}
    for key in ("elements", "display"):
        if key in model:
            merged[key] = model[key]
    return merged


def render(model_path, out_path, scale=22, pad=40, pitch=30.0, yaw=225.0):
    model = load_model(model_path)
    textures = model.get("textures", {})
    transform = rotation_matrix(pitch, yaw)

    texture_cache = {}

    def tex_path(ref):
        name = ref.lstrip("#")
        full = textures.get(name)
        if full is None:
            return None
        namespace, _, path = full.partition(":")
        return os.path.join(ASSETS, "textures", path + ".png")

    faces = []
    for element in model.get("elements", []):
        frm = element["from"]
        to = element["to"]
        for face_name, face in element.get("faces", {}).items():
            path = tex_path(face["texture"])
            if path is None or not os.path.exists(path):
                continue
            key = (path, tuple(face["uv"]))
            if key not in texture_cache:
                texture_cache[key] = average_color(path, face["uv"])
            color = texture_cache[key]
            if color[3] == 0:
                continue
            corners = []
            for corner in FACE_CORNERS[face_name]:
                world = [frm[i] + (to[i] - frm[i]) * corner[i] for i in range(3)]
                # 居中到模型中心，便于对称摆放在画布里
                centered = [world[i] - 8.0 for i in range(3)]
                corners.append(transform(centered))
            depth = sum(c[2] for c in corners) / 4.0
            faces.append((depth, corners, color))

    # 画家算法：远处先画
    faces.sort(key=lambda item: -item[0])

    width = height = int(16 * scale + pad * 2)
    canvas = [[(34, 32, 42, 255) for _ in range(width)] for _ in range(height)]
    cx, cy = width / 2.0, height / 2.0

    for depth, corners, color in faces:
        # 正交投影：只取 x 与 -y，z 仅用于排序
        pts = [(cx + c[0] * scale, cy - c[1] * scale) for c in corners]
        area = 0.0
        for i in range(len(pts)):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % len(pts)]
            area += x0 * y1 - x1 * y0
        if area >= 0:
            continue  # 背面剔除

        # 按深度轻微压暗，制造层次
        shade = 1.0 - min(0.35, max(0.0, (depth + 12.0) / 40.0))
        fill = (int(color[0] * shade), int(color[1] * shade), int(color[2] * shade), 255)

        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        min_x, max_x = int(min(xs)), int(math.ceil(max(xs)))
        min_y, max_y = int(min(ys)), int(math.ceil(max(ys)))
        for py in range(max(0, min_y), min(height, max_y + 1)):
            for px in range(max(0, min_x), min(width, max_x + 1)):
                inside = False
                j = len(pts) - 1
                for i in range(len(pts)):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi) + xi):
                        inside = not inside
                    j = i
                if inside:
                    canvas[py][px] = fill

    write_png(out_path, width, height, canvas)
    print("rendered", os.path.basename(out_path), width, "x", height,
          "faces:", len(faces))


def main():
    models = os.path.join(ASSETS, "models", "item")
    render(os.path.join(models, "butterfly_jar.json"),
           os.path.join(PROJECT_ROOT, "preview_model_jar_0.png"))
    render(os.path.join(models, "butterfly_jar_5.json"),
           os.path.join(PROJECT_ROOT, "preview_model_jar_5.png"))


if __name__ == "__main__":
    main()
