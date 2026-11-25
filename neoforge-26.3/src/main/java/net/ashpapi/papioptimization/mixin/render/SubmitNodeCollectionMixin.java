package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiMaskedBlockSubmit;
import net.ashpapi.papioptimization.access.PapiMaskedSubmitCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SubmitNodeCollection.class, priority = 1500)
public abstract class SubmitNodeCollectionMixin implements PapiMaskedSubmitCollector {

    @Shadow
    @Final
    public SimpleFeatureRenderPhase solid;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase outline;

    @Shadow
    @Final
    public FeatureRenderPhase<? super TranslucentSubmit> translucentBlocksAndItems;

    @Shadow
    private static @Nullable RenderType getOutlineRenderType(RenderType renderType) {
        throw new AssertionError();
    }

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
        int masked = faceMask & 0x7F;
        if (masked == 0 && outlineColor == 0) {
            return;
        }
        PoseStack.Pose poseCopy = poseStack.last().copy();
        if (masked != 0 && !renderType.isOutline()) {
            BlockModelFeatureRenderer.Submit submit = new BlockModelFeatureRenderer.Submit(
                    poseCopy,
                    renderType,
                    modelParts,
                    tintLayers,
                    lightCoords,
                    overlayCoords,
                    -1,
                    null
            );
            if (masked != 0x7F) {
                ((PapiMaskedBlockSubmit) (Object) submit).papi$setFaceMask((byte) masked);
            }
            if (renderType.hasBlending()) {
                this.translucentBlocksAndItems.submit(submit);
            } else {
                this.solid.submit(submit);
            }
        }
        if (outlineColor != 0) {
            RenderType outlineType = getOutlineRenderType(renderType);
            if (outlineType != null) {
                this.outline.submit(new BlockModelFeatureRenderer.Submit(
                        poseCopy,
                        outlineType,
                        modelParts,
                        BlockModelRenderState.EMPTY_TINTS,
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        outlineColor,
                        null
                ));
            }
        }
    }
}
