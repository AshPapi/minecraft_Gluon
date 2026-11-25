package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = IntegratedServer.class, priority = 1500)
public abstract class IntegratedServerPriorityMixin {

    @Inject(method = "initServer", at = @At("HEAD"))
    private void papi$lowerIntegratedServerThreadPriority(CallbackInfoReturnable<Boolean> cir) {
        if (!PapiConfig.modernFix()) {
            return;
        }
        Thread thread = Thread.currentThread();
        int current = thread.getPriority();
        int target = Math.min(current, Thread.NORM_PRIORITY - 1);
        if (target < current && target >= Thread.MIN_PRIORITY) {
            thread.setPriority(target);
        }
    }
}
