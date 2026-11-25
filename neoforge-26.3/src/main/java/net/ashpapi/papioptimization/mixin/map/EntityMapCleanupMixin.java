package net.ashpapi.papioptimization.mixin.map;

import net.ashpapi.papioptimization.access.PapiFramedMapPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityMapCleanupMixin {

    @Inject(method = "remove", at = @At("RETURN"))
    private void papi$onEntityRemoveCleanupMaps(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof PapiFramedMapPlayer fmp) {
            fmp.papi$cleanupAllTrackedMaps();
        }
    }
}
