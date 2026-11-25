package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.concurrent.CompletableFuture;
import net.ashpapi.papioptimization.core.PapiEncoderCacheCleaner;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ReloadableServerResources.class, priority = 1500)
public abstract class ReloadableServerResourcesMixin {

    @ModifyReturnValue(method = "loadResources", at = @At("RETURN"))
    private static CompletableFuture<ReloadableServerResources> papi$clearEncoderCacheAfterReload(
            CompletableFuture<ReloadableServerResources> future
    ) {
        return future.whenComplete((resources, throwable) -> PapiEncoderCacheCleaner.clear());
    }
}
