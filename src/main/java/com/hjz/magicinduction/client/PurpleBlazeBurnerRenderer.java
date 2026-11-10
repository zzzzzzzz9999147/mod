package com.hjz.magicinduction.client;

import com.hjz.magicinduction.compat.MagicCakeEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 烈焰人燃烧室渲染器：吃过魔法蛋糕时，把火焰换成紫色「原版火苗」。
 *
 * <h2>为什么必须换掉整个渲染器，而不是只换贴图</h2>
 * Create 的火焰是 {@code blaze_burner/flame.obj} 一个"底宽顶窄的收口方柱"，
 * 四面包一张 16×16 单帧贴图。哪怕把贴图换成紫色，形状还是那个方柱，
 * 玩家看到的只会是「被染色的方块」，不是「一簇在烧的火」。
 *
 * <p>而 {@code BlazeBurnerRenderer.renderShared} 是写死的流程：
 * 火焰的贴图（{@code AllSpriteShifts.BURNER_FLAME}）和模型
 * （{@code AllPartialModels.BLAZE_BURNER_FLAME}）都直接引用静态字段，
 * 没有注入点。所以这里把 {@code renderShared} 的逻辑完整复刻一遍，
 * 唯独「画火焰」那一段换成 {@link PurpleFlameRenderer}。
 *
 * <h2>复刻的范围</h2>
 * 只复刻 {@code renderShared} 的<b>可见</b>分支：本体、护目镜、帽子、火焰。
 * 与 Contraption（装置里被带动）有关的那条路径没有复刻，因为那需要
 * {@code VirtualRenderWorld}，而且装置上的燃烧室吃不到蛋糕（右键被装置拦截）。
 */
@OnlyIn(Dist.CLIENT)
public class PurpleBlazeBurnerRenderer extends SafeBlockEntityRenderer<BlazeBurnerBlockEntity> {

    /** 与 Create 一致的常量：头部上下浮动的换算系数。 */
    private static final float HEAD_BOB_SCALE = 0.175F;

    /**
     * {@code BlazeBurnerBlockEntity.headAngle} 是 protected，外部读不到。
     *
     * <p>用反射取一次、缓存 Field 对象。这是整个紫色火焰方案里唯一的反射点，
     * 而且只读不写：读不到（比如 Create 改了字段可见性）就退化成角度 0，
     * 火焰和本体整体不转，视觉上只是"头不晃了"，不会崩。
     */
    private static final java.lang.reflect.Field HEAD_ANGLE_FIELD = findHeadAngleField();

    private static java.lang.reflect.Field findHeadAngleField() {
        try {
            java.lang.reflect.Field f = BlazeBurnerBlockEntity.class.getDeclaredField("headAngle");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            com.hjz.magicinduction.MagicInduction.LOGGER.warn(
                    "[Magic Induction] BlazeBurnerBlockEntity.headAngle not found; purple flame will not follow head rotation.");
            return null;
        }
    }

    private static float headAngleOf(BlazeBurnerBlockEntity be, float partialTicks) {
        if (HEAD_ANGLE_FIELD == null) {
            return 0.0F;
        }
        try {
            Object v = HEAD_ANGLE_FIELD.get(be);
            if (v instanceof net.createmod.catnip.animation.LerpedFloat lf) {
                return (float) Math.toRadians(lf.getValue(partialTicks));
            }
        } catch (IllegalAccessException ignored) {
            // 见上：读不到就算了
        }
        return 0.0F;
    }

    public PurpleBlazeBurnerRenderer(BlockEntityRendererProvider.Context ctx) {
        super();
    }

    @Override
    protected void renderSafe(BlazeBurnerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 没吃过蛋糕的，原样交给 Create —— 保证普通燃烧室的观感 100% 一致。
        // 走 renderShared（public static）而不是自己 new 一个渲染器：
        // BlazeBurnerRenderer.renderSafe 是 protected，外部调不到；
        // renderShared 是它唯一公开的绘制入口，参数也够还原全部状态。
        if (!MagicCakeEvents.isSated(be)) {
            BlazeBurnerBlock.HeatLevel heatLevel = be.getHeatLevelForRender();
            if (heatLevel == BlazeBurnerBlock.HeatLevel.NONE) {
                return;
            }
            float headBob = be.headAnimation.getValue(partialTicks) * HEAD_BOB_SCALE;
            float headAngle = headAngleOf(be, partialTicks);
            boolean fading = heatLevel.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
            BlazeBurnerRenderer.renderShared(ms, ms, buffer, be.getLevel(), be.getBlockState(),
                    heatLevel, headBob, headAngle, fading, be.goggles,
                    headHatOrNull(be), be.hashCode());
            return;
        }
        renderPurple(be, partialTicks, ms, buffer);
    }

