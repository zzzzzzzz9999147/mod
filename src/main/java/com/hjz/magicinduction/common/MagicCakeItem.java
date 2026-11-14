package com.hjz.magicinduction.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔法蛋糕。
 *
 * <h2>为什么不做成食物</h2>
 * <ul>
 *   <li>玩家不该啃它。蛋糕是给蝴蝶和烈焰人的，"专门喂给谁"这件事必须清楚；</li>
 *   <li>一旦标成食物，就会被 {@code #minecraft:is_food} 相关逻辑收进去
 *       （狼/狐狸抢食、饥饿条提示），那些行为都不该出现；</li>
 *   <li>{@code Animal#mobInteract} 里写死了 {@code isFood(stack) && ... setInLove(...)}，
 *       挂上食物属性会让蝴蝶和"普通食物"绑在一起，破坏"结晶才是繁殖食物"的设计。</li>
 * </ul>
 *
 * <p>两个用途的入口分别写在：
 * <ul>
 *   <li>蝴蝶 → {@code ManaButterfly#mobInteract}</li>
 *   <li>烈焰人燃烧室 → {@code compat.MagicCakeEvents}（仅装 Create 时生效）</li>
 * </ul>
 *
 * <p>获取途径只有 Create 的注液器一条：烈焰蛋糕胚 + 250 mB 魔法原液。
 * 配方在 {@code recipes/magic_cake_from_filling.json}，被 {@code forge:mod_loaded} 包着，
 * 没装 Create 时这个物品仍可通过创造模式物品栏取得。
 */
public class MagicCakeItem extends Item {

    /**
     * 燃烧时长（tick）。
     *
     * <p>3200 与 Create 的烈焰蛋糕在 {@code BlazeBurnerBlockEntity.tryUpdateFuel}
     * 里拿到的值完全一致（烈焰蛋糕物品本身是 6400，进炉时被 SPECIAL 分支改成 3200）。
     * 为了让魔法蛋糕走<b>完全相同</b>的那条分支，这里直接给物品本身挂 3200，
     * 并在物品标签里把自己列进 {@code #create:blaze_burner_fuel/special}，
     * 于是 Create 不需要任何改动就会把它当 SPECIAL 燃料烧。
     *
     * <p>为什么是 3200 而不是 6400：改 6400 会让紫火比原版烈焰蛋糕长一倍，
     * "按原版机制做"就名不副实了；玩家对照烈焰蛋糕时会觉得数值被动过。
     */
    public static final int BURN_TICKS = 3200;

    public MagicCakeItem(Properties properties) {
        super(properties);
    }

    /**
     * 燃烧值：让 {@code ForgeHooks.getBurnTime} 认这块蛋糕能烧 3200 tick。
     *
     * <p>Create 的 {@code tryUpdateFuel} 判定顺序是
     * 「SPECIAL 标签 → {@code getBurnTime} → REGULAR 标签」。
     * 有标签就够进 SPECIAL 分支了，但如果没有燃烧值，
     * {@code tryUpdateFuel} 里 {@code remainingBurnTime} 的累加会出问题
     * （第二参数 charge=true 时走"加到当前这一炉"的分支，需要正数）。
     * 两个都给上，行为才和烈焰蛋糕逐 tick 一致。
     */
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable net.minecraft.world.item.crafting.RecipeType<?> recipeType) {
        return BURN_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.magicinduction.magic_cake_hint")
                .withStyle(ChatFormatting.GRAY));
        // 只有装了 Create 才提示燃烧室用途——否则玩家照着提示右键原版方块，
        // 会以为这是个 bug。
        if (net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            tooltip.add(Component.translatable("tooltip.magicinduction.magic_cake_hint_burner")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
