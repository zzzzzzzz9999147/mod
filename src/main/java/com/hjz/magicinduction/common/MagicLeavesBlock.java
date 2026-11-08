package com.hjz.magicinduction.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 魔法树叶。
 *
 * purpose: 让玩家隔着老远就能认出"这里有一棵魔法树"，同时充当蝴蝶的生成锚点。
 * design note: 刻意**不用** block light（.lightLevel()）。树叶成片出现，每片都发光会引发
 *              海量光照更新，是服务器卡顿的经典来源。改用 animateTick 撒紫色粒子，
 *              视觉效果一样有"魔法感"，但零光照开销。
 * edge cases: 只有下方是空气时才落粒子，否则大片树叶内部会堆积看不见的粒子。
 */
public class MagicLeavesBlock extends LeavesBlock {

    public MagicLeavesBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 1/12 的概率，避免整片树冠每秒喷出上百个粒子
        if (random.nextInt(12) != 0) {
            return;
        }
        // 只在树叶下沿抖落，内部方块不产生粒子
        if (!level.isEmptyBlock(pos.below())) {
            return;
        }
        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;
        double y = pos.getY() - 0.06D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;
        level.addParticle(ParticleTypes.WITCH, x, y, z, 0.0D, -0.012D, 0.0D);
    }
}
