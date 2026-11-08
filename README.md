# Magic Induction · 魔力诱导

Minecraft **Java 版 1.20.1 / Forge 47.x** 的小型机制模组：魔力蝴蝶把石头转成基础魔力矿，玻璃罐捕获上限 5 只，装在魔力诱导机器上沿朝向定向转化一整片区域。

- modid：`magicinduction`
- 版本：`1.2.4`
- 依赖：Forge `47.4.28`（`minecraft_version_range=[1.20.1,1.21)`）
- 许可：MIT

**1.1.0 新增**：魔力蝴蝶刷怪蛋 · 3D 立体玻璃罐（6 档容量外观）· 按住 W 查看用途的 Ponder 说明书

**1.1.1 修复**：右键蝴蝶无反应（模型悬在碰撞箱上方 + 准星容差为 0）· 蝴蝶重做（4 倍几何 / 26×20 像素翅面 / 停驻收翅）

**1.2.0 外观重构**：蝴蝶改成参考 Bok's Banging Butterflies 的单片侧翅结构——每侧一片 12×17 薄翅，前翅与后翅画在同一张图里、靠 alpha 掏轮廓，贴图从 256×256 降到 64×64。翅展 0.84 格，与 0.8×0.7 碰撞箱对齐。

**1.2.1 细节调整**：去掉翅缘深色描边（贴图 alpha 外圈那圈暗线 + 翅侧薄边的深紫底）· 触角长度 6 → 3 单位 · 头上去掉复眼亮斑 · 翅脉改为只暗一档，不再出现黑格窗

**1.2.2 换配色**：贴图配色改为参考 mod 的 `butterfly_admiral`（深蓝灰翅面 + 橙红外缘带 + 橙红斜带 + 白蓝斑点），身体同步改灰蓝。几何与 12×17 单侧薄翅结构不变。

**1.2.3 配色回滚**：贴图配色回到原先的紫色系（DEEP `72,46,112` → MID `120,70,190` → BRIGHT `168,110,232` → GLOW `214,176,255`）。几何、触角长度、无复眼、无描边全部沿用 1.2.1。

**1.2.5 修掉翅膀方框**：`ModelPart$Cube` 的四条侧面 UV 全部落在 `y=v+d` 那一行（h=1 时就只有一行），之前把整块 UV 铺色时连这一行也铺了，于是翅膀外沿出现一圈矩形边框。现在侧面保持 alpha=0。同时把 UP / DOWN 面对调——玩家从上往下看到的是 UP 面，之前原色画在 DOWN、压暗的 0.72 倍画在 UP，所以整只蝴蝶偏暗。

**1.2.4 去掉翅膀方框**：翅膀盒子（12×17×1）的四条薄侧面原本上了颜色，会沿盒子外沿勾出一整圈矩形边框；现在留全透明，只保留被 alpha 挖出轮廓的上下两面。这是游戏内看到的"翅膀旁边的紫色方框"，与贴图描边无关。

---

## 1. 构建

**已编译通过**：`BUILD SUCCESSFUL in 4m 33s`（Gradle 8.8 / JDK 17.0.15 / Forge 1.20.1-47.4.28）。

产物：`build/libs/magicinduction-1.0.0.jar`，45 KB，76 个条目（16 class + 30 assets + 21 data + 3 META-INF），`mods.toml` 占位符已正确展开为 `version="1.0.0"` / `license="MIT"`。

### 环境（本机已就绪）

| 组件 | 位置 / 版本 | 备注 |
|---|---|---|
| JDK 17 | `%APPDATA%\.minecraft\runtime\java-runtime-beta`（17.0.15） | **Minecraft 官方启动器自带**，含 `javac`，可直接当 JDK 用，不必单独安装 |
| Gradle | 8.8 | `gradle/` + `gradlew` + `gradlew.bat` 已随工程提供（取自官方 MDK 47.4.28） |
| Forge | 1.20.1-47.4.28 | 与 `gradle.properties` 一致 |

### 构建命令

Windows CMD：
```bat
set JAVA_HOME=%APPDATA%\.minecraft\runtime\java-runtime-beta
gradlew.bat build
```

Git Bash：
```bash
export JAVA_HOME="$APPDATA/.minecraft/runtime/java-runtime-beta"
./gradlew build
```

产物在 `build/libs/magicinduction-1.0.0.jar`，丢进 `.minecraft/mods/` 即可。

