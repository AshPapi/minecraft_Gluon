package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.ashpapi.papioptimization.physics.LevelCollisionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityCollideMixin {

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract Level level();

    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroOrGravityCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(Vec3.ZERO);
            return;
        }
        if (movement.x == 0.0 && movement.z == 0.0 && movement.y < 0.0 && movement.y > -2.0) {
            Level level = this.level();
            if (LevelCollisionHelper.IS_STANDARD_LEVEL.get(level.getClass())) {
                AABB box = this.getBoundingBox();
                if (LevelCollisionHelper.probeStandingSupportStop((Entity) (Object) this, level, box, movement.y)) {
                    cir.setReturnValue(Vec3.ZERO);
                }
            }
        }
    }

    @Inject(method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastZeroMovementCollideBox(
            Entity entity, Vec3 movement, AABB aabb, Level level, List<VoxelShape> entityCollisions,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(movement);
        }
    }

    @Inject(method = "collideBoundingBox(Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastZeroMovementCollideBoxContext(
            CollisionContext context, Vec3 movement, AABB aabb, Level level, List<VoxelShape> entityCollisions,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(movement);
        }
    }

    @WrapOperation(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;expandTowards(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB papi$narrowSingleAxisEntityCollide(AABB box, Vec3 movement, Operation<AABB> original) {
        return LevelCollisionHelper.narrowSingleAxisExpand(box, movement);
    }

    @WrapOperation(
            method = "collideBoundingBox(Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;expandTowards(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB papi$narrowSingleAxisContextCollide(AABB box, Vec3 movement, Operation<AABB> original) {
        return LevelCollisionHelper.narrowSingleAxisExpand(box, movement);
    }
}
