package com.hjz.magicinduction.client;

import com.hjz.magicinduction.MagicInduction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 用「原版火苗」的十字面片画紫色火焰。
 *
 * <h2>为什么不用 Create 自带的 blaze_burner_flame 换个色</h2>
 * Create 的火焰模型（{@code blaze_burner/flame}）是一个底宽顶窄的收口方柱，
 * 四面各贴一张单帧 16×16 的图，火苗轮廓是个梯形色块。
 * 换色调之后玩家看到的仍然是「一个被染紫的方柱」——像贴图被 PS 调了色相。
 *
 * <p>这里改成原版 {@code fire_0}/{@code fire_1} 的做法：两层竖直面片，每层十字交叉，
 * 两层之间错开 22.5°，贴图是 32 帧的动画长条，火苗顶部三簇尖角会独立跳动。
 *
 * <h2>贴图不做成 mcmeta 动画</h2>
 * 有意让 {@code magic_flame_*.png} 保持为一张 16×512 的<b>静态</b>长条，
 * 不放 {@code .png.mcmeta}。因为一旦交给图集当动画管理，
 * {@code sprite.getV()} 返回的就是"当前帧"的 UV，渲染器再做一次帧偏移会叠加两次，
 * 火焰会高频抖动。让渲染器独占帧号还有一个好处：每个燃烧室可以有自己的相位，
 * 一排炉子不会像复制粘贴一样同时跳火。
 */
@OnlyIn(Dist.CLIENT)
public final class PurpleFlameRenderer {

    /** 火焰贴图：16×16 一帧，竖着 32 帧。见 tools/gen_purple_flame.py。 */
    private static final ResourceLocation FLAME_0 =
            new ResourceLocation(MagicInduction.MODID, "block/magic_flame_0");
    private static final ResourceLocation FLAME_1 =
            new ResourceLocation(MagicInduction.MODID, "block/magic_flame_1");

    /**
     * 一帧的边长（像素）。原版火是 16×16 一帧，竖着叠 32 帧。
     *
     * <p>帧数不写死：相邻版本或资源包可能改帧数，甚至换成单帧静态图。
     * {@code sprite.contents().height() / 16} 能同时覆盖
     * 「16×512 的 32 帧」和「16×16 的单帧」两种情况
     * ——（原版 {@code fire_0} 就是单帧，动画靠 mcmeta 驱动，
     * 按 32 帧算会越界采样到图集里别的贴图上，这个坑实测踩过）。
     */
    private static final int FRAME_PIXELS = 16;

    /**
     * 火焰高度（方块单位）。
     *
     * <p>原版火方块的面片是 1 格宽、约 1.125 格高（上下各探出 1/16），
     * 贴图被纵向拉伸 1.125 倍。这里取 0.92，比例和原版几乎一样，
     * 又不会顶穿燃烧室上面压着的工作盆。
     */
    private static final float FRAME_HEIGHT = 0.92F;

    /**
     * 面片半宽。0.40 → 0.8 格宽。
     *
     * <p>不铺满整格：原版火是烧一整格，而燃烧室的原版火焰只占中间一小圈，
     * 铺满会让炉子看起来像被大火吞掉。0.8 格既保留「满格火」的体量，
     * 又能看出火焰是从炉口里冒出来的。
     */
    private static final float HALF_WIDTH = 0.40F;

    /** 火焰底面的 y 偏移，避免和燃烧室顶面共面。 */
    private static final float BASE_Y = 0.02F;

    private PurpleFlameRenderer() {
    }

    /**
     * 画一簇紫色火焰。
     *
     * @param pose      {@code PoseStack}，进入时已定位到方块局部原点（贴图坐标随方块 0..1）
     * @param buffers   顶点缓冲
     * @param animTicks 动画相位（tick）。同一个炉子要传同一个值才连贯，
     *                  不同炉子传不同值可以错开跳动
     * @param scale     整体缩放，超级加热时放大一点
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, float animTicks, float scale) {
        render(pose, buffers, FLAME_0, FLAME_1, animTicks, scale);
    }

    /**
     * 用任意贴图画一簇火苗。
     *
     * <p>抽出来是为了让开发期探针能拿原版 {@code minecraft:block/fire_0} /
     * {@code fire_1} 走同一条代码路径做 A/B 对照——形状不对时能立刻分清
     * 是「贴图的问题」还是「几何/UV 的问题」。
     *
     * @param layer0 外层贴图
     * @param layer1 内层贴图（错开 22.5° 叠在里侧）
     */
    public static void render(PoseStack pose, MultiBufferSource buffers,
                              ResourceLocation layer0, ResourceLocation layer1,
                              float animTicks, float scale) {
        TextureAtlasSprite sprite0 = spriteFor(layer0);
        TextureAtlasSprite sprite1 = spriteFor(layer1);
        if (sprite0 == null || sprite1 == null) {
            return;
        }

        // 火焰自发光：用满亮度，不吃环境光。和 Create 的 draw() 一致。
        int light = LightTexture.FULL_BRIGHT;
        // 双层贴图交替，叠出「内外两层火」的层次，原版 fire 方块也是这么做的。
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());

