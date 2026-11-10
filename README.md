# Magic Induction · 魔力诱导

Minecraft **Java 版 1.20.1 / Forge 47.4.28** 的小型机制模组：魔力蝴蝶把圆石转成基础魔力矿，玻璃罐捕获上限 5 只，装在魔力诱导机器上，把紧贴机器四个侧面的圆石逐块转成矿。

- modid：`magicinduction`
- 版本：`1.9.12`
- 依赖：Forge `47.4.28`（`minecraft_version_range=[1.20.1,1.21)`）
- 软联动：机械动力 Create 6.x（三条配方 + 魔法蛋糕喂烈焰人燃烧室。不装也能正常启动，紫色火焰只在装了 Create 时启用）
- 许可：MIT

---

## 1. 玩法循环

```
魔法树 ──┬─→ 魔法树叶 ──→ 魔力蝴蝶（自然生成 / 刷怪蛋 / 繁殖）
         └─→ 魔法原木 ──→ 木板 ──→ 木棍 ──→ 五件木制工具

魔力蝴蝶 ──→ 玻璃罐（≤5 只）──→ 魔力诱导机器 ←── 魔力粉尘（1 块 1 个）
                                      │
                          四个侧面的圆石 ↓
                                  基础魔力矿
                                      │ 铁镐挖掘
                          ┌───────────┴───────────┐
                    魔力结晶 ×1              魔力粉尘 ×1
                          │                       │
              ┌───────────┴─────────┐             └──→ 回到机器
      Create 加热搅拌         繁殖 / 吸引蝴蝶
              ↓
        魔法原液 250 mB
              │ 注液器（spout）
              ↓
         魔法蛋糕
              │ 右键烈焰人燃烧室
              ↓
      这一炉燃料烧出紫色火焰
```

粉尘 1:1 自循环，真正被消耗的只有圆石。

### 游戏内流程

| 步骤 | 操作 | 说明 |
|---|---|---|
| 1 | 森林 / 平原找紫色树冠 | 树叶下沿会掉紫色女巫粒子 |
| 2 | 7 块玻璃合成罐子，右键蝴蝶 | 罐子提示「蝴蝶：n / 5」，装满带附魔光效 |
| 3 | 铁锭 ×4 + 玻璃 ×4 + 结晶 ×1 合成机器 | 摆放方向不影响功能，只影响贴图正面 |
| 4 | 右键装罐 / 手持粉尘右键喂料 | 每转化 1 块消耗 1 个粉尘 |
| 5 | 在机器**东、南、西、北紧贴位置**各放一块圆石 | 圆石被逐块替换成矿，挖走记得补 |
| 6 | （需 Create）粉尘丢水里洗成结晶，结晶进加热工作盆搅 | 1 结晶 → 250 mB 魔法原液 |
| 7 | （需 Create）原液经注液器注入烈焰蛋糕胚 | 烈焰蛋糕胚 + 250 mB → 1 个魔法蛋糕 |
| 8 | （需 Create）手持魔法蛋糕右键烈焰人燃烧室 | 这一炉燃料期间火焰变紫色，烧完自动恢复 |

补充操作：

- **取出罐子**：潜行 + 空手右键（旧罐会直接回背包，不落地）
- **查看状态**：空手右键，快捷栏上方显示蝴蝶数、粉尘数、累计转化块数
- **拆机器**：罐子与粉尘会掉出来
- **繁殖蝴蝶**：手持魔力结晶喂两只，每只 1 个；魔法蛋糕也能顶一次（省 1 个结晶）
- **吸引蝴蝶**：手持魔力结晶，12 格内会被引过来
- **按住 W 查看用途**：物品栏里悬停本 mod 物品 → 按住 `W`

### 蝴蝶生成条件

五个条件同时满足才会自然生成：

| 条件 | 判定 |
|---|---|
| 生物群系 | 全主世界（weight 8，每次 1 只） |
| 露天 | `pos.y >= 地表 - 3`，洞穴与地下直接排除 |
| 亮度 | `>= 9`，夜晚与雷暴不刷 |
| 栖息地 | 6 格内有魔法树叶 |
| 配额 | 每 32×32 格终身最多 5 只，死亡或被抓走都不补货 |

配额是记账式的，不是"同时存在 5 只"。砍光树 → 蝴蝶不再出现；自己种一棵 → 蝴蝶回来。