> **踩坑记录**：不要在构建时设置 `http_proxy` / `https_proxy` 环境变量。实测（`HttpProbe` 对照实验）Java **直连** Forge Maven 返回 200，走代理反而 `SSLHandshakeException: Remote host terminated the handshake`。Java 不读环境变量代理，只能读 `-Dhttps.proxyHost` 系统属性，混着设置会互相干扰。

### 跑起来看效果

```bash
./gradlew runClient      # 启动带 mod 的开发客户端
./gradlew runServer
```

首次启动会下载 Minecraft 资源与映射，耗时较长且必须能访问 Forge Maven。

### 关于 4 条编译警告

```
ResourceLocation(String,String) 已过时  ×3   (ManaButterflyModel / ManaButterflyRenderer / ModTags)
FMLJavaModLoadingContext.get() 已过时   ×1   (MagicInduction)
```

**不是错误，不影响运行，也不该改。** 这两组 API 的替代品（`ResourceLocation.fromNamespaceAndPath`、`@Mod` 构造器注入 `IEventBus`）是 1.20.5 / 1.21 才引入的，在 1.20.1 里没有对应替代，官方 MDK 1.20.1 模板同样这么写。升级到 1.21 时再统一改。

---

## 2. 游戏内流程

| 步骤 | 操作 | 说明 |
|---|---|---|
| 1 | 下矿到 Y < 48 的暗处 | 蝴蝶只在光照 < 8 的地方生成 |
| 2 | 听见翅膀声，用玻璃罐右键捕捉 | 罐子提示「蝴蝶：n / 5」，装满 5 只会附魔光效 |
| 3 | 合成诱导机器，摆下并转向想去的那面石壁 | 朝 NPC 反方向放置，右键可看状态 |
| 4 | 右键装罐（手持罐右键机器） | 罐内蝴蝶数量 = 产能倍数 |
| 5 | 右键塞入魔力粉尘 | **每转化 1 块消耗 1 个**，没粉尘就停 |
| 6 | 看着前方 8×5×3 区域一块块亮成矿 | 每块伴随紫水晶音效 + 末地烛粒子 |

补充操作：

- **取出罐子**：潜行 + 空手右键
- **查看状态**：空手右键（显示蝴蝶数、粉尘数、已转化块数）
- **拆机器**：罐子与粉尘会掉出来，不会蒸发
- **繁殖蝴蝶**：手持魔力结晶喂两只
- **刷怪蛋**：创造模式直接右键生成一只蝴蝶，跳过寻找环节
- **按住 W 查看用途**：物品栏里鼠标悬停任意本 mod 物品 → 按住 `W` → 弹出图文说明书

### 3D 玻璃罐

罐子不是平面图标，是用 `elements` 定义的真 3D 模型（罐身 → 收肩 → 瓶颈 → 木塞），
罐内蝴蝶数量通过 `ItemProperties` 的 `butterflies` 属性在 6 个模型变体间切换，
罐子会随着蝴蝶变多而"装满"。

模型由 `tools/gen_item_models.py` 生成（6 个变体共享 `butterfly_jar_base.json` 的几何），
不用手写 6 份 elements。`tools/preview_model.py` 可以在不开游戏的情况下渲染等轴测预览，
用于检查几何比例是否成立。

### Ponder 说明书

仿 Create 的 "Ponder"，但是**简化版**：没有 3D 场景演示和相机运镜，
做的是分页图文说明（每页一个标题 + 一段正文 + 物品大图标）。

- **触发**：物品栏界面里，鼠标悬停物品 → 按 `W`
- **翻页**：`←` `→` 或 `A` `D`
- **关闭**：`ESC`，关闭后回到物品栏而不是退回游戏
- **覆盖物品**：6 个，共 13 页

实现要点（`client/ClientForgeEvents.java`）：
- 怎么知道鼠标悬停哪个物品 → 监听 `ItemTooltipEvent` 记录最近一次被渲染 tooltip 的物品栈，
  绕开 `AbstractContainerScreen.getSlotUnderMouse()` 是 protected 的问题，不需要 AccessTransformer
- 记录会过期 → 300ms 时间窗判定悬停是否仍然有效
- 创造模式搜索框要能打字母 W → 检测到 `EditBox` 有焦点时直接放行

### 合成表

| 物品 | 配方 |
|---|---|
| 玻璃罐 | 7 × 玻璃（U 形） |
| 诱导机器 | 四角铁锭 ×4 + 玻璃 ×4 + 魔力结晶 ×1（中心） |
| 魔力矿 | 掉落 1 魔力结晶 + 1 魔力粉尘，时运加成 |
| 刷怪蛋 | 无配方（创造模式限定） |