    /**
     * 帽子模型：Create 的 renderSafe 里也是这么选的。
     *
     * <p>返回 null 表示不戴帽子——renderShared 会判空。
     */
    private static PartialModel headHatOrNull(BlazeBurnerBlockEntity be) {
        if (be.hat) {
            return AllPartialModels.TRAIN_HAT;
        }
        if (be.stockKeeper) {
            return AllPartialModels.LOGISTICS_HAT;
        }
        return null;
    }

    private void renderPurple(BlazeBurnerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer) {
        BlazeBurnerBlock.HeatLevel heatLevel = be.getHeatLevelForRender();
        if (heatLevel == BlazeBurnerBlock.HeatLevel.NONE) {
            // 火灭了：蛋糕的效果跟着这一炉燃料走，燃料烧完就恢复普通火焰。
            return;
        }

        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        float headBob = be.headAnimation.getValue(partialTicks) * HEAD_BOB_SCALE;
        float headAngle = headAngleOf(be, partialTicks);
        boolean fading = heatLevel.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
        boolean goggles = be.goggles;

        // 火焰走 cutout（镂空），本体走 translucent（Create 本体贴图带 alpha 过渡）。
        VertexConsumer superBuffer = buffer.getBuffer(RenderType.translucent());

        // ── 火焰（紫色，画在本体之前）────────────────────────────
        // Create 的火焰模型从 y=0.25 起，这里用同一个基准，让火焰"从炉口里长出来"。
        if (fading) {
            ms.pushPose();
            ms.translate(0.0F, 0.25F + headBob, 0.0F);
            // 相位错开：用方块坐标做种子，一排炉子不会同步跳火
            float phase = be.getBlockPos().getX() * 3.1F + be.getBlockPos().getZ() * 7.7F;
            float animTicks = AnimationTickHolder.getRenderTime(level) + phase;
            boolean superHeat = heatLevel == BlazeBurnerBlock.HeatLevel.SEETHING;
            PurpleFlameRenderer.render(ms, buffer, animTicks, superHeat ? 1.15F : 1.0F);
            ms.popPose();
        }

        // ── 烈焰人本体 ───────────────────────────────────────────
        float bodyY = -headBob;
        PartialModel bodyModel = BlazeBurnerRenderer.getBlazeModel(heatLevel, fading);
        SuperByteBuffer body = CachedBuffers.partial(bodyModel, state);
        body.translate(0.0F, bodyY, 0.0F);
        drawCentered(body, headAngle, ms, superBuffer);

        // ── 护目镜 ───────────────────────────────────────────────
        if (goggles) {
            PartialModel gogglesModel = bodyModel == AllPartialModels.BLAZE_INERT
                    ? AllPartialModels.BLAZE_GOGGLES_SMALL
                    : AllPartialModels.BLAZE_GOGGLES;
            SuperByteBuffer gogglesBuffer = CachedBuffers.partial(gogglesModel, state);
            gogglesBuffer.translate(0.0F, bodyY + 0.5F, 0.0F);
            drawCentered(gogglesBuffer, headAngle, ms, superBuffer);
        }

        // ── 帽子（列车帽 / 物流帽）───────────────────────────────
        // 日志里那几行跟 Create 一致：INERT 时帽子要缩放 0.75 并上移 0.5，
        // 否则空炉子上的帽子会悬在半空。
        PartialModel hatModel = headHatOrNull(be);
        if (hatModel != null) {
            SuperByteBuffer hat = CachedBuffers.partial(hatModel, state);
            hat.translate(0.0F, bodyY, 0.0F);
            if (bodyModel == AllPartialModels.BLAZE_INERT) {
                hat.translateY(0.5F).center().scale(0.75F).uncenter();
            } else {
                hat.translateY(0.75F);
            }
            drawCentered(hat, headAngle, ms, superBuffer);
        }
    }

    /**
     * 等价于 Create 的 {@code draw(buf, angle, ms, vc)}：
     * 绕竖直轴旋转 → 满亮度 → 铺进 PoseStack。
     *
     * <p>用 15728880 = {@code 0xF000F0}，即天光 15 + 方块光 15，
     * 让火焰和烈焰人不吃环境光——这是 Create 也是原版火的做法。
     */
    private static void drawCentered(SuperByteBuffer buf, float angle, PoseStack ms, VertexConsumer vc) {
        buf.rotateCentered(angle, Direction.UP)
                .light(15728880)
                .renderInto(ms, vc);
    }
}
