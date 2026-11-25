package net.ashpapi.papioptimization.access;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface PapiMapRenderState {

    default boolean papi$hasTransparency() {
        return true;
    }

    default void papi$setHasTransparency(boolean hasTransparency) {
    }

    default @Nullable Identifier papi$getAtlasTexture() {
        return null;
    }

    default float papi$getAtlasU0() {
        return 0.0F;
    }

    default float papi$getAtlasV0() {
        return 0.0F;
    }

    default float papi$getAtlasU1() {
        return 1.0F;
    }

    default float papi$getAtlasV1() {
        return 1.0F;
    }

    default void papi$setAtlasRegion(@Nullable Identifier atlasTexture, float u0, float v0, float u1, float v1) {
    }
}