---

## 3. 数值表（全部 [PLACEHOLDER]，未经 playtest）

| 参数 | 取值 | 代码位置 | rationale | 验证方式 |
|---|---|---|---|---|
| 罐容量 | 5 | `ButterflyJarItem.MAX_BUTTERFLIES` | 需求硬约束 | — |
| 每块进度需求 | 100 ticks | `PROGRESS_PER_BLOCK` | 5 只 = 1 块/秒；再快玩家看不见过程，再慢等待变惩罚 | 计时一次完整转化，问测试者"太快/太慢" |
| 粉尘消耗 | 1 / 块 | `DUST_PER_BLOCK` | 见第 4 节，这是平衡的地基 | 产出一致性核对 |
| 覆盖范围 | 8 长 × 5 宽 × 3 高 = 120 格 | `RANGE_*` | 120 格 ≈ 2 分钟清空，刚好一条矿脉而非一座山 | 让测试者摆一次，看是否想"再大点" |
| 蝴蝶野生转化 | 每 200 ticks 掷 1/12，半径 6 | `ManaButterfly` | 平均 2 分钟一次，是"线索"不是"产线" | 放 20 只蝴蝶挂 1 小时看矿数 |
| 野生转化终身上限 | 6 次 | `WILD_CONVERSION_LIMIT` | 无上限即可挂机刷矿 | 同上 |
| 生成条件 | 光照 < 8 且 Y < 48 | `checkSpawnRules` | 让玩家在矿洞里遇见它，形成"这附近有矿"的线索 | 记录首个存档集满 5 只耗时，目标 40-70 分钟 |
| 生成权重 | 8（AMBIENT 类别） | `biome_modifier/spawn_mana_butterfly.json` | 参考蝙蝠档位，稀有但可遇 | 同上 |

---

## 4. 经济模型：为什么粉尘是 1:1

你的原始设计里我写的方案是「每 10 块消耗 1 粉尘」，落地时**改成了 1:1**。理由必须说清楚：

> 若 1 粉尘 = 10 块，那么 20 块矿（掉 20 结晶 + 10 粉尘）可以供养 100 块转化，净收益 5 倍且自我循环。玩家挖一座山能换来五座山，10 分钟就把矿物价值打穿。这就是典型的"source 找不到 sink"。

1:1 之后，闭环是：

```
魔力矿 → (1 结晶 + 1 粉尘)
                ↓ 粉尘
        机器每转化 1 块消耗 1 粉尘 → 再得 1 结晶 + 1 粉尘
```

净效果是 **2 倍产出放大器**，且**产能上限 = 你亲手挖过的矿量**。机器永远跑不过手工挖矿，只能改变挖矿的"空间形状"——这才是它该有的定位。

结晶的 sink：合成机器、繁殖蝴蝶（每对 2 个）。所以结晶也不会无限堆积。

---

## 5. 与原始需求的 3 处设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 粉尘消耗比例 | 1 块 : 1 粉尘（原提案 10:1） | 见第 4 节，10:1 会通胀 |
| 罐子槽位 | 1 个罐装 5 只（你已确认） | 简化机器结构，罐子本身成为收集目标 |
| 繁殖食物 | **魔力结晶**（你选了"可繁殖"） | 把繁殖挂到模组自身经济上，每次繁殖都是结晶 sink；用花繁殖会让蝴蝶失去稀缺性 |

---

## 6. 验证状态：已验证 / 未验证

### 已验证（可复现）

| 项 | 方式 | 结果 |
|---|---|---|
| **Gradle 编译** | `./gradlew build`（Gradle 8.8 / JDK 17.0.15 / Forge 47.4.28） | **BUILD SUCCESSFUL in 4m 33s** |
| **jar 产物结构** | `unzip -l` 检查 | 45 KB / 76 条目，四类齐全 |
| **`mods.toml` 占位符展开** | `unzip -p ... META-INF/mods.toml` | `version="1.0.0"`、`license="MIT"`、`[47,)`、`[1.20.1,1.21)` 全部正确，无残留 `${}` |
| JSON 合法性 | `python tools/verify_project.py` | 19/19 通过 |
| 注册名 ↔ 方块状态 / 模型 / 战利品表 / 语言键对应 | 同上 | 0 错误 |
| 贴图引用真实存在 | 同上 | 8/8 张 PNG 命中 |
| 语言键中英一致 + 代码用到的键全部存在 | 同上 | 0 缺失 |
| Java 括号平衡、包声明、疑似缺 import | 同上 | 0 错误（2 条已确认误报） |
| 纹理可读性 | 生成 `preview_sheet.png` 目视检查 | 8 张贴图均可辨识 |