野生蝴蝶每 200 tick 掷 1/12 的概率，扫描 6 格半径内的石类方块并随机转化一块，**终身最多 6 次**。平均约 2 分钟一次，是寻找蝴蝶的线索，不是产线。

---

## 2. 数值表

| 参数 | 取值 | 代码位置 |
|---|---|---|
| 罐容量 | 5 只 | `ButterflyJarItem.MAX_BUTTERFLIES` |
| 每块进度需求 | 100 tick | `PROGRESS_PER_BLOCK` |
| 粉尘消耗 | 1 个 / 块 | `DUST_PER_BLOCK` |
| 转化速度 | 1 只 = 5 秒/块，5 只 = 1 秒/块 | `progress += 蝴蝶数` / tick |
| 转化位置 | 紧贴的东/南/西/北四个面，每面 1 块 | `ManaInducerBlockEntity.SIDES` |
| 可转化方块 | `#magicinduction:inducer_input`，默认仅圆石 | `data/magicinduction/tags/blocks/inducer_input.json` |
| 野生转化 | 每 200 tick 掷 1/12，半径 6，终身 6 次 | `ManaButterfly` |
| 野生可转化方块 | `#magicinduction:convertible`：石、圆石、深板岩、安山岩、闪长岩、花岗岩、凝灰岩 | 同上 |
| 出生配额 | 5 只 / 32×32 格 / 终身 | `ButterflyQuotaData.CAP` |
| 蝴蝶属性 | 6 血、飞行 1.8、跟随 12 | `ManaButterfly.createAttributes` |
| 矿石掉落 | 1 结晶（时运加成）+ 1 粉尘（必掉）+ 2–4 经验 | `loot_tables/blocks/magic_ore.json` |
| 树生成 | 森林/平原/向日葵平原/草甸，每区块 1/5 概率 | `placed_feature/magic_tree_placed.json` |
| 原液产量 | 250 mB / 结晶 | `recipes/mana_essence_from_mixing.json` |

---

## 3. 经济模型：为什么粉尘是 1:1

最初提案是「每 10 块消耗 1 粉尘」，落地时改成了 1:1：

> 若 1 粉尘 = 10 块，那么 20 块矿（掉 20 结晶 + 10 粉尘）可以供养 100 块转化，净收益 5 倍且自我循环。玩家挖一座山能换来五座山，10 分钟就把矿物价值打穿。这是典型的 source 找不到 sink。

1:1 之后闭环是：

```
魔力矿 → (1 结晶 + 1 粉尘)
                ↓ 粉尘
        机器每转化 1 块消耗 1 粉尘 → 再得 1 结晶 + 1 粉尘
```

净效果是 **2 倍产出放大器**，产能上限 = 你亲手挖过的矿量。机器跑不过手工挖矿，只改变挖矿的"空间形状"。

结晶的 sink：合成机器、繁殖蝴蝶。所以结晶不会无限堆积。

---

## 4. 与 Create 的联动

两条配方都在 `forge:conditional` + `mod_loaded: create` 里，**未装 Create 时静默跳过**。

| 配方 | 类型 | 输入 | 输出 | 条件 |
|---|---|---|---|---|
| 粉尘 → 结晶 | `create:splashing` | 1 魔力粉尘 | 1 魔力结晶 | 工作盆有水 / 溜槽下浇水 |
| 结晶 → 原液 | `create:mixing` | 1 魔力结晶 | 250 mB 魔法原液 | `heatRequirement: "heated"`，下方需热源 |

**原液属性**：密度 1200、粘度 2000、亮度 6、每格降 3 级（流动约 3 格）、不可造源、不淹死、不可游泳、不可行船、抗爆 100。桶行为同原版水桶（不堆叠、倒完返空桶）。

**外观**：流体贴图直接复用原版 `water_still` / `water_flow` 的 32 帧结构（16×512、32×1024），把灰度映射到「深紫 `#3E1E6C` → 浅紫 `#9264D0`」区间，配 `.png.mcmeta` 的 `animation`，所以有和原版水一样的波光动画，不是一块平涂色。方块模型 `parent` 为 `minecraft:block/water`，顶/底面用 still、侧面用 flow，保留原版水侧面偏暗的做法。

> ⚠️ 两条配方**未在实机验证**。开发环境里 Create 6.0.8 的 Flywheel mixin 存在映射冲突，无法加载。配方逐条对照 Create 自带样例（`brass_ingot`、`chocolate`、`crushed_raw_iron`）编写，需在正式客户端确认。

