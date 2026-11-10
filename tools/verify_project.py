"""Static consistency checks for the Magic Induction mod.

This replaces the parts of a compile that can be replaced without a JDK:
JSON validity, registry-id <-> asset/data file correspondence, lang key coverage,
texture path resolution, and crude Java bracket balance.

What it CANNOT do: type checking, API signature verification, or class loading.
Those still require `gradlew build` on a machine with JDK 17.
"""

import json
import os
import re
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(PROJECT, "src", "main", "resources")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "hjz", "magicinduction")
ASSETS = os.path.join(RES, "assets", "magicinduction")
DATA = os.path.join(RES, "data")

errors = []
warnings = []
notes = []


def fail(message):
    errors.append(message)


def warn(message):
    warnings.append(message)


def walk(root, suffix):
    found = []
    for base, _, files in os.walk(root):
        for name in files:
            if name.endswith(suffix):
                found.append(os.path.join(base, name))
    return sorted(found)


def rel(path):
    return os.path.relpath(path, PROJECT).replace("\\", "/")


# ---------------------------------------------------------------- 1. JSON valid
json_files = walk(RES, ".json")
parsed = {}
for path in json_files:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            parsed[os.path.normpath(path)] = json.load(handle)
    except json.JSONDecodeError as exc:
        fail(f"JSON 语法错误 {rel(path)}: {exc}")
notes.append(f"JSON 文件 {len(json_files)} 个，解析成功 {len(parsed)} 个")


def doc_at(path):
    return parsed.get(os.path.normpath(path))

# ------------------------------------------------- 2. models reference textures
texture_keys = {"layer0", "layer1", "all", "top", "bottom", "side", "front", "particle", "texture"}
model_refs = []
for path, doc in parsed.items():
    if "/models/" not in path.replace("\\", "/"):
        continue
    textures = doc.get("textures", {})
    for key, value in textures.items():
        if key not in texture_keys:
            continue
        if not value.startswith("magicinduction:"):
            continue
        tex_path = os.path.join(ASSETS, "textures", value.split(":", 1)[1] + ".png")
        model_refs.append(value)
        if not os.path.isfile(tex_path):
            fail(f"贴图缺失: {value} (被 {rel(path)} 引用)")

# ------------------------------------- 3. blockstates reference existing models
for path, doc in parsed.items():
    if "/blockstates/" not in path.replace("\\", "/"):
        continue
    variants = doc.get("variants", {})
    for state_key, entry in variants.items():
        entries = entry if isinstance(entry, list) else [entry]
        for item in entries:
            model = item.get("model")
            if not model or not model.startswith("magicinduction:"):
                continue
            target = os.path.join(ASSETS, "models", model.split(":", 1)[1] + ".json")
            if not os.path.isfile(target):
                fail(f"方块状态引用了不存在的模型: {model} (在 {rel(path)})")

# ----------------------------------------------- 4. registry ids from Java code
item_names, block_names, entity_names = set(), set(), set()
for path in walk(JAVA, ".java"):
    source = open(path, "r", encoding="utf-8").read()
    normalised = rel(path)
    for name in re.findall(r'(?:ITEMS|BLOCKS)\.register\("([a-z0-9_]+)"', source):
        if "Blocks.java" in normalised:
            block_names.add(name)
        else:
            item_names.add(name)
    for name in re.findall(r'(?<![A-Za-z0-9_])ENTITIES\.register\("([a-z0-9_]+)"', source):
        entity_names.add(name)
    for name in re.findall(r'BLOCK_ENTITIES\.register\("([a-z0-9_]+)"', source):
        block_names.add(name)

notes.append(f"注册: 方块 {len(block_names)} / 物品 {len(item_names)} / 实体 {len(entity_names)}")

# -------------------------------------- 5. every block needs state + loot + tag
# 声明了 noLootTable() 的方块（例如流体方块）挖掉本就不该掉落任何东西，
# 没有战利品表是正确状态，不算缺失。
_no_loot_blocks = set()
_blocks_src = ""
for _p in walk(JAVA, ".java"):
    if _p.endswith("ModBlocks.java"):
        _blocks_src = open(_p, encoding="utf-8").read()
        break
for _chunk in _blocks_src.split("BLOCKS.register(")[1:]:
    _m = re.search(r'"([a-z0-9_]+)"', _chunk)
    if _m and "noLootTable()" in _chunk:
        _no_loot_blocks.add(_m.group(1))

for name in sorted(block_names):
    if not os.path.isfile(os.path.join(ASSETS, "blockstates", name + ".json")):
        fail(f"方块 {name} 缺少 blockstates/{name}.json")
    if name in _no_loot_blocks:
        continue
    loot = os.path.join(DATA, "magicinduction", "loot_tables", "blocks", name + ".json")
    if not os.path.isfile(loot):
        fail(f"方块 {name} 缺少战利品表 loot_tables/blocks/{name}.json")

