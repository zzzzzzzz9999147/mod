package com.hjz.magicinduction.common;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 玻璃罐（含蝴蝶计数）。
 *
 * purpose: 把"一次偶遇"变成"可携带资产"，并给机器一个明确的产能输入。
 * player experience goal: 右键蝴蝶那一下有"抓住了"的实感（音效 + 物品变闪）。
 * inputs:  右键魔力蝴蝶 +1，上限 5；右键诱导机器装机。
 * outputs: 携带 0-5 只蝴蝶的罐子。
 * edge cases: 罐满仍右键 → FAIL + 提示；罐内 0 只装进机器 → 拒绝。
 * failure states: 若允许无限叠加，玩家会囤 50 只蝴蝶，机器的 5 只上限失去意义。
 */
public class ButterflyJarItem extends Item {
    /** 硬约束：来自需求「最多装 5 只」，不可调。 */
    public static final int MAX_BUTTERFLIES = 5;
    private static final String TAG_COUNT = "Butterflies";

    public ButterflyJarItem(Properties properties) {
        super(properties);
    }

    public static int getButterflies(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_COUNT);
    }

    public static void setButterflies(ItemStack stack, int count) {
        stack.getOrCreateTag().putInt(TAG_COUNT, Mth.clamp(count, 0, MAX_BUTTERFLIES));
    }

    /**
     * 捕获一只蝴蝶。
     *
     * 抽成静态方法是被迫的：1.20.1 的 {@code Mob.interact} 是 final，交互顺序是
     * 实体侧 mobInteract 优先、物品侧 interactLivingEntity 兜底。两条路径只要有一条
     * 被游戏调用到，捕获就得生效，所以逻辑必须放在两边都能调的地方。
     *
     * @return FAIL = 罐满（不消耗动作），sidedSuccess = 捕获成功
     */
    public static InteractionResult capture(ItemStack stack, Player player, ManaButterfly butterfly) {
        if (getButterflies(stack) >= MAX_BUTTERFLIES) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.magicinduction.jar_full"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide) {
            setButterflies(stack, getButterflies(stack) + 1);
            butterfly.discard();
            player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
            // 服务端直接改手持物品的 NBT 后，客户端容器不会自己刷新——不广播的话
            // 玩家看到的还是空罐，会以为没抓到。这是"右键没反应"的另一半原因。
            player.getInventory().setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof ManaButterfly butterfly) {
            return capture(stack, player, butterfly);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getButterflies(stack) >= MAX_BUTTERFLIES;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = getButterflies(stack);
        tooltip.add(Component.translatable("tooltip.magicinduction.jar_count", count, MAX_BUTTERFLIES)
                .withStyle(ChatFormatting.GRAY));
        if (count >= MAX_BUTTERFLIES) {
            tooltip.add(Component.translatable("tooltip.magicinduction.jar_full_hint").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (count > 0) {
            tooltip.add(Component.translatable("tooltip.magicinduction.jar_install_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.magicinduction.jar_empty_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
