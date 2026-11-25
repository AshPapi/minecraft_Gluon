package net.ashpapi.papioptimization.core;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public final class PapiHopperClassCache {

    private static final ClassValue<Boolean> VANILLA_CONTAINER_BE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return "net.minecraft.world.level.block.entity".equals(type.getPackageName())
                    && !AbstractFurnaceBlockEntity.class.isAssignableFrom(type)
                    && !BrewingStandBlockEntity.class.isAssignableFrom(type);
        }
    };

    private PapiHopperClassCache() {
    }

    public static boolean isVanillaEpochContainer(Class<?> cls) {
        return VANILLA_CONTAINER_BE.get(cls);
    }
}
