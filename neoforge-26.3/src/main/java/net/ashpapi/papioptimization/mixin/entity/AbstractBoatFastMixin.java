package net.ashpapi.papioptimization.mixin.entity;

import com.google.common.base.Predicates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractBoat.class, priority = 1500)
public abstract class AbstractBoatFastMixin {

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List<Entity> papi$skipAlwaysFalseBoatPush(
            Level level,
            @Nullable Entity except,
            AABB box,
            Predicate<? super Entity> predicate,
            Operation<List<Entity>> original
    ) {
        if (predicate == (Predicate<?>) Predicates.alwaysFalse()) {
            return List.of();
        }
        return original.call(level, except, box, predicate);
    }
}
