package com.hjz.magicinduction;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MagicInduction.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.magicinduction"))
                    .icon(() -> new ItemStack(ModItems.MANA_INDUCER_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MANA_BUTTERFLY_SPAWN_EGG.get());
                        output.accept(ModItems.BUTTERFLY_JAR.get());
                        output.accept(ModItems.MANA_INDUCER_ITEM.get());
                        output.accept(ModItems.MAGIC_LOG_ITEM.get());
                        output.accept(ModItems.MAGIC_PLANKS_ITEM.get());
                        output.accept(ModItems.MAGIC_STICK.get());
                        output.accept(ModItems.MAGIC_LEAVES_ITEM.get());
                        output.accept(ModItems.MAGIC_SAPLING_ITEM.get());
                        output.accept(ModItems.MAGIC_SWORD.get());
                        output.accept(ModItems.MAGIC_PICKAXE.get());
                        output.accept(ModItems.MAGIC_AXE.get());
                        output.accept(ModItems.MAGIC_SHOVEL.get());
                        output.accept(ModItems.MAGIC_HOE.get());
                        output.accept(ModItems.MAGIC_ORE_ITEM.get());
                        output.accept(ModItems.MAGIC_CRYSTAL.get());
                        output.accept(ModItems.MANA_DUST.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
