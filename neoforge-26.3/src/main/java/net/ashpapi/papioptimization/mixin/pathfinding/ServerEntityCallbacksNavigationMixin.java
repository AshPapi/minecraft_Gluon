package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigatingMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks", priority = 1500)
public abstract class ServerEntityCallbacksNavigationMixin {

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void papi$onMobTrackingStart(Entity entity, CallbackInfo ci) {
        if (entity instanceof Mob) {
            ((PapiTrackedNavigatingMob) entity).papi$setTrackedInServerLevel(true);
        }
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void papi$onMobTrackingEnd(Entity entity, CallbackInfo ci) {
        if (entity instanceof Mob) {
            ((PapiTrackedNavigatingMob) entity).papi$setTrackedInServerLevel(false);
        }
    }
}
