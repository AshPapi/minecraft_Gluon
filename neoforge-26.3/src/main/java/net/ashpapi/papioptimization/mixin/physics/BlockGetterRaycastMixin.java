package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.physics.LevelCollisionHelper;
import net.ashpapi.papioptimization.physics.RaycastHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockGetter.class, priority = 1500)
public interface BlockGetterRaycastMixin {

    @WrapMethod(method = "clip")
    private BlockHitResult papi$fastClip(ClipContext context, Operation<BlockHitResult> original) {
        if (this instanceof Level level && LevelCollisionHelper.IS_STANDARD_LEVEL.get(level.getClass())) {
            return RaycastHelper.clipFast(level, context);
        }
        return original.call(context);
    }
}
