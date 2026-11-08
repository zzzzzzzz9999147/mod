package com.hjz.magicinduction.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/**
 * 魔力诱导机器。
 *
 * purpose: 把「收藏品」转成「产能」，并且用"喂料"这个动作把资源循环变成玩家要持续维护的一件事。
 * player experience goal: 摆下机器、四个侧面各放一块圆石、看着它们一块块亮成矿——10 秒内看得到结果。
 * inputs:  罐子（1-5 只蝴蝶）+ 魔力粉尘（每转化 1 块消耗 1 个）+ 紧贴东/南/西/北四个侧面的圆石。
 * outputs: 被替换成魔力矿的圆石。
 * edge cases: 罐中 0 只、粉尘耗尽、四个侧面都没圆石、区块卸载。
 * failure states: 无粉尘仍在转化 = 印钞机；因此 tick 里硬性拦截。
 *
 * 注意：FACING 现在只决定"哪一面朝外"的贴图朝向，不再影响转化范围——
 * 四面都转，玩家不用再为了方向挪机器。保留这个属性是为了让模型正面纹路朝向不变。
 */
public class ManaInducerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 侧面指示灯档位 0..5：0 全灭、1..4 顺时针点亮、5 = 四点全亮 + 中间竖条点亮。 */
    public static final int MAX_INDICATOR = 5;
    public static final IntegerProperty BUTTERFLIES = IntegerProperty.create("butterflies", 0, MAX_INDICATOR);

    public ManaInducerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BUTTERFLIES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BUTTERFLIES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaInducerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof ManaInducerBlockEntity inducer) {
                ManaInducerBlockEntity.serverTick(lvl, pos, st, inducer);
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ManaInducerBlockEntity inducer)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return inducer.onPlayerUse(player, hand);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ManaInducerBlockEntity inducer) {
                inducer.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
