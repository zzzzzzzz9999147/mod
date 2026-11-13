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
 * 一簇「参照原版火」的紫色火焰。
 *
 * <h2>为什么不用 Create 自带的 blaze_burner_flame 换个色</h2>
 * Create 的火焰模型（{@code blaze_burner/flame}）是一个底宽顶窄的收口方柱，
 * 四面各贴一张单帧 16×16 的图，火苗轮廓是个梯形色块。
 * 换色调之后玩家看到的仍然是「一个被染紫的方柱」——像贴图被 PS 调了色相。
 *
 * <p>这里改成原版 {@code fire_0}/{@code fire_1} 的做法：竖直面片，每层 4 片绕一圈，
 * 贴图是 32 帧的动画长条，火苗顶部几簇尖角会独立跳动。
 *
 * <h2>贴图刻意不做成 mcmeta 动画</h2>
 * {@code magic_flame_*.png} 保持为一张 16×512 的<b>静态</b>长条，不放
 * {@code .png.mcmeta}。一旦交给图集当动画管理，{@code sprite.getV()} 返回的就是
 * "当前帧"的 UV，渲染器再做一次帧偏移会叠加两次，火焰高频抖动。
 * 让渲染器独占帧号还有一个额外好处：每个燃烧室可以有自己的相位，
 * 一排炉子不会像复制粘贴一样同时跳火。
 *
 * <h2>UV 单位是 16，不是 1</h2>
 * {@link TextureAtlasSprite#getU}/{@link TextureAtlasSprite#getV} 的参数单位是
 * 「贴图内的 16 等分坐标」：整张贴图对应 {@code 0..16}，不是 {@code 0..1}。
 * 按 0~1 传参时 UV 被压缩 16 倍，整簇火退化成一块纯紫色方块。
 *
 * <h2>RenderType 必须 cutout</h2>
 * {@code MultiBufferSource.BufferSource.getBuffer} 对 translucent 这类「需要排序」的类型
 * 会把顶点收进内部 Map，等整批结束才统一提交；cutout 立即写出。
 * 用 translucent 的实测表现是：火被晚提交的炉体整个盖住，只剩炉子、一点紫色都没有。
 *
 * <h2>单帧图不能整张压成一片</h2>
 * 贴图是 32 帧竖着叠的 16×512；如果误判成 {@code frameCount = 1}，
 * 采样区间会变成整张图，屏幕上就是一块写满 32 帧的紫斑。所以
 * {@link #frameCountOf} 只看 {@code sprite.contents().height() / 16}，不看 sprite 的动画状态。
 */
@OnlyIn(Dist.CLIENT)
public final class PurpleFlameRenderer {

    /** 火焰贴图：16×16 一帧，竖着 32 帧。见 tools/gen_purple_flame.py。 */
    private static final ResourceLocation FLAME_0 =
            new ResourceLocation(MagicInduction.MODID, "block/magic_flame_0");
    private static final ResourceLocation FLAME_1 =
            new ResourceLocation(MagicInduction.MODID, "block/magic_flame_1");

    /** 一帧的边长（像素）。原版火是 16×16 一帧，竖着叠 32 帧。 */
    private static final int FRAME_PIXELS = 16;

    /**
     * 火焰面片的纵向范围（方块单位），以方块原点为基准。
     *
     * <p>直接用 Create 自己火焰 OBJ（{@code blaze_flame.obj}）的包围盒：
     * y 从 0.25 到 1.0。这样紫色火焰和原来橙色火焰占的体积完全一致，
     * 既不会缩在炉口里被烈焰人挡住，也不会顶穿笼子横梁。
     *
     * <p>曾经用过 0.10~0.82（贴着炉口往上 0.72），实拍结果是：
     * 火的下半截正好落在炉口金属边里，上半截又比烈焰人本体矮一截，
     * 远处看只像一片紫色贴纸。改用 Create 的包围盒之后不用再调。
     */
    private static final float FLAME_BOTTOM = 0.25F;
    private static final float FLAME_TOP = 1.0F;

    /**
     * 面片半宽（方块单位）。0.375 → 四片合起来和 Create 火焰一样占 0.125~0.875。
     *
     * <p>贴图本身已经在 {@code tools/gen_purple_flame.py} 里做过「向上收口」，
     * 所以这里给满宽不会显得方；给窄了反而会让火焰变成一根紫条。
     */
    private static final float HALF_WIDTH = 0.375F;

    /** 每帧前进的帧号步长，取与 32 互质的奇数，让帧序打散（见 drawLayer）。 */
    private static final float FRAME_ADVANCE = 41.0F;

    /** 帧间隔（tick）。原版火是 1 tick 一帧，观感偏「抽搐」；2 tick 一帧时火苗起伏读起来是「烧」。 */
    private static final float FRAME_TICKS = 2.0F;

    private PurpleFlameRenderer() {
    }

    /**
     * 画一簇紫色火焰。
     *
     * @param pose      {@code PoseStack}，进入时已定位到方块局部原点（坐标 0..1 对应整格）
     * @param buffers   顶点缓冲源；内部固定用 {@code RenderType.cutout()}
     * @param animTicks 动画相位（tick）。同一个炉子要传同一个值才连贯
     * @param scale     整体缩放；超级加热时放大、噪声起伏时小幅伸缩
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, float animTicks, float scale) {
        render(pose, buffers, FLAME_0, FLAME_1, animTicks, scale);
    }

    /** 用任意贴图画一簇火苗（留作换色/换形状的入口）。 */
    public static void render(PoseStack pose, MultiBufferSource buffers,
                              ResourceLocation layer0, ResourceLocation layer1,
                              float animTicks, float scale) {
        TextureAtlasSprite sprite0 = spriteFor(layer0);
        TextureAtlasSprite sprite1 = spriteFor(layer1);
        if (sprite0 == null || sprite1 == null) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());

        // 火焰自发光：满亮度，不吃环境光。和 Create 的 draw() 一致。
        int light = LightTexture.FULL_BRIGHT;
        // 外层：大轮廓；内层：0.72 倍、绕竖直轴再转 45°。
        // 两层轮廓互相咬合，叠出来才像"一团火"而不是"两张纸片"。
        drawLayer(pose, vc, sprite0, animTicks, scale, 0.0F, 0.0F, light);
        drawLayer(pose, vc, sprite1, animTicks, scale * 0.72F, 0.0F, 45.0F, light);
    }

    /** 帧数 = 贴图高度 / 16，至少 1 帧；单帧贴图永远是 1。 */
    private static int frameCountOf(TextureAtlasSprite sprite) {
        return Math.max(1, sprite.contents().height() / FRAME_PIXELS);
    }

    private static void drawLayer(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite,
                                  float animTicks, float scale, float yLift, float angleOffset, int light) {
        // 帧号用「乘一个大奇数再取模」而不是直接 animTicks % frames：
        // 直接取模时帧号是 0,1,2,… 顺着的，而贴图帧自上而下叠，屏幕上就读成
        // "紫色从下往上一帧帧扫过去"，像在加载贴图。乘 41（与 32 互质）后帧序被打散。
        int frames = frameCountOf(sprite);
        int frame = (int) Math.floor(animTicks / FRAME_TICKS * FRAME_ADVANCE) % frames;
        if (frame < 0) {
            frame += frames;
        }

        // 4 片，绕方块中心每 90° 一片；相邻两片同心不共面，不会 z-fighting。
        //
        // 曾经只画 2 片（45°/135° 的十字）。斜视时十字有一片几乎侧对相机，
        // 投影宽度趋近 0，整簇火退化成"一条竖直的紫色细线"——实拍截图里就是这样。
        // 4 片时任何水平视角都至少有一片接近正对相机，轮廓才立得住。
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
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
     * <p>UV 用 {@code getU}/{@code getV} 换算，不能自己乘 1/16 ——
     * sprite 在图集里的位置是动态打包的。它们的入参单位是 16 等分坐标：
     * 整张贴图对应 {@code 0..16}，所以 32 帧时一帧占 {@code 16/32 = 0.5}。
     *
     * <p><b>UV 的上下方向</b>：贴图第 0 帧在文件顶端（y=0..16），
     * 火苗尖角在帧内的上半部分，所以 {@code v = 0} 对应<b>火焰顶端</b>、
     * {@code v = 1} 对应<b>火焰底端</b>（炉口燃面）。宽端朝下、尖角朝上，
     * 这样贴上去才是一簇火，而不是倒挂的一颗紫水滴。
     *
     * <p><b>法线不能省。</b>方块用的是 {@code DefaultVertexFormat.BLOCK}，
     * 元素顺序固定为 位置 → 颜色 → UV0 → overlay → light → 法线。
     * {@code BufferBuilder} 按顺序校验，少填一个就在 {@code endVertex()} 抛
     * {@code Not filled all elements of the vertex}，整帧火焰一个都画不出来。
     */
    private static void emitQuad(VertexConsumer vc, TextureAtlasSprite sprite, PoseStack pose,
                                 int frame, int frames, float scale, float yLift, int light) {
        Matrix4f m = pose.last().pose();

        // 面片以方块中轴为中心、上下居中缩放：放大时向上下同时伸，
        // 不会因为缩放把火根从炉口里拔出来。
        float midY = (FLAME_BOTTOM + FLAME_TOP) * 0.5F + yLift;
        float halfH = (FLAME_TOP - FLAME_BOTTOM) * 0.5F * scale;

        float x0 = 0.5F - HALF_WIDTH * Math.min(1.0F, scale * 0.5F + 0.5F);
        float x1 = 0.5F + HALF_WIDTH * Math.min(1.0F, scale * 0.5F + 0.5F);
        float y0 = midY - halfH;
        float y1 = midY + halfH;

        // getV 的 0 在 sprite 顶部、16 在底部（图集坐标 y 向下）
        float u0 = sprite.getU(0.0F);
        float u1 = sprite.getU(16.0F);
        float vTop = sprite.getV(16.0F * frame / frames);
        float vBottom = sprite.getV(16.0F * (frame + 1) / frames);

        int overlay = OverlayTexture.NO_OVERLAY;

        // 正面（外法线 +Z）。
        //
        // 【必须顺时针序 + 外法线】，不能用 JC 的「外法线 + 逆时针序」。
        // Minecraft 从 1.20.x 起用 JK 的引擎管线的顶点绕序来判面朝向：
        // JC 项里把球坐标的 θ（复环绕竖直角）方向反过来了，所以同一组顶点在
        // 引擎眼里是<b>背面</b>。实测表现就是——正面看是空的，绕到炉子背后却能看到
        // 一片紫火浮在方块前面。这里把正面写成外法线 + 顺时针，正好对上。
        vc.vertex(m, x0, y1, 0.0F).color(255, 255, 255, 255).uv(u0, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x0, y0, 0.0F).color(255, 255, 255, 255).uv(u0, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y0, 0.0F).color(255, 255, 255, 255).uv(u1, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y1, 0.0F).color(255, 255, 255, 255).uv(u1, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();

        // 背面（内法线 -Z，与正面反向的绕序），否则绕到炉子另一侧火焰就"消失"了
        vc.vertex(m, x0, y1, 0.0F).color(255, 255, 255, 255).uv(u0, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x1, y1, 0.0F).color(255, 255, 255, 255).uv(u1, vTop).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x1, y0, 0.0F).color(255, 255, 255, 255).uv(u1, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
        vc.vertex(m, x0, y0, 0.0F).color(255, 255, 255, 255).uv(u0, vBottom).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, -1.0F).endVertex();
    }

    /**
     * 从方块图集里取 sprite。
     *
     * <p>贴图要先被图集收录。1.20.1 的 Forge <b>没有</b> {@code TextureStitchEvent.Pre}，
     * 所以走 {@code assets/magicinduction/atlases/blocks.json} 的 {@code minecraft:single} 声明。
     * 漏了这一步的表现是火焰完全不画，日志只有一行缺 sprite 的提示，不报错也不崩。
     */
    private static TextureAtlasSprite spriteFor(ResourceLocation loc) {
        return Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(loc);
    }
}
