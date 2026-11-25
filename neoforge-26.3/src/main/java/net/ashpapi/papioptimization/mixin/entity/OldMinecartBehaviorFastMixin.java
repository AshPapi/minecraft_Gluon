package net.ashpapi.papioptimization.mixin.entity;

import com.google.common.base.Predicates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = OldMinecartBehavior.class, priority = 1500)
public abstract class OldMinecartBehaviorFastMixin {

    @WrapOperation(
        method = "pushAndPickupEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List<Entity> papi$skipAlwaysFalsePushable(
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(
        method = "pushAndPickupEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<Entity> papi$queryMinecartBucketOnly(
            Level level,
            @Nullable Entity except,
            AABB box,
            Operation<List<Entity>> original
    ) {
        List<AbstractMinecart> carts = level.getEntities(
                SpatialEntityQueryHelper.MINECART_TEST,
                box,
                cart -> cart != except
        );
        return (List) carts;
    }
}
