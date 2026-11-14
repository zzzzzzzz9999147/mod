package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.ModItems;
import com.hjz.magicinduction.common.MagicCakeItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 魔法蛋糕 → 烈焰人燃烧室：机制完全照搬 Create 原版烈焰蛋糕，只额外把火焰换成紫色。
 *
 * <h2>原版烈焰蛋糕的机制（实测反编译 Create 6.0.8）</h2>
 * <ol>
 *   <li>{@code create:blaze_cake} 是个 {@code CombustibleItem}，燃烧 6400 tick；</li>
 *   <li>它被收在 {@code #create:blaze_burner_fuel/special} 标签里；</li>
 *   <li>右键燃烧室 → {@code BlazeBurnerBlock.use} → {@code tryInsert} →
 *       {@code BlazeBurnerBlockEntity.tryUpdateFuel(stack, charge, consume)}；</li>
 *   <li>{@code tryUpdateFuel} 看到 SPECIAL 标签 → {@code FuelType.SPECIAL} +
 *       <b>3200 tick</b>（不是 6400，SPECIAL 分支写死了 3200）；</li>
 *   <li>累加剩余时间 → {@code updateBlockState()} 推到 {@code SEETHING} →
 *       下料音效 + 粒子 → 消耗一个物品。</li>
 * </ol>
 *
 * <h2>这里怎么"照搬"</h2>
 * 不自己记账、不额外补煤、不改音效、不改消耗时机：
 * <ul>
 *   <li><b>燃料身份</b>：{@link MagicCakeItem} 挂 3200 tick 的燃烧值，并且
 *       {@code data/create/tags/items/blaze_burner_fuel_special.json}
 *       把魔法蛋糕列进 {@code #create:blaze_burner_fuel/special}。
 *       于是 Create 自己的判定链原封不动就能认出它；</li>
 *   <li><b>喂食</b>：这里只调一次 {@code tryUpdateFuel(stack, true, true)}，
 *       参数与原版 {@code tryInsert} 逐字一致（charge=true、consume=true）。
 *       consume 分支里 {@code tryUpdateFuel} 只做判定并直接 return true，
 *       扣减物品由调用方负责——和 {@code tryInsert} 一样，先 shrink 再往下走；</li>
 *   <li><b>空炉子</b>：{@code HEAT_LEVEL == NONE} 的燃烧室在 Create 里<b>根本没有
 *       BlockEntity</b>（{@code BlazeBurnerBlock.newBlockEntity} 返回 null），
 *       所以魔法蛋糕和烈焰蛋糕一样喂不进去，两条路径表现同步，没有特例；</li>
 *   <li><b>拒绝</b>：Create 不收（炉子已 SEETHING / 创造模式）时<b>不 cancel 事件</b>，
 *       让它照自己的流程返回失败，玩家听到的还是那个"喂不进去"的声音。</li>
 * </ul>
 *
 * <p>唯一的额外动作：成功后打一个 {@link #TAG_SATED} 标记，渲染层读到它画紫火。
 * 标记跟着 {@code activeFuel} 走，燃料烧完自动失效。
 *
 * <h2>为什么不干脆只靠标签、不要这段代码</h2>
 * 只靠标签的话，Create 会照常把蛋糕当燃料烧，但<b>没法区分</b>"这一炉吃的是魔法蛋糕"
 * 还是"这一炉吃的是烈焰蛋糕"——两者都是 SPECIAL，状态完全相同，渲染层无从下手。
 * 所以需要一次右键拦截来打标记，但拦截之后不抢 Create 的活。
 */
public final class MagicCakeEvents {

    /**
     * 存在燃烧室 BlockEntity 里的标记，键名带命名空间前缀避免和别人撞。
     *
     * <p>语义是"这一炉燃料里有一份是魔法蛋糕"，不是"永久变紫"。
     * 判定见 {@link #isSated}：燃料烧完（{@code activeFuel} 回到 {@code NONE}）自动失效。
     */
    public static final String TAG_SATED = "magicinduction:magic_cake";

    /**
     * {@code BlazeBurnerBlockEntity#tryUpdateFuel} 是 protected。
     *
     * <p>为什么非要用它：它是 Create 唯一一处"把一块燃料记进炉子"的地方——
     * 燃料类型判定、剩余时间累加、方块状态刷新、音效、粒子全在里面。
     * 自己写 {@code remainingBurnTime += 3200} 等于复制一份 Create 的记账逻辑，
     * 版本一改就对不上。这本来也是原版烈焰蛋糕走的路，行为天然一致。
     *
     * <p>反射失败时退化为"只打标记、不补燃料"：不会崩，但炉子不会因此被点亮。
     */
    private static final java.lang.reflect.Method TRY_UPDATE_FUEL = findTryUpdateFuel();

    private static java.lang.reflect.Method findTryUpdateFuel() {
        try {
            java.lang.reflect.Method m = BlazeBurnerBlockEntity.class.getDeclaredMethod(
                    "tryUpdateFuel", ItemStack.class, boolean.class, boolean.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            MagicInduction.LOGGER.warn("[Magic Induction] BlazeBurnerBlockEntity.tryUpdateFuel not found; "
                    + "magic cake will only mark the burner, not fuel it.");
            return null;
        }
    }

    private MagicCakeEvents() {
    }

    /**
     * 右键燃烧室时，把魔法蛋糕喂进去（和烈焰蛋糕同一套调用），成功后打紫火标记。
     *
     * <p>优先级 HIGH：要跑在 Create 自己的 {@code use()} 之前，因为 Create 处理完之后
     * 物品栈可能已经被它按"不是燃料"的逻辑动过。我们这里只做"提前喂一次"，
     * 喂成功了才 cancel，防止 Create 再喂一遍。
     */
    @SubscribeEvent
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

        // tryUpdateFuel 会改 remainingBurnTime、方块状态、音效，只能在服务端跑。
        // 客户端什么也不做：服务端改完会 notifyUpdate，客户端下一帧自然变紫。
        if (level.isClientSide) {
            return;
        }

        // 与 BlazeBurnerBlock.tryInsert 给 tryUpdateFuel 的参数完全一致：
        // charge=true（续到当前这一炉上）、consume=true（由它自己扣物品）。
        if (!tryFeed(burner, stack)) {
            // Create 拒绝了（炉子已 SEETHING / 创造模式炉子）：完全不插手，
            // 让事件继续往下走，Create 的 use() 按自己的逻辑返回失败。
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);

        // 燃料已经进炉 → 这一炉就是魔法蛋糕烧的，打标记。
        // 和燃料写入在同一个 tick，客户端下一帧就变紫，没有"先橙后紫"的过渡。
        burner.getPersistentData().putBoolean(TAG_SATED, true);
        burner.setChanged();
        burner.notifyUpdate();
    }

    /**
     * 把魔法蛋糕当作 SPECIAL 燃料交进炉子。
     *
     * <p><b>不做任何预加工</b>：直接传原始 {@code ItemStack}。
     * 因为 {@link MagicCakeItem#getBurnTime} 已经返回 3200，且物品在
     * {@code #create:blaze_burner_fuel/special} 标签里，所以
     * {@code tryUpdateFuel} 会自己走进 SPECIAL 分支——和烈焰蛋糕的判定路径逐行一致。
     *
     * <p>早期版本在这里额外塞了一块煤来"给紫火一个时长"，那是错的：
     * 玩家会看到炉子里多出一份燃料，而且燃烧时长和烈焰蛋糕对不上。
     *
     * @return Create 是否真的收下了这块燃料
     */
    private static boolean tryFeed(BlazeBurnerBlockEntity burner, ItemStack stack) {
        if (TRY_UPDATE_FUEL == null) {
            return false;
        }
        try {
            // 传一份 1 个的副本进去：consume=true 时 tryUpdateFuel 只做判定、不改栈，
            // 但传副本能保证将来 Create 若改成自己 shrink 也不会波及玩家手上那一叠。
            ItemStack one = stack.copy();
            one.setCount(1);
            Object result = TRY_UPDATE_FUEL.invoke(burner, one, true, true);
            if (!Boolean.TRUE.equals(result)) {
                return false;
            }
            // tryUpdateFuel(consume=true) 只做判定并 return true，扣减由调用方负责，
            // 位置也和 BlazeBurnerBlock.tryInsert 一致：先扣玩家手上的，再往下走。
            stack.shrink(1);
            return true;
        } catch (ReflectiveOperationException e) {
            MagicInduction.LOGGER.warn("[Magic Induction] failed to feed magic cake to blaze burner", e);
            return false;
        }
    }

    /**
     * 给客户端渲染判断"这个燃烧室现在该不该是紫火"。
     *
     * <p>两个条件：吃过魔法蛋糕 <b>且</b> 炉子还在烧。
     *
     * <p>第二个条件不能省。燃料烧完时 {@code activeFuel} 回到 {@code NONE}，
     * 如果只看标记，熄灭的炉子仍然会被认成"该画紫火"——虽然渲染分支里
     * {@code getHeatLevelForRender() != NONE} 会兜住，但把判据收在一处，
     * 以后改渲染逻辑时不容易漏。
     */
    public static boolean isSated(BlazeBurnerBlockEntity burner) {
        if (burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NONE) {
            return false;
        }
        var data = burner.getPersistentData();
        return data != null && data.getBoolean(TAG_SATED);
    }

    /**
     * 清掉标记，立刻恢复橙色火焰。
     *
     * <p>正常玩法用不到：燃料烧完时 {@link #isSated} 会因为 {@code activeFuel == NONE}
     * 自动返回 false。保留它是为了留一个明确入口——排错时用命令调、或者将来加
     * "铲子右键清除"之类玩法。
     */
    public static void clearSated(BlazeBurnerBlockEntity burner) {
        burner.getPersistentData().remove(TAG_SATED);
        burner.setChanged();
        burner.notifyUpdate();
    }

    /** 供渲染层/调试引用，避免魔法值散落。 */
    public static int burnTicks() {
        return MagicCakeItem.BURN_TICKS;
    }
}
