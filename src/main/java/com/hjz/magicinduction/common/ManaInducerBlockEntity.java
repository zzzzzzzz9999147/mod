package com.hjz.magicinduction.common;

import com.hjz.magicinduction.ModBlockEntities;
import com.hjz.magicinduction.ModBlocks;
import com.hjz.magicinduction.ModItems;
import com.hjz.magicinduction.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 魔力诱导机器的逻辑单元。
 *
 * 核心公式：每 tick 累积 progress += 蝴蝶数量；progress >= 100 时转化 1 块并扣 100。
 *  => 1 只 = 5 秒/块，5 只 = 1 秒/块。
 * 每转化 1 块消耗 1 个魔力粉尘，粉尘只能从魔力矿获得 => 长期产能上限 = 你挖过的矿量，不会失控。
 *
 * 转化对象：紧贴机器东/南/西/北四个侧面的圆石（可被数据包改标签）。机器不朝向了，
 * 玩家要做的事变成"喂料"——四个面放圆石，机器把它们一块块换成矿，挖走再补。
 */
public class ManaInducerBlockEntity extends BlockEntity {
    public static final int SLOT_JAR = 0;
    public static final int SLOT_DUST = 1;

    /** [PLACEHOLDER] 100 ticks：5 只蝴蝶 = 1 块/秒。低于 2 秒玩家看不到过程，高于 8 秒等待变惩罚。 */
    public static final int PROGRESS_PER_BLOCK = 100;
    /** [PLACEHOLDER] 1 粉尘 = 1 块。刻意做成 1:1，使机器是"2 倍收益放大器"而不是印钞机。 */
    public static final int DUST_PER_BLOCK = 1;

    /** 机器能一次转化的位置数：四个水平侧面。数字变小是设计的一部分——玩家要持续补料。 */
    public static final int SIDES = 4;

