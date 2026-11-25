package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiMaskedBlockSubmit;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockModelFeatureRenderer.class, priority = 1500)
public abstract class BlockModelFeatureRendererMixin {

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    @Shadow
    private static void putQuad(
            PoseStack.Pose pose,
            BakedQuad quad,
            QuadInstance quadInstance,
            int tintColor,
            int[] tintLayers,
            VertexConsumer consumer
    ) {
        throw new AssertionError();
    }

    @WrapOperation(
            method = "buildGroup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/BlockModelFeatureRenderer;putPartQuads(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelPart;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/QuadInstance;I[ILcom/mojang/blaze3d/vertex/VertexConsumer;)V"
            )
    )
    private void papi$emitMaskedPartQuads(
            BlockStateModelPart part,
            PoseStack.Pose pose,
            QuadInstance quadInstance,
            int tintColor,
            int[] tintLayers,
            VertexConsumer consumer,
            Operation<Void> original,
            @Local BlockModelFeatureRenderer.Submit submit
    ) {
        int mask = ((PapiMaskedBlockSubmit) (Object) submit).papi$getFaceMask() & 0x7F;
        if (mask == 0x7F || PapiOcclusionEngine.isShadowPass()) {
            original.call(part, pose, quadInstance, tintColor, tintLayers, consumer);
            return;
        }
        if (mask == 0) {
            return;
        }
        for (int d = 0, n = DIRECTIONS.length; d < n; d++) {
            Direction dir = DIRECTIONS[d];
            if ((mask & (1 << dir.ordinal())) == 0) {
                continue;
            }
            List<BakedQuad> quads = part.getQuads(dir);
            for (int i = 0, qLen = quads.size(); i < qLen; i++) {
                putQuad(pose, quads.get(i), quadInstance, tintColor, tintLayers, consumer);
            }
        }
        if ((mask & 0x40) != 0) {
            List<BakedQuad> unculled = part.getQuads(null);
            for (int i = 0, qLen = unculled.size(); i < qLen; i++) {
                BakedQuad quad = unculled.get(i);
                Direction qDir = quad.direction();
                if (qDir == null || (mask & (1 << qDir.ordinal())) != 0) {
                    putQuad(pose, quad, quadInstance, tintColor, tintLayers, consumer);
                }
            }
        }
    }
}
