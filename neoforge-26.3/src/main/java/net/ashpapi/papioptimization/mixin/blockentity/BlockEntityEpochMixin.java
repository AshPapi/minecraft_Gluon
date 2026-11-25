package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntity.class, priority = 1500)
public abstract class BlockEntityEpochMixin {

    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void papi$onSetChanged(CallbackInfo ci) {
        if ((Object) this instanceof PapiEpochContainer epochContainer) {
            epochContainer.papi$incrementEpoch();
        }
    }
}
