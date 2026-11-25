package net.ashpapi.papioptimization.access;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PapiMaskedBlockModelState {

    default void papi$submitWithZOffsetMasked(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            byte faceMask
    ) {
    }
}
