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
| 1.9.7 | 原液贴图改为纯紫（去掉噪点纹理），tint 改白色；原液桶改为复用原版 water_bucket 像素布局 |
| 1.9.8 | 原液由 155,107,224 压深到 88,48,145；桶内水面按同一基色等比缩放，避免桶比液面亮 |
| 1.9.9 | 纯文档修正：Ponder 中英文案改为实际规则（露天 / 亮度 ≥9 / 魔法树叶 / 32×32 终身 5 只 / 粉尘必掉 / 2–4 经验），mods.toml 描述去掉 "directional"，README 重写 |
| 1.9.10 | 原液贴图改为复用原版 water_still/water_flow 的 32 帧结构 + 紫色映射（告别纯色块），方块模型 parent 改为 minecraft:block/water，配 mcmeta animate。**中间包，被 1.9.11 取代** |
| 1.9.11 | 移除用于截图验证的 `__probe` 临时类（1.9.10 的 jar 里混进了探针，会强制 45 tick 后关游戏）。内容与 1.9.10 完全一致，**1.9.10 勿用** |
| 1.9.12 | 魔法蛋糕 + 紫色火焰：新增 `magicinduction:magic_cake`（注液器：烈焰蛋糕胚 + 250 mB 魔法原液）与 `magic_cake_from_filling` 配方；右键烈焰人燃烧室写入 `magicinduction:magic_cake` 标记，该炉燃料期间火焰改为紫色。紫色火焰用原版 fire_0/fire_1 的 32 帧像素重新上色（371/375 色），十字面片几何；因为 Create 6.x 走 Flywheel visual，额外包一层 `BlockEntityVisualizer` 让吃过蛋糕的炉子掉回 BE 渲染路径 |
| 1.9.16 | **紫火定案**：贴图竖向收口（原版帧是横向火墙）、面片改用 Create `blaze_flame.obj` 包围盒（y 0.25~1.0）、修背面绕序、Flywheel `recreateAll` 清掉旧橙色实例、覆盖 `assets/minecraft/particles/flame.json` 去掉橙色粒子。1.9.13–1.9.15 只有贴图调整，均被本版取代 |

## 构建环境

- JDK 17（`JAVA_HOME` → `/root/.sdkman/candidates/java/17.0.21.0+9-zulu`），Gradle 8.8 wrapper 已缓存。
- `./gradlew build` 约 1–9 分钟（首次需建缓存）。产物 `build/libs/magicinduction-<ver>.jar`。
- 无 GPU，客户端验证用软件渲染：

```bash
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --args="--quickPlaySingleplayer 新的世界" --console=plain
```

截图落在 `run/screenshots/`，日志 `run/logs/latest.log`。

## 踩过的坑（别重复）

**客户端渲染**

1. `TextureAtlasSprite.getU(double)` / `getV(double)` 的参数单位是 **16 像素**，不是 0~1。
   反编译可见 `v0 + (v1 - v0) * value / 16.0F`。按 0~1 传参，UV 被压缩 16 倍，
   整簇火焰退化成一块纯色方块——不报错，只是看着像贴图丢了。
2. `RenderType.cutout()` 用 `DefaultVertexFormat.BLOCK`，元素顺序是
   位置 → 颜色 → UV0 → overlay → light → **法线**。
   少填法线会在 `endVertex()` 抛 `IllegalStateException: Not filled all elements of the vertex`，
   并让**整帧**的火焰都画不出来。
3. 同一平面在两个互为 180° 的角度各画一遍 = 共面 z-fighting，画面全是闪烁噪点。
   双面画一次就够（`emitQuad` 里正反两遍绕序）。
4. 帧数别写死：`sprite.contents().height() / 16`。
   原版 `fire_0` 是**单帧 16×16**（动画由 mcmeta 驱动），按 32 帧算会越界采样到
   图集里相邻的贴图上，表现为「原版火焰显示成别的贴图」。

**Create 6.x 联动**

5. 燃烧室走 Flywheel 的 `BlazeBurnerVisual`，注册 `BlockEntityRenderer` 不会被调用；
   必须给 `BlockEntityVisualizer` 包一层，用 `createVisual` 返回 null +
   `skipVanillaRender` 返回 false 让它逐实例掉回原版路径。这两个返回值必须严格互补。
6. Create 6.0.8 的发布 jar 不含 Registrate → 不能引用 `AllBlockEntityTypes.HEATER`，
   要按 key（`create:blaze_burner`）从 `ForgeRegistries.BLOCK_ENTITY_TYPES` 反查。
