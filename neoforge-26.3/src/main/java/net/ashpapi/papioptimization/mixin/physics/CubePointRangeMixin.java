package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.physics.PapiDoubleArrayBacked;
import net.minecraft.world.phys.shapes.CubePointRange;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CubePointRange.class, priority = 1500)
public abstract class CubePointRangeMixin implements PapiDoubleArrayBacked {

    @Unique
    private double @Nullable [] papi$points;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$precomputePoints(int parts, CallbackInfo ci) {
        if (parts <= 64) {
            double[] pts = new double[parts + 1];
            double div = (double) parts;
            for (int i = 0; i <= parts; i++) {
                pts[i] = (double) i / div;
            }
            this.papi$points = pts;
        }
    }

    @WrapMethod(method = "getDouble")
    private double papi$fastGetDouble(int index, Operation<Double> original) {
        double[] pts = this.papi$points;
        if (pts != null && index >= 0 && index < pts.length) {
            return pts[index];
        }
        return original.call(index);
    }

    @Override
    public double @Nullable [] papi$getRawPoints() {
        return this.papi$points;
    }
}
