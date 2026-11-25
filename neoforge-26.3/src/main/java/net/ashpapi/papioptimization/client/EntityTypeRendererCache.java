package net.ashpapi.papioptimization.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface EntityTypeRendererCache {

    Object PAPI$NO_RENDERER = new Object();

    @Nullable Object papi$getRendererSlot();

    @Nullable EntityRenderer<?, ?> papi$getRenderer();

    void papi$setRenderer(@Nullable EntityRenderer<?, ?> renderer);

    boolean papi$isCullWhitelisted();

    boolean papi$isTickCullAllowed(Entity sample);
}
