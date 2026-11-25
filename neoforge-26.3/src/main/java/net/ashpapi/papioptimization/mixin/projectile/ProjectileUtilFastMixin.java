package net.ashpapi.papioptimization.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.ProjectileRayHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ProjectileUtil.class, priority = 1500)
public abstract class ProjectileUtilFastMixin {

    @WrapMethod(
        method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"
    )
    private static @Nullable EntityHitResult papi$getEntityHitResult(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            float entityMargin,
            Operation<@Nullable EntityHitResult> original
    ) {
        return ProjectileRayHelper.findNearestEntityHit(level, source, from, to, targetSearchArea, matching, entityMargin);
    }
}
