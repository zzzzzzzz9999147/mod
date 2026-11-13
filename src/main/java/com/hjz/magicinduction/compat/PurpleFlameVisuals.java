package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/**
 * 让 Flywheel <b>彻底不再画</b>烈焰人燃烧室，改由普通 {@code BlockEntityRenderer} 画。
 *
 * <h2>为什么必须两头都做</h2>
 * 实测结论（用"跳过紫火绘制"的对照探针验证过）：
 * <ul>
 *   <li>{@code createVisual} 返回 null 只意味着"没有实例"，<b>不等于</b>Flywheel 不画。
 *       Create 的 {@code BlazeBurnerVisual} 里除了会动的烈焰人本体，
 *       还画了炉体（笼子、红色底座）和那团橙色火焰——这些不随 visual 为 null 消失。</li>
 *   <li>{@code skipVanillaRender} 的返回值决定"原版渲染要不要走"：
 *       返回 {@code false} 时原版渲染器会跑，但 Flywheel 的实例也没被停掉，
 *       屏幕上就是两团火叠在一起（实测：中间炉子同时有橙火和紫火）。</li>
 * </ul>
 *
 * <p>所以正确组合是"Flywheel 侧不画 + 原版侧画"：
 * {@code createVisual} 返回一个<b>空 visual</b>（不生成任何 Instance），
 * {@code skipVanillaRender} 返回 {@code true}。这样
 * {@link com.hjz.magicinduction.client.PurpleBlazeBurnerRenderer} 是唯一绘制方。
 *
 * <h2>代价</h2>
 * 所有燃烧室都退化成普通 TER，失去 Flywheel 的实例化合批。燃烧室数量通常是个位数，
 * 换来的是"颜色一定对"。
 */
@OnlyIn(Dist.CLIENT)
public final class PurpleFlameVisuals {

    private static boolean installed;

    private PurpleFlameVisuals() {
    }

    /**
     * 在客户端世界加载后调用。
     *
     * <p>必须晚于 Create 注册它的 visualizer（Create 在 {@code FMLClientSetupEvent}
     * 里注册，两个 mod 的 {@code enqueueWork} 顺序不可控）。放在
     * {@code LevelEvent.Load} 里最稳：那时所有 mod 的客户端初始化都结束了，
     * 而区块还没开始编译。
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        BlockEntityType<?> type = BlazeBurnerLookup.findOrWarn(
                "Flywheel keeps drawing the blaze burner, so its colour cannot be changed");
        if (type == null) {
            return;
        }
        installFor(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void installFor(BlockEntityType<?> type) {
        BlockEntityType<BlazeBurnerBlockEntity> typed = (BlockEntityType<BlazeBurnerBlockEntity>) type;

        VisualizerRegistry.setVisualizer(typed, new BlockEntityVisualizer<>() {
            @Override
            public BlockEntityVisual<? super BlazeBurnerBlockEntity> createVisual(
                    VisualizationContext ctx, BlazeBurnerBlockEntity be, float partialTick) {
                // 返回 null = 不建实例。
                // Storage.add 对 createRaw 的返回值有判空（if (visual == null) return;），
                // 所以这是被支持的写法，Flywheel 这一侧不会画任何东西。
                return null;
            }

            @Override
            public boolean skipVanillaRender(BlazeBurnerBlockEntity be) {
                // 语义（反编译 LevelRendererMixin 确认）：返回 true 会 cancel 原版渲染调用。
                // 所以这里必须返回 false —— 让 BlockEntityRenderDispatcher 照常调
                // PurpleBlazeBurnerRenderer；Flywheel 侧因为 createVisual 返回 null 而不画。
                return false;
            }
        });

        MagicInduction.LOGGER.info("[Magic Induction] blaze burner handed back to the vanilla render "
                + "path; purple flame renderer is now authoritative.");
    }

    /** 已重建过的存档次数；每个存档只需要重建一次（区块加载时才建实例）。 */
    private static int recreatedForLevelHash;

    /** 延迟重建的重试计数，防止在拿不到 context 时每帧都反射一遍。 */
    private static int recreateAttempts;

