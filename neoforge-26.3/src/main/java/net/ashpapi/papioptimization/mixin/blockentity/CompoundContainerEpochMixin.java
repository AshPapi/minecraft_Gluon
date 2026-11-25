package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompoundContainer.class, priority = 1500)
public abstract class CompoundContainerEpochMixin implements PapiEpochContainer {

    @Shadow
    @Final
    private Container container1;

    @Shadow
    @Final
    private Container container2;

    @Unique
    private long papi$compoundEpoch = 1L;

    @Override
    public long papi$getChangeEpoch() {
        if (!(this.container1 instanceof PapiEpochContainer c1) || !(this.container2 instanceof PapiEpochContainer c2)) {
            return -1L;
        }
        long e1 = c1.papi$getChangeEpoch();
        long e2 = c2.papi$getChangeEpoch();
        if (e1 < 0L || e2 < 0L) {
            return -1L;
        }
        return (this.papi$compoundEpoch + e1 * 31L + e2 * 1315423911L) & Long.MAX_VALUE;
    }

    @Override
    public void papi$incrementEpoch() {
        this.papi$compoundEpoch++;
    }

    @Override
    public int papi$getCachedRedstoneSignal(long epoch, int maxStackSize) {
        if (this.container1 instanceof PapiEpochContainer c1) {
            return c1.papi$getPairedRedstoneSignal(this.container2, epoch, maxStackSize);
        }
        return -1;
    }

    @Override
    public void papi$setCachedRedstoneSignal(long epoch, int maxStackSize, int signal) {
        if (this.container1 instanceof PapiEpochContainer c1) {
            c1.papi$setPairedRedstoneSignal(this.container2, epoch, maxStackSize, signal);
        }
    }

    @Inject(method = "setChanged", at = @At("HEAD"))
    private void papi$onSetChanged(CallbackInfo ci) {
        this.papi$compoundEpoch++;
    }
}
