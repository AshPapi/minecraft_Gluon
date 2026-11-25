package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidState.class, priority = 1500)
public abstract class FluidStateCacheMixin {

    @Unique
    private byte papi$flags;

    @Unique
    private byte papi$cachedAmount = -1;

    @Unique
    private float papi$cachedOwnHeight = Float.NaN;

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void papi$fastIsEmpty(CallbackInfoReturnable<Boolean> cir) {
        byte f = this.papi$flags;
        if ((f & 1) != 0) {
            cir.setReturnValue((f & 2) != 0);
        }
    }

    @Inject(method = "isEmpty", at = @At("RETURN"))
    private void papi$storeIsEmpty(CallbackInfoReturnable<Boolean> cir) {
        if ((this.papi$flags & 1) == 0) {
            this.papi$flags |= (byte) (1 | (cir.getReturnValueZ() ? 2 : 0));
        }
    }

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void papi$fastIsRandomlyTicking(CallbackInfoReturnable<Boolean> cir) {
        byte f = this.papi$flags;
        if ((f & 4) != 0) {
            cir.setReturnValue((f & 8) != 0);
        }
    }

    @Inject(method = "isRandomlyTicking", at = @At("RETURN"))
    private void papi$storeIsRandomlyTicking(CallbackInfoReturnable<Boolean> cir) {
        if ((this.papi$flags & 4) == 0) {
            this.papi$flags |= (byte) (4 | (cir.getReturnValueZ() ? 8 : 0));
        }
    }

    @Inject(method = "getAmount", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetAmount(CallbackInfoReturnable<Integer> cir) {
        byte a = this.papi$cachedAmount;
        if (a >= 0) {
            cir.setReturnValue((int) a);
        }
    }

    @Inject(method = "getAmount", at = @At("RETURN"))
    private void papi$storeGetAmount(CallbackInfoReturnable<Integer> cir) {
        if (this.papi$cachedAmount < 0) {
            int val = cir.getReturnValueI();
            if (val >= 0 && val <= 127) {
                this.papi$cachedAmount = (byte) val;
            }
        }
    }

    @Inject(method = "getOwnHeight", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetOwnHeight(CallbackInfoReturnable<Float> cir) {
        float h = this.papi$cachedOwnHeight;
        if (!Float.isNaN(h)) {
            cir.setReturnValue(h);
        }
    }

    @Inject(method = "getOwnHeight", at = @At("RETURN"))
    private void papi$storeGetOwnHeight(CallbackInfoReturnable<Float> cir) {
        if (Float.isNaN(this.papi$cachedOwnHeight)) {
            this.papi$cachedOwnHeight = cir.getReturnValueF();
        }
    }
}
