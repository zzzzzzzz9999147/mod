package com.hjz.magicinduction.client;

import com.hjz.magicinduction.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「按住 W 思考」的页面数据表。
 *
 * purpose: 让玩家不必离开游戏去查文档，就能知道一件物品在整个循环里的位置。
 * design note: 正文全部走 lang 键而不硬编码中文，因为界面文字属于可翻译内容；
 *   代码里只保留"哪个物品有几页"这一层结构信息。
 * failure state: 若某个物品的 lang 键缺失，界面会显示原始 key 而不是崩溃——
 *   这是 MC 的默认降级行为，可以接受，但比崩溃更难发现，所以 verify_project.py
 *   需要能检查出 lang 键缺失。
 */
public final class PonderEntries {

    /** 一页：标题 + 正文。正文会在界面里按面板宽度自动换行。 */
    public record Page(Component title, Component body) {
    }

    private PonderEntries() {
    }

    public static boolean hasPonder(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == ModItems.MANA_BUTTERFLY_SPAWN_EGG.get()
                || item == ModItems.BUTTERFLY_JAR.get()
                || item == ModItems.MANA_INDUCER_ITEM.get()
                || item == ModItems.MAGIC_ORE_ITEM.get()
                || item == ModItems.MANA_DUST.get()
                || item == ModItems.MAGIC_CRYSTAL.get()
                || item == ModItems.MAGIC_PLANKS_ITEM.get();
    }

    public static List<Page> pagesFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Collections.emptyList();
        }
        Item item = stack.getItem();
        if (item == ModItems.MANA_BUTTERFLY_SPAWN_EGG.get()) {
            return build("mana_butterfly_spawn_egg", 1);
        }
        if (item == ModItems.BUTTERFLY_JAR.get()) {
            return build("butterfly_jar", 3);
        }
        if (item == ModItems.MANA_INDUCER_ITEM.get()) {
            return build("mana_inducer", 3);
        }
        if (item == ModItems.MAGIC_ORE_ITEM.get()) {
            return build("magic_ore", 2);
        }
        if (item == ModItems.MANA_DUST.get()) {
            return build("mana_dust", 2);
        }
        if (item == ModItems.MAGIC_CRYSTAL.get()) {
            return build("magic_crystal", 2);
        }
        if (item == ModItems.MAGIC_PLANKS_ITEM.get()) {
            return build("magic_planks", 1);
        }
        return Collections.emptyList();
    }

    private static List<Page> build(String id, int count) {
        List<Page> pages = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            pages.add(new Page(
                    Component.translatable("ponder.magicinduction." + id + ".p" + index + ".t"),
                    Component.translatable("ponder.magicinduction." + id + ".p" + index + ".b")));
        }
        return pages;
    }
}
