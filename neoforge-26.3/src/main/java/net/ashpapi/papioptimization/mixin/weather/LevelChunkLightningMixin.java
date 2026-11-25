package net.ashpapi.papioptimization.mixin.weather;

import net.ashpapi.papioptimization.access.PapiLightningChunk;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class LevelChunkLightningMixin implements PapiLightningChunk {

    @Shadow
    @Final
    Level level;

    @Unique
    private int papi$lightningTicks = -1;

    @Override
    public boolean papi$shouldDoLightning(RandomSource random, int chance) {
        if (this.level.isClientSide()) {
            return false;
        }
        if (chance <= 1) {
            this.papi$lightningTicks = -1;
            return true;
        }
        if (this.papi$lightningTicks < 0) {
            this.papi$lightningTicks = random.nextInt(chance);
        }
        if (this.papi$lightningTicks-- <= 0) {
            int bound = chance < (Integer.MAX_VALUE >>> 1) ? (chance << 1) : Integer.MAX_VALUE;
            this.papi$lightningTicks = random.nextInt(bound);
            return true;
        }
        return false;
    }
}
