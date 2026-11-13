package com.hjz.magicinduction.compat;

import com.hjz.magicinduction.MagicInduction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 在注册表里找到「烈焰人燃烧室」的方块实体类型。
 *
 * <h2>为什么不能写死一个 ID</h2>
 * Create 6.0.x 把燃烧室的<b>注册名</b>改成了 {@code create:blaze_heater}，
 * 但类名还是 {@code BlazeBurnerBlockEntity}、模型也还是 {@code blaze_burner}。
 * 也就是说"名"和"东西"对不上，只有查注册表才能确认当前版本用哪个 ID。
 *
 * <p>写死 {@code blaze_burner} 的后果实测过：{@code registerBlockEntityRenderer}
 * 里查不到类型 → 渲染器没换掉 → 日志刷一条 warn → 玩家喂了蛋糕却一点变化都没有。
 * 而且不崩、不报错，非常难发现。
 *
 * <h2>做法</h2>
 * 按候选列表逐个试，第一个命中就用。候选包含旧名与新名，
 * 同时限定命名空间必须是 {@code create}，避免撞到别的 mod 的同名方块。
 */
public final class BlazeBurnerLookup {

    /**
     * 候选注册名，按"先新后旧"排。
     *
     * <p>{@code blaze_heater} 是 Create 6.0.x 实际使用的名字（反编译
     * {@code AllBlockEntityTypes} 确认）；{@code blaze_burner} 留给更早的 6.x 小版本。
     */
    private static final List<String> CANDIDATES = List.of("blaze_heater", "blaze_burner");

    private BlazeBurnerLookup() {
    }

    /**
     * 找到燃烧室的方块实体类型。
     *
     * @return 找不到时返回 null（调用方负责打日志与降级）
     */
    public static BlockEntityType<?> find() {
        for (String path : CANDIDATES) {
            for (BlockEntityType<?> type : ForgeRegistries.BLOCK_ENTITY_TYPES.getValues()) {
                var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type);
                if (key != null && "create".equals(key.getNamespace()) && path.equals(key.getPath())) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * 找方块实体类型，找不到就记一条可读的日志。
     *
     * <p>日志里把候选名和"当前 Create 版本可能又改名了"一起写出来，
     * 下次版本升级后出问题能一眼看出是改名导致的。
     */
    public static BlockEntityType<?> findOrWarn(String purpose) {
        BlockEntityType<?> type = find();
        if (type == null) {
            MagicInduction.LOGGER.warn("[Magic Induction] could not find the blaze burner block entity "
                    + "(tried {}); {}. The Create version in use may have renamed it again.",
                    String.join(" / ", CANDIDATES), purpose);
        }
        return type;
    }
}
