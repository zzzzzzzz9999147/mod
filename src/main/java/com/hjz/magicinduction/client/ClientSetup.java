package com.hjz.magicinduction.client;

import com.hjz.magicinduction.ModBlocks;
import com.hjz.magicinduction.ModEntities;
import com.hjz.magicinduction.ModItems;
import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.common.ButterflyJarItem;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MagicInduction.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ManaButterflyModel.LAYER_LOCATION, ManaButterflyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MANA_BUTTERFLY.get(), ManaButterflyRenderer::new);
    }

    /**
     * 注册"罐内蝴蝶数"这个模型切换属性。
     *
     * 模型文件 butterfly_jar_base.json 里声明了 5 条 override（predicate 1.0~5.0），
     * 这里返回的浮点值直接就是蝴蝶只数（0.0~5.0）。用整数而非 count/5.0 的归一化值，
     * 是因为归一化会产生 0.2 / 0.6 这类无法用 float 精确表示的数值，
     * predicate 的相等比较有概率漏匹配到错误档位。
     *
     * 为什么不放在 common 侧：ItemProperties 是纯客户端渲染机制，专用服务端没有这个类，
     * 放错侧会直接 NoClassDefFoundError。
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.BUTTERFLY_JAR.get(),
                    new ResourceLocation(MagicInduction.MODID, "butterflies"),
                    (stack, level, entity, seed) -> (float) ButterflyJarItem.getButterflies(stack));

            // 树叶与树苗的贴图带 alpha 孔洞，必须显式走 cutout 渲染层。
            // 默认的 solid 层会丢弃透明通道，孔洞会被画成实心色块——而且不会报任何错，
            // 只会静默显示错误，属于"看起来像美术崩了其实是漏了一行代码"的典型。
            // 树叶用 cutout_mipped 是为了远处有 mipmap 过渡，不然会闪；树苗单株用 cutout 更锐利。
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGIC_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGIC_SAPLING.get(), RenderType.cutout());

            // 流体方块必须走 translucent 层，否则 alpha 会被当不透明，整滩液体变成实心色块。
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MANA_ESSENCE_BLOCK.get(), RenderType.translucent());
        });
    }
}
