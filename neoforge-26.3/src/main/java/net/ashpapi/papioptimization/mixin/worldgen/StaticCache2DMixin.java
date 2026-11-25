package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StaticCache2D.class, priority = 1500)
public abstract class StaticCache2DMixin<T> {

    @Shadow @Final private int minX;
    @Shadow @Final private int minZ;
    @Shadow @Final private int sizeX;
    @Shadow @Final private int sizeZ;
    @Shadow @Final private Object[] cache;

    @SuppressWarnings("unchecked")
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void papi$fastGet(int x, int z, CallbackInfoReturnable<T> cir) {
        int dx = x - this.minX;
        int dz = z - this.minZ;
        if (Integer.compareUnsigned(dx, this.sizeX) < 0 && Integer.compareUnsigned(dz, this.sizeZ) < 0) {
            cir.setReturnValue((T) this.cache[dx * this.sizeZ + dz]);
        }
    }

    @Inject(method = "contains", at = @At("HEAD"), cancellable = true)
    private void papi$fastContains(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(
                Integer.compareUnsigned(x - this.minX, this.sizeX) < 0
                        && Integer.compareUnsigned(z - this.minZ, this.sizeZ) < 0);
    }
}
