package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.access.PapiCullableDecorationState;
import net.ashpapi.papioptimization.access.PapiMapRenderState;
import net.ashpapi.papioptimization.access.PapiMaskedBlockModelState;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.client.culling.PapiItemFrameSubmitContext;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ItemFrameRenderer.class, priority = 1500)
public abstract class ItemFrameRendererMixin<T extends ItemFrame> {

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

    @Unique
    private static final int BIT_UNCULLED = 0x40;

    @Unique
    private static final Vec3[] PAPI$RENDER_OFFSETS = new Vec3[6];

    static {
        for (Direction dir : Direction.values()) {
            PAPI$RENDER_OFFSETS[dir.get3DDataValue()] = new Vec3(
                    (double) ((float) dir.getStepX() * 0.3F),
                    -0.25,
                    (double) ((float) dir.getStepZ() * 0.3F)
            );
        }
    }

    @Inject(
            method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void papi$fastGetRenderOffset(ItemFrameRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (state.direction != null) {
            cir.setReturnValue(PAPI$RENDER_OFFSETS[state.direction.get3DDataValue()]);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("RETURN")
    )
    private void papi$extractBackfaceOcclusion(T frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        int mark = BlockPosPool.mark();
        try {
            Direction dir = frame.getDirection();
            Direction opp = dir.getOpposite();
            BlockPos framePos = frame.getPos();
            BlockPos.MutableBlockPos supportPos = BlockPosPool.get();
            supportPos.set(
                    framePos.getX() + opp.getStepX(),
                    framePos.getY() + opp.getStepY(),
                    framePos.getZ() + opp.getStepZ()
            );
            BlockState support = frame.level().getBlockState(supportPos);
            boolean solidBack = (!support.isAir()
                    && support.canOcclude()
                    && Block.isFaceFull(support.getFaceOcclusionShape(dir), dir))
                    || (PapiConfig.rawSolidLeaves() && PapiBlockCullingCache.isFullLeaves(support));
            ((PapiCullableDecorationState) state).papi$setBackOccluded(solidBack);
        } finally {
            BlockPosPool.reset(mark);
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void papi$cullOccludedItemFrameBehindSupport(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!((PapiCullableDecorationState) state).papi$isBackOccluded()
                || state.appearsGlowing()
                || cameraRenderState == null
                || !cameraRenderState.initialized
                || cameraRenderState.pos == null
                || state.direction == null
                || PapiOcclusionEngine.isShadowPass()) {
            return;
        }

        Direction dir = state.direction;
        double stepX = (double) dir.getStepX();
        double stepY = (double) dir.getStepY();
        double stepZ = (double) dir.getStepZ();

        double wallX = state.x - 0.03125 * stepX;
        double wallY = state.y - 0.03125 * stepY;
        double wallZ = state.z - 0.03125 * stepZ;

        double rx = cameraRenderState.pos.x - wallX;
        double ry = cameraRenderState.pos.y - wallY;
        double rz = cameraRenderState.pos.z - wallZ;

        double camNormal = rx * stepX + ry * stepY + rz * stepZ;
        if (camNormal >= -1.0E-4) {
            return;
        }

        double camU;
        double camV;
        Direction.Axis axis = dir.getAxis();
        if (axis == Direction.Axis.X) {
            camU = rz;
            camV = ry;
        } else if (axis == Direction.Axis.Y) {
            camU = rx;
            camV = rz;
        } else {
            camU = rx;
            camV = ry;
        }

        double frontDepth;
        double halfExtent;
        if (state.mapId != null) {
            frontDepth = 0.075;
            halfExtent = 0.5;
        } else if (state.item != null && !state.item.isEmpty()) {
            if (state.item.usesBlockLight()) {
                frontDepth = 0.35;
                halfExtent = 0.40;
            } else {
                frontDepth = 0.10;
                halfExtent = 0.375;
            }
        } else {
            frontDepth = 0.0625;
            halfExtent = 0.375;
        }

        double absCamN = -camNormal;
        double limit = 0.5 * (frontDepth + absCamN);
        if (Math.abs(camU) * frontDepth + absCamN * halfExtent <= limit
                && Math.abs(camV) * frontDepth + absCamN * halfExtent <= limit) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockModelRenderState;submitWithZOffset(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void papi$submitMaskedFrameModel(
            BlockModelRenderState frameModel,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            Operation<Void> original,
            @Local(argsOnly = true) ItemFrameRenderState state,
            @Local(argsOnly = true) CameraRenderState cameraRenderState
    ) {
        if (PapiOcclusionEngine.isShadowPass()
                || cameraRenderState == null
                || !cameraRenderState.initialized) {
            original.call(frameModel, poseStack, collector, lightCoords, overlayCoords, outlineColor);
            return;
        }

        byte faceMask = this.papi$computeFrameFaceMask(state, poseStack.last().pose());
        if (faceMask == (byte) 0x7F) {
            original.call(frameModel, poseStack, collector, lightCoords, overlayCoords, outlineColor);
        } else {
            ((PapiMaskedBlockModelState) frameModel).papi$submitWithZOffsetMasked(
                    poseStack,
                    collector,
                    lightCoords,
                    overlayCoords,
                    outlineColor,
                    faceMask
            );
        }
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MapRenderer;render(Lnet/minecraft/client/renderer/state/MapRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ZI)V"
            )
    )
    private void papi$submitCulledAndBiasedMap(
            MapRenderer mapRenderer,
            MapRenderState mapState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            boolean inFrame,
            int lightCoords,
            Operation<Void> original,
            @Local(argsOnly = true) ItemFrameRenderState state,
            @Local(argsOnly = true) CameraRenderState cameraRenderState
    ) {
        if (!PapiOcclusionEngine.isShadowPass()
                && cameraRenderState != null
                && cameraRenderState.initialized) {
            Matrix4f m = poseStack.last().pose();
            float c2x = m.m20();
            float c2y = m.m21();
            float c2z = m.m22();
            float viewDot = -(c2x * m.m30() + c2y * m.m31() + c2z * m.m32());
            if (viewDot > 0.0F) {
                return;
            }
            boolean opaqueMap = mapState instanceof PapiMapRenderState mrs && !mrs.papi$hasTransparency();
            double distSq = state.distanceToCameraSq;
            if (opaqueMap && distSq > 16.0) {
                float zBias = (float) Math.min(0.18, Math.sqrt(distSq) * 0.0025);
                poseStack.translate(0.0F, 0.0F, -zBias);
            }
        }
        original.call(mapRenderer, mapState, poseStack, collector, inFrame, lightCoords);
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void papi$submitLodFrameItem(
            ItemStackRenderState itemState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            Operation<Void> original,
            @Local(argsOnly = true) ItemFrameRenderState state,
            @Local(argsOnly = true) CameraRenderState cameraRenderState
    ) {
        if (PapiOcclusionEngine.isShadowPass()
                || cameraRenderState == null
                || !cameraRenderState.initialized) {
            original.call(itemState, poseStack, collector, lightCoords, overlayCoords, outlineColor);
            return;
        }
        double distSq = state.distanceToCameraSq;
        boolean farLod = distSq >= PapiConfig.rawItemFrameLodRangeSqr();
        boolean medLod = distSq >= PapiConfig.rawItemFrame3FaceRangeSqr();
        boolean solidBack = ((PapiCullableDecorationState) state).papi$isBackOccluded();
        PapiItemFrameSubmitContext.begin(farLod, medLod, solidBack);
        try {
            original.call(itemState, poseStack, collector, lightCoords, overlayCoords, outlineColor);
        } finally {
            PapiItemFrameSubmitContext.end();
        }
    }

    @Unique
    private byte papi$computeFrameFaceMask(ItemFrameRenderState state, Matrix4f m) {
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
            return (byte) 0x7F;
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

        boolean solidBack = ((PapiCullableDecorationState) state).papi$isBackOccluded();
        boolean hasMap = state.mapId != null;
        boolean opaqueMap = hasMap && state.mapRenderState instanceof PapiMapRenderState mrs && !mrs.papi$hasTransparency();
        double distSq = state.distanceToCameraSq;

        if (opaqueMap) {
            int mask = 0;
            if (!solidBack && cz > 1.0F) {
                mask |= BIT_SOUTH;
            }
            if (distSq < PapiConfig.rawItemFrameLodRangeSqr()) {
                if (cy < 0.0F) mask |= BIT_DOWN;
                if (cy > 1.0F) mask |= BIT_UP;
                if (cx < 0.0F) mask |= BIT_WEST;
                if (cx > 1.0F) mask |= BIT_EAST;
            }
            return mask == 0 ? 0 : (byte) (mask | BIT_UNCULLED);
        }

        if (distSq >= PapiConfig.rawItemFrameLodRangeSqr() && cz < 0.9375F) {
            return (byte) (BIT_NORTH | BIT_UNCULLED);
        }

        int mask = 0;
        if (cz < 0.96875F) {
            mask |= BIT_NORTH;
        }
        if (!solidBack && cz > 1.0F) {
            mask |= BIT_SOUTH;
        }

        if (distSq >= PapiConfig.rawItemFrame3FaceRangeSqr()) {
            if (cy < 0.5F) {
                mask |= BIT_DOWN;
            } else {
                mask |= BIT_UP;
            }
            if (cx < 0.5F) {
                mask |= BIT_WEST;
            } else {
                mask |= BIT_EAST;
            }
        } else {
            if (cy <= 0.8125F) mask |= BIT_DOWN;
            if (cy >= 0.1875F) mask |= BIT_UP;
            if (cx <= 0.8125F) mask |= BIT_WEST;
            if (cx >= 0.1875F) mask |= BIT_EAST;
        }

        return mask == 0 ? 0 : (byte) (mask | BIT_UNCULLED);
    }
}
