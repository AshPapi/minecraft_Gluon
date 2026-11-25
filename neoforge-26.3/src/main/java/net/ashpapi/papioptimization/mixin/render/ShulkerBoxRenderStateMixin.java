package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiShulkerRenderState;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ShulkerBoxRenderState.class, priority = 1500)
public abstract class ShulkerBoxRenderStateMixin implements PapiShulkerRenderState {

    @Unique
    private boolean papi$bottomOccluded = false;

    @Override
    public boolean papi$isBottomOccluded() {
        return this.papi$bottomOccluded;
    }

    @Override
    public void papi$setBottomOccluded(boolean occluded) {
        this.papi$bottomOccluded = occluded;
    }
}
