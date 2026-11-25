package net.ashpapi.papioptimization.mixin.client;

import java.util.concurrent.atomic.AtomicReference;
import net.ashpapi.papioptimization.client.MapDecorationCache;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.client.PapiQuadCanonicalizer;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.client.culling.PapiSpriteOpacityTracker;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ModelManager.class, priority = 1500)
public abstract class ModelManagerMemoryMixin {

    @Shadow @Final private AtomicReference<ModelBakery> modelBakery;

    @Inject(method = "apply", at = @At("RETURN"))
    private void papi$releaseTemporaryModelBakery(CallbackInfo ci) {
        this.modelBakery.set(null);
        PapiEbeRuntime.onResourceReload();
        PapiQuadCanonicalizer.clear();
        MapDecorationCache.clear();
        PapiSpriteOpacityTracker.refreshStandaloneTextures();
        PapiBlockCullingCache.rebuild((ModelManager) (Object) this);
        PapiClientCaches.invalidateAll();
    }
}
