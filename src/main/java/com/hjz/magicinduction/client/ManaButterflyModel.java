package com.hjz.magicinduction.client;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.common.ManaButterfly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 魔力蝴蝶模型。
 *
 * <p>几何**整份照抄** Bok's Banging Butterflies 7.10.12 的
 * {@code ButterflyModel.createBodyLayer}，一个数字都没改；贴图也是从它那 71 只蝴蝶里
 * 拿了一只（admiral，紫蛱蝶）改色而成，见 {@code tools/steal_butterfly.py}。
 * 所以游戏里看到的形状、比例、UV 分块和那只 mod 里飞的完全一致，只有颜色换成了紫罗兰。
 *
 * <pre>
 *   body       10 x 2 x 2      一根横杆，原点 (0,24,0)，就是头胸腹的全部
 *   left_wing  17 x 0 x 10     左翅：x 方向 17 是体长，z 方向 10 是伸出去的那一侧
 *   right_wing 17 x 0 x 10     右翅，贴在 -z 一侧
 *   antennae    3 x 2 x 0 ×2   两根触角，在贴图左上角 6x2 那一小块
 * </pre>
 *
 * <p><b>翅膀为什么是零厚度。</b>盒子 y 尺寸写 0，UP 面和 DOWN 面就落在同一个平面上。
 * 参考 mod 的贴图只画了 UP 面那一块（17x10），DOWN 面那一块是全透明的，
 * 而实体默认用 {@code entityCutoutNoCull} 渲染——两面都画、不剔背面，
 * 所以透明的那一面什么也画不出来，剩下一面从上往下、从下往上看都看得见，
 * 既不会 z-fighting 也不用为另一面再画一张图。
 * 上一版为了躲 z-fighting 给了 1 元厚度，反而把贴图用量翻倍，这次退回参考 mod 的做法。
 *
 * <p><b>整棵模型带一个 +90° 的 Y 旋转（不是参考 mod 的 -90°）。</b>参考 mod 把蝴蝶建成
 * "沿 x 横躺"，靠渲染器里 {@code poseStack.mulPose(Axis.YP(-90))} 把身体转到 z 方向。
 * 这里把同一个旋转写在 {@code pivot} 部件上——不用碰渲染器，也能被预览脚本还原，
 * 但**角度取了相反的 +90**，原因见下。
 *
 * <p><b>为什么是 +90。</b>{@code LivingEntityRenderer} 先转 {@code 180 - yBodyRot}，
 * 这一步把模型的 -z 对到实体朝向上（牛、猪的鼻子都在 -z，可以对照）。参考 mod 的几何里
 * 触角盒 (-8..-5) 自身还带一个绕 Y 的 π，转完落到 +x 端；再乘 Ry(-90) 正好落到 +z——
 * 也就是"头"跑到模型背后去了。照抄 -90 时整只蝴蝶是倒着飞的：触角在前、身体往后飘，
 * 翅膀的前翅也甩在身后。把角度取反，头/触角/前翅才一起落到 -z，跟着移动方向走。
 *
 * <p>注意它是挂在 pivot 上、不是挂在 body 上：{@link #setupAnim} 每帧要写
 * {@code body.yRot} 做左右摆，如果 90° 也在 body 上，第一帧就被覆盖掉了，
 * 整只蝴蝶会横着飘。
 *
 * <p><b>扑翼绕 X 轴、左右取反。</b>翼面是从 z=±1 往 ±z 伸出去的，
 * 绕 x 转才是"抬起 / 落下"；绕 z 转是原地打转，绕 y 是左右摆。
 * 两片翅膀分居 z 的两侧，同一个角度会让一片抬起一片压下，所以要取反。
 *
 * <p><b>头的方向是 -z。</b>实体朝 +z 时 {@code 180 - yBodyRot} 把模型空间的 +z 转去世界 -z，
 * 所以"模型 -z"才是前进方向。pivot 转 +90 之后，触角（几何上在 +x 端）落到 z=-8..-5，
 * 正好在 -z 这一头。
 *
 * <p>尺寸：1 单位 = 1/16 格。翼展 20 元、体长 17 元，渲染缩放 0.45 后
 * 约 0.56 x 0.48 格，落在 0.6 x 0.45 的碰撞箱里。贴图 64x64（参考 mod 也是这个尺寸）。
 */
public class ManaButterflyModel extends EntityModel<ManaButterfly> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(MagicInduction.MODID, "mana_butterfly"), "main");

    /**
     * 扑翼角速度，rad/tick。1.3 是参考 mod 的原值：一个来回约 4.8 tick，
     * 也就是每秒扇 4 次上下，比每秒 6~7 次的"电风扇"稳，看着像蝴蝶。
     */
    private static final float FLAP_SPEED = 1.3F;

    /** 摆幅 0.85 rad ≈ 49°，和参考 mod 的 π/4 同一个量级。再小就像在滑翔。 */
    private static final float FLAP_AMPLITUDE = 0.85F;

    /** 参考 mod 把身体原点挂在 y=24（脚底那一条线）。 */
    private static final float BODY_Y = 24.0F;

    /** 悬停上下浮动幅度，0.9 元 ≈ 0.025 格（乘 0.45 缩放后）。 */
    private static final float BOBBLE = 0.9F;

    /** 停驻时翅膀的残留角度：几乎水平，只留一点上扬避免和身体穿模。 */
    private static final float PERCH_SPREAD = 0.05F;

    private final ModelPart pivot;
    private final ModelPart body;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public ManaButterflyModel(ModelPart root) {
        this.pivot = root.getChild("pivot");
        this.body = this.pivot.getChild("body");
        this.leftWing = this.body.getChild("left_wing");
        this.rightWing = this.body.getChild("right_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();

        // 整棵子树绕 Y 转 +90°（参考渲染器是 -90°，这里取反，理由见类注释）：
        // 身体从"沿 x 横躺"变成"沿 z 前后"，触角那一头（几何上的 +x）落到 -z，即前进方向。
        PartDefinition pivot = mesh.getRoot()
                .addOrReplaceChild("pivot", CubeListBuilder.create(),
                        PartPose.offsetAndRotation(0.0F, BODY_Y, 0.0F,
                                0.0F, 90.0F * Mth.DEG_TO_RAD, 0.0F));

        PartDefinition body = pivot.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.ZERO);

        // 躯干：texOffs(0,20)，10x2x2 横杆，部件自身再绕 Y 转 180°（照抄）。
        // 那个 180° 管的是贴图方向：身体纹理 u 小的一端是头，转完才和"头朝 -z"对上。
        body.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 20)
                        .addBox(-5.0F, -2.0F, -1.0F, 10.0F, 2.0F, 2.0F),
                PartPose.rotation(0.0F, Mth.PI, 0.0F));

        // 翅膀：零厚度薄片。左片在 +z（texOffs(0,0)），右片在 -z（texOffs(0,10)），
        // 两片都从 x=-9 画到 x=8，整整 17 元的体长，前翅后翅画在同一片里。
        body.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-9.0F, -1.0F, 1.0F, 17.0F, 0.0F, 10.0F),
                PartPose.offset(0.0F, -1.0F, 0.0F));

        body.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 10)
                        .addBox(-9.0F, -1.0F, -11.0F, 17.0F, 0.0F, 10.0F),
                PartPose.offset(0.0F, -1.0F, 0.0F));

        // 触角：两个 3x2x0 薄片，取贴图左上角 6x2 那一小块。照抄不删——
        // 它们只有几像素，删了省不了事，留着才有参考 mod 那种前头的小凸起。
        body.addOrReplaceChild("antennae",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-8.0F, -4.0F, -1.0F, 3.0F, 2.0F, 0.0F)
                        .addBox(-8.0F, -4.0F, 1.0F, 3.0F, 2.0F, 0.0F),
                PartPose.rotation(0.0F, Mth.PI, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ManaButterfly entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isPerched()) {
            // 停在方块上：翅膀摊平张着，只做极慢的抽动，像真蝴蝶在晒太阳。
            float flutter = Mth.sin(ageInTicks * 0.12F) * 0.04F;
            this.leftWing.xRot = -PERCH_SPREAD - flutter;
            this.rightWing.xRot = PERCH_SPREAD + flutter;
            this.pivot.y = BODY_Y;
            this.body.yRot = 0.0F;
        } else {
            float flap = Mth.sin(ageInTicks * FLAP_SPEED) * FLAP_AMPLITUDE;

            // 两片翅膀分居 z 的两侧，同一个角度会一片抬起一片压下，必须取反。
            this.leftWing.xRot = -flap;
            this.rightWing.xRot = flap;

            // 悬停时整体上下浮动 + 一点左右摆，免得像一块静止的贴图在平移。
            // 写的是 pivot.y 和 body.yRot，别再碰 body 上的 -90°（那在 pivot 上）。
            this.pivot.y = BODY_Y + Mth.cos(ageInTicks * 0.28F) * BOBBLE;
            this.body.yRot = Mth.cos(ageInTicks * 0.19F) * 0.10F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.pivot.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
