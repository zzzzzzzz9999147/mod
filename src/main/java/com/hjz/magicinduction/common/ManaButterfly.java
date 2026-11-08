package com.hjz.magicinduction.common;

import com.hjz.magicinduction.ModBlocks;
import com.hjz.magicinduction.ModEntities;
import com.hjz.magicinduction.ModItems;
import com.hjz.magicinduction.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 魔力蝴蝶。
 *
 * purpose: 把"野外随机遇到的生物"变成玩家主动探索的理由，并且是可携带的产能单位。
 * player experience goal: 在矿洞里听见翅膀声、看见石头闪成矿，产生"这里值得蹲守"的判断。
 * inputs:  自然生成（露天 + 白天明亮 + 附近 6 格内有魔法树叶）、玩家用玻璃罐右键捕获、用魔力结晶繁殖。
 * outputs: 野生状态下缓慢把附近圆石转成魔力矿（终身有限次），被捕后成为机器产能倍数。
 * edge cases: 野生转化次数用尽、被捕获时正在转化、繁殖冷却。
 * failure states: 若野生转化不限次数，玩家可以挂机刷矿；因此有 WILD_CONVERSION_LIMIT。
 *
 * 飞行手感的来源（对照 Bok's Banging Butterflies 的实现）：
 * 1) 无重力 + FlyingMoveControl(20, true)：蝴蝶靠振翅悬停，不受重力牵引；
 * 2) travel 里复刻原版空中运动学（固定 0.02 输入 + 0.91 空气摩擦），这是"飘"而不是"滑"的关键；
 * 3) tick 里每 tick 把竖直速度乘 0.6：上下速度很快被吃掉，蝴蝶才会平着飘，不会像炮弹一样抛上抛下；
 * 4) 不停留在地面：像蜜蜂那样从一个点飘到下一个点，落点始终在地表之上 1.5 格。
 */
public class ManaButterfly extends Animal {
    /** 野生转化终身次数上限：野生转化是"线索"不是"产线"，防止挂机刷矿。 */
    public static final int WILD_CONVERSION_LIMIT = 6;
    /** 200 ticks = 10s 扫描一次，配合 1/12 概率，平均 2 分钟一次成功转化。 */
    private static final int WILD_SCAN_INTERVAL = 200;
    private static final int WILD_SCAN_RADIUS = 6;
    private static final int WILD_SUCCESS_ROLL = 12;

    /**
     * 每几 tick 扇一次翅。
     *
     * 模型那边用的是参考 mod 的原值 1.3 rad/tick，一个来回 2π/1.3 ≈ 4.8 tick。
     * 这里取 5 让音效/扑翼判定和视觉同拍——两处差太多的话，蝴蝶看着在扇、
     * 声音却是另一个频率。
     */
    public static final int TICKS_PER_FLAP = 5;

    /**
     * 停驻状态。必须同步到客户端，否则服务端把它钉在地上、客户端模型还在狂扇翅膀，
     * 看起来像"卡住的贴图"。
     */
    private static final EntityDataAccessor<Boolean> DATA_PERCHED =
            SynchedEntityData.defineId(ManaButterfly.class, EntityDataSerializers.BOOLEAN);

    private int wildConversions;

    /**
     * 是否处于"被打飞之后的坠落"。
     *
     * 蝴蝶自主飞行（含主动降落）时会每 tick 清掉 fallDistance，因为它是"飞下去"而不是"掉下去"，
     * 从树冠滑到地面不该掉血。但被玩家打中、被爆炸掀飞之后，它就真的在往下掉，这时才累积
     * 摔落高度，落地按伤害结算。落地后标记自动解除。
     */
    private boolean fallAfterHit;

    public ManaButterfly(EntityType<? extends ManaButterfly> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        // 蝴蝶靠振翅悬停，不该受重力牵引。开着重力时，停驻那几秒会往方块里沉。
        this.setNoGravity(true);
    }