### 魔法蛋糕 → 紫色火焰

手持魔法蛋糕右键烈焰人燃烧室，这一炉燃料期间火焰换成紫色，燃料烧完自动恢复橙色。蛋糕本身不进玩家的胃，只喂炉子；一次只影响这一个炉子。

**火焰是重新画的，不是给 Create 的火焰贴图调色相。** Create 的火焰模型 `blaze_burner/flame` 是一个「底宽顶窄的收口方柱」，四面各贴一张单帧 16×16 的图，换色之后玩家看到的只会是「一个被染紫的方柱」。这里改成原版火焰的做法：

| 项 | Create 原版 | 本 mod |
|---|---|---|
| 几何 | 一个收口方柱（OBJ） | 两层十字交叉的竖直面片（每层 2 片，共 4 片） |
| 贴图 | 单帧 16×16 | 32 帧竖堆 16×512，1 tick 一帧，和原版 `fire_0` 同节奏 |
| 配色 | 5 色橙黄 | 7 档紫色渐变（`#2C104E` → `#F6E4FF`），按原版火焰的亮度排序映射 |

贴图由 `tools/gen_purple_flame.py` 从原版 `fire_0` / `fire_1` 逐像素取亮度排名生成，所以火苗的轮廓、三簇尖角、内焰外焰的层次都和原版一致，只是颜色是紫的。

**怎么只替换吃过蛋糕的那一个炉子**：Create 6.x 的燃烧室不走普通 `BlockEntityRenderer`，走 Flywheel 的实例化管线，所以注册 BE 渲染器并不会被调用。解法是给 Create 的 `BlockEntityVisualizer` 包一层，在 `createVisual` 返回 null、`skipVanillaRender` 返回 false ——这两个都是**逐实例**问的谓词，于是只有吃过蛋糕的炉子掉回 BE 渲染路径，其余炉子的渲染性能一点不受影响。标记存在方块实体的 `persistentData` 里（`magicinduction:magic_cake`），随炉子本身持久化，退出重进还在。

---

## 5. 版本历史

| 版本 | 变更 |
|---|---|
| 1.9.12 | **魔法蛋糕 + 紫色火焰**：注液器把魔法原液注进烈焰蛋糕胚得到魔法蛋糕，右键喂烈焰人燃烧室，这一炉燃料的火焰变成紫色。火焰按原版 `fire_0` 的十字面片 + 32 帧重画，不是给 Create 的方柱调色；因为 Create 6.x 走 Flywheel visual，额外包一层 `BlockEntityVisualizer` 让吃过蛋糕的炉子掉回 BE 渲染路径 |
| 1.9.11 | 移除截图验证用的临时探针类（1.9.10 误把探针打进 jar）。内容与 1.9.10 一致，**1.9.10 勿用** |
| 1.9.10 | 原液贴图改为复用原版 `water_still` / `water_flow` 的 32 帧结构 + 紫色映射，告别纯色块；方块模型改为 `minecraft:block/water` 派生，加 `.mcmeta` 动画 |
| 1.9.9 | 修正 Ponder 文案与 `mods.toml` 描述（去掉"地下/光照<8/几率掉落/定向机器"等过时说法），README 重写 |
| 1.9.8 | 原液由 `155,107,224` 压深到 `88,48,145`；桶内水面按同一基色等比缩放 |
| 1.9.7 | 原液贴图改纯色去噪点，tint 改白；桶复用原版 `water_bucket` 像素布局 |
| 1.9.6 | 新增魔法原液流体（方块/桶/渲染）+ 两条 Create 配方 |
| 1.9.5 | 新增 mod 图标（256×256 双翅蝴蝶），`mods.toml` 声明 `logoFile` |
| 1.9.4 | 诱导机支持自动化输入：按面过滤、禁外部抽取、换罐返还 |
| 1.9.3 | 魔法树苗物品模型改 `item/generated` |
| 1.9.2 | 碰撞箱 0.65×0.30，抬升 0.095，模型垂直居中 |
| 1.9.1 | 修蝴蝶渲染下沉（`MODEL_LIFT = 0.20`） |
| 1.9.0 | 刷怪蛋蝴蝶无模型（坏包，勿用） |
| 1.2.5 | 修掉翅膀外沿方框（侧面 UV 保持 alpha=0），修正上下面对调导致的整体偏暗 |
| 1.2.4 | 翅膀薄侧面改全透明，去掉紫色矩形边框 |
| 1.2.3 | 贴图配色回到紫色系 |
| 1.2.x | 蝴蝶改为 64×64 单侧薄翅结构，翅展 0.84 格 |
| 1.1.x | 新增刷怪蛋、3D 玻璃罐、按住 W 的 Ponder 说明书 |

