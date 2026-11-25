package net.ashpapi.papioptimization.mixin.math;

import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Vec3.class, priority = 1500)
public abstract class Vec3Mixin {
    @Shadow
    @Final
    public double x;

    @Shadow
    @Final
    public double y;

    @Shadow
    @Final
    public double z;

    @Inject(method = "normalize", at = @At("HEAD"), cancellable = true)
    private void papi$fastNormalize(CallbackInfoReturnable<Vec3> cir) {
        double sqr = this.x * this.x + this.y * this.y + this.z * this.z;
        if (sqr < 9.999999494757505E-11) {
            cir.setReturnValue(Vec3.ZERO);
        } else {
            double len = Math.sqrt(sqr);
            cir.setReturnValue(new Vec3(this.x / len, this.y / len, this.z / len));
        }
    }

    @Inject(method = "closerThan(Lnet/minecraft/core/Position;D)Z", at = @At("HEAD"), cancellable = true)
    private void papi$fastCloserThan(Position pos, double distance, CallbackInfoReturnable<Boolean> cir) {
        double dx = pos.x() - this.x;
        double dy = pos.y() - this.y;
        double dz = pos.z() - this.z;
        cir.setReturnValue((dx * dx + dy * dy + dz * dz) < (distance * distance));
    }

    @Inject(method = "add(DDD)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroAdd(double dx, double dy, double dz, CallbackInfoReturnable<Vec3> cir) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            cir.setReturnValue((Vec3) (Object) this);
        }
    }

    @Inject(method = "subtract(DDD)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroSubtract(double dx, double dy, double dz, CallbackInfoReturnable<Vec3> cir) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            cir.setReturnValue((Vec3) (Object) this);
        }
    }

    @Inject(method = "scale(D)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroScale(double factor, CallbackInfoReturnable<Vec3> cir) {
        if (factor == 1.0) {
            cir.setReturnValue((Vec3) (Object) this);
        } else if (factor == 0.0 && Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "multiply(DDD)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroMultiply(double mx, double my, double mz, CallbackInfoReturnable<Vec3> cir) {
        if (mx == 1.0 && my == 1.0 && mz == 1.0) {
            cir.setReturnValue((Vec3) (Object) this);
        } else if (mx == 0.0 && my == 0.0 && mz == 0.0
                && Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }
}