    /**
     * 属性照着参考 mod 的成体蝴蝶配：3 血、飞行速度 1.8、跟随 9。
     *
     * 飞行速度 1.8 看着夸张，但 FlyingMoveControl 会把它乘进扇翅推力里，
     * 折算下来约 7~8 格/秒——就是"人一靠近就忽地飞开"的那个速度。
     * 之前用 0.4（鹦鹉的值）时，飞行接近滑翔，观感是"贴图在平移"。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.FLYING_SPEED, 1.8D)
                .add(Attributes.MOVEMENT_SPEED, 1.8D)
                .add(Attributes.FOLLOW_RANGE, 12.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PERCHED, false);
    }

    /**
     * 准星容差。
     *
     * 这个值只在 ProjectileUtil.getEntityHitResult 里被用来膨胀碰撞箱，
     * 不改变实体真实体积。蝴蝶现在像蜜蜂一样一直在飞、不再落地停驻，
     * 右键它就得瞄一个"以 7~8 格/秒乱窜的 0.6 格目标"——不给容差基本抓不到。
     * 0.70 让可点范围膨胀到约 2 格，比原来 0.55 更宽松，是"取消落地"的必要补偿。
     */
    @Override
    public float getPickRadius() {
        return 0.70F;
    }

    public boolean isPerched() {
        return this.entityData.get(DATA_PERCHED);
    }

