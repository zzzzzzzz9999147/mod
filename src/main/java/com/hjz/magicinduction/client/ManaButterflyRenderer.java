package com.hjz.magicinduction.client;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.common.ManaButterfly;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ManaButterflyRenderer extends MobRenderer<ManaButterfly, ManaButterflyModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicInduction.MODID, "textures/entity/mana_butterfly.png");

    /**
     * 模型按 1 单位 = 1 贴图像素建，贴图 64x64（和参考 mod 一致）。
     * 模型空间 22 x 4 x 17 单位（翅展 x 厚 x 体长），0.45 缩放后是
     * 0.62 x 0.11 x 0.48 格，落在 0.65 x 0.30 的碰撞箱里面。
     *
     * <p>这个 0.45 和模型尺寸是绑定的：换模型（比如再偷一只别的蝴蝶）就要重算，
     * 否则整只蝴蝶会明显变大或变小。
     */
    private static final float MODEL_SCALE = 0.45F;

    /**
     * 模型在碰撞箱内的抬高量，单位是格，正值向上。
     *
     * <p><b>0.095 是"让 0.11 格厚的身体居中于 0.30 高的碰撞箱"算出来的。</b>
     * 先把 LivingEntityRenderer 的变换链算清楚，模型顶点的 y 走到屏幕前要经过
     * （自左向右作用于顶点）：
     *
     * <pre>
     *   M = S(-1,-1,1) · T(0, -LIFT, 0) · S(0.45) · T(0, -1.501, 0)
     * </pre>
     *
     * <p>{@code ModelPart} 按像素存坐标、渲染时除以 16，模型落在 y_model∈[1.25, 1.50] 格；
     * {@code T(0,-1.501,0)} 把 y=1.5 落到实体脚下（y≈0），再乘 0.45，
     * 于是世界坐标 {@code y = LIFT - 0.45*(y_model - 1.501)}，模型占
     * {@code [LIFT+0.0005, LIFT+0.113]}。碰撞箱高 0.30，取 LIFT = 0.095
     * → 模型 0.096~0.208，上下各留约 0.09。
     *
     * <p><b>这个值不覆盖扑翼。</b>翅膀摆到 ±0.85 rad 时模型竖直包络是 -0.16~0.36，
     * 必然出框；要罩住得把框做到 0.52 高，蝴蝶在方块上会占掉半格。原版蜜蜂、蝙蝠
     * 也不这么做——碰撞箱描述身体占位，不描述动画。所以这里只保证"静止姿态居中"。
     *
     * <p><b>历史上这里错过两次。</b>最早写 {@code translate(0, 0.42, 0)}，当时以为那是
     * "从 0.675 格压到 0.20~0.26 格"，其实 0.675 已被 {@code T(0,-1.501,0)} 抵掉，
     * 0.42 等于再往下压 0.42 格——蝴蝶一贴地模型就整只埋进方块，看起来就是"没有模型"
     * （影子还在，影子是渲染器另外画的）。改成 +0.20 后模型不再消失，但 0.45 高的框
     * 只罩住上半截，F3+B 看就是"框悬在蝴蝶下面、模型贴框上沿"。现在是框缩到 0.30、
     * 模型摆到正中的组合。
     *
     * <p>T 在 S(0.45) 外层，位移不参与缩放，所以 1 格就是 1 格。
     */
    private static final float MODEL_LIFT = 0.095F;

    public ManaButterflyRenderer(EntityRendererProvider.Context context) {
        super(context, new ManaButterflyModel(context.bakeLayer(ManaButterflyModel.LAYER_LOCATION)), 0.25F);
    }

    @Override
    protected void scale(ManaButterfly entity, PoseStack poseStack, float partialTickTime) {
        // 此处已经在 scale(-1,-1,1) 之后，所以 +Y 是"往下"：抬高要传负数。
        poseStack.translate(0.0F, -MODEL_LIFT, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(ManaButterfly entity) {
        return TEXTURE;
    }
}
