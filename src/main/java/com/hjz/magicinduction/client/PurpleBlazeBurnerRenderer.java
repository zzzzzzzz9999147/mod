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
 * 烈焰人燃烧室渲染器：吃过魔法蛋糕的燃烧室，火焰换成紫色「原版火苗」。
 *
 * <p>判定只有一条：{@code isSated(be) && 正在燃烧}。不满足就整段转交给 Create，
 * 所以普通燃烧室的观感与原版一致（连护目镜、帽子、装置矩阵那套逻辑都是 Create 自己画的）。
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
 * 与 Contraption（装置里被带动）有关的那条路径没有复刻，因为没有重复绘制的必要：
 * 装置上的燃烧室由 {@code renderInContraption} 单独绘制，喂蛋糕的标记随 NBT 一起被装置带走，
 * 但颜色仍是 Create 的橙色——这是已知限制，不是 bug（装置里换贴图需要接管 Contraption 的材质管线）。
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
        // 这里是每帧判定一次的地方。因为 PurpleFlameVisuals 让 Flywheel 从不认领这个方块实体，
        // 它每一帧都会被 BlockEntityRenderDispatcher 问一次 —— 所以喂完蛋糕的下一帧就变紫，
        // 燃料烧完的下一帧就变回橙色，不需要走两步、不需要重载区块。
        if (isBurningForMagicCake(be)) {
            renderPurple(be, partialTicks, ms, buffer);
            return;
        }

        // 其余情况原样交给 Create —— 普通燃烧室的观感 100% 一致。
        // 走 renderShared（public static）而不是自己 new 一个渲染器：
        // BlazeBurnerRenderer.renderSafe 是 protected，外部调不到；
        // renderShared 是它唯一公开的绘制入口，参数也够还原全部状态。
        BlazeBurnerBlock.HeatLevel heatLevel = be.getHeatLevelForRender();
        if (heatLevel == BlazeBurnerBlock.HeatLevel.NONE) {
            return;
        }
        float headBob = be.headAnimation.getValue(partialTicks) * HEAD_BOB_SCALE;
        float headAngle = headAngleOf(be, partialTicks);
        boolean fading = heatLevel.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
        // 第二个参数是 Create 用来叠装置矩阵的 transform，世界里的方块传 null。
        // 这里曾经误传成 ms，结果同一套变换被应用两次，本体画在偏移的位置上。
        BlazeBurnerRenderer.renderShared(ms, null, buffer, be.getLevel(), be.getBlockState(),
                heatLevel, headBob, headAngle, fading, be.goggles,
                headHatOrNull(be), be.hashCode());
    }

    /**
     * 这个燃烧室现在该不该是紫火 = 吃过魔法蛋糕 且 正在燃烧。
     *
     * <p>两个条件缺一不可：
     * <ul>
     *   <li>只吃过、没在烧 → 正常显示"熄火"的样子，不该凭空冒紫火；</li>
     *   <li>在烧、没吃过 → 橙色，交给 Create 自己画。</li>
     * </ul>
     *
     * <p>标记与热量都从 BlockEntity / 方块状态上读，每帧重算，不做任何缓存——
     * 燃烧室的这些值本身就每 tick 在变，缓存只会带来"改完要等一会儿才生效"。
     */
    private static boolean isBurningForMagicCake(BlazeBurnerBlockEntity be) {
        return MagicCakeEvents.isSated(be) && be.getHeatLevelForRender() != BlazeBurnerBlock.HeatLevel.NONE;
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

    /**
     * 单纯形噪声（2D），用来让火苗高度/宽度以「秒级」节奏缓慢起伏。
     *
     * <p>为什么不直接用品噪声缓存又为什么不抄 Create：Create 的火焰是 64×64 的一张
     * 单帧贴图 + {@code shiftUVScrolling} 做 UV 卷动，起伏靠贴图本身；
     * 我们是逐帧切的 16×512 长条，帧与帧之间只是"换了一张图"，
     * 高度是死的，看起来像幻灯机。加一层低频噪声做几何伸缩，火才"活"。
     */
    private static float noise1(float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = hash1(i);
        float b = hash1(i + 1);
        float u = f * f * (3.0F - 2.0F * f);
        return a + (b - a) * u;
    }

    private static float hash1(int x) {
        int h = x * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
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

        // 本体的贴图是不透明的（blaze_active.png 没有半透明像素），所以走 cutout 即可。
        //
        // 原先用 translucent 是个坑：MultiBufferSource 会把 translucent 顶点收进内部 Map，
        // 等这一批全部结束再按 VertexSorting 统一提交；而火焰用的 cutout 是立刻写出去的。
        // 结果是"先画的火焰被晚提交的本体整个盖住"，屏幕上只看得到本体，一点紫色都没有。
        // 两者统一用 cutout，谁先画谁后画就真的由代码顺序决定。
        VertexConsumer superBuffer = buffer.getBuffer(RenderType.cutout());

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

        // ── 火焰（紫色，最后画）──────────────────────────────────
        // 放在最后是有原因的：cutout 批次是"立刻写出去"的，谁后写谁在上层。
        // 火焰画在笼子和本体之后，才不会有一半被笼子横梁或烈焰人本体吃掉。
        //
        // 高度：PurpleFlameRenderer 内部用的就是 Create 火焰 OBJ 的包围盒
        // （y 0.25~1.0），所以这里只补 headBob，不再额外 translate(0.25)——
        // 两次叠加会让火焰悬空、露出一截在笼子外面（踩过）。
        if (fading) {
            ms.pushPose();
            ms.translate(0.0F, headBob, 0.0F);
            // 相位错开：用方块坐标做种子，一排炉子不会同步跳火
            float phase = be.getBlockPos().getX() * 3.1F + be.getBlockPos().getZ() * 7.7F;
            float animTicks = AnimationTickHolder.getRenderTime(level) + phase;
            boolean superHeat = heatLevel == BlazeBurnerBlock.HeatLevel.SEETHING;
            // 低频起伏：每 ~0.7 秒一个周期，幅度 ±6%。
            // 相位用方块坐标错开，相邻两台不同步；super heat 时振幅加倍。
            float wobblePhase = animTicks * 0.045F
                    + be.getBlockPos().getX() * 3.7F + be.getBlockPos().getZ() * 1.9F;
            float wobbleAmp = superHeat ? 0.12F : 0.06F;
            float wobble = 1.0F + (noise1(wobblePhase) - 0.5F) * 2.0F * wobbleAmp;
            PurpleFlameRenderer.render(ms, buffer, animTicks,
                    (superHeat ? 1.15F : 1.0F) * wobble);
            ms.popPose();
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
