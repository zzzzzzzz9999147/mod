package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.ModItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 魔法蛋糕 → 烈焰人燃烧室（Create 6.x）：吃完之后这一炉的火焰变紫。
 *
 * <h2>效果边界（这里定义"成功"是什么）</h2>
 * <ul>
 *   <li>手持魔法蛋糕右键<b>正在燃烧</b>的燃烧室 → 立刻变紫，并往炉里补一格燃料；</li>
 *   <li>紫火<b>跟着这一炉燃料</b>走：燃料烧完（或蛋糕被吃掉的那一格燃料用完）就恢复橙色；</li>
 *   <li>变紫期间再喂普通燃料，紫火继续；</li>
 *   <li>喂进超级燃料（烈焰粉类）时紫色保留，颜色仍是紫，只是火更大——不做"紫转蓝"这种额外花样，
 *       避免玩家以为这是两种不同的状态。</li>
 * </ul>
 *
 * <h2>为什么是"补一格燃料"而不是"塞进去当燃料"</h2>
 * 蛋糕本身不该被 {@code #create:blaze_burner_fuel/*} 认成燃料：那样它只是"又一个煤"，
 * 烧完恢复橙色，玩家看不出区别。这里要的是<b>状态</b>——一个存在 BlockEntity 上的标记，
 * 渲染层读到它就换紫色火焰。喂进去的那块煤只是为了让"紫火这段"有明确的长度，
 * 空炉子吃蛋糕也不会出现"标记在、火不亮"的怪状态。
 *
 * <h2>为什么用事件而不是 Mixin / 自定义方块</h2>
 * <ul>
 *   <li>自定义方块：等于把 Create 的热量系统重做一遍，工作盆认不出新方块，联动直接断；</li>
 *   <li>Mixin：最直接，但要多维护一份配置，还容易和 Flywheel 这类 coremod 抢注入点；</li>
 *   <li>事件：监听 {@link PlayerInteractEvent.RightClickBlock}，在 Create 处理右键之前
 *       把蛋糕接住。只依赖公开事件与公开方法，Create 小版本升级最不容易坏。</li>
 * </ul>
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
     * <p>用 Boolean 而不是剩余时间戳：效果跟着"这一炉燃料"走，不是固定秒数。
     * 玩家喂一个蛋糕就该享受完这一整段燃烧，烧到一半褪色反而不合理。
     */
    public static final String TAG_SATED = "magicinduction:magic_cake";

    /**
     * {@code BlazeBurnerBlockEntity#tryUpdateFuel} 是 protected。
     *
     * <p>为什么非要用它：它是 Create 唯一一处"把一块燃料记进炉子"的地方——
     * 燃料类型判定（普通 / 超级）、剩余时间累加、方块状态刷新、音效全在里面。
     * 自己写 {@code remainingBurnTime = 1600} 等于复制一份 Create 的记账逻辑，
     * 版本一改就对不上。
     *
     * <p>反射失败时退化为"只打标记、不补燃料"：空炉子喂蛋糕会得到紫色标记但没有火，
     * 再喂一块煤就正常。是个可接受的降级，不会崩。
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
                    + "magic cake will only mark the burner, not refuel it.");
            return null;
        }
    }

    private MagicCakeEvents() {
    }

    /**
     * 右键燃烧室时拦住蛋糕。
     *
     * <p>优先级 HIGH：必须跑在 Create 自己的 {@code use()} 之前，否则它会先下
     * "这不是燃料"的结论，我们没机会插手。
     */
    /**
     * 压掉吃过魔法蛋糕的燃烧室自己撒的橙色粒子。
     *
     * <h2>为什么紫火换完了还看得到橙色的火</h2>
     * 炉口上方那几簇橙色火苗<b>不是方块模型</b>，是 {@code spawnParticles}
     * 每 tick 用 {@code ParticleTypes.FLAME}（超级加热时还有 {@code LARGE_SMOKE}、
     * 普通时 {@code SMOKE}）撒出来的粒子。换渲染器只换掉了"模型里的火"，
     * 换不掉"飘在空中的火"。
     *
     * <p>实测症状：紫色火苗画在 y≈0.25~1.0 的模型位置，橙色粒子悬在它上方，
     * 一开始很容易被误判成"Create 的原版渲染还在跑"。
     *
     * <h2>为什么在服务端拦</h2>
     * 粒子是服务端 {@code level.addParticle} 发出的，客户端收到才画。
     * 在服务端取消派发，连远处的其他玩家都看不到；
     * {@code level.isClientSide} 的调用一律放行（那是玩家自己踩火之类的情况）。
     */
    /**
     * 每次燃烧室要撒粒子时问一句："这炉吃过魔法蛋糕吗？"
     *
     * <p>判据与紫火渲染完全一致（{@code isSated && heat != NONE}），
     * 保证"紫色模型火"和"不冒橙色粒子"永远同时发生、同时消失。
     *
     * <p>调用方：{@code BlazeBurnerBlockEntity.spawnParticles} 的注入点
     * （见 {@code MagicCakeMixin}）。没有 Mixin 时这个方法不会被调用，
     * 效果退化成"紫色模型火 + 橙色悬浮粒子"，玩家仍能看出变紫了。
     */
    public static boolean suppressVanillaParticles(BlazeBurnerBlockEntity burner,
                                                   BlazeBurnerBlock.HeatLevel heat) {
        return heat != BlazeBurnerBlock.HeatLevel.NONE && isSated(burner);
    }

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

        // 全程 cancel：不管喂成功没有，都不能让 Create 拿到这个 ItemStack。
        event.setCanceled(true);

        // 空炉子（HEAT_LEVEL=NONE）在 Create 里根本不存在 BlockEntity（newBlockEntity 返回 null），
        // 上面那个 instanceof 判断已经把它挡掉了，走到这里的炉子一定是有火的。
        // 但"有 BE 且熄火"(SMOULDERING) 还是会进来 —— 这种情况说明正在烧或者快烧完，允许喂。

        // 吃过的不再吃第二个：重复喂食没有额外收益，只会让玩家以为要囤蛋糕。
        if (isSated(burner)) {
            if (event.getLevel().isClientSide && event.getEntity() != null) {
                event.getEntity().displayClientMessage(
                        Component.translatable("message.magicinduction.cake.already_sated")
                                .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        feed(burner);

        Player player = event.getEntity();
        // 消耗一个蛋糕（创造模式不扣）。只扣主手那一格，避免副手被连带清空。
        if (player != null && !player.getAbilities().instabuild
                && event.getHand() == InteractionHand.MAIN_HAND) {
            stack.shrink(1);
        }
        level.playSound(null, event.getPos(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 1.0F, 1.0F);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /**
     * 蛋糕被吃下去之后：打标记、补一格燃料、刷新方块状态。
     *
     * <p>顺序有讲究：<b>先补燃料再打标记</b>。{@code tryUpdateFuel} 内部会调用
     * {@code updateBlockState()} 把方块状态从 NONE 推到 FADING，
     * 那一刻如果标记已经在了，客户端下一帧就会画紫火——正好是玩家想看到的即时反馈。
     * 反过来先打标记，中间会有一帧"有标记但没火"的空档。
     */
    private static void feed(BlazeBurnerBlockEntity burner) {
        if (burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NONE) {
            // 空炉子：借 Create 自己的记账入口喂一块煤，让这一炉有个明确的时长。
            // 第三参数 simulate=true：先问"这块煤收不收"，避免在超级燃料炉里白白算一遍。
            insertCoal(burner);
        }

        burner.getPersistentData().putBoolean(TAG_SATED, true);
        burner.setChanged();

        // 让客户端立刻知道颜色变了。
        // notifyUpdate 发的是 BlockEntity 的 update tag：只带方块状态与持久数据，
        // 不会把整块方块重发一遍，也不会把工作盆顶掉。
        burner.notifyUpdate();
    }

    private static void insertCoal(BlazeBurnerBlockEntity burner) {
        if (TRY_UPDATE_FUEL == null) {
            return;
        }
        try {
            TRY_UPDATE_FUEL.invoke(burner, new ItemStack(Items.COAL), false, false);
        } catch (ReflectiveOperationException e) {
            MagicInduction.LOGGER.warn("[Magic Induction] failed to refuel blaze burner for magic cake", e);
        }
    }

    /** 给客户端渲染判断"这个燃烧室是不是吃过魔法蛋糕"。 */
    public static boolean isSated(BlazeBurnerBlockEntity burner) {
        CompoundTag data = burner.getPersistentData();
        return data != null && data.getBoolean(TAG_SATED);
    }

    /**
     * 清掉标记，恢复正常橙色火焰。
     *
     * <p>目前没有调用方——保留是因为它是 {@link #isSated} 的反操作，
     * 将来若要加"紫火只持续 N 秒"或"能用铲子铲掉"这类玩法，入口在这里。
     */
    public static void clearSated(BlazeBurnerBlockEntity burner) {
        burner.getPersistentData().remove(TAG_SATED);
        burner.setChanged();
        burner.notifyUpdate();
    }
}