---

## 6. 构建

```bash
export JAVA_HOME=/root/.sdkman/candidates/java/17.0.21.0+9-zulu
python3 tools/release.py              # 升 patch 版号 → build → 出 dist 的 jar 与 zip
python3 tools/release.py --no-bump    # 只重建不升版本
```

产物：`build/libs/magicinduction-<ver>.jar`，同步复制到 `dist/`。

### 无 GPU 环境跑客户端

```bash
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --args="--quickPlaySingleplayer 新的世界" --console=plain
```

截图在 `run/screenshots/`，日志在 `run/logs/latest.log`。

> **启动前先清掉 `run/mods/create-*.jar`**：Create 6.0.8 在 ForgeGradle 开发环境有 Flywheel mixin 映射冲突，会导致启动崩溃。这不影响正式客户端。

### 关于编译警告

`ResourceLocation(String,String)` 与 `FMLJavaModLoadingContext.get()` 的 deprecation 警告是正常的——替代品（`fromNamespaceAndPath`、构造器注入 `IEventBus`）在 1.20.5 / 1.21 才引入，1.20.1 官方 MDK 模板同样这么写。

---

## 7. 验证状态

### 已验证

| 项 | 方式 | 结果 |
|---|---|---|
| Gradle 编译 | `./gradlew build` | 通过，约 14 秒（有缓存） |
| 服务端加载 | `./gradlew runServer` | `Done (5.8s)`，数据包 0 解析错误 |
| 流体渲染 | 实机截图 | 液面深紫均匀无花纹；桶图标灰桶紫液，轮廓与原版一致 |
| 紫色火焰几何 + 贴图 | 实机截图（临时探针把火焰画在测试台上，未装 Create） | 图集加载成功、贴合正确、火苗轮廓与原版一致；A/B 换回 `minecraft:block/fire_0` 走同一套代码，形状对得上 |
| 无 Create 环境加载 | `./gradlew runClient` | 正常启动、进世界、退出，Create 相关类不会被提前加载 |
| 配置一致性 | `python3 tools/verify_project.py` | 注册名 ↔ 方块状态 / 模型 / 战利品表 / 语言键全部对应 |

### 未验证

| 项 | 原因 |
|---|---|
| Create 两条配方能否实际跑通 | 开发环境加载不了 Create（Flywheel mixin 冲突） |
| 机器速率手感 | 需要人类测试者 |
| 自然生成是否真的刷出蝴蝶 | 生成率低，短时冒烟测不出 |

---

## 8. 已知限制

1. **没有 GUI**，全部通过右键 + 快捷栏文字交互；塞粉尘需要一次一次右键（一次最多 64）。
2. **没有进度条**，进度只体现在侧面 5 档指示灯与方块变色上。
3. **只有一种魔力矿**，不区分深板岩变体。
4. **无自定义音效**，复用原版紫水晶与悦灵音效。
5. **区块卸载时机器停止**，回来继续。
6. **Ponder 是简化版**：分页图文，没有 Create 那种 3D 场景演示与相机运镜。
7. **Ponder 只能在 GUI 里触发**，世界里按 W 是前进键。
8. **3D 罐子的内容层贴着罐身前壁外侧**，不是真在罐子里。
9. **无 Forge Config**：速度、配额、转化上限都写死在常量里。
10. **紫色火焰的挂接未经真机确认**：火焰的几何、贴图、动画都已在游戏内截图验证，但「吃过蛋糕的炉子掉回 BE 渲染路径」这一步只能在装了 Create 的正式客户端确认。如果没生效，表现是火焰仍然是橙色（`skipVanillaRender` 没被采纳），而不是崩溃。

---

## 9. 调试命令（1.20.1 语法）

```mcfunction
# 召唤一只蝴蝶
/summon magicinduction:mana_butterfly ~ ~1 ~

# 拿一个装满 5 只的罐子
/give @s magicinduction:butterfly_jar{Butterflies:5}

# 拿粉尘与结晶
/give @s magicinduction:mana_dust 64
/give @s magicinduction:magic_crystal 64

# 摆一台机器
/setblock ~ ~ ~ magicinduction:mana_inducer[facing=east]
```

