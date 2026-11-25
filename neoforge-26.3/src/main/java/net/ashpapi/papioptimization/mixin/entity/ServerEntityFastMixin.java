package net.ashpapi.papioptimization.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEntity.class, priority = 1500)
public abstract class ServerEntityFastMixin {

    @Unique
    private static final double PAPI$LP_VEC3_ZERO_THRESHOLD = 3.051944088384301E-5D;

    @Shadow
    private List<Entity> lastPassengers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void papi$canonicalizeEmptyPassengers(CallbackInfo ci) {
        if (this.lastPassengers != null && this.lastPassengers.isEmpty()) {
            this.lastPassengers = ImmutableList.of();
        }
    }

    @WrapOperation(
            method = "sendChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;equals(Ljava/lang/Object;)Z"
            )
    )
    private boolean papi$fastPassengersEquals(List<?> passengers, Object other, Operation<Boolean> original) {
        if (passengers == other) {
            return true;
        }
        if (other instanceof List<?> otherList) {
            int size = passengers.size();
            if (size != otherList.size()) {
                return false;
            }
            if (size == 0) {
                return true;
            }
        }
        return original.call(passengers, other);
    }

    @WrapOperation(
            method = {"sendChanges", "handleMinecartPosRot"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            )
    )
    private double papi$suppressSubQuantumMotion(Vec3 current, Vec3 last, Operation<Double> original) {
        if (current == last) {
            return 0.0D;
        }
        if (last != null
                && Math.abs(current.x) < PAPI$LP_VEC3_ZERO_THRESHOLD
                && Math.abs(current.y) < PAPI$LP_VEC3_ZERO_THRESHOLD
                && Math.abs(current.z) < PAPI$LP_VEC3_ZERO_THRESHOLD
                && Math.abs(last.x) < PAPI$LP_VEC3_ZERO_THRESHOLD
                && Math.abs(last.y) < PAPI$LP_VEC3_ZERO_THRESHOLD
                && Math.abs(last.z) < PAPI$LP_VEC3_ZERO_THRESHOLD) {
            return 0.0D;
        }
        return original.call(current, last);
    }
}
