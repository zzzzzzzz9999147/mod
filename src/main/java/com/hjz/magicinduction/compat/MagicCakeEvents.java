package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.ModItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 魔法蛋糕 → 烈焰人燃烧室（Create 6.x）。
 *
 * <p>purpose: 给魔法原液开一条"有去处"的出口。在此之前原液只能倒在地上或装进桶里，
 * 玩家辛苦搭完搅拌产线，拿到的是一桶没有用途的东西。
 *
 * <h2>为什么用事件而不是 Mixin / 自定义方块</h2>
 * <ul>
 *   <li>自定义方块：等于把 Create 的热量系统重做一遍，而且工作盆认不出新方块，联动直接断；</li>
 *   <li>Mixin：最直接，但要多维护一份配置，还容易和 Flywheel 这类 coremod 抢注入点；</li>
 *   <li>事件：监听 {@link PlayerInteractEvent.RightClickBlock}，在 Create 处理右键之前
 *       把蛋糕接住。只依赖公开事件和燃烧室的公开方法，Create 小版本升级最不容易坏。</li>
 * </ul>
 *
 * <h2>为什么不把蛋糕塞进燃料表</h2>
 * 燃烧室判断燃料只看 {@code #create:blaze_burner_fuel/*} 标签和熔炉燃料值。塞进燃料表
 * 得到的是"又一个普通燃料"，烧完变回普通火焰，玩家看不出它和一块煤的区别。
 * 这里要的是<b>状态</b>：吃过蛋糕的燃烧室带一个自定义标记，火焰与粒子换成紫色，
 * 直到这炉燃料烧完。
 *
 * <h2>类加载安全：不能加 @Mod.EventBusSubscriber</h2>
 * 本类 import 了 {@code BlazeBurnerBlockEntity}。Forge 的
 * {@code AutomaticEventSubscriber} 会在 mod 构造期扫描所有带该注解的类，
 * <b>只看注解、不看 ModList</b>，所以一加注解就会触发类加载，
 * 没装 Create 时直接 {@code NoClassDefFoundError}，整个 mod 加载失败。
 *
 * <p>正确做法：不写注解，由 {@link MagicInduction} 在
 * {@code ModList.get().isLoaded("create")} 为真后手动 register。
 */
public final class MagicCakeEvents {

    /**
     * 存在燃烧室 BlockEntity 里的标记，键名带命名空间前缀避免和别人撞。
     *
     * <p>用 Boolean 而不是时间戳：效果跟着"这一炉燃料"走，不是固定秒数。
     * 玩家塞一个蛋糕就该享受完这一整段燃烧，烧到一半褪色反而不合理。
     */
    public static final String TAG_SATED = "magicinduction:magic_cake";

    private MagicCakeEvents() {
    }

    /**
     * 右键燃烧室时拦住蛋糕。
     *
     * <p>优先级 HIGH：必须跑在 Create 自己的 {@code use()} 之前，否则它会先下
     * "这不是燃料"的结论，我们没机会插手。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.MAGIC_CAKE.get())) {
            return;
        }
        Level level = event.getLevel();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (!(be instanceof BlazeBurnerBlockEntity burner)) {
            return;
        }

        // 吃过的不再吃第二个：重复喂食没有额外收益，只会让玩家以为要囤蛋糕。
        if (isSated(burner)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        event.setCanceled(true);
        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        feed(burner);
        // 消耗一个蛋糕（创造模式不扣）
        if (event.getEntity() != null && !event.getEntity().getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, event.getPos(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 1.0F, 1.0F);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /**
     * 蛋糕被吃下去之后：打标记、确保有火、刷新方块状态。
     *
     * <p>{@code tryUpdateFuel} 是 protected，不能直接调。空燃烧室吃蛋糕时，
     * 用 Create 自己的公开入口 {@code BlazeBurnerBlock.tryInsert} 喂一块煤，
     * 让它先进入 NORMAL 档——这样内部记账（activeFuel / remainingBurnTime）
     * 仍然由 Create 维护，我们只负责加"紫色"这个额外状态。
     */
    private static void feed(BlazeBurnerBlockEntity burner) {
        burner.getPersistentData().putBoolean(TAG_SATED, true);

        if (burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NONE) {
            // 空炉子：先点起来。tryInsert 的第三个参数 consume=false 表示只试不扣，
            // 这里传进去的是新建的煤栈，本来也不会进玩家背包。
            BlazeBurnerBlock.tryInsert(burner.getBlockState(), burner.getLevel(),
                    burner.getBlockPos(), new ItemStack(Items.COAL), false, false, false);
        }

        // 把方块状态同步到客户端（火焰渲染读的是 HEAT_LEVEL），并让 Create 重绘。
        BlockState state = burner.getBlockState();
        if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            burner.getLevel().setBlock(burner.getBlockPos(),
                    state.setValue(BlazeBurnerBlock.HEAT_LEVEL, burner.getHeatLevelFromBlock()), 3);
        }
        burner.setChanged();
        burner.notifyUpdate();
    }

    /** 给客户端渲染判断"这个燃烧室是不是吃过魔法蛋糕"。 */
    public static boolean isSated(BlazeBurnerBlockEntity burner) {
        CompoundTag data = burner.getPersistentData();
        return data != null && data.getBoolean(TAG_SATED);
    }
}