机器摆好后，手动在它东南西北四个紧贴位置各放一块圆石即可开始转化。这四个位置是机器坐标的 `x±1, z±1`，不能有空气以外的阻挡。

---

## 10. 文件结构

```
magic-induction/
├── build.gradle / settings.gradle / gradle.properties
├── tools/
│   ├── release.py            # 升版本 → build → 出 dist 的 jar 与 zip
│   ├── gen_fluid_assets.py   # 原液液面 / 流动 / 桶贴图
│   ├── gen_purple_flame.py   # 从原版 fire_0/fire_1 生成紫色 32 帧火焰
│   ├── gen_cake_asset.py     # 魔法蛋糕物品贴图
│   ├── gen_mod_icon.py       # 从蝴蝶贴图拼 256×256 图标
│   ├── gen_textures.py       # 16×16 方块与物品贴图
│   ├── gen_item_models.py    # 3D 罐子 base + 5 档变体
│   ├── preview_model.py      # 等轴测离线预览
│   └── verify_project.py     # 静态一致性自检
└── src/main/
    ├── java/com/hjz/magicinduction/
    │   ├── MagicInduction.java              # 入口 + 注册总线
    │   ├── ModBlocks / ModItems / ModEntities / ModBlockEntities
    │   ├── ModFluids.java                   # 原液流体类型 + 方块 + 桶
    │   ├── ModCreativeTabs / ModTags / ModEvents
    │   ├── common/
    │   │   ├── ManaButterfly.java           # AI + 野生转化 + 生成规则
    │   │   ├── ButterflyJarItem.java        # 捕获 / NBT 计数
    │   │   ├── ButterflyQuotaData.java      # 32×32 区域出生配额
    │   │   ├── ManaInducerBlock.java        # 方块状态 / 指示灯
    │   │   ├── ManaInducerBlockEntity.java  # 转化循环 + 按面输入
    │   │   ├── MagicLeavesBlock.java        # 粒子
    │   │   ├── MagicTreeGrower.java         # 树苗生长
    │   │   ├── MagicCakeItem.java           # 魔法蛋糕（只喂炉子，不给玩家吃）
    │   │   └── WoodFuels.java               # 燃料值
    │   ├── compat/                          # 只在装了 Create 时加载
    │   │   ├── MagicCakeEvents.java         # 右键燃烧室 → 打标记 / 喂燃料
    │   │   ├── MagicFlameClient.java        # 换掉燃烧室 BE 渲染器
    │   │   └── PurpleFlameVisuals.java      # 包一层 Flywheel visualizer 逐实例让位
    │   └── client/
    │       ├── ClientSetup.java             # 渲染器 / 模型层 / ItemProperties
    │       ├── ManaButterflyModel.java
    │       ├── ManaButterflyRenderer.java
    │       ├── ClientForgeEvents.java       # 悬停记录 + W 键触发
    │       ├── PonderEntries.java           # 物品 → 页面映射
    │       ├── PonderScreen.java            # 分页图文界面
    │       ├── PurpleBlazeBurnerRenderer.java # 吃过蛋糕的燃烧室：换紫色火焰
    │       └── PurpleFlameRenderer.java     # 原版火苗几何（十字面片 + 32 帧）
    └── resources/
        ├── assets/magicinduction/           # 模型、方块状态、语言、纹理
        │   └── atlases/blocks.json          # 把 magic_flame_* 显式加进方块图集
        ├── data/magicinduction/             # 配方、战利品、标签、世界生成
        └── icon.png                         # mod 图标（jar 根目录）
```

---

## 11. 待办

1. 机器运转态视觉（贴图或发光）——反馈通道不足是当前最大体验缺口
2. 深板岩魔力矿变体
3. Forge Config：`PROGRESS_PER_BLOCK`、`DUST_PER_BLOCK`、配额上限
4. JEI 配方页
5. 圆石进料缓冲槽（自动补料）
6. 罐内蝴蝶寿命衰减
7. 发布到 Modrinth / CurseForge（PCL 的图标只认这两个平台的封面）
8. 紫色火焰在真实 Create 环境里跑一次，确认「吃过蛋糕的炉子掉回 BE 渲染路径」生效