# --------------------------------------- 6. every item needs an item-side model
for name in sorted(item_names):
    has_item_model = os.path.isfile(os.path.join(ASSETS, "models", "item", name + ".json"))
    is_block_item = name in block_names
    if not has_item_model and not is_block_item:
        fail(f"物品 {name} 缺少 models/item/{name}.json")

# ------------------------------------------------ 7. entity needs a texture
for name in sorted(entity_names):
    tex = os.path.join(ASSETS, "textures", "entity", name + ".png")
    if not os.path.isfile(tex):
        fail(f"实体 {name} 缺少 textures/entity/{name}.png")

# ---------------------------------------------------------- 8. lang key parity
lang_paths = {
    code: os.path.join(ASSETS, "lang", code + ".json")
    for code in ("en_us", "zh_cn")
}
langs = {code: doc_at(path) or {} for code, path in lang_paths.items()}
en_keys, zh_keys = set(langs["en_us"]), set(langs["zh_cn"])
for key in sorted(en_keys - zh_keys):
    warn(f"语言键缺 zh_cn 翻译: {key}")
for key in sorted(zh_keys - en_keys):
    warn(f"语言键缺 en_us 原文: {key}")

# ------------------------------------------- 9. translatable keys used in code
used_keys = set()
for path in walk(JAVA, ".java"):
    source = open(path, "r", encoding="utf-8").read()
    used_keys.update(re.findall(r'translatable\("([a-zA-Z0-9_.]+)"', source))
concatenated_keys = 0
for key in sorted(used_keys):
    if key.endswith("."):
        # 正则只能抓到字符串字面量前缀，这类键由第 16 项的 Ponder 检查按页覆盖
        concatenated_keys += 1
        continue
    if key not in en_keys:
        fail(f"代码使用的语言键在 en_us 里不存在: {key}")
if concatenated_keys:
    notes.append(f"运行时拼接的语言键 {concatenated_keys} 组（由 Ponder 检查覆盖）")

# ------------------------------------------- 10. registration name coverage
for name in sorted(block_names):
    block_key = f"block.magicinduction.{name}"
    if block_key not in en_keys:
        fail(f"方块 {name} 缺少语言键 {block_key}")
for name in sorted(item_names - block_names):
    item_key = f"item.magicinduction.{name}"
    if item_key not in en_keys:
        fail(f"物品 {name} 缺少语言键 {item_key}")
for name in sorted(entity_names):
    entity_key = f"entity.magicinduction.{name}"
    if entity_key not in en_keys:
        fail(f"实体 {name} 缺少语言键 {entity_key}")

# --------------------------------------- 11. recipes must target real items
for path in walk(os.path.join(DATA, "magicinduction", "recipes"), ".json"):
    doc = doc_at(path) or {}
    result = doc.get("result", {})
    target = result.get("item") or result.get("tag")
    if target and target.startswith("magicinduction:"):
        if target.split(":", 1)[1] not in item_names:
            fail(f"配方产物未注册: {target} (在 {rel(path)})")
    for key, value in doc.get("key", {}).items():
        ref = value.get("item", "")
        if ref.startswith("magicinduction:") and ref.split(":", 1)[1] not in item_names:
            fail(f"配方材料未注册: {ref} (在 {rel(path)})")

# --------------------------------- 12. block tag values must be real blocks
for path in walk(os.path.join(DATA, "magicinduction", "tags"), ".json"):
    doc = doc_at(path) or {}
    for value in doc.get("values", []):
        if isinstance(value, str) and value.startswith("magicinduction:"):
            if value.split(":", 1)[1] not in block_names:
                fail(f"标签引用了未注册方块: {value} (在 {rel(path)})")

# ------------------------------------ 13. minecraft tags must list our own blocks
for name in ("mineable/pickaxe", "needs_iron_tool"):
    path = os.path.join(DATA, "minecraft", "tags", "blocks", name + ".json")
    doc = doc_at(path)
    if doc is None:
        fail(f"缺少原版标签文件 minecraft/tags/blocks/{name}.json")
        continue
    listed = {v.split(":", 1)[1] for v in doc.get("values", []) if isinstance(v, str)}
    missing = block_names - listed
    if missing:
        warn(f"原版标签 {name} 未覆盖方块: {sorted(missing)}")

# ---------------------- 14. model overrides must point at existing models
# 玻璃罐用 overrides 按蝴蝶数切换 6 档模型。文件名写错的话，游戏里不会崩，
# 而是静默退回默认档——即"罐子永远显示空的"。这种失败没有报错，只能靠静态检查兜住。
for path, doc in parsed.items():
    normalised = path.replace("\\", "/")
    if "/models/" not in normalised:
        continue
    for entry in doc.get("overrides", []) or []:
        model = entry.get("model")
        if model and model.startswith("magicinduction:"):
            target = os.path.join(ASSETS, "models", model.split(":", 1)[1] + ".json")
            if not os.path.isfile(target):
                fail(f"模型 override 指向不存在的模型: {model} (在 {rel(path)})")

