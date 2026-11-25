package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AgeableWaterCreature.class, priority = 1500)
public abstract class AgeableWaterCreatureFastMixin {

    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/AgeableWaterCreature;handleAirSupply(I)V"
        )
    )
    private void papi$skipClientHandleAirSupply(
            AgeableWaterCreature instance,
            int airSupply,
            Operation<Void> original
    ) {
        if (!instance.level().isClientSide()) {
            original.call(instance, airSupply);
        }
    }
}
