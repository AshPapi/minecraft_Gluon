package net.ashpapi.papioptimization.mixin.map;

import net.ashpapi.papioptimization.client.map.PapiMapAtlasManager;
import net.minecraft.client.resources.MapTextureManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MapTextureManager.class, priority = 1500)
public abstract class MapTextureManagerMixin {

    @Inject(method = "resetData", at = @At("RETURN"))
    private void papi$resetMapAtlases(CallbackInfo ci) {
        PapiMapAtlasManager.reset();
    }
}
