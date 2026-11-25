package net.ashpapi.papioptimization.mixin.map;

import net.ashpapi.papioptimization.access.PapiMapRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MapRenderState.class, priority = 1500)
public abstract class MapRenderStateMixin implements PapiMapRenderState {

    @Unique
    private boolean papi$hasTransparency = true;

    @Unique
    private @Nullable Identifier papi$atlasTexture;

    @Unique
    private float papi$atlasU0 = 0.0F;

    @Unique
    private float papi$atlasV0 = 0.0F;

    @Unique
    private float papi$atlasU1 = 1.0F;

    @Unique
    private float papi$atlasV1 = 1.0F;

    @Override
    public boolean papi$hasTransparency() {
        return this.papi$hasTransparency;
    }

    @Override
    public void papi$setHasTransparency(boolean hasTransparency) {
        this.papi$hasTransparency = hasTransparency;
    }

    @Override
    public @Nullable Identifier papi$getAtlasTexture() {
        return this.papi$atlasTexture;
    }

    @Override
    public float papi$getAtlasU0() {
        return this.papi$atlasU0;
    }

    @Override
    public float papi$getAtlasV0() {
        return this.papi$atlasV0;
    }

    @Override
    public float papi$getAtlasU1() {
        return this.papi$atlasU1;
    }

    @Override
    public float papi$getAtlasV1() {
        return this.papi$atlasV1;
    }

    @Override
    public void papi$setAtlasRegion(@Nullable Identifier atlasTexture, float u0, float v0, float u1, float v1) {
        this.papi$atlasTexture = atlasTexture;
        this.papi$atlasU0 = u0;
        this.papi$atlasV0 = v0;
        this.papi$atlasU1 = u1;
        this.papi$atlasV1 = v1;
    }
}
