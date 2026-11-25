package net.ashpapi.papioptimization.mixin.math;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.ashpapi.papioptimization.math.FastRayAABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AABB.class, priority = 1500)
public abstract class AABBMixin {

    @Shadow
    @Final
    public double minX;

    @Shadow
    @Final
    public double minY;

    @Shadow
    @Final
    public double minZ;

    @Shadow
    @Final
    public double maxX;

    @Shadow
    @Final
    public double maxY;

    @Shadow
    @Final
    public double maxZ;

    @WrapMethod(method = "min")
    private double papi$fastMin(Direction.Axis axis, Operation<Double> original) {
        return switch (axis.ordinal()) {
            case 0 -> this.minX;
            case 1 -> this.minY;
            default -> this.minZ;
        };
    }

    @WrapMethod(method = "max")
    private double papi$fastMax(Direction.Axis axis, Operation<Double> original) {
        return switch (axis.ordinal()) {
            case 0 -> this.maxX;
            case 1 -> this.maxY;
            default -> this.maxZ;
        };
    }

    @Inject(method = "intersects(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void papi$fastIntersectsBlockPos(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        cir.setReturnValue(this.minX < (double) (x + 1) && this.maxX > (double) x
                && this.minY < (double) (y + 1) && this.maxY > (double) y
                && this.minZ < (double) (z + 1) && this.maxZ > (double) z);
    }

    @Inject(method = "clip(Ljava/lang/Iterable;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastIterableClip(Iterable<AABB> aabBs, Vec3 from, Vec3 to, BlockPos pos, CallbackInfoReturnable<BlockHitResult> cir) {
        cir.setReturnValue(FastRayAABB.clip(aabBs, from, to, pos));
    }

    @Inject(method = "clip(DDDDDDLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastSingleClip(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3 from, Vec3 to, CallbackInfoReturnable<Optional<Vec3>> cir) {
        cir.setReturnValue(FastRayAABB.clip(minX, minY, minZ, maxX, maxY, maxZ, from, to));
    }

    @Inject(method = "move(DDD)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroMove(double x, double y, double z, CallbackInfoReturnable<AABB> cir) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "move(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroVecMove(Vec3 vec, CallbackInfoReturnable<AABB> cir) {
        if (vec == Vec3.ZERO || (vec.x == 0.0 && vec.y == 0.0 && vec.z == 0.0)) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "inflate(DDD)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroInflate(double x, double y, double z, CallbackInfoReturnable<AABB> cir) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "deflate(DDD)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroDeflate(double x, double y, double z, CallbackInfoReturnable<AABB> cir) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "expandTowards(DDD)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroExpand(double x, double y, double z, CallbackInfoReturnable<AABB> cir) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "expandTowards(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroVecExpand(Vec3 vec, CallbackInfoReturnable<AABB> cir) {
        if (vec == Vec3.ZERO || (vec.x == 0.0 && vec.y == 0.0 && vec.z == 0.0)) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "contract(DDD)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroContract(double x, double y, double z, CallbackInfoReturnable<AABB> cir) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "minmax(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastIdentityMinMax(AABB other, CallbackInfoReturnable<AABB> cir) {
        if (other == (Object) this) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }

    @Inject(method = "intersect(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void papi$fastIdentityIntersect(AABB other, CallbackInfoReturnable<AABB> cir) {
        if (other == (Object) this) {
            cir.setReturnValue((AABB) (Object) this);
        }
    }
}
