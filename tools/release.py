#!/usr/bin/env python3
"""构建并发布 Magic Induction：升版本号 → ./gradlew build → 出 dist 的 jar 与 zip。

用法：
    python3 tools/release.py            # patch 版号 +1 后构建并发布
    python3 tools/release.py --no-bump  # 保持当前版本，只构建 + 发布
    python3 tools/release.py --no-build # 只用 build/libs 里已有的 jar 发布

约定：每次改完代码都要让 mod_version 递增，这样 dist 里每个 jar 都能区分。
"""
import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROPS = ROOT / "gradle.properties"
DIST = ROOT / "dist"


def read_props() -> dict:
    out = {}
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def bump(version: str) -> str:
    """1.9.0 -> 1.9.1；没有 patch 段就补一个。"""
    parts = version.split(".")
    while len(parts) < 3:
        parts.append("0")
    parts[2] = str(int(re.sub(r"\D", "", parts[2]) or 0) + 1)
    return ".".join(parts[:3])


def set_version(version: str) -> None:
    text = PROPS.read_text(encoding="utf-8")
    text = re.sub(r"(?m)^mod_version=.*$", f"mod_version={version}", text)
    PROPS.write_text(text, encoding="utf-8")


def build() -> None:
    print(">>> ./gradlew build")
    r = subprocess.run(["./gradlew", "build", "--console=plain"], cwd=ROOT)
    if r.returncode != 0:
        sys.exit("构建失败，未发布")


def pack(mod_id: str, version: str) -> tuple[pathlib.Path, pathlib.Path]:
    jar_src = ROOT / "build" / "libs" / f"{mod_id}-{version}.jar"
    if not jar_src.is_file():
        sys.exit(f"找不到构建产物 {jar_src}")

    DIST.mkdir(exist_ok=True)

    # 防呆：临时探针（截图/自动停服）绝不能被发出去。
    # 1.9.10 就是这么翻的车 —— 探针混进 jar，玩家 45 tick 后游戏自己关了。
    with zipfile.ZipFile(jar_src) as zf:
        probes = [n for n in zf.namelist() if "__probe" in n]
    if probes:
        sys.exit(
            "拒绝打包：jar 里含临时探针类 " + ", ".join(probes) +
            "\n先删除 src/main/java/.../__probe/ 再重跑。"
        )

    jar_dst = DIST / jar_src.name
    shutil.copy2(jar_src, jar_dst)

    zip_path = DIST / f"MagicInduction-{version}-forge-1.20.1.zip"
    members = [
        (jar_dst, f"mods/{jar_src.name}"),
        (DIST / "INSTALL.md", "INSTALL.md"),
        (DIST / "安装说明.md", "安装说明.md"),
        (DIST / "LICENSE", "LICENSE"),
    ]
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for src, arc in members:
            if not src.is_file():
                print(f"    跳过（不存在）：{src.name}")
                continue
            # 0x800 = UTF-8 文件名标志，否则中文名在 Windows 上会变乱码
            zf.write(src, arc)
            zf.getinfo(arc).flag_bits |= 0x800
    return jar_dst, zip_path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-bump", action="store_true", help="不升版本号")
    ap.add_argument("--no-build", action="store_true", help="跳过 gradle build")
    args = ap.parse_args()

    props = read_props()
    mod_id = props["mod_id"]
    old = props["mod_version"]
    new = old if args.no_bump else bump(old)
    if new != old:
        set_version(new)
        print(f">>> 版本 {old} -> {new}")
    else:
        print(f">>> 版本保持 {new}")

    if not args.no_build:
        build()

    jar, zp = pack(mod_id, new)
    print(f">>> 已发布 {jar.relative_to(ROOT)}  ({jar.stat().st_size // 1024} KB)")
    print(f">>> 已发布 {zp.relative_to(ROOT)}  ({zp.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
