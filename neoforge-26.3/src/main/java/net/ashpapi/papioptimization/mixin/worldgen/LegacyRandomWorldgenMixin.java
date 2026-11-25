package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.math.PapiFastLegacyRandomSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LegacyRandomSource.LegacyPositionalRandomFactory.class, priority = 1500)
public abstract class LegacyRandomWorldgenMixin {

    @Shadow @Final private long seed;

    @WrapMethod(method = "at")
    private RandomSource papi$fastAt(int x, int y, int z, Operation<RandomSource> original) {
        return new PapiFastLegacyRandomSource(Mth.getSeed(x, y, z) ^ this.seed);
    }

    @WrapMethod(method = "fromHashOf")
    private RandomSource papi$fastFromHashOf(String name, Operation<RandomSource> original) {
        return new PapiFastLegacyRandomSource((long) name.hashCode() ^ this.seed);
    }

    @WrapMethod(method = "fromSeed")
    private RandomSource papi$fastFromSeed(long seedValue, Operation<RandomSource> original) {
        return new PapiFastLegacyRandomSource(seedValue);
    }
}