    private void setPerched(boolean perched) {
        this.entityData.set(DATA_PERCHED, perched);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 只有"跑着的玩家"会惊起蝴蝶。走路或站着不动时它照旧飞在手边——
        // 这条是观感（活物感）和玩法（贴脸右键捕捉）之间唯一能两全的分界线。
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.4D, 1.4D,
                living -> living instanceof Player player && player.isSprinting()));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, Ingredient.of(ModItems.MAGIC_CRYSTAL.get()), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        // 蜜蜂式飞行：不停地从一个点飘到下一个点，永远不主动落地。
        // 这里刻意没有"落地停驻"目标——蝴蝶停在地上就不像蝴蝶了。
        this.goalSelector.addGoal(5, new ButterflyWanderGoal(this, 1.0D));
    }

    /**
     * 漫游目标：在父类的基础上把目标点抬离地面。
     *
     * WaterAvoidingRandomFlyingGoal 是给鹦鹉那类鸟写的，它选点只看"有没有水"，
     * 不管高度。对无重力实体来说，连续几次低空选点会让蝴蝶一路贴着草皮滑行，
     * 看起来既不像飞也不像停——正是"像蜜蜂一样飞"要避免的状态。
     * 这里补一条：目标高度至少是地表之上 1.5 格。
     */
    private static class ButterflyWanderGoal extends WaterAvoidingRandomFlyingGoal {
        ButterflyWanderGoal(ManaButterfly butterfly, double speedModifier) {
            super(butterfly, speedModifier);
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            Vec3 target = super.getPosition();
            if (target == null) {
                return null;
            }
            BlockPos pos = BlockPos.containing(target);
            int surface = this.mob.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            double minY = surface + 1.5D;
            return target.y < minY ? new Vec3(target.x, minY, target.z) : target;
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ModItems.MAGIC_CRYSTAL.get());
    }

    /**
     * 实体优先的交互入口。
     *
     * 为什么不只靠 Item.interactLivingEntity：1.20.1 的 Mob.interact 是 final，
     * 它会先调 mobInteract，只有返回 PASS 才轮到物品侧。这条路径更靠前、更可靠，
     * 而且和原版"手持特定物品右键生物"的写法一致。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof ButterflyJarItem) {
            return ButterflyJarItem.capture(stack, player, this);
        }
        return super.mobInteract(player, hand);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.MANA_BUTTERFLY.get().create(level);
    }

    /**
     * 每 tick 消掉六成竖直速度。
     *
     * 没有这一行，扇翅推力攒下来的竖直速度会一直留着，蝴蝶就会一路向上飘出树冠再掉下来。
     * 乘 0.6 之后竖直速度只够撑一两 tick，看起来是"悬着微微起伏"，横向速度不受影响。
     */
    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
    }

    /**
     * 扇翅节拍，供渲染/音效查询。与服务端同步无关——它只由 tickCount 推出来。
     */
    @Override
    protected boolean isFlapping() {
        return this.tickCount % TICKS_PER_FLAP == 0;
    }

    /**
     * 飞行运动学：照抄原版空中分支，但**不加重力**。
     *
     * 原版 LivingEntity.travel 在末尾会按 isEffectiveAi 走两套逻辑；这里只保留空中那一套，
     * 并固定用 0.02 的输入系数（原版在此处对玩家用 this.getSpeed()，对生物是 0.02）。
     * 摩擦 0.91 让加速一步步衰减，形成"起飞一顿、然后匀速飘"的手感。
     */
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8D));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            } else {
                BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
                float friction = 0.91F;
                if (this.onGround()) {
                    friction = this.level().getBlockState(ground)
                            .getFriction(this.level(), ground, this) * 0.91F;
                }
                float frictionCoefficient = 0.16277137F / (friction * friction * friction);
                this.moveRelative(this.onGround() ? 0.1F * frictionCoefficient : 0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(friction));
            }
        }
        this.trackFallDamage();
        this.calculateEntityAnimation(false);
    }

    /**
     * 摔落高度的记账规则。
     *
     * 必须放在 move() 之后：真正的落地判定（checkFallDamage）发生在 move() 内部，
     * 结算完才轮到我们清理，否则会把刚算出来的那一记伤害一起抹掉。
     * 放在 move() 之前清也可以，但那样"被打飞后落地"这一帧同样会被吞掉。
     */
    private void trackFallDamage() {
        if (this.level().isClientSide) {
            return;
        }
        if (!this.fallAfterHit) {
            // 自主飞行：蝴蝶是飞下去的，不是掉下去的，不累计摔落高度
            this.resetFallDistance();
        } else if (this.onGround()) {
            // 被打飞之后终于落地，结算完成，恢复正常
            this.fallAfterHit = false;
            this.resetFallDistance();
        }
    }

    /**
     * 被打中之后才允许累积摔落高度。
     *
     * 蝴蝶 6 点血，从树冠掉到地面本来就是它的日常动作；如果一路上都算摔落，
     * 它会在正常降落时莫名其妙掉血。只有"被打飞"这种失控坠落才该摔。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            this.fallAfterHit = true;
            this.resetFallDistance();
        }
        return hurt;
    }

    /**
     * 摔落伤害。
     *
     * 原版 LivingEntity.causeFallDamage 第一行就是 `if (this.isNoGravity()) return false;`，
     * 而蝴蝶必须 setNoGravity(true) 才能悬停——两条需求直接互斥，于是它从再高的地方掉下来
     * 也毫发无伤。这里绕开那个短路，自己按"体轻"折算：3.5 格以内免伤，之后每 3 格 1 点。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance <= 3.5F) {
            return false;
        }
        int damage = Mth.ceil((fallDistance - 3.5F) / 3.0F * multiplier);
        if (damage <= 0) {
            return false;
        }
        return this.hurt(source, (float) damage);
    }

    /**
     * 是否"正贴着地面静止"。
     *
     * onGround 在无重力实体上不一定每 tick 都为 true（竖直速度被 tick 里的 ×0.6 压到极小，
     * move() 偶尔判不出碰撞），所以再补一次几何判定：碰撞箱底面几乎贴在某块硬方块顶面上。
     * 这是"被地形卡住了"的兜底，正常飞行时永远是 false。
     */
    private boolean isRestingOnGround() {
        if (this.isInWater() || this.isInLava() || this.isPassenger()) {
            return false;
        }
        // 刚起飞那一 tick 的 onGround 还残留着上一格的 true，靠向上的速度把它否掉
        if (this.getDeltaMovement().y > 0.02D) {
            return false;
        }
        if (this.getDeltaMovement().horizontalDistanceSqr() > 0.004D) {
            return false;
        }
        if (this.onGround()) {
            return true;
        }
        // getY() 就是碰撞箱底面。站在方块顶面时它是整数，gap 约等于 0。
        double gap = this.getY() - Math.floor(this.getY());
        if (gap > 0.08D) {
            return false;
        }
        BlockPos ground = BlockPos.containing(this.getX(), this.getY() - 0.02D, this.getZ());
        return this.level().getBlockState(ground).isFaceSturdy(this.level(), ground, Direction.UP);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // 蝴蝶不再主动落地，所以这个状态只有一个来源：真的被地形卡在地面上了
        // （低空撞墙、被击退到底、水边扑腾）。那种时候让它摊开翅膀比让它扇翅更像活的，
        // 而不是一只卡住的贴图。
        this.setPerched(this.isRestingOnGround());
        if (this.tickCount % WILD_SCAN_INTERVAL == 0 && this.wildConversions < WILD_CONVERSION_LIMIT) {
            this.tryWildConversion();
        }
    }

    /**
     * 野生转化：只处理"圆石类"方块，完成后计数 +1，终身最多 6 次。
     * 先掷概率再扫描，避免每只蝴蝶每 10 秒都做 3375 次方块查询。
     */
    private void tryWildConversion() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (this.getRandom().nextInt(WILD_SUCCESS_ROLL) != 0) {
            return;
        }
        BlockPos origin = this.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-WILD_SCAN_RADIUS, -WILD_SCAN_RADIUS, -WILD_SCAN_RADIUS),
                origin.offset(WILD_SCAN_RADIUS, WILD_SCAN_RADIUS, WILD_SCAN_RADIUS))) {
            if (server.getBlockState(pos).is(ModTags.CONVERTIBLE)) {
                candidates.add(pos.immutable());
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        BlockPos target = candidates.get(this.getRandom().nextInt(candidates.size()));
        server.setBlockAndUpdate(target, ModBlocks.MAGIC_ORE.get().defaultBlockState());
        this.wildConversions++;
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
        server.sendParticles(ParticleTypes.END_ROD,
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                6, 0.3D, 0.3D, 0.3D, 0.02D);
    }

    public int getWildConversions() {
        return this.wildConversions;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WildConversions", this.wildConversions);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.wildConversions = tag.getInt("WildConversions");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * 生成条件：露天 + 白天明亮 + 附近有魔法树冠。
     *
     * 这套规则是照着"蜜蜂必须有蜂巢"的思路做的，区别是蝴蝶不需要一个巢方块——
     * 魔法树叶本身就充当栖息地标记。砍光附近的树，蝴蝶就不再出现；自己种一棵，蝴蝶会回来。
     *
     * 最后一关是出生配额（见 ButterflyQuotaData）：每个 32×32 格区域终身只放行 5 只，
     * 死掉或被罐子抓走都不会补货。这样一片林子是"会被用光的资源点"，
     * 而不是一台无限运转的刷怪机。
     *
     * 为什么先查高度图再扫方块：蝴蝶是 AMBIENT 类别，生成尝试非常频繁。直接进 13×13×10
     * 的方块扫描会让主线程每秒查上百万次方块。先用 25 次高度图查询把"附近根本没有高物体"
     * 的情况挡掉，只有真可能有树冠时才做精确扫描。
     */
    public static boolean checkSpawnRules(EntityType<ManaButterfly> type, ServerLevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.SPAWN_EGG) {
            return true;
        }
        // 只在露天生成：MOTION_BLOCKING_NO_LEAVES 不含树叶，所以站在树冠下方仍然算露天，
        // 但待在洞穴或地下时 pos.y 会明显低于这个高度，直接被拒——这就是"去除地下生成"。
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (pos.getY() < surface - 3) {
            return false;
        }
        // 夜晚和雷暴不刷，蝴蝶是白天活动的
        if (level.getRawBrightness(pos, 0) < 9) {
            return false;
        }
        if (!hasMagicCanopyNearby(level, pos)) {
            return false;
        }
        // 出生配额放在最后：这里只查余量，真正的扣减在 finalizeSpawn 里做。
        // 生成尝试失败（位置被占、事件取消）不应该吃掉名额。
        if (level instanceof ServerLevel server) {
            return ButterflyQuotaData.get(server).remaining(pos.getX(), pos.getZ()) > 0;
        }
        return true;
    }

    /**
     * 真正扣名额的地方。
     *
     * 用 finalizeSpawn 而不是 checkSpawnRules：前者每只蝴蝶只跑一次，后者会被反复试探。
     * 只认 NATURAL / CHUNK_GENERATION，刷怪蛋、刷怪笼、繁殖都不占配额——
     * 创造模式的刷怪蛋是给玩家调试用的，不该被"这片林子用光了"拦住。
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        if (level instanceof ServerLevel server
                && (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)) {
            BlockPos pos = this.blockPosition();
            ButterflyQuotaData.get(server).tryConsume(pos.getX(), pos.getZ());
        }
        return data;
    }

    /**
     * 附近 6 格内有没有魔法树叶。
     * 第一次循环是廉价预筛：在 5×5 个采样列上问"这一列最高点是不是比蝴蝶高 2 格以上"。
     * 平坦的草原和沙漠在这里就会被挡掉，不会进入下面的方块扫描。
     */
    private static boolean hasMagicCanopyNearby(ServerLevelAccessor level, BlockPos pos) {
        boolean possible = false;
        for (int dx = -6; dx <= 6 && !possible; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        pos.getX() + dx, pos.getZ() + dz);
                if (top > pos.getY() + 2) {
                    possible = true;
                    break;
                }
            }
        }
        if (!possible) {
            return false;
        }

        Block leaves = ModBlocks.MAGIC_LEAVES.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy <= 8; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(cursor).is(leaves)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        // 停在方块上时不叫：收翅歇着还一直响，会被误当成"卡住的生物"
        return this.isPerched() ? null : SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }
}
