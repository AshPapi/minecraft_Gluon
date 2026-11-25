package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.client.culling.PapiCullableItemQuads;
import net.ashpapi.papioptimization.client.culling.PapiItemFrameSubmitContext;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ItemStackRenderState.LayerRenderState.class, priority = 1500)
public abstract class ItemStackLayerCullingMixin {

    @Unique
    private static final int BIT_DOWN = 1 << Direction.DOWN.ordinal();

    @Unique
    private static final int BIT_UP = 1 << Direction.UP.ordinal();

    @Unique
    private static final int BIT_NORTH = 1 << Direction.NORTH.ordinal();

    @Unique
    private static final int BIT_SOUTH = 1 << Direction.SOUTH.ordinal();

    @Unique
    private static final int BIT_WEST = 1 << Direction.WEST.ordinal();

    @Unique
    private static final int BIT_EAST = 1 << Direction.EAST.ordinal();

    @Shadow
    private boolean usesBlockLight;

    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
            )
    )
    private void papi$submitCulledItemFrameQuads(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            ItemQuads quads,
            ItemStackRenderState.FoilType foilType,
            Operation<Void> original
    ) {
        ItemQuads selected = quads;
        if (PapiItemFrameSubmitContext.isActive()
                && !quads.isEmpty()
                && !PapiOcclusionEngine.isShadowPass()) {
            int mask = this.papi$computeVisibleQuadMask(poseStack.last().pose());
            if (mask != 0x3F) {
                selected = ((PapiCullableItemQuads) (Object) quads).papi$getFiltered(mask);
            }
        }
        original.call(collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, selected, foilType);
    }

    @Unique
    private int papi$computeVisibleQuadMask(Matrix4f m) {
        float c0x = m.m00();
        float c0y = m.m01();
        float c0z = m.m02();
        float c1x = m.m10();
        float c1y = m.m11();
        float c1z = m.m12();
        float c2x = m.m20();
        float c2y = m.m21();
        float c2z = m.m22();

        float r0x = c1y * c2z - c1z * c2y;
        float r0y = c1z * c2x - c1x * c2z;
        float r0z = c1x * c2y - c1y * c2x;
        float det = c0x * r0x + c0y * r0y + c0z * r0z;
        if (Math.abs(det) <= 1.0E-6F) {
            return 0x3F;
        }

        float invDet = 1.0F / det;
        float bx = -m.m30();
        float by = -m.m31();
        float bz = -m.m32();

        float r1x = c2y * c0z - c2z * c0y;
        float r1y = c2z * c0x - c2x * c0z;
        float r1z = c2x * c0y - c2y * c0x;

        float r2x = c0y * c1z - c0z * c1y;
        float r2y = c0z * c1x - c0x * c1z;
        float r2z = c0x * c1y - c0y * c1x;

        float cx = (bx * r0x + by * r0y + bz * r0z) * invDet;
        float cy = (bx * r1x + by * r1y + bz * r1z) * invDet;
        float cz = (bx * r2x + by * r2y + bz * r2z) * invDet;

        boolean farLod = PapiItemFrameSubmitContext.isFarLod();
        boolean mediumLod = PapiItemFrameSubmitContext.isMediumLod();

        if (!this.usesBlockLight) {
            if (farLod) {
                if (cz > 0.53125F) {
                    return BIT_SOUTH;
                }
                if (cz < 0.46875F) {
                    return BIT_NORTH;
                }
                return BIT_NORTH | BIT_SOUTH;
            }
            int mask = 0x3F;
            if (cz > 0.53125F) {
                mask &= ~BIT_NORTH;
            } else if (cz < 0.46875F) {
                mask &= ~BIT_SOUTH;
            }
            if (mediumLod) {
                if (Math.abs(cz - 0.5F) > 0.35F && cx > 0.1F && cx < 0.9F && cy > 0.1F && cy < 0.9F) {
                    mask &= ~(BIT_UP | BIT_DOWN | BIT_WEST | BIT_EAST);
                }
            }
            return mask;
        }

        int mask = 0x3F;
        if (cy < -0.01F) mask &= ~BIT_UP;
        if (cy > 1.01F) mask &= ~BIT_DOWN;
        if (cz < -0.01F) mask &= ~BIT_SOUTH;
        if (cz > 1.01F) mask &= ~BIT_NORTH;
        if (cx < -0.01F) mask &= ~BIT_EAST;
        if (cx > 1.01F) mask &= ~BIT_WEST;
        return mask;
    }
}
