package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts", priority = 1500)
public abstract class LocalMobCapFastMixin {

    @Unique
    private static final int PAPI$CATEGORY_COUNT = MobCategory.values().length;

    @Unique
    private final int[] papi$counts = new int[PAPI$CATEGORY_COUNT];

    @WrapMethod(method = "add")
    private void papi$fastAdd(MobCategory category, Operation<Void> original) {
        this.papi$counts[category.ordinal()]++;
    }

    @WrapMethod(method = "canSpawn")
    private boolean papi$fastCanSpawn(MobCategory category, Operation<Boolean> original) {
        return this.papi$counts[category.ordinal()] < category.getMaxInstancesPerChunk();
    }
}
