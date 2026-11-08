package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ModFluids;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
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

        LOGGER.info("[Magic Induction] loaded.");
    }
}