编译输出仅有 4 条 deprecation 警告（见第 1 节），**0 个编译错误**。

**服务端冒烟测试已通过**（`./gradlew runServer`，1.20.1 专用服务端，加载完整世界）：

```
[modloading-worker-0/INFO] [co.hj.ma.MagicInduction/]: [Magic Induction] loaded.
[Server thread/INFO] [minecraft/DedicatedServer]: Done (13.753s)! For help, type "help"
```

| 检查项 | 结果 |
|---|---|
| mod 被 ModLauncher 识别 | `Found valid mod file main with {magicinduction} mods - versions {1.1.0}` |
| 主类构造 + `DeferredRegister` 全量执行 | `[Magic Induction] loaded.`（该行仅在所有 register 调用之后打印） |
| `@EventBusSubscriber` 自动订阅 | `Auto-subscribing com.hjz.magicinduction.ModEvents to MOD` |
| 注册表状态 | block / item / entity_type / block_entity_type / recipe_type 等全部 `VANILLA -> ACTIVE` |
| 数据包运行期解析 | **0 条解析错误**（配方 / 战利品表 / 方块状态 / 生物群系修饰器均无 WARN） |
| 世界生成 + spawn 区域准备 | 完成，`Done (13.753s)` |
| ERROR / WARN 计数（排除下载与 netty 噪音） | **0 / 0** |

**客户端启动验证已通过**（`./gradlew runClient`，1.1.0 新增内容专项）：

| 检查项 | 结果 |
|---|---|
| OpenGL 后端 / 声音引擎 / 纹理图集 | 全部正常初始化，`blocks.png-atlas` 建成 |
| 3D 罐子模型烘焙（7 个 JSON：base + 6 档） | **无 `Unable to load model`、无 `Missing model`** |
| 模型 `overrides` 的 `butterflies` 属性注册 | **无 `Unknown property`**（属性名与模型 predicate 对得上） |
| `jar_glass` / `jar_cork` / `jar_contents_0-5` 贴图 | 全部命中，无 `Missing texture` |
| 刷怪蛋复用 `minecraft:item/template_spawn_egg` | 正常，双色染色由 Forge 自动接管 |
| 过滤噪音后的 WARN / ERROR | **0 / 0** |

静态自检脚本同步扩充了 3 项检查，专防"不崩但坏"的静默失效：

- `overrides` 指向的模型文件必须存在（写错不会崩，只会永远显示空罐）
- 模型 `elements` 的 `#贴图变量` 必须在 `textures` 段声明（写错只在客户端显示紫黑格子）
- `PonderEntries.build(id, count)` 声明的每一页，中英 lang 都必须有 `.t` / `.b` 键

### 未验证（只能靠肉眼）

| 项 | 为什么没做 | 怎么补 |
|---|---|---|
| 蝴蝶渲染朝向、翅膀扑动幅度 | 需要目视 | `./gradlew runClient`，`/summon` 后看 |
| **左右翅图案是否镜像正确** | 预览脚本按"u 沿 +x"的假设拼图，若实际相反，翅尖会朝内 | 若两翅图案方向一致（不成镜像），把 `paint_wing(0, 20, mirror=True)` 的 `True` 改成 `False` |
| **3D 罐子在物品栏里的实际观感** | 已用 `tools/preview_model.py` 离线渲染验证过形状，但光照与贴图细节只有实机为准 | 打开物品栏看罐子是否会随蝴蝶数变化而"变满" |
| **Ponder 界面的排版** | 文字换行、行距、页码位置需要目视 | 悬停物品按 `W`，翻完 13 页 |
| 机器转化速率手感 | 需要人类测试者 | 按第 3 节验证方式 |
| 生物群系生成是否真的刷出蝴蝶 | 生成率约每 3-4 区块 1 只，短时冒烟测不出 | 生存模式探索 10+ 区块，或 `/execute as @e[type=magicinduction:mana_butterfly] run say` 计数 |
| 与其它 mod 的兼容 | 未测 | 重点看生物生成权重的挤出效应 |
| 多人服务器表现 | 单人环境未测并发 | BE tick 只在服务端跑，理论无虞，但需实测 |

