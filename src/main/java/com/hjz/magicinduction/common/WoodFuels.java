package com.hjz.magicinduction.common;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.ModItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔法木头的燃料值。
 *
 * <p>purpose: 让魔法原木 / 木板 / 木棍能像原版木头一样丢进熔炉当燃料。
 *
 * <p>design note: 1.20.1 的燃料值没有数据包标签可用（原版是写死在
 * {@code AbstractFurnaceBlockEntity} 里的静态表），Mod 想加燃料只能监听这个事件。
 * 数值直接对齐原版橡木：原木 300 tick、木板 300 tick、木棍 100 tick——
 * 也就是说一根魔法原木烧 15 秒、一个木板 15 秒、一根木棍 5 秒，和玩家已有的直觉一致。
 *
 * <p>edge cases: 这里只**加**燃料值，不改木头能不能被火烧。刻意没注册
 * {@code FlammableBlockRegistry}：魔法树是蝴蝶的栖息地，让它在雷雨天烧掉
 * 等于把玩家辛苦种的资源点一键清零，不是这个 mod 想要的体验。
 */
@Mod.EventBusSubscriber(modid = MagicInduction.MODID)
public final class WoodFuels {

    private static final int LOG_TICKS = 300;
    private static final int PLANK_TICKS = 300;
    private static final int STICK_TICKS = 100;

    private WoodFuels() {
    }

    @SubscribeEvent
    public static void burnTime(FurnaceFuelBurnTimeEvent event) {
        Item item = event.getItemStack().getItem();
        if (item == ModItems.MAGIC_LOG_ITEM.get() || item == ModItems.MAGIC_PLANKS_ITEM.get()) {
            event.setBurnTime(PLANK_TICKS);
        } else if (item == ModItems.MAGIC_STICK.get()) {
            event.setBurnTime(STICK_TICKS);
        }
    }
}
