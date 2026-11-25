package net.ashpapi.papioptimization.access;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PapiMaskedSubmitCollector {

    default void papi$submitMaskedBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> modelParts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            byte faceMask
    ) {
        ((OrderedSubmitNodeCollector) this).submitBlockModel(
                poseStack,
                renderType,
                modelParts,
                tintLayers,
                lightCoords,
                overlayCoords,
                outlineColor
        );
    }
}
