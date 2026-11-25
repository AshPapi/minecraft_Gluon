package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiMaskedBlockModelState;
import net.ashpapi.papioptimization.access.PapiMaskedSubmitCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockModelRenderState.class, priority = 1500)
public abstract class BlockModelRenderStateMixin implements PapiMaskedBlockModelState {

    @Shadow
    @Final
    public static int[] EMPTY_TINTS;

    @Shadow
    private @Nullable List<BlockStateModelPart> modelParts;

    @Shadow
    private @Nullable Matrix4fc transformation;

    @Shadow
    private @Nullable IntList tintLayers;

    @Shadow
    public int blockLightCoords;

    @Unique
    private int @Nullable [] papi$cachedTints;

    @Unique
    private int papi$cachedTintSize = -1;

    @Shadow
    private void submitSpecialRenderer(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor
    ) {
        throw new AssertionError();
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void papi$invalidateTintArrayCache(CallbackInfo ci) {
        this.papi$cachedTintSize = -1;
    }

    @Unique
    private int[] papi$resolveTintArray(@Nullable IntList list) {
        if (list == null || list.isEmpty()) {
            return EMPTY_TINTS;
        }
        int size = list.size();
        int[] cached = this.papi$cachedTints;
        if (this.papi$cachedTintSize == size && cached != null && cached.length == size) {
            return cached;
        }
        if (cached == null || cached.length != size) {
            cached = new int[size];
            this.papi$cachedTints = cached;
        }
        list.getElements(0, cached, 0, size);
        this.papi$cachedTintSize = size;
        return cached;
    }

    @WrapOperation(
            method = {"submitModel", "submitMultiLayer"},
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/IntList;toArray([I)[I"
            )
    )
    private int[] papi$reuseTintArray(IntList instance, int[] fallback, Operation<int[]> original) {
        return this.papi$resolveTintArray(instance);
    }

    @Override
    public void papi$submitWithZOffsetMasked(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            byte faceMask
    ) {
        List<BlockStateModelPart> parts = this.modelParts;
        if (parts != null && !parts.isEmpty() && ((faceMask & 0x7F) != 0 || outlineColor != 0)) {
            RenderType rt = RenderTypes.entitySolidZOffsetForward(TextureAtlas.LOCATION_BLOCKS);
            List<BlockStateModelPart> copiedParts = new ObjectArrayList<>(parts);
            int[] tints = this.papi$resolveTintArray(this.tintLayers);
            int combinedLight = LightCoordsUtil.max(lightCoords, this.blockLightCoords);
            Matrix4fc xf = this.transformation;
            PapiMaskedSubmitCollector maskedCollector = (PapiMaskedSubmitCollector) collector;
            if (xf != null) {
                poseStack.pushPose();
                poseStack.mulPose(xf);
                maskedCollector.papi$submitMaskedBlockModel(
                        poseStack, rt, copiedParts, tints, combinedLight, overlayCoords, outlineColor, faceMask
                );
                poseStack.popPose();
            } else {
                maskedCollector.papi$submitMaskedBlockModel(
                        poseStack, rt, copiedParts, tints, combinedLight, overlayCoords, outlineColor, faceMask
                );
            }
        }
        this.submitSpecialRenderer(poseStack, collector, lightCoords, overlayCoords, outlineColor);
    }
}
