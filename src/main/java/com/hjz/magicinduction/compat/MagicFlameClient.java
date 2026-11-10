package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.client.PurpleBlazeBurnerRenderer;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 紫色火焰的客户端接线：替换燃烧室渲染器。
 *
 * <h2>这个类为什么必须手动注册，不能靠 @Mod.EventBusSubscriber 自动订阅</h2>
 * 它 import 了 {@code BlazeBurnerBlockEntity}。没装 Create 时这类一旦被加载就是
 * {@code NoClassDefFoundError}，而 Forge 的自动订阅会在 mod 构造期就去读它。
 *
 * <p>所以本类<b>不能</b>写 {@code @Mod.EventBusSubscriber}：
 * Forge 的 {@code AutomaticEventSubscriber} 只看注解不看 ModList，
 * 一加注解就会在 mod 构造期触发类加载，没装 Create 时整个 mod 加载失败。
 * 由 {@code MagicInduction} 在确认 Create 存在后手动 register。
 *
 * <h2>贴图登记不在这里</h2>
 * 1.20.1 的 Forge 没有 {@code TextureStitchEvent.Pre}（只有 Post），
 * 贴图进图集要写 {@code assets/magicinduction/atlases/blocks.json}，
 * 用 {@code minecraft:single} source 显式声明。见该文件。
 */
public final class MagicFlameClient {

    private MagicFlameClient() {
    }

    /**
     * 用 {@link PurpleBlazeBurnerRenderer} 顶掉 Create 的燃烧室渲染器。
     *
     * <p>{@code BlockEntityRenderers.register} 底层就是一次 {@code Map.put}，
     * 后注册的覆盖先注册的。Forge 的 {@code RegisterRenderers} 事件在
     * Create 用 Registrate 注册完之后才派发，所以这次 register 会生效。
     *
     * <p>按注册名反查而<b>不</b>引用 {@code AllBlockEntityTypes.HEATER}：
     * 后者类型是 Registrate 的 {@code BlockEntityEntry}，而 Create 6.0.8 的发布 jar
     * 里不含 Registrate（整个 {@code com.tterrag.registrate.*} 缺失），
     * 引用它会让 javac 报 {@code cannot access BlockEntityEntry}。
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (BlockEntityType<?> type : ForgeRegistries.BLOCK_ENTITY_TYPES.getValues()) {
            var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type);
            if (key == null || !"create".equals(key.getNamespace()) || !"blaze_burner".equals(key.getPath())) {
                continue;
            }
            registerFor(event, type);
            MagicInduction.LOGGER.info("[Magic Induction] blaze burner renderer replaced (purple flame enabled).");
            return;
        }
        MagicInduction.LOGGER.warn("[Magic Induction] create:blaze_burner block entity type not found; "
                + "magic cake will still feed burners but the flame stays orange.");
    }

    @SuppressWarnings("unchecked")
    private static void registerFor(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<?> type) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<BlazeBurnerBlockEntity>) type,
                (BlockEntityRendererProvider.Context ctx) -> new PurpleBlazeBurnerRenderer(ctx));
    }
}
