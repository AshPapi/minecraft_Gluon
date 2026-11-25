package net.ashpapi.papioptimization.mixin.memory;

import net.ashpapi.papioptimization.memory.PapiCapabilityDeduplicator;
import net.neoforged.neoforge.capabilities.CapabilityHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CapabilityHooks.class, priority = 1500, remap = false)
public abstract class CapabilityHooksCompactMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private static void papi$compactProvidersAfterInit(CallbackInfo ci) {
        PapiCapabilityDeduplicator.compactAll();
    }
}
