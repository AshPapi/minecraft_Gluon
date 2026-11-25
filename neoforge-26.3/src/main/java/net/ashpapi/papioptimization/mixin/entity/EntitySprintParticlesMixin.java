package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.ai.PapiEquipmentTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 1500)
public abstract class EntitySprintParticlesMixin {

    @Shadow public abstract Level level();

    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;canSpawnSprintParticle()Z"
        )
    )
    private boolean papi$skipServerSprintParticleCheck(Entity instance, Operation<Boolean> original) {
        if (!instance.level().isClientSide() && PapiEquipmentTracker.hasVanillaSprintParticle(instance)) {
            return false;
        }
        return original.call(instance);
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void papi$skipServerSpawnSprintParticle(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!this.level().isClientSide() && PapiEquipmentTracker.hasVanillaSprintParticle(self)) {
            ci.cancel();
        }
    }
}
