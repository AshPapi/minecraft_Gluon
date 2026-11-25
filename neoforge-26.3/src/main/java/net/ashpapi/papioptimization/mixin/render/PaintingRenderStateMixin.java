package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiPaintingCullState;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PaintingRenderState.class, priority = 1500)
public abstract class PaintingRenderStateMixin implements PapiPaintingCullState {

    @Unique
    private long papi$solidBackMask = 0L;

    @Unique
    private boolean @Nullable [] papi$largeSolidBack = null;

    @Unique
    private byte papi$occludedEdgesMask = 0;

    @Override
    public long papi$getSolidBackMask() {
        return this.papi$solidBackMask;
    }

    @Override
    public void papi$setSolidBackMask(long mask) {
        this.papi$solidBackMask = mask;
    }

    @Override
    public boolean @Nullable [] papi$getLargeSolidBack() {
        return this.papi$largeSolidBack;
    }

    @Override
    public void papi$setLargeSolidBack(boolean @Nullable [] mask) {
        this.papi$largeSolidBack = mask;
    }

    @Override
    public byte papi$getOccludedEdgesMask() {
        return this.papi$occludedEdgesMask;
    }

    @Override
    public void papi$setOccludedEdgesMask(byte mask) {
        this.papi$occludedEdgesMask = mask;
    }
}
