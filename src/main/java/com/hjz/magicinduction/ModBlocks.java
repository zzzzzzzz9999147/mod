package com.hjz.magicinduction;

import com.hjz.magicinduction.common.MagicLeavesBlock;
import com.hjz.magicinduction.common.MagicTreeGrower;
import com.hjz.magicinduction.common.ManaInducerBlock;
import com.hjz.magicinduction.common.ModFluids;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MagicInduction.MODID);

    public static final RegistryObject<Block> MAGIC_ORE = BLOCKS.register("magic_ore",
            () -> new DropExperienceBlock(
                    BlockBehaviour.Properties.copy(Blocks.IRON_ORE)
                            .strength(3.0F, 3.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE),
                    UniformInt.of(2, 4)));

    public static final RegistryObject<Block> MANA_INDUCER = BLOCKS.register("mana_inducer",
            () -> new ManaInducerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /**
     * 魔法原木。
     *
     * purpose: 魔法树的树干，也是玩家唯一能大量获取的魔法木质资源。
     * design note: 用 RotatedPillarBlock 而不是普通 Block——树干必须有 axis 属性，
     *              否则横着放的原木看起来是错的，而且原版所有原木都是这个类。
     * edge cases: 需要加入 minecraft:logs 标签，否则自己家的树叶会判定"没有树干支撑"而凋落。
     */
    public static final RegistryObject<Block> MAGIC_LOG = BLOCKS.register("magic_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)
                    .mapColor(MapColor.COLOR_PURPLE)));

    /**
     * 魔法树叶。
     *
     * purpose: 蝴蝶的生成锚点——玩家砍光树，蝴蝶就不再自然出现。
     * design note: 用自定义 MagicLeavesBlock，靠粒子而不是发光来体现魔法感（性能）。
     */
    public static final RegistryObject<Block> MAGIC_LEAVES = BLOCKS.register("magic_leaves",
            () -> new MagicLeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)
                    .mapColor(MapColor.COLOR_PURPLE)));

    /**
     * 魔法树苗。
     *
     * purpose: 让玩家能把魔法树"带回家"，在自己院子里养蝴蝶。
     * design note: SaplingBlock + MagicTreeGrower，骨粉可催熟，和野生树共用同一个
     *              ConfiguredFeature，所以种出来的和野外长的一模一样。
     */
    public static final RegistryObject<Block> MAGIC_SAPLING = BLOCKS.register("magic_sapling",
            () -> new SaplingBlock(new MagicTreeGrower(),
                    BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)
                            .mapColor(MapColor.COLOR_PURPLE)));

    /**
     * 魔法木板。
     *
     * <p>purpose: 让魔法树真正变成"一种木头"——原版所有木质配方（箱子、工作台、书柜、
     * 火把、碗……）用的都是 {@code #minecraft:planks} 这个标签，木板挂进标签之后，
     * 那些配方不用碰就自动认魔法木板。
     *
     * <p>design note: 用普通 {@link Block} 而不是自定义类，属性 copy 原版橡木木板
     * （硬度 2、抗爆 3、木头音效），只改 mapColor 让地图上看得出是紫色的林区。
     *
     * <p>edge cases: 标签写在 data/minecraft/tags/items/planks.json（物品标签）和
     * blocks/planks.json（方块标签）两处——配方查的是物品标签，缺一个就会出现
     * "能拿到但配方不认"这种最难查的状态。
     */
    public static final RegistryObject<Block> MAGIC_PLANKS = BLOCKS.register("magic_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_PURPLE)));

    /**
     * 魔法原液的液体方块。
     *
     * <p>purpose: 让桶能倒出来、让原液能在地面上流成一小滩。没有这个方块，桶只能"装"
     * 不能"放"，Create 工作盆里的原液就只能靠管道抽走，玩家会觉得这是个半成品。
     *
     * <p>design note: 参数照着水的反面来——不替换（replaceable 但 noCollission）、
     * 抗爆拉满（不想让它被苦力怕一炸就没）、noLootTable（流体不该掉落任何东西）。
     * 真正决定"流多远"的是 {@link com.hjz.magicinduction.common.ModFluids} 里的
     * slopeFindDistance / levelDecreasePerBlock，不是这里。
     */
    public static final RegistryObject<LiquidBlock> MANA_ESSENCE_BLOCK = BLOCKS.register("mana_essence",
            () -> new LiquidBlock(ModFluids.MANA_ESSENCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .replaceable()
                            .noCollission()
                            .strength(100.0F)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()
                            .liquid()
                            .sound(SoundType.EMPTY)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
