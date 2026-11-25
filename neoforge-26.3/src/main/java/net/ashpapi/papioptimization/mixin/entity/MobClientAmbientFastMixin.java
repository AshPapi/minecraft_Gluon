package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Mob.class, priority = 1500)
public abstract class MobClientAmbientFastMixin {

    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;isAlive()Z"
        )
    )
    private boolean papi$skipClientIdleAmbientSound(Mob instance, Operation<Boolean> original) {
        if (instance.level().isClientSide()
                && SpatialEntityQueryHelper.VANILLA_AMBIENT_SOUND_MOB.get(instance.getClass())) {
            return false;
        }
        return original.call(instance);
    }
}