**服务端与客户端的加载链路都已经干净了：注册、数据包、模型烘焙、贴图、属性注册全部无异常。** 剩下的全是"好不好看、手感对不对"——这类只能上手玩，没法用日志证明。

---

## 7. 已知限制

1. **只有一种魔力矿**，不区分深板岩变体。深板岩区域被转化后会出现石头质感的矿，视觉上不协调。
2. **没有 GUI**，全部通过右键 + 动作栏文字交互。好处是不用写菜单，坏处是塞粉尘要一次一次右键。
3. **无自定义音效资源**，复用原版紫水晶与悦灵音效。
4. **蝴蝶没有腿**（真蝴蝶飞行时也收着，且 0.9 格宽下腿是噪点）；触角有做，2×2×14 单位。
5. **机器没有"运转中"贴图变化**，只靠粒子和音效反馈。若你觉得不够明显，这是下个版本第一优先级。
6. **区块卸载时机器停止**（BE tick 的正常行为），回来会继续。
7. **Ponder 是简化版**：只有分页图文，没有 Create 那种 3D 场景演示、相机运镜和时间轴动画。
8. **Ponder 只能在 GUI 里触发**（物品栏 / 容器界面悬停物品按 W），不能在游戏世界里手持物品按 W——那是前进键。
9. **3D 罐子的内容层贴着罐身前壁外侧**，不是真的在罐子内部。物品栏视角下读起来是"透过玻璃看到蝴蝶"，但拿着转圈时会看出它是贴在表面的。

---

## 8. 调试命令（1.20.1 语法）

```mcfunction
# 直接召唤一只蝴蝶
/summon magicinduction:mana_butterfly ~ ~1 ~

# 拿一个装满 5 只的罐子
/give @s magicinduction:butterfly_jar{Butterflies:5}

# 拿粉尘与结晶
/give @s magicinduction:mana_dust 64
/give @s magicinduction:magic_crystal 64

# 摆一台朝东的机器
/setblock ~ ~ ~ magicinduction:mana_inducer[facing=east]

# 造一面测试石墙（机器正前方 8x5x3）
/fill ~1 ~ ~1 ~8 ~2 ~5 minecraft:stone
```

---

## 9. 文件结构

```
magic-induction/
├── build.gradle / settings.gradle / gradle.properties
├── tools/
│   ├── gen_textures.py       # 纯标准库生成 16x16 PNG（无 PIL 依赖）
│   ├── gen_item_models.py    # 生成 3D 罐子的 base + 6 档变体模型 JSON
│   ├── gen_preview.py        # 贴图拼贴预览
│   ├── preview_model.py      # 等轴测渲染模型预览（不开游戏检查几何）
│   └── verify_project.py     # 静态一致性自检（18 项）
└── src/main/
    ├── java/com/hjz/magicinduction/
    │   ├── MagicInduction.java        # 入口 + 注册总线
    │   ├── ModBlocks / ModItems / ModEntities / ModBlockEntities / ModCreativeTabs / ModTags
    │   ├── ModEvents.java             # 生成点 + 属性注册
    │   ├── common/
    │   │   ├── ManaButterfly.java         # 实体 AI + 野生转化
    │   │   ├── ButterflyJarItem.java      # 捕获 / NBT 计数
    │   │   ├── ManaInducerBlock.java      # 朝向 + 掉落
    │   │   └── ManaInducerBlockEntity.java# 定向转化 + 粉尘消耗 + 物品能力
    │   └── client/
    │       ├── ClientSetup.java           # 渲染器 / 模型层 / ItemProperties
    │       ├── ManaButterflyModel.java    # 蝴蝶模型
    │       ├── ManaButterflyRenderer.java # 蝴蝶渲染器
    │       ├── ClientForgeEvents.java     # 悬停记录 + W 键触发
    │       ├── PonderEntries.java         # 物品 -> 页面映射
    │       └── PonderScreen.java          # 分页图文说明书界面
    └── resources/
        ├── assets/magicinduction/         # 模型、方块状态、语言、15 张纹理
        └── data/magicinduction/           # 配方、战利品、标签、生物生成修饰器
```

---

## 10. v1.2 待办（按优先级）

1. 机器运转态视觉（贴图或发光）——反馈通道不足是当前最大体验缺口
2. 深板岩魔力矿变体
3. 粉尘批量投喂（右键一次塞一组）
4. 转化进度可视化（机器顶部纹理随进度变化）
5. 数据包可配置：把 `PROGRESS_PER_BLOCK`、`RANGE_*` 提到 Forge config，方便整合包作者调
