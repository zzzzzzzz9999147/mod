package com.hjz.magicinduction.common;

import com.hjz.magicinduction.MagicInduction;
import com.hjz.magicinduction.ModBlocks;
import com.hjz.magicinduction.ModItems;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔法原液。
 *
 * <p>purpose: 给 Create 的搅拌配方一个流体产物，而不是又一个物品。做成流体是因为
 * "原液"本身就应该是能进管道、能装桶、能在工作盆里混的东西——如果做成物品，
 * Create 的搅拌就退化成合成台，联动也就失去了意义。
 *
 * <p>设计取向：粘稠（viscosity 高于水）+ 微光（lightLevel 6）+ 流动距离短（3 格）。
 * 这三条都是为了让它在视觉上"不像水"：水会铺满整片地，原液应该是一小滩。
 *
 * <p>为什么不注册 FluidType 的 {@code canConvertToSource}：原液不该像水一样两格成一格，
 * 否则玩家可以拿桶刷，绕过"每块矿只能出 250 mB"的产能设计。
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MagicInduction.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, MagicInduction.MODID);

    private static final ResourceLocation STILL_TEXTURE =
            new ResourceLocation(MagicInduction.MODID, "block/mana_essence_still");
    private static final ResourceLocation FLOWING_TEXTURE =
            new ResourceLocation(MagicInduction.MODID, "block/mana_essence_flow");

    public static final RegistryObject<FluidType> MANA_ESSENCE_TYPE = FLUID_TYPES.register("mana_essence",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.magicinduction.mana_essence")
                    .density(1200)
                    .viscosity(2000)
                    .lightLevel(6)
                    .canConvertToSource(false)
                    .canDrown(false)
                    .canSwim(false)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)) {
                /**
                 * 客户端外观。
                 *
                 * <p>1.20.1 的 Forge 还没有 {@code RegisterClientExtensionsEvent}（那是 1.20.2 才有的），
                 * 唯一正确的入口就是覆写这个方法。Forge 只在客户端调用它，服务端永远不会执行方法体，
                 * 所以这里出现的 {@link IClientFluidTypeExtensions} 不会在专用服务端上被解析。
                 *
                 * <p>不注册的后果：原液渲染成紫黑格子，日志里只有一行很不起眼的 missing texture。
                 */
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOWING_TEXTURE;
                        }

                        /** 偏亮的紫，让原液在远处也能一眼从水里分出来。 */
                        @Override
                        public int getTintColor() {
                            return 0xFF9B6BE0;
                        }
                    });
                }
            });

    public static final RegistryObject<ForgeFlowingFluid.Source> MANA_ESSENCE = FLUIDS.register("mana_essence",
            () -> new ForgeFlowingFluid.Source(properties()));

    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING_MANA_ESSENCE =
            FLUIDS.register("flowing_mana_essence",
                    () -> new ForgeFlowingFluid.Flowing(properties()));

    /**
     * 物性参数。
     *
     * <p>block / bucket 都用 RegistryObject 延迟求值：这个类在静态初始化时
     * ModBlocks、ModItems 可能还没注册完，直接 .get() 会拿到 null。
     * 传 Supplier 可以让 Forge 在真正需要它们的时候再取值。
     */
    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(MANA_ESSENCE_TYPE, MANA_ESSENCE, FLOWING_MANA_ESSENCE)
                .block(ModBlocks.MANA_ESSENCE_BLOCK)
                .bucket(ModItems.MANA_ESSENCE_BUCKET)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(3)
                .tickRate(20)
                .explosionResistance(100.0F);
    }

    private ModFluids() {
    }
}