    /**
     * 内部库存。sided 包装见 {@link #EXPOSED}，朝向规则见 {@link #isInputSide}。
     */
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_JAR) {
                // 空罐也是合法输入：管道把空罐塞进来不算错，只是它不产生产能。
                return stack.getItem() instanceof ButterflyJarItem;
            }
            return stack.is(ModItems.MANA_DUST.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_JAR ? 1 : 64;
        }
    };

    /**
     * 对外暴露的库存视图：漏斗、MEK 管道、热力膨胀的伺服器都只看这个。
     *
     * <p><b>为什么不直接用 inventory。</b>裸的 ItemStackHandler 允许外部 extract，
     * 于是一根抽水管就能把罐子和粉尘抽回仓库，机器永远转不起来，而且玩家看不出原因
     * （库存确实"有东西"，但每 tick 就被抽走了）。这里把两个槽的 extract 全封死：
     * 产出走的是"替换旁边的圆石"，本来就不该有物品输出槽。
     *
     * <p>输入侧按面区分：<b>顶面和底面只收粉尘，四个水平面只收罐子</b>。
     * 分开是因为两个槽位语义差太远——罐子是"唯一一个、带着 NBT"的贵重品，
     * 粉尘是"可以堆 64"的耗材，混在同一个面里会让玩家排管道时反复试错。
     * 这样一条管道送粉尘（从上往下）、一条送罐子（沿水平面）就能各就各位，
     * 而不需要额外的过滤配置。
     *
     * <p>两侧都能插：{@link IItemHandler#insertItem} 时 Forge 会自己找合适的槽，
     * 所以哪怕玩家把粉尘接到侧面，也只是被拒（返回原栈），不会串槽。
     */
    private final IItemHandler exposed = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!inventory.isItemValid(slot, stack)) {
                return stack;
            }
            if (slot == SLOT_JAR) {
                return insertJar(stack, simulate);
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 硬拦截：这台机器没有"取回"这条路，拿东西请用手右键或 Shift+右键。
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };

    private LazyOptional<IItemHandler> itemHandler = LazyOptional.empty();
    private int progress;
    private int converted;
    /** 上一次写进方块状态的指示档位；-1 表示本生命周期还没写过（读档后第一 tick 会补一次）。 */
    private int lastIndicator = -1;

    /** 被换下来的空罐：先暂存一 tick，让下一个插罐请求能当场带走；没人要就丢到地上。 */
    @Nullable
    private ItemStack pendingJar = null;

    /**
     * 自动化输入专用的换罐逻辑。
     *
     * <p>罐子槽只能放一个，而管道通常是"一只带蝴蝶的罐子送进去，一只空罐换出来"。
     * 直接 {@code setStackInSlot} 会把旧罐悄悄吞掉——玩家会发现罐子越来越少了却
     * 不知道为什么。这里把旧罐存进 {@link #pendingJar}，先尝试塞回调用方
     * （返回"剩余的栈"给管道，管道会自己找地方），塞不下就在 tick 里丢到地上。
     *
     * <p>{@code simulate=true} 时只看"能不能收"，不改任何状态。
     *
     * @return 剩余没法接收的部分；空栈表示全部收下
     */
    private ItemStack insertJar(ItemStack stack, boolean simulate) {
        ItemStack current = inventory.getStackInSlot(SLOT_JAR);
        // 槽里已有带蝴蝶的罐子：不换。让玩家自己决定什么时候换，避免管道反复来回换罐。
        if (!current.isEmpty() && ButterflyJarItem.getButterflies(current) > 0) {
            return stack;
        }
        if (simulate) {
            return ItemStack.EMPTY;
        }

        ItemStack incoming = stack.copyWithCount(1);
        if (!current.isEmpty()) {
            // 现有的空罐先挂到待推出队列，别直接消失。
            ItemStack leftover = mergeIntoPending(current);
            if (!leftover.isEmpty()) {
                // 连暂存位都满了，这次就不收，免得物品被吞。
                return stack;
            }
        }
        inventory.setStackInSlot(SLOT_JAR, incoming);

        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    /**
     * 把一只换下来的空罐并进待推出队列。
     *
     * @return 放不进去的部分（调用方负责原样退回）
     */
    private ItemStack mergeIntoPending(ItemStack jar) {
        if (pendingJar != null && ItemStack.isSameItemSameTags(pendingJar, jar)) {
            // 空罐可以叠，顺手合一下，免得地上散一堆单个罐子。
            int room = pendingJar.getMaxStackSize() - pendingJar.getCount();
            int move = Math.min(room, jar.getCount());
            pendingJar.grow(move);
            ItemStack leftover = jar.copy();
            leftover.shrink(move);
            return leftover;
        }
        if (pendingJar == null) {
            pendingJar = jar.copy();
            return ItemStack.EMPTY;
        }
        return jar;
    }

    /** 把待推出的空罐丢到世界上。只在 serverTick 里调用，保证主线程。 */
    private void flushPendingJar() {
        if (pendingJar == null || this.level == null) {
            return;
        }
        BlockPos drop = this.worldPosition.relative(Direction.UP);
        Block.popResource(this.level, drop, pendingJar);
        pendingJar = null;
        this.setChanged();
    }

    public ManaInducerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MANA_INDUCER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaInducerBlockEntity inducer) {
        inducer.syncIndicator(state);
        // 换下来的空罐：给管道一 tick 的机会自己接走（insertJar 会优先从 pending 里取），
        // 没人要就落地，不能让它在体内无限攒着。
        inducer.flushPendingJar();
        int butterflies = inducer.getButterflies();
        if (butterflies <= 0) {
            return;
        }
        if (inducer.getDust() < DUST_PER_BLOCK) {
            return;
        }
        inducer.progress += butterflies;
        if (inducer.progress < PROGRESS_PER_BLOCK) {
            return;
        }
        if (inducer.convertOneBlock()) {
            inducer.progress -= PROGRESS_PER_BLOCK;
        } else {
            // 四个面都没圆石：进度停在满格，玩家一补料下一 tick 立刻转化，不用再等一轮。
            inducer.progress = PROGRESS_PER_BLOCK;
        }
    }

    /**
     * 从紧贴机器的四个水平侧面里，挑一个还是圆石的位置替换成魔力矿。
     * 随机挑是刻意的：玩家围着机器补料时，四个面会交替亮起来，而不是固定从左到右。
     * @return 是否成功转化，失败通常意味着四个侧面都空了。
     */
    private boolean convertOneBlock() {
        if (!(this.level instanceof ServerLevel server)) {
            return false;
        }

        List<BlockPos> candidates = new ArrayList<>(SIDES);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = this.worldPosition.relative(dir);
            if (server.getBlockState(pos).is(ModTags.INDUCER_INPUT)) {
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }

        BlockPos target = candidates.get(server.random.nextInt(candidates.size()));
        server.setBlockAndUpdate(target, ModBlocks.MAGIC_ORE.get().defaultBlockState());
        this.inventory.extractItem(SLOT_DUST, DUST_PER_BLOCK, false);
        this.converted++;
        this.setChanged();

        server.sendParticles(ParticleTypes.END_ROD,
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                8, 0.35D, 0.35D, 0.35D, 0.02D);
        server.playSound(null, target, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                0.5F, 1.0F + server.random.nextFloat() * 0.35F);
        return true;
    }

    /** 四个侧面是否还有圆石。只在玩家右键查状态时调用，不是热路径。 */
    public boolean hasInput() {
        if (this.level == null) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (this.level.getBlockState(this.worldPosition.relative(dir)).is(ModTags.INDUCER_INPUT)) {
                return true;
            }
        }
        return false;
    }

    public InteractionResult onPlayerUse(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (held.is(ModItems.MANA_DUST.get())) {
            ItemStack remainder = this.inventory.insertItem(SLOT_DUST, held, false);
            player.setItemInHand(hand, remainder);
            this.setChanged();
            this.sendStatus(player);
            return InteractionResult.CONSUME;
        }

        if (held.getItem() instanceof ButterflyJarItem) {
            if (ButterflyJarItem.getButterflies(held) <= 0) {
                player.displayClientMessage(Component.translatable("message.magicinduction.empty_jar"), true);
                return InteractionResult.CONSUME;
            }
            ItemStack existing = this.inventory.getStackInSlot(SLOT_JAR);
            if (!existing.isEmpty()) {
                // 手动换罐也要把旧罐还给玩家，不能只在地上吐一个——手上正拿着新罐，
                // 地上的那个很容易漏捡。
                if (!player.getInventory().add(existing)) {
                    player.drop(existing, false);
                }
            }
            this.inventory.setStackInSlot(SLOT_JAR, held.copy());
            player.setItemInHand(hand, ItemStack.EMPTY);
            this.setChanged();
            this.sendStatus(player);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && !this.inventory.getStackInSlot(SLOT_JAR).isEmpty()) {
            ItemStack jar = this.inventory.extractItem(SLOT_JAR, 1, false);
            if (!player.getInventory().add(jar)) {
                player.drop(jar, false);
            }
            this.setChanged();
            this.sendStatus(player);
            return InteractionResult.CONSUME;
        }

        this.sendStatus(player);
        return InteractionResult.CONSUME;
    }

    private void sendStatus(Player player) {
        int butterflies = this.getButterflies();
        int dust = this.getDust();
        if (butterflies <= 0) {
            player.displayClientMessage(Component.translatable("message.magicinduction.status_idle"), true);
            return;
        }
        if (dust < DUST_PER_BLOCK) {
            player.displayClientMessage(Component.translatable("message.magicinduction.status_no_dust", butterflies), true);
            return;
        }
        if (!this.hasInput()) {
            player.displayClientMessage(Component.translatable("message.magicinduction.status_no_input"), true);
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.magicinduction.status_running", butterflies,
                        ButterflyJarItem.MAX_BUTTERFLIES, dust, this.converted), true);
    }

    /**
     * 把方块状态的指示档位对齐到罐里真实的蝴蝶数。
     *
     * 放在 tick 开头而不是只写在"装罐/取罐"里，是因为它顺带把三种情况一起兜住：
     * 老存档（状态还是 0）、状态被别的途径改过、以及客户端重连后状态与物品不同步。
     * 绝大多数 tick 只做一次 int 比较，开销可以忽略。
     */
    private void syncIndicator(BlockState state) {
        int level = Math.min(this.getButterflies(), ManaInducerBlock.MAX_INDICATOR);
        if (level == this.lastIndicator) {
            return;
        }
        this.lastIndicator = level;
        if (state.getValue(ManaInducerBlock.BUTTERFLIES) == level) {
            return;
        }
        if (this.level == null) {
            return;
        }
        // 只发包给客户端，不触发邻块更新——否则每变化一次都会唤醒周围的红石和方块实体。
        this.level.setBlock(this.worldPosition,
                state.setValue(ManaInducerBlock.BUTTERFLIES, level), Block.UPDATE_CLIENTS);
        this.setChanged();
    }

    public int getButterflies() {
        ItemStack jar = this.inventory.getStackInSlot(SLOT_JAR);
        return jar.getItem() instanceof ButterflyJarItem ? ButterflyJarItem.getButterflies(jar) : 0;
    }

    public int getDust() {
        return this.inventory.getStackInSlot(SLOT_DUST).getCount();
    }

    public int getConverted() {
        return this.converted;
    }

    public void dropContents() {
        if (this.level == null) {
            return;
        }
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(this.level, this.worldPosition, stack);
                this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", this.inventory.serializeNBT());
        tag.putInt("Progress", this.progress);
        tag.putInt("Converted", this.converted);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.inventory.deserializeNBT(tag.getCompound("Inventory"));
        this.progress = tag.getInt("Progress");
        this.converted = tag.getInt("Converted");
    }

    /**
     * 判断某个面是否允许把 {@code stack} 送进 {@code slot}。
     *
     * <p>规则：罐子只能从四个水平面进，粉尘只能从顶/底进。{@code side == null}
     * 视为"不限面"，给没有面概念的调用方（比如 {@code ItemHandlerHelper} 在
     * 不知道朝向时做的试探）留一条通路，避免把正常交互也堵死。
     *
     * <p>热路径注意：{@link #getCapability} 每次被问都会走这里，所以只做几个
     * enum 比较，不碰世界状态。
     */
    private boolean isInputSide(@Nullable Direction side, int slot, ItemStack stack) {
        if (!inventory.isItemValid(slot, stack)) {
            return false;
        }
        if (side == null) {
            return true;
        }
        boolean vertical = side.getAxis().isVertical();
        return slot == SLOT_DUST ? vertical : !vertical;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            // 每个面一个薄包装，只做输入过滤；库存本体仍是同一个 inventory。
            return this.itemHandler.lazyMap(handler -> new SidedItemHandler(handler, side)).cast();
        }
        return super.getCapability(capability, side);
    }

    /**
     * 某一个面上的库存视图。
     *
     * <p>存在的理由只有一个：把"这个面能收什么"这件事在能力查找时就表达清楚，
     * 而不是等管道真的把物品塞进来才拒绝。MEK 的管道、Create 的溜槽、原版漏斗
     * 都会先问 {@code isItemValid} 再决定要不要抽，所以过滤放在这一层最省事。
     *
     * <p>不持有可变状态（side 与 handler 都是构造时给定），所以每个面可以随意新建，
     * 不会造成泄漏；LazyOptional 失效时外层容器会把它们一起丢掉。
     */
    private final class SidedItemHandler implements IItemHandler {
        private final IItemHandler delegate;
        @Nullable
        private final Direction side;

        SidedItemHandler(IItemHandler delegate, @Nullable Direction side) {
            this.delegate = delegate;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isInputSide(side, slot, stack)) {
                return stack;
            }
            if (slot == SLOT_JAR) {
                return insertJar(stack, simulate);
            }
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isInputSide(side, slot, stack);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.itemHandler = LazyOptional.of(() -> this.exposed);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.itemHandler = LazyOptional.of(() -> this.exposed);
    }
}
