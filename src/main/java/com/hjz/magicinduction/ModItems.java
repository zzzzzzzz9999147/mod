package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ButterflyJarItem;
import com.hjz.magicinduction.common.ModFluids;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Items;
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

    /**
     * 魔法原液桶。
     *
     * <p>purpose: 让原液能被取出、搬运、倒进 Create 的储罐或工作盆。
     *
     * <p>design note: 直接用原版 {@link BucketItem}，不自己实现 IFluidHandlerItem——
     * 1.20.1 的 BucketItem 已经通过 Forge 的 Capability 提供了流体容器能力，
     * Create 的桶交互（工作盆、储罐、灌注）走的就是这条通用路径。
     * 自己造一个只会多出一堆边界情况（比如堆叠时的 NBT 同步）。
     *
     * <p>edge cases: craftRemainder(BUCKET) 保证倒完还你一个空桶；
     * stacksTo(1) 是必须的，桶是带流体状态的物品，堆叠会让 NBT 互相污染。
     */
    public static final RegistryObject<Item> MANA_ESSENCE_BUCKET = ITEMS.register("mana_essence_bucket",
            () -> new BucketItem(ModFluids.MANA_ESSENCE,
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    /**
     * 魔法蛋糕 —— 烈焰蛋糕胚浇 250 mB 魔法原液的产物。
     *
     * <p>purpose: 把"原液"这桶死物变成一个能喂生物的东西，顺便给原液开第二条出口。
     * 在它之前，原液只有"倒在地上"和"装桶"两种命运，玩家辛苦搭出搅拌产线，
     * 得到的却是一个没有用途的罐子——这是整个 mod 里最长的一段空转。
     *
     * <p>design note: 普通的 {@link Item}，不是 {@code food()}。理由有两条：
     * <ul>
     *   <li>玩家不该啃它。蛋糕是给蝴蝶的，人能吃掉的话，"专门喂给谁"这件事就模糊了；</li>
     *   <li>食物属性会把它塞进 {@code #minecraft:is_food} 相关逻辑（狼/狐狸抢食、
     *       饥饿值界面提示），那些行为都不该出现。</li>
     * </ul>
     * 喂食入口写在 {@link com.hjz.magicinduction.common.ManaButterfly} 的 mobInteract 里。
     *
     * <p>获取途径：只有 Create 的注液器（spout）一条路。没装 Create 时这个物品仍然存在、
     * 仍然能在创造模式物品栏里拿到，只是做不出来——配方在
     * {@code recipes/magic_cake_from_filling.json} 里被 {@code forge:mod_loaded} 包着。
     */
    public static final RegistryObject<Item> MAGIC_CAKE = ITEMS.register("magic_cake",
            () -> new com.hjz.magicinduction.common.MagicCakeItem(new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
