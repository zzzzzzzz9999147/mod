package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ManaInducerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MagicInduction.MODID);

    public static final RegistryObject<BlockEntityType<ManaInducerBlockEntity>> MANA_INDUCER =
            BLOCK_ENTITIES.register("mana_inducer",
                    () -> BlockEntityType.Builder.of(ManaInducerBlockEntity::new, ModBlocks.MANA_INDUCER.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
