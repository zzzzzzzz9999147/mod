package com.hjz.magicinduction.common;

import com.hjz.magicinduction.MagicInduction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import javax.annotation.Nullable;

/**
 * 魔法树苗的生长规则。
 *
 * purpose: 把 JSON 里定义的 magicinduction:magic_tree 接到树苗上，让玩家能自己种树养蝴蝶。
 * design note: 1.20.1 的这个基类是抽象类（不是新版本那种传参的普通类），必须写子类实现
 *              getConfiguredFeature。世界生成和骨粉催熟共用同一个 ConfiguredFeature，
 *              所以玩家种出来的树和野生的长得一模一样。
 */
public class MagicTreeGrower extends AbstractTreeGrower {
    public static final ResourceKey<ConfiguredFeature<?, ?>> MAGIC_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    new ResourceLocation(MagicInduction.MODID, "magic_tree"));

    @Nullable
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return MAGIC_TREE;
    }
}