    /**
     * 每客户端 tick 调一次：把<b>已经存在</b>的 Flywheel 实例重建一遍。
     *
     * <h2>为什么必须做这一步</h2>
     * {@code setVisualizer} 只影响之后新建的实例。玩家存档里的燃烧室在区块加载时
     * 就已经按 Create 的 visualizer 建好了 {@code BlazeBurnerVisual}，里面装着
     * 烈焰人本体、笼子和那团<b>橙色火焰</b>。换掉 visualizer 之后这些旧实例还在，
     * 于是屏幕上同时出现橙色火焰和紫色火焰——紫色被橙色盖在下面，看起来"没生效"。
     *
     * <p>实测症状：紫色火苗只在橙色火苗的<b>上方</b>露出一截，炉口附近永远是橙的；
     * 飞远再飞回来（区块重新加载、实例重建）后就变成纯紫。
     *
     * <h2>为什么不在 LevelEvent.Load 里一次做完</h2>
     * 那时 Flywheel 的 {@code VisualizationManagerImpl.lateInit} 还是 null
     * （引擎要到第一次开始渲染世界时才建），拿不到 {@code VisualizationContext}，
     * 也就没法重建。所以改成每个 tick 检查一次，第一次拿到 context 就重建并收手。
     *
     * <p>反射调用是必要的：Flywheel 的 impl 包不是公开 API，
     * 直接 import 会在 Flywheel 换版本时编译失败。这里整段兜住，
     * 失败只记 warn——最坏结果是"要飞远再飞回来才变紫"，不该让玩家开不了游戏。
     */
    public static void tickRecreate() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || !installed) {
            return;
        }
        int hash = System.identityHashCode(mc.level);
        if (hash == recreatedForLevelHash) {
            return;
        }
        // 引擎还没建好时每 tick 都会进这里，限制重试次数避免无限反射
        if (recreateAttempts > 600) {
            return;
        }
        recreateAttempts++;
        if (recreateExistingVisuals()) {
            recreatedForLevelHash = hash;
            recreateAttempts = 0;
        }
    }

    /** @return true 表示这次真的重建了；false 表示引擎还没就绪，下一 tick 再试 */
    private static boolean recreateExistingVisuals() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        try {
            Class<?> mgrImpl = Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
            Object manager = mgrImpl.getMethod("get", net.minecraft.world.level.LevelAccessor.class)
                    .invoke(null, mc.level);
            if (manager == null) {
                return false;
            }
            Object ctx = visualizationContext(mgrImpl, manager);
            if (ctx == null) {
                // 引擎未就绪：不算失败，下一 tick 再试
                return false;
            }
            Object blockEntities = mgrImpl.getMethod("blockEntities").invoke(manager);
            Object storage = blockEntities.getClass().getMethod("getStorage").invoke(blockEntities);
            java.lang.reflect.Method recreate = null;
            for (Class<?> c = storage.getClass(); c != null && recreate == null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("recreateAll") && m.getParameterCount() == 2) {
                        recreate = m;
                        break;
                    }
                }
            }
            if (recreate == null) {
                MagicInduction.LOGGER.warn("[Magic Induction] Flywheel Storage.recreateAll not found; "
                        + "existing burners may keep their orange flame until the chunk reloads.");
                return true;
            }
            recreate.setAccessible(true);
            recreate.invoke(storage, ctx, 1.0F);
            MagicInduction.LOGGER.info("[Magic Induction] Flywheel visuals recreated; "
                    + "previously built blaze burners dropped their orange flame.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MagicInduction.LOGGER.warn("[Magic Induction] could not recreate Flywheel visuals; "
                    + "existing burners may keep their orange flame until the chunk reloads.", e);
            return true;
        }
    }

    /**
     * 拿到当前维度的 {@code VisualizationContext}。
     *
     * <p>Flywheel 没有公开的 getter：它的唯一实现是
     * {@code EngineImpl.VisualizationContextImpl}，只能在
     * {@code EngineImpl.createVisualizationContext()} 里 new 出来，
     * 而 Engine 实例藏在 {@code VisualizationManagerImpl.lateInit.engine} 里。
     * 所以路径是 manager → lateInit → engine → createVisualizationContext()。
     * 这条链每一环都是私有的，任何一环失败就返回 null 由调用方降级。
     */
    private static Object visualizationContext(Class<?> mgrImpl, Object manager) throws ReflectiveOperationException {
        java.lang.reflect.Field lateInitField = null;
        for (Class<?> c = mgrImpl; c != null && lateInitField == null; c = c.getSuperclass()) {
            try {
                lateInitField = c.getDeclaredField("lateInit");
            } catch (NoSuchFieldException ignored) {
                // 往父类继续找
            }
        }
        if (lateInitField == null) {
            return null;
        }
        lateInitField.setAccessible(true);
        Object lateInit = lateInitField.get(manager);
        if (lateInit == null) {
            // 第一次渲染之前 lateInit 还是 null，这时候也没必要重建
            return null;
        }
        java.lang.reflect.Field engineField = null;
        for (Class<?> c = lateInit.getClass(); c != null && engineField == null; c = c.getSuperclass()) {
            try {
                engineField = c.getDeclaredField("engine");
            } catch (NoSuchFieldException ignored) {
                // 往父类继续找
            }
        }
        if (engineField == null) {
            return null;
        }
        engineField.setAccessible(true);
        Object engine = engineField.get(lateInit);
        if (engine == null) {
            return null;
        }
        return engine.getClass().getMethod("createVisualizationContext").invoke(engine);
    }

}
