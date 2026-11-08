package com.hjz.magicinduction;

import com.hjz.magicinduction.common.ManaButterfly;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MagicInduction.MODID);

    /**
     * 碰撞箱 0.65×0.30×0.65，按模型的**静止外廓**定，不去罩扑翼动画。
     *
     * 静止时模型是 0.62（翅展）× 0.48（体长）× 0.11（厚）：0.65 刚好贴住翅尖，
     * 0.30 让 0.11 格厚的身体上下各留约 0.09，视觉重心落在框正中。
     *
     * <b>为什么不按扑翼包络定。</b>翅膀绕 X 轴摆到 ±0.85 rad 时，10 单位长的翅尖
     * 上下各扫出约 0.25 格，竖直包络拉到约 0.5 格——要罩住就得做成 0.52 高的框，
     * 蝴蝶在方块上会占掉半格，比"翅尖短暂出框"更怪。原版蜜蜂、蝙蝠同样是
     * 碰撞箱只描述身体占位、不罩翅膀动画，所以这里跟着原版的做法。
     *
     * 演化：0.8×0.7 太占地方（0.7 相当于一只鸡，蝴蝶停一格方块上像把方块盖住了）；
     * 后来缩到 0.6×0.45，横向刚好卡住翅膀、竖直却空了一半——框明显比蝴蝶大一圈，
     * F3+B 里能看到模型只贴在框的上沿。
     *
     * 点击容差和这个尺寸解耦：getPickRadius(0.70) 只膨胀射线检测，
     * 不影响实体真实体积，也不会挡住别的方块/实体交互，所以框做小不影响抓蝴蝶。
     */
    public static final RegistryObject<EntityType<ManaButterfly>> MANA_BUTTERFLY = ENTITIES.register("mana_butterfly",
            () -> EntityType.Builder.of(ManaButterfly::new, MobCategory.AMBIENT)
                    .sized(0.65F, 0.30F)
                    .clientTrackingRange(8)
                    .build("mana_butterfly"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
