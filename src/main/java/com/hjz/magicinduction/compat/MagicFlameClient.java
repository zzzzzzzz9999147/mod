package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.client.PurpleBlazeBurnerRenderer;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

/**
 * 紫色火焰的客户端接线：把燃烧室的渲染器换成 {@link PurpleBlazeBurnerRenderer}。
 *
 * <h2>为什么不能用 {@code EntityRenderersEvent.RegisterRenderers} 注册</h2>
 * 这条是实测出来的，不是猜的：
 *
 * <p>Create 6.x 的方块实体渲染器不是自己 {@code register} 的，而是交给
 * Registrate（Create 用 JarJar 内嵌的一份）批量注册。Registrate 的注册时机
 * <b>晚于</b> Forge 派发 {@code RegisterRenderers}。事件已经结束，它的
 * {@code .register()} 才落到 {@code BlockEntityRenderers.PROVIDERS} 里。
 *
 * <p>表现（已用探针确认）：我们在事件里 register 之后，
 * {@code BlockEntityRenderDispatcher.getRenderer(be)} 拿到的仍然是
 * {@code com.simibubi.create...BlazeBurnerRenderer}；日志里我们自己的
 * "renderer replaced" 打印了，但渲染器一次都没被调用，颜色当然也不会变。
 * 把订阅优先级改成 LOWEST 也没用——问题不在事件内部顺序，而在事件之后还有写入。
 *
 * <h2>所以改成"最后一步直接改写静态表"</h2>
 * {@code FMLLoadCompleteEvent} 是加载流程的终点：所有 mod 的注册、
 * 所有 Registrate 的 deferred 注册、所有事件都跑完了，之后才开始渲染。
 * 在这一刻把 {@code PROVIDERS} 里的那一条替换掉，就没有东西能再覆盖它。
 *
 * <p>用反射访问 {@code PROVIDERS}：1.20.1 里 {@code register} 是 private
 * （public 的只有 {@code create(Context)}）。反射失败时只记一条 warn、不抛异常——
 * 渲染器没换掉最坏结果是"紫火不出现"，不该让玩家开不了游戏。
 */
@OnlyIn(Dist.CLIENT)
public final class MagicFlameClient {

    /**
     * {@code BlockEntityRenderers.PROVIDERS}（SRG: {@code f_173587_}）。
     *
     * <p>官方映射下字段名是 {@code PROVIDERS}；保留 SRG 名兜底是为了兼容
     * 少数运行时仍以 SRG 名反射的环境。取不到就是 null，由调用方降级。
     */
    private static final java.lang.reflect.Field PROVIDERS = findProviders();

    private MagicFlameClient() {
    }

    private static java.lang.reflect.Field findProviders() {
        for (String name : new String[]{"PROVIDERS", "f_173587_"}) {
            try {
                java.lang.reflect.Field f =
                        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // 试下一个名字
            }
        }
        return null;
    }

    /**
     * 在加载流程最后一步（{@code FMLLoadCompleteEvent}）替换渲染器。
     *
     * <p>类加载安全：本类只被 {@code MagicInduction} 在确认 Create 存在后加载，
     * 而且没有 {@code @Mod.EventBusSubscriber}——Forge 的自动订阅只看注解不看 ModList，
     * 加了注解会在没装 Create 时触发类加载 → {@code NoClassDefFoundError}。
     */
    @SuppressWarnings("unchecked")
    public static void installRenderer() {
        BlockEntityType<?> type = BlazeBurnerLookup.findOrWarn("the purple flame renderer was not installed");
        if (type == null) {
            return;
        }
        if (PROVIDERS == null) {
            MagicInduction.LOGGER.warn("[Magic Induction] BlockEntityRenderers.PROVIDERS is not accessible; "
                    + "the magic cake will still work but the flame stays orange.");
            return;
        }

        try {
            BlockEntityRendererProvider<BlazeBurnerBlockEntity> provider = PurpleBlazeBurnerRenderer::new;
            Map<BlockEntityType<?>, BlockEntityRendererProvider<?>> providers =
                    (Map<BlockEntityType<?>, BlockEntityRendererProvider<?>>) PROVIDERS.get(null);
            if (providers == null) {
                MagicInduction.LOGGER.warn("[Magic Induction] BlockEntityRenderers.PROVIDERS was null; "
                        + "the flame stays orange.");
                return;
            }
            providers.put(type, provider);
            MagicInduction.LOGGER.info("[Magic Induction] blaze burner renderer replaced (purple flame enabled).");
        } catch (IllegalAccessException e) {
            MagicInduction.LOGGER.warn("[Magic Induction] could not replace the blaze burner renderer; "
                    + "the flame stays orange.", e);
        }
    }
}
