# 项目约定

## 版本号：每改一次就升一

**凡是动了 `src/` 下的代码或资源，交付前必须把 `gradle.properties` 的 `mod_version` 的第三位 +1，
然后重新构建、拷进 `dist/`。** 目的是让每个 jar 都能和具体改动一一对应，不会出现"两个 1.9.0
但内容不一样"的情况。

一键完成（升版本 → build → 出 dist jar 和 zip）：

```bash
python3 tools/release.py
```

只重建不升版本（例如连续调试中间态，最后统一升一次）：

```bash
python3 tools/release.py --no-bump
```

规则细节：

- 只做纯代码修复 / 微调 → patch +1（1.9.1 → 1.9.2）。**不要**停留在同一个版本号上覆盖旧包。
- 新增玩法或破坏存档兼容 → 由我提议升 minor（1.9.x → 1.10.0），经你确认再动。
- 旧版本的 jar / zip **不删**，`dist/` 就是完整的历史序列。
- 每轮改动同时在 `.ai-memory/<日期>/daily.md` 记一条，写清改了什么、为什么。

### 版本序列

| 版本 | 说明 |
|---|---|
| 1.9.0 | 刷怪蛋放出的蝴蝶没有模型（模型被下沉 0.42 格埋进方块）—— **坏包，勿用** |
| 1.9.1 | 修渲染：`MODEL_DROP` → `MODEL_LIFT = 0.20`，`translate(0, -MODEL_LIFT, 0)` |
| 1.9.2 | 碰撞箱 0.6×0.45 → **0.65×0.30**，抬升 0.20 → **0.095**，模型在框内垂直居中 |
| 1.9.3 | 魔法树苗物品模型 `block/cross` → `item/generated`，手持/物品栏与原版树苗一致 |
| 1.9.4 | 魔力诱导机支持自动化输入：按面过滤、禁止外部抽取、换罐返还空罐 |
| 1.9.5 | 新增 mod 图标 `icon.png`（由蝴蝶贴图拼出的 256×256 双翅图），mods.toml 声明 `logoFile` |
| 1.9.6 | 新增魔法原液流体（流体方块 / 桶 / 客户端渲染）+ 两条 Create 配方：粉尘水洗成晶体、晶体加热搅拌出原液 |

## 构建环境

- JDK 17（`JAVA_HOME` → `/root/.sdkman/candidates/java/17.0.21.0+9-zulu`），Gradle 8.8 wrapper 已缓存。
- `./gradlew build` 约 1–9 分钟（首次需建缓存）。产物 `build/libs/magicinduction-<ver>.jar`。
- 无 GPU，客户端验证用软件渲染：

```bash
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --args="--quickPlaySingleplayer 新的世界" --console=plain
```

截图落在 `run/screenshots/`，日志 `run/logs/latest.log`。
