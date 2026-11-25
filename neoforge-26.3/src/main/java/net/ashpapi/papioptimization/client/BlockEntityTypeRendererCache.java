package net.ashpapi.papioptimization.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface BlockEntityTypeRendererCache {

    Object PAPI$NO_RENDERER = new Object();

    @Nullable Object papi$getRendererSlot();

    void papi$setRenderer(@Nullable BlockEntityRenderer<?, ?> renderer);

    void papi$resetWhitelistCache();

    boolean papi$isCullWhitelisted();
}
