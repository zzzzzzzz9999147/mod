package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ButterflyJarItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MagicInduction.MODID);

    public static final RegistryObject<Item> MAGIC_CRYSTAL = ITEMS.register("magic_crystal",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MANA_DUST = ITEMS.register("mana_dust",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BUTTERFLY_JAR = ITEMS.register("butterfly_jar",
            () -> new ButterflyJarItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MAGIC_ORE_ITEM = ITEMS.register("magic_ore",
            () -> new BlockItem(ModBlocks.MAGIC_ORE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MANA_INDUCER_ITEM = ITEMS.register("mana_inducer",
            () -> new BlockItem(ModBlocks.MANA_INDUCER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_LOG_ITEM = ITEMS.register("magic_log",
            () -> new BlockItem(ModBlocks.MAGIC_LOG.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_LEAVES_ITEM = ITEMS.register("magic_leaves",
            () -> new BlockItem(ModBlocks.MAGIC_LEAVES.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_SAPLING_ITEM = ITEMS.register("magic_sapling",
            () -> new BlockItem(ModBlocks.MAGIC_SAPLING.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_PLANKS_ITEM = ITEMS.register("magic_planks",
            () -> new BlockItem(ModBlocks.MAGIC_PLANKS.get(), new Item.Properties()));

    /**
     * 魔法木棍。
     *
     * <p>purpose: 和原版木棍一样是"工具手柄"，同时是这棵树的木头跟原版木头平起平坐的标志。
     * design note: 单独注册而不是让玩家用原版木棍——魔法木板做出来的手柄应该是魔法木棍，
     *              这样五件工具的配方里不会出现"紫木板 + 橡木棍"这种混搭。
     * edge cases: 原版工具的配方写死了 {@code minecraft:stick}，所以魔法木棍**不能**用来做
     *              原版工具；要用它得配我们自己那五件（见下）。
     */
    public static final RegistryObject<Item> MAGIC_STICK = ITEMS.register("magic_stick",
            () -> new Item(new Item.Properties()));

    // ── 魔法木制工具 ───────────────────────────────────────────────────────
    // 数值照抄原版木制工具的构造参数（剑 3/-2.4、锹 1.5/-3.0、镐 1/-2.8、斧 6/-3.2、锄 0/-3.0），
    // 等级直接用原版的 Tiers.WOOD——"与原版木头完全同级"最准确的写法就是真的用它。
    //
    // 为什么没有自定义 Tier：1.20.1 的 Tier.getLevel 被 Forge 标成了 @Deprecated
    // （判定挖掘等级改成走 TierSortingRegistry 标签），实现一个自定义 Tier 必然要覆盖它，
    // 于是每次编译都会多一条废弃提示。而自定义 Tier 唯一能多做的一件事是"只认魔法木板修理"，
    // 可 Tiers.WOOD 的修理材料本来就是 #minecraft:planks 标签，魔法木板已经挂进那个标签了，
    // 所以自定义 Tier 一点收益都换不到，白白换来一条告警。
    public static final RegistryObject<Item> MAGIC_SWORD = ITEMS.register("magic_sword",
            () -> new SwordItem(Tiers.WOOD, 3, -2.4F, new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_PICKAXE = ITEMS.register("magic_pickaxe",
            () -> new PickaxeItem(Tiers.WOOD, 1, -2.8F, new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_AXE = ITEMS.register("magic_axe",
            () -> new AxeItem(Tiers.WOOD, 6.0F, -3.2F, new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_SHOVEL = ITEMS.register("magic_shovel",
            () -> new ShovelItem(Tiers.WOOD, 1.5F, -3.0F, new Item.Properties()));

    public static final RegistryObject<Item> MAGIC_HOE = ITEMS.register("magic_hoe",
            () -> new HoeItem(Tiers.WOOD, 0, -3.0F, new Item.Properties()));

    /**
     * 蝴蝶刷怪蛋。
     *
     * purpose: 给调试和创造模式提供一条不依赖世界生成的入口——野生蝴蝶生成率刻意做得很低，
     *          没有刷怪蛋的话创造模式玩家基本没机会看到这个 mod 的核心内容。
     * design note: 用 Forge 的 ForgeSpawnEggItem 而不是自定义物品，它自带刷怪逻辑、
     *          自带两段染色（主色/高光色），贴图只需画一张灰度蛋形模板。
     */
    public static final RegistryObject<Item> MANA_BUTTERFLY_SPAWN_EGG = ITEMS.register("mana_butterfly_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MANA_BUTTERFLY, 0x6A3EA8, 0xD8B0FF, new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
