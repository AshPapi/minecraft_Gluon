package net.ashpapi.papioptimization.mixin.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.client.PapiFontAtlasSupport;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = FontManager.class, priority = 1500)
public abstract class FontManagerCacheMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private void papi$detectCustomTextShaderOnReload(
            PreparableReloadListener.SharedState sharedState,
            Executor backgroundExecutor,
            PreparableReloadListener.PreparationBarrier preparationBarrier,
            Executor gameExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        PapiFontAtlasSupport.updateFromResourceManager(sharedState != null ? sharedState.resourceManager() : null);
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void papi$invalidateClientCachesOnFontReload(@Coerce Object preparation, ProfilerFiller profiler, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
    }

    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void papi$invalidateClientCachesOnFontOptions(Options options, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
    }
}