        // 每层 2 个十字交叉的面片，层间错开 22.5°，形成一圈 4 个方向都有火的效果。
        // 原版 fire 是 1 tick 一帧，帧数按贴图实际高度算。
        drawLayer(pose, vc, sprite0, animTicks, scale, 0.0F, 45.0F, light);
        drawLayer(pose, vc, sprite1, animTicks, scale, 0.14F, 67.5F, light);
    }

    /** 帧数 = 贴图高度 / 16，至少 1 帧；单帧贴图永远是 1。 */
    private static int frameCountOf(TextureAtlasSprite sprite) {
        return Math.max(1, sprite.contents().height() / FRAME_PIXELS);
    }

    private static void drawLayer(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite,
                                  float animTicks, float scale, float yLift, float angleOffset, int light) {
        // 原版 fire 是 1 tick 一帧
        int frames = frameCountOf(sprite);
        int frame = (int) animTicks % frames;
        if (frame < 0) {
            frame += frames;
        }

        // 只要 2 个面片（45° 和 135°，即十字交叉），因为 emitQuad 已经双面画了：
        // 0° 和 180° 的平面在空间上完全重合，循环 4 次等于每个平面画两遍。
        // 共面的两片会 z-fighting，屏幕上表现为火苗里一片闪烁的噪点（踩过）。
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            // 绕方块中心的竖直轴旋转，两次得到十字交叉的两个面片
            pose.translate(0.5F, 0.0F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(i * 90.0F + angleOffset));
            pose.translate(-0.5F, 0.0F, -0.5F);

            emitQuad(vc, sprite, pose, frame, frames, scale, yLift, light);
            pose.popPose();
        }
    }

    /**
     * 画一张竖直面片（双面）。
     *
     * <p>UV 用 {@link TextureAtlasSprite#getU}/{@link TextureAtlasSprite#getV}
     * 把「贴图内的坐标」换算到图集坐标——不能自己乘 1/16，
     * sprite 在图集里的位置是动态打包的。
     *
     * <p><b>这两个方法的参数单位是「16 像素」而不是 0~1</b>：
     * 反编译 {@code getV(double)} 得到的是
     * {@code v0 + (v1 - v0) * value / 16.0F}。
     * 所以整张贴图对应 {@code 0..16}，一帧 16×16 就是 {@code 0..1}。
     * 早先按 0~1 传参，UV 被压缩 16 倍，整簇火焰退化成一块纯紫色方块
     * ——这是整个功能里最隐蔽的一个坑。
     *
     * <p>还要注意 getV 的 0 在 sprite 顶部、16 在底部（图集坐标 y 向下），
     * 所以「第 frame 帧」的区间要按 sprite 总高换算：
     * 32 帧时一帧占 {@code 16/32 = 0.5} 个单位。
     *
     * <p><b>法线不能省。</b>{@code RenderType.cutout()} 用的是
     * {@code DefaultVertexFormat.BLOCK}，元素顺序固定为
     * 位置 → 颜色 → UV0 → overlay → light → <b>法线</b>。
     * {@code BufferBuilder} 按顺序校验，少填一个就在 {@code endVertex()}
     * 抛 {@code IllegalStateException: Not filled all elements of the vertex}，
     * 整帧的火焰一个都画不出来（这个坑也踩过一次）。
     */
    private static void emitQuad(VertexConsumer vc, TextureAtlasSprite sprite, PoseStack pose,
                                 int frame, int frames, float scale, float yLift, int light) {
        Matrix4f m = pose.last().pose();

        float x0 = 0.5F - HALF_WIDTH * scale;
        float x1 = 0.5F + HALF_WIDTH * scale;
        float y0 = BASE_Y + yLift;
        float y1 = BASE_Y + yLift + FRAME_HEIGHT * scale;

        // 贴图整宽 = 16 个单位；整高 16 个单位再按帧数切
        float u0 = sprite.getU(0.0F);
        float u1 = sprite.getU(16.0F);
        float vTop = sprite.getV(16.0F * frame / frames);
        float vBottom = sprite.getV(16.0F * (frame + 1) / frames);

        int overlay = OverlayTexture.NO_OVERLAY;

        // 正面
        vc.vertex(m, x0, y1, 0.0F).color(255, 255, 255, 255).uv(u0, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y1, 0.0F).color(255, 255, 255, 255).uv(u1, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y0, 0.0F).color(255, 255, 255, 255).uv(u1, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x0, y0, 0.0F).color(255, 255, 255, 255).uv(u0, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();

        // 背面（绕序反过来），否则绕到炉子另一侧火焰就"消失"了
        vc.vertex(m, x1, y1, 0.0F).color(255, 255, 255, 255).uv(u1, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x0, y1, 0.0F).color(255, 255, 255, 255).uv(u0, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x0, y0, 0.0F).color(255, 255, 255, 255).uv(u0, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x1, y0, 0.0F).color(255, 255, 255, 255).uv(u1, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
    }

    /**
     * 从方块图集里取 sprite。
     *
     * <p>贴图要先被图集收录。1.20.1 的 Forge <b>没有</b> {@code TextureStitchEvent.Pre}，
     * 所以不能靠事件登记，而是在
     * {@code assets/magicinduction/atlases/blocks.json} 里用
     * {@code minecraft:single} 显式声明——漏了这一步的表现是火焰完全不画，
     * 日志只有一行 Missing sprite，不报错也不崩。
     */
    private static TextureAtlasSprite spriteFor(ResourceLocation loc) {
        return Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(loc);
    }
}
