package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiMaskedBlockSubmit;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockModelFeatureRenderer.Submit.class, priority = 1500)
public abstract class BlockModelSubmitMixin implements PapiMaskedBlockSubmit {

    @Unique
    private byte papi$faceMask = (byte) 0x7F;

    @Override
    public byte papi$getFaceMask() {
        return this.papi$faceMask;
    }

    @Override
    public void papi$setFaceMask(byte mask) {
        this.papi$faceMask = mask;
    }
}