# ---------------- 15. element faces must reference declared texture variables
# 3D 模型里 faces 用 "#变量名" 引用 textures 段。变量名拼错时客户端会加载失败并
# 显示紫黑格子，但只在客户端暴露，服务端测试查不出来。
for path, doc in parsed.items():
    normalised = path.replace("\\", "/")
    if "/models/" not in normalised:
        continue
    declared = set((doc.get("textures") or {}).keys())
    for element in doc.get("elements", []) or []:
        for face_name, face in (element.get("faces") or {}).items():
            ref = face.get("texture", "")
            if ref.startswith("#") and ref[1:] not in declared:
                fail(f"模型面引用了未定义的贴图变量 {ref} (在 {rel(path)} 的 {face_name} 面)")

# -------------------------- 16. every ponder page has both lang entries
# PonderEntries 里写了"每个物品有几页"，lang 里必须有对应 key。
# 少一个 key 不会崩，界面会直接显示 ponder.magicinduction.xxx.p2.b 这样的原始键名，
# 属于最容易被漏掉、又最难看的一类问题。
ponder_java = os.path.join(JAVA, "client", "PonderEntries.java")
if os.path.isfile(ponder_java):
    ponder_source = open(ponder_java, "r", encoding="utf-8").read()
    ponder_pairs = re.findall(r'build\("([a-z0-9_]+)",\s*(\d+)\)', ponder_source)
    if not ponder_pairs:
        warn("PonderEntries.java 里没找到任何 build(id, count) 调用，Ponder 检查形同虚设")
    page_total = 0
    for item_id, count in ponder_pairs:
        for index in range(1, int(count) + 1):
            page_total += 1
            for suffix, label in (("t", "标题"), ("b", "正文")):
                key = f"ponder.magicinduction.{item_id}.p{index}.{suffix}"
                for code, doc in langs.items():
                    if key not in doc:
                        fail(f"Ponder {label}缺语言键 {key}（{code}）")
    notes.append(f"Ponder 条目 {len(ponder_pairs)} 个物品 / {page_total} 页")
else:
    warn("未找到 client/PonderEntries.java，跳过 Ponder 语言键检查")

# ------------------------------------------------- 17. crude Java brace balance
for path in walk(JAVA, ".java"):
    source = open(path, "r", encoding="utf-8").read()
    stripped = re.sub(r'"(\\.|[^"\\])*"', '""', source)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    stripped = re.sub(r"/\*.*?\*/", "", stripped, flags=re.S)
    for open_char, close_char, label in (("{", "}", "花括号"), ("(", ")", "圆括号")):
        delta = stripped.count(open_char) - stripped.count(close_char)
        if delta != 0:
            fail(f"{rel(path)} {label}不平衡: {delta:+d}")
    if not source.rstrip().endswith("}"):
        fail(f"{rel(path)} 结尾不是右花括号")
    if "package com.hjz.magicinduction" not in source:
        fail(f"{rel(path)} 包声明异常")

java_files = walk(JAVA, ".java")

# --------------------------------------- 18. missing-import heuristic (no javac)
JAVA_LANG = {
    "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Character",
    "Object", "Math", "System", "Exception", "RuntimeException", "IllegalArgumentException",
    "Override", "Deprecated", "SuppressWarnings", "Runnable", "Iterable", "Comparable", "Void",
}
own_classes = {os.path.splitext(os.path.basename(path))[0] for path in java_files}

for path in java_files:
    source = open(path, "r", encoding="utf-8").read()
    imports = set()
    for full in re.findall(r"^import\s+(?:static\s+)?([\w.]+);", source, re.M):
        imports.add(full.rsplit(".", 1)[1])
    body = re.sub(r'^import\s+.*$', "", source, flags=re.M)
    body = re.sub(r'"(\\.|[^"\\])*"', '""', body)
    body = re.sub(r"//[^\n]*", "", body)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)

    local_types = set(re.findall(r"\b(?:class|record|interface|enum)\s+([A-Z][A-Za-z0-9_]*)", body))
    suspects = set()
    for token in re.findall(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)\b", body):
        if len(token) == 1:
            continue
        if re.fullmatch(r"[A-Z0-9_]+", token):
            continue
        if token in imports or token in own_classes or token in JAVA_LANG or token in local_types:
            continue
        suspects.add(token)
    for token in sorted(suspects):
        warn(f"{rel(path)} 疑似缺少 import 或拼写错误: {token}")

notes.append(f"Java 源文件 {len(java_files)} 个")
notes.append(f"资源文件 {len(json_files)} 个 JSON, {len(walk(RES, '.png'))} 个 PNG")

# --------------------------------------------------------------- report
print("=" * 62)
print("Magic Induction 静态一致性自检")
print("=" * 62)
for note in notes:
    print("  ·", note)
print()
if errors:
    print(f"错误 {len(errors)} 项:")
    for item in errors:
        print("  [X]", item)
else:
    print("错误 0 项")
print()
if warnings:
    print(f"警告 {len(warnings)} 项:")
    for item in warnings:
        print("  [!]", item)
else:
    print("警告 0 项")
print()
print("结论:", "通过（可进入构建阶段）" if not errors else "未通过，需修复")
sys.exit(1 if errors else 0)
