"""把参考 mod（butterflies-7.10.12）的蝴蝶模型按真实变换链投影出来，用来挑贴图 / 验配色。

几何完全照抄 com.bokmcdok.butterflies.client.model.ButterflyModel 的 createBodyLayer：

    body       origin (0,24,0)，盒 -5,-2,-1 尺寸 10x2x2，自身绕 Y 转 π
    left_wing  origin (0,-1,0)，盒 -9,-1, 1 尺寸 17x0x10   （零厚度，在 +z 一侧）
    right_wing origin (0,-1,0)，盒 -9,-1,-11 尺寸 17x0x10  （在 -z 一侧）
    antennae   origin (0,0,0)，两个 3x2x0，自身绕 Y 转 π

渲染器 ButterflyRenderer 在 super.render 之前 mulPose(YP(-90))，这里也加上，
否则整只蝶是"横着飞"的。

注意 PIVOT_DEG 用的是 +90 而不是参考的 -90：参考那套几何转 -90 之后头/触角落在 +z，
而 -z 才是前进方向（1.8.1 修正，见 ManaButterflyModel 的类注释）。预览要和游戏一致，
所以这里跟着取反。

用法：
    python tools/preview_ref.py <贴图路径> <输出前缀>
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_model_render as R     # 复用 png 读写 / 矩阵 / 光栅器


FLAP = 55.0      # 扇翅相位（度），和别的预览保持一致
PIVOT_DEG = 90.0 # 整棵模型的 Y 旋转：+90 让头朝 -z（前进方向）


def build(tex_scale=1.0):
    """返回 [(world 顶点 4 个, uv 4 个, 面名)]。"""
    out = []

    # 根: 整棵模型的 Y 旋转。模型空间的 +x 因此落到世界 -z（头朝前）。
    m_root = R.mat_rot("y", PIVOT_DEG)

    # body 部件：origin y=24（脚底），自身不转
    m_body = R.mat_mul(m_root, R.part_matrix((0, 24, 0), (0, 0, 0)))

    # 躯干：texOffs(0,20)，盒 -5,-2,-1 尺寸 10x2x2，部件自身绕 Y 转 π
    m_torso = R.mat_mul(m_body, R.part_matrix((0, 0, 0), (0, 180, 0)))
    for pts, uvs, name in R.quad(-5.0, -2.0, -1.0, 10.0, 2.0, 2.0, 0, 20):
        out.append(([R.mat_apply(m_torso, p) for p in pts], uvs, "body." + name))

    # 触角：texOffs(0,0)，3x2x0 两片（贴图左上角，只有 3x2 那么点内容）
    m_ant = R.mat_mul(m_body, R.part_matrix((0, 0, 0), (0, 180, 0)))
    for z0 in (-1.0, 1.0):
        for pts, uvs, name in R.quad(-8.0, -4.0, z0, 3.0, 2.0, 0.0, 0, 0):
            out.append(([R.mat_apply(m_ant, p) for p in pts], uvs, "antennae." + name))

    # 翅膀：绕 X 轴扑翼（翼面沿 z 伸出去，绕 x 转才是抬翅膀）
    for z0, tex_off, sign in ((1.0, 0, 1.0), (-11.0, 10, -1.0)):
        m_wing = R.mat_mul(m_body,
                           R.part_matrix((0, -1, 0), (sign * FLAP, 0, 0)))
        for pts, uvs, name in R.quad(-9.0, -1.0, z0, 17.0, 0.0, 10.0, 0, tex_off):
            out.append(([R.mat_apply(m_wing, p) for p in pts], uvs, "wing." + name))
    return out


def main():
    tex = sys.argv[1]
    tag = sys.argv[2]
    size, scale = 260, 150.0
    bg = (92, 100, 86, 255)

    R.TEX_W, R.TEX_H, R.TEX = R.read_png(tex)

    outs = {}
    for flap_deg, flap_tag in ((0.0, "rest"), (55.0, "flap")):
        globals()["FLAP"] = flap_deg
        for view in ("top", "front", "side"):
            img = R.render(build(), view, size, scale, bg)
            path = os.path.join(R.OUT_DIR, f"ref_{tag}_{flap_tag}_{view}.png")
            R.write_png(path, size, size, img)
            outs[(flap_tag, view)] = path

    w, h = size * 3, size * 2
    sheet = [[bg for _ in range(w)] for _ in range(h)]
    for row, flap_tag in enumerate(("rest", "flap")):
        for col, view in enumerate(("top", "front", "side")):
            _, _, img = R.read_png(outs[(flap_tag, view)])
            for y in range(size):
                for x in range(size):
                    sheet[row * size + y][col * size + x] = img[y][x]
    path = os.path.join(R.OUT_DIR, f"ref_{tag}_sheet.png")
    R.write_png(path, w, h, sheet)
    print("wrote", path)


if __name__ == "__main__":
    main()
