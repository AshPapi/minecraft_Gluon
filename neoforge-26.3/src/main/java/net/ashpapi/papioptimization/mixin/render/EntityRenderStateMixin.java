package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiCullableDecorationState;
import net.ashpapi.papioptimization.access.PapiLodEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EntityRenderState.class, priority = 1500)
public abstract class EntityRenderStateMixin implements PapiLodEntityRenderState, PapiCullableDecorationState {

    @Unique
    private boolean papi$farLod = false;

    @Unique
    private boolean papi$backOccluded = false;

    @Override
    public boolean papi$isFarLod() {
        return this.papi$farLod;
    }

    @Override
    public void papi$setFarLod(boolean farLod) {
        this.papi$farLod = farLod;
    }

    @Override
    public boolean papi$isBackOccluded() {
        return this.papi$backOccluded;
    }

    @Override
    public void papi$setBackOccluded(boolean occluded) {
        this.papi$backOccluded = occluded;
    }
}
