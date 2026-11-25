package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiMaskedSubmitCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SubmitNodeStorage.class, priority = 1500)
public abstract class SubmitNodeStorageMixin implements PapiMaskedSubmitCollector {

    @Shadow
    public abstract SubmitNodeCollection order(int order);

    @Override
    public void papi$submitMaskedBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> modelParts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            byte faceMask
    ) {
        ((PapiMaskedSubmitCollector) this.order(0)).papi$submitMaskedBlockModel(
                poseStack,
                renderType,
                modelParts,
                tintLayers,
                lightCoords,
                overlayCoords,
                outlineColor,
                faceMask
        );
    }
}
