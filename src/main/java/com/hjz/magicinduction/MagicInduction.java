package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ModFluids;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MagicInduction.MODID)
public class MagicInduction {
    public static final String MODID = "magicinduction";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagicInduction() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 流体类型必须在方块/物品之前注册：LiquidBlock 与 BucketItem 的构造器都会
        // 立刻引用 FluidType 的 RegistryObject，顺序反了会在注册表里拿到 null。
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);

        // 与机械动力的联动只有一处需要 Java 侧代码（蛋糕喂燃烧室），
        // 所以单独拿一个类出来，并且只在 Create 存在时才碰它。
        // 关键：if 判断放在这一层，compat 包里的类在没装 Create 时根本不会被加载，
        // 它的 import（BlazeBurnerBlockEntity 等）也就不会被解析，
        // 不会出现 NoClassDefFoundError。如果把判断写进那个类内部就晚了——
        // 类加载发生在方法执行之前。
        if (ModList.get().isLoaded("create")) {
            // forge 总线：右键拦截（MagicCakeEvents）
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    com.hjz.magicinduction.compat.MagicCakeEvents.class);
            LOGGER.info("[Magic Induction] Create detected, magic cake hooked to blaze burners.");

            // mod 总线：渲染器替换必须等到加载流程最后一步（FMLLoadCompleteEvent），
            // 因为 Create 的 Registrate 在 RegisterRenderers 事件之后才写入渲染器表。
            // 紫色火焰只在客户端有意义，DistExecutor 保证专用服务端不会碰渲染类。
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> {
                        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e) ->
                                com.hjz.magicinduction.compat.MagicFlameClient.installRenderer());
                        LOGGER.info("[Magic Induction] purple flame renderer queued for load-complete.");
                    });
        }

        LOGGER.info("[Magic Induction] loaded.");
    }
}
