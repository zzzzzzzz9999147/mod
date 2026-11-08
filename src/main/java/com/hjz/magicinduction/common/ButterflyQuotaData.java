package com.hjz.magicinduction.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 野生蝴蝶的"出生配额"。
 *
 * purpose: 让魔法树林变成一个会被"用光"的资源点，而不是无限刷怪机。
 * player experience goal: 一片林子最多出 5 只；死了或被抓走都不会补货，
 *                          想要更多就得去找下一片林子（或自己种树到新地块）。
 * inputs:  ManaButterfly.checkSpawnRules 每次生成尝试。
 * outputs: 每个 32×32 格区域终身最多放行 5 只，跨存档保存。
 * failure states: 配额耗尽后该区域永久不再自然生成——这是刻意设计，不是 bug。
 *
 * 为什么是"终身配额"而不是"实时数量上限"：
 * 实时上限只能保证"同时存在 5 只"，死一只就立刻补一只，本质上还是无限刷。
 * 用户要的是"死了也不会再重新生成"，那就必须记账而不是数人头。
 *
 * 为什么按 32 格网格而不是"最近那棵树"：
 * 找"最近一棵魔法树"要给每只蝴蝶做一次方块搜索，代价高且边界会漂移
 * （两棵树挨得近时归属会来回跳）。固定网格无状态、可预测，玩家也不需要一个
 * 精确的边界概念——一片林子大致就是一个网格。
 */
public class ButterflyQuotaData extends SavedData {
    /** 每个网格区域终生最多自然生成几只。 */
    public static final int CAP = 5;
    /** 网格边长 = 2^5 = 32 格，比一棵魔法树的树冠大一圈。 */
    private static final int CELL_SHIFT = 5;
    private static final String DATA_NAME = "magicinduction_butterfly_quota";

    private final Map<Long, Integer> used = new HashMap<>();

    public static ButterflyQuotaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ButterflyQuotaData::load, ButterflyQuotaData::new, DATA_NAME);
    }

    /** 把坐标折进 32 格网格，X/Z 各取高 32 位拼成一个 long。 */
    private static long cellKey(int x, int z) {
        return ((long) (x >> CELL_SHIFT) << 32) ^ ((z >> CELL_SHIFT) & 0xFFFFFFFFL);
    }

    /**
     * 申请一个出生名额。返回 true 表示放行，调用方应当真的把蝴蝶生成出来。
     *
     * 记在"生成前"而不是"生成后"：Mob 生成是一锤子买卖，checkSpawnRules 通过后
     * 几乎必然落地。反过来先放行再补记，会有一个 tick 的窗口让同格的多次尝试全部通过。
     */
    public boolean tryConsume(int x, int z) {
        long key = cellKey(x, z);
        int count = this.used.getOrDefault(key, 0);
        if (count >= CAP) {
            return false;
        }
        this.used.put(key, count + 1);
        this.setDirty();
        return true;
    }

    /** 该区域还剩几个名额，给调试/提示用。 */
    public int remaining(int x, int z) {
        return CAP - this.used.getOrDefault(cellKey(x, z), 0);
    }

    public static ButterflyQuotaData load(CompoundTag tag) {
        ButterflyQuotaData data = new ButterflyQuotaData();
        ListTag cells = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cell = cells.getCompound(i);
            data.used.put(cell.getLong("Key"), cell.getInt("Used"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag cells = new ListTag();
        this.used.forEach((key, count) -> {
            CompoundTag cell = new CompoundTag();
            cell.putLong("Key", key);
            cell.putInt("Used", count);
            cells.add(cell);
        });
        tag.put("Cells", cells);
        return tag;
    }
}