7. **任何 import 了 Create 类的类都不能带 `@Mod.EventBusSubscriber`**：Forge 的
   `AutomaticEventSubscriber` 只看注解不看 ModList，没装 Create 时会在构造期
   `NoClassDefFoundError`，整个 mod 加载失败。改成 `ModList.isLoaded("create")` 后手动 register。

**开发环境跑客户端**

8. 必须有虚拟显示：`Xvfb :99 -screen 0 1280x720x24` + `DISPLAY=:99`，
   否则 `fmlearlywindow` 直接 `Unable to initialize GLFW` 退出。
9. 探针截图路径别用 `mc.gameDirectory.toPath().getParent()`——runClient 的 gameDir 是
   相对路径 `.`，`getParent()` 是 null。用 `new File("probe_shots").getAbsoluteFile()`。
10. 想移动相机别用 `player.setPos`，会被服务端的 "moved wrongly" 纠偏弹回来；
    走 `/tp @a x y z yaw pitch`。
11. **装了 Create 就跑不了 runClient**：Flywheel / Create 的 mixin refmap 在 userdev
    （official 映射）下缺 SRG 名，客户端一进世界就崩。要测自己的渲染就走
    「临时探针 + 不装 Create」这条路，把待验证的火焰几何单独画出来看。
12. **生产客户端测模组时，`mods/` 里放的是旧 jar 会让你白调半天**：`gradlew build`
    的产物在 `build/libs/`，它不会自动同步到 `/root/mc/mods/`。改完代码忘记 `cp`，
    屏幕上就是旧行为的截图，日志里也找不到新加的打印——非常容易误判成"代码没生效"。
    每次跑之前 `md5sum` 比对一下两边的 jar。
13. **`assets/magicinduction/atlases/blocks.json` 不能留空**：临时改成
    `{"sources": []}` 之后 `spriteFor()` 只能拿到 missing sprite，
    火焰会退化成紫黑相间的棋盘格。改完记得恢复 `minecraft:single` 条目。

**紫色火焰专项（1.9.16 定案）**

14. **原版 `fire_0` 的帧是横向火墙，不是竖直火苗**：每一帧都铺满 16 像素宽
    （它要盖住整个方块面）。把它当竖直面片的贴图，屏幕上是一条横向紫色色带。
    必须在生成器里按行做竖向收口（`narrow_to_pyramid`），底端两行保留满宽当炉口燃面。
15. **面片几何直接抄 Create 的 `blaze_flame.obj` 包围盒**：x/z 0.125~0.875、y 0.25~1.0。
    自己拍脑袋定 0.10~0.82 的结果是火根埋进炉口、火顶又比烈焰人矮，远看像紫色贴纸。
16. **顶点的绕序：外法线配顺时针**。Minecraft 1.20.x 用 JK 引擎的球坐标判面朝向，
    JC 项里 θ 方向反了，所以"外法线 + 逆时针"在引擎眼里是背面。
    症状是正面看不见、绕到炉子背后反倒能看到火焰浮在方块前面。
17. **换掉 visualizer 不会销毁已经建好的实例**：区块加载时 Create 的
    `BlazeBurnerVisual` 就已经把烈焰人本体 + 笼子 + **橙色火焰** 建成 Flywheel 实例了。
    `setVisualizer` 只影响之后新建的实例，所以橙色火焰会一直盖在紫火下面
    （表现：紫火只在橙火**上方**露出一截，炉口永远是橙的）。
    解法是调 Flywheel 的 `Storage.recreateAll(ctx, partialTick)` 全量重建。
18. **`recreateAll` 不能在 `LevelEvent.Load` 里调**：那时
    `VisualizationManagerImpl.lateInit` 还是 null（引擎要到第一次渲染世界才建），
    拿不到 `VisualizationContext`。改到客户端每个 tick 检查一次，第一次拿到就重建并收手。
    Context 的获取路径是 manager → 私有字段 `lateInit` → 私有字段 `engine` →
    `createVisualizationContext()`。
19. **炉口上方那几簇橙色火苗是粒子，不是模型**：`BlazeBurnerBlockEntity.spawnParticles`
    每 tick 撒 `ParticleTypes.FLAME`（超级加热还有 `LARGE_SMOKE`，普通有 `SMOKE`）。
    换渲染器只换掉模型里的火。因为项目没有 Mixin，这里用资源包覆盖
    `assets/minecraft/particles/flame.json` 指向紫色 sprite
    （`assets/magicinduction/textures/particle/magic_flame.png`）。
    代价：原版火把/营火的**粒子**也变紫——本项目主题就是紫色魔法火，可接受。
