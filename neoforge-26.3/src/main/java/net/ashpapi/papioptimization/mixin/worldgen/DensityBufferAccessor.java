package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DensityBuffer.class)
public interface DensityBufferAccessor {

    @Accessor("values")
    float[] papi$getValues();

    @Accessor("size")
    void papi$setSize(int size);
}
