package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.ashpapi.papioptimization.physics.LevelCollisionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CollisionGetter.class, priority = 1500)
public interface LevelCollisionFastMixin {

    @WrapMethod(method = "noBlockCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Z)Z")
    private boolean papi$fastNoBlockCollision(
            @Nullable Entity entity,
            AABB box,
            boolean checkLiquid,
            Operation<Boolean> original
    ) {
        if (!checkLiquid && this instanceof Level level && LevelCollisionHelper.canUseFastLevelPath(level, box)) {
            return LevelCollisionHelper.noBlockCollisionFast(level, entity, box);
        }
        return original.call(entity, box, checkLiquid);
    }

    @WrapMethod(method = "noEntityCollision")
    private boolean papi$fastNoEntityCollision(
            @Nullable Entity entity,
            AABB box,
            Operation<Boolean> original
    ) {
        if (this instanceof Level level && LevelCollisionHelper.IS_STANDARD_LEVEL.get(level.getClass())) {
            return LevelCollisionHelper.noEntityCollisionFast(level, entity, box);
        }
        return original.call(entity, box);
    }

    @WrapMethod(method = "noBorderCollision")
    private boolean papi$fastNoBorderCollision(
            @Nullable Entity entity,
            AABB box,
            Operation<Boolean> original
    ) {
        if (entity == null) {
            return true;
        }
        WorldBorder border = ((CollisionGetter) this).getWorldBorder();
        if (border != null && LevelCollisionHelper.isStrictlyInsideWorldBorder(border, box)) {
            return true;
        }
        return original.call(entity, box);
    }

    @WrapMethod(method = "findSupportingBlock")
    private Optional<BlockPos> papi$fastFindSupportingBlock(
            Entity entity,
            AABB box,
            Operation<Optional<BlockPos>> original
    ) {
        if (this instanceof Level level && LevelCollisionHelper.canUseFastLevelPath(level, box)) {
            return LevelCollisionHelper.findSupportingBlockFast(level, entity, box);
        }
        return original.call(entity, box);
    }
}
