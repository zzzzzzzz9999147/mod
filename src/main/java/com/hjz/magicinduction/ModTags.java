package com.hjz.magicinduction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** 野生蝴蝶会转化的方块：所有普通石类。 */
    public static final TagKey<Block> CONVERTIBLE =
            TagKey.create(Registries.BLOCK, new ResourceLocation(MagicInduction.MODID, "convertible"));

    /** 机器只吃紧贴四个侧面的「喂料方块」，默认只有圆石。独立成标签是为了让整合包改配方不用动代码。 */
    public static final TagKey<Block> INDUCER_INPUT =
            TagKey.create(Registries.BLOCK, new ResourceLocation(MagicInduction.MODID, "inducer_input"));

    private ModTags() {
    }
}
