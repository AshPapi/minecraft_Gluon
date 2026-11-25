package net.ashpapi.papioptimization.mixin.biome;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Biome.class, priority = 1500)
public abstract class BiomeFastMixin {

    @Shadow
    public abstract float getHeightAdjustedTemperature(BlockPos pos, int seaLevel);

    @WrapMethod(method = "getTemperature")
    private float papi$directTemperature(BlockPos pos, int seaLevel, Operation<Float> original) {
        return this.getHeightAdjustedTemperature(pos, seaLevel);
    }
}
