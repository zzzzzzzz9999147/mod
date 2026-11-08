package com.hjz.magicinduction;

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

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);

        LOGGER.info("[Magic Induction] loaded.");
    }
}
