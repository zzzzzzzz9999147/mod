package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 让「吃过魔法蛋糕」的燃烧室从 Flywheel 手里抢回渲染权。
 *
 * <h2>为什么还需要这一步</h2>
 * Create 6.x 的燃烧室不走普通的 {@code BlockEntityRenderer}，而是走
 * Flywheel 的实例化管线（{@code BlazeBurnerVisual}）。也就是说：
 * <b>只要 Flywheel 在渲染，我们自己注册的 BE 渲染器永远不会被调用。</b>
 *
 * <p>如果直接把 Create 的 visualizer 清掉（{@code setVisualizer(type, null)}），
 * 所有燃烧室都会掉回原版渲染路径，等于让装了 Create 的玩家平白丢掉性能优化。
 *
 * <h2>做法：包一层，按实例决定</h2>
 * {@link BlockEntityVisualizer#skipVanillaRender} 是<b>逐实例</b>问的谓词：
 * 返回 true 表示「Flywheel 会画，别让原版再画一遍」。所以只要让它在
 * 吃过蛋糕的燃烧室上返回 {@code false}，并让 {@code createVisual} 返回 null，
 * 那一个方块就自然掉回 BE 渲染器（也就是我们的紫色火焰），
 * 其余燃烧室完全不受影响。
 *
 * <p>这个类在 {@code ModList.isLoaded("create")} 之后才会被加载，
 * 见 {@code MagicInduction}。
 */
@OnlyIn(Dist.CLIENT)
public final class PurpleFlameVisuals {

    private PurpleFlameVisuals() {
    }

    /**
     * 在 {@code FMLClientSetupEvent} 里调用（此时 Create 已注册完它的 visualizer）。
     */
    public static void install() {
        for (BlockEntityType<?> type : ForgeRegistries.BLOCK_ENTITY_TYPES.getValues()) {
            var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type);
            if (key == null || !"create".equals(key.getNamespace()) || !"blaze_burner".equals(key.getPath())) {
                continue;
            }
            installFor(type);
            return;
        }
        MagicInduction.LOGGER.warn("[Magic Induction] create:blaze_burner not found, "
                + "purple flame falls back to the vanilla renderer path only.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void installFor(BlockEntityType<?> type) {
        BlockEntityType<BlazeBurnerBlockEntity> typed = (BlockEntityType<BlazeBurnerBlockEntity>) type;
        BlockEntityVisualizer<BlazeBurnerBlockEntity> original =
                (BlockEntityVisualizer<BlazeBurnerBlockEntity>) (BlockEntityVisualizer) VisualizerRegistry.getVisualizer(typed);
        if (original == null) {
            MagicInduction.LOGGER.warn("[Magic Induction] no blaze burner visualizer to wrap; "
                    + "purple flame relies on the BE renderer alone.");
            return;
        }

        BlockEntityVisualizer<BlazeBurnerBlockEntity> wrapper = new BlockEntityVisualizer<>() {
            @Override
            public BlockEntityVisual<? super BlazeBurnerBlockEntity> createVisual(
                    VisualizationContext ctx, BlazeBurnerBlockEntity be, float partialTick) {
                // 吃过蛋糕 → 不建实例，交给 BE 渲染器画紫色火焰
                if (MagicCakeEvents.isSated(be)) {
                    return null;
                }
                return original.createVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(BlazeBurnerBlockEntity be) {
                // false = "别跳过原版渲染"，即让 BE 渲染器接手。
                // 这里必须和 createVisual 严格互补，否则会出现
                // 「两边都不画」或「两边都画」的闪烁。
                if (MagicCakeEvents.isSated(be)) {
                    return false;
                }
                return original.skipVanillaRender(be);
            }
        };

        VisualizerRegistry.setVisualizer(typed, wrapper);
        MagicInduction.LOGGER.info("[Magic Induction] blaze burner visualizer wrapped for purple flame.");
    }
}
