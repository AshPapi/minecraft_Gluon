package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ashpapi.papioptimization.access.PapiCullableDecorationState;
import net.ashpapi.papioptimization.access.PapiPaintingCullState;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PaintingRenderer.class, priority = 1500)
public abstract class PaintingRendererMixin {

    @Unique
    private static final byte EDGE_TOP = 1;

    @Unique
    private static final byte EDGE_BOTTOM = 2;

    @Unique
    private static final byte EDGE_RIGHT = 4;

    @Unique
    private static final byte EDGE_LEFT = 8;

    @Unique
    private static boolean papi$isFaceOccluding(BlockState state, Direction towardsFace, boolean solidLeaves) {
        if (state.isAir()) {
            return false;
        }
        if (state.canOcclude() && Block.isFaceFull(state.getFaceOcclusionShape(towardsFace), towardsFace)) {
            return true;
        }
        return solidLeaves && PapiBlockCullingCache.isFullLeaves(state);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void papi$fastExtractPaintingState(Painting painting, PaintingRenderState state, float partialTick, CallbackInfo ci) {
        Direction direction = painting.getDirection();
        PaintingVariant variant = painting.getVariant().value();
        state.direction = direction;
        state.variant = variant;
        int width = variant.width();
        int height = variant.height();
        int total = width * height;
        if (state.lightCoordsPerBlock.length != total) {
            state.lightCoordsPerBlock = new int[total];
        }
        PapiCullableDecorationState decExt = (PapiCullableDecorationState) state;
        PapiPaintingCullState paintExt = (PapiPaintingCullState) state;
        boolean[] largeMask = null;
        if (total > 64) {
            boolean[] prev = paintExt.papi$getLargeSolidBack();
            largeMask = (prev != null && prev.length == total) ? prev : new boolean[total];
        } else {
            paintExt.papi$setLargeSolidBack(null);
        }

        float halfW = (float) (-width) / 2.0F;
        float halfH = (float) (-height) / 2.0F;
        Level level = painting.level();
        int baseBlockX = painting.getBlockX();
        int baseBlockY = painting.getBlockY();
        int baseBlockZ = painting.getBlockZ();
        double px = painting.getX();
        double py = painting.getY();
        double pz = painting.getZ();
        int backStepX = -direction.getStepX();
        int backStepY = -direction.getStepY();
        int backStepZ = -direction.getStepZ();
        boolean solidLeaves = PapiConfig.rawSolidLeaves();
        boolean horizontal = direction.getAxis().isHorizontal();

        Direction rightDir = horizontal ? direction.getClockWise() : null;
        Direction leftDir = horizontal ? direction.getCounterClockWise() : null;

        boolean allBackSolid = true;
        boolean topSealed = horizontal;
        boolean bottomSealed = horizontal;
        boolean rightSealed = horizontal;
        boolean leftSealed = horizontal;
        long solidBackMask = 0L;

        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos pos = BlockPosPool.get();
            for (int row = 0; row < height; row++) {
                float offsetY = (float) row + halfH + 0.5F;
                int rowBase = row * width;
                for (int col = 0; col < width; col++) {
                    float offsetX = (float) col + halfW + 0.5F;
                    int blockX = baseBlockX;
                    int blockY;
                    int blockZ = baseBlockZ;
                    if (direction == Direction.NORTH) {
                        blockX = Mth.floor(px + (double) offsetX);
                        blockY = Mth.floor(py + (double) offsetY);
                    } else if (direction == Direction.WEST) {
                        blockY = Mth.floor(py + (double) offsetY);
                        blockZ = Mth.floor(pz - (double) offsetX);
                    } else if (direction == Direction.SOUTH) {
                        blockX = Mth.floor(px - (double) offsetX);
                        blockY = Mth.floor(py + (double) offsetY);
                    } else if (direction == Direction.EAST) {
                        blockY = Mth.floor(py + (double) offsetY);
                        blockZ = Mth.floor(pz + (double) offsetX);
                    } else if (direction == Direction.UP) {
                        blockX = Mth.floor(px - (double) offsetX);
                        blockY = baseBlockY;
                        blockZ = Mth.floor(pz - (double) offsetY);
                    } else {
                        blockX = Mth.floor(px - (double) offsetX);
                        blockY = baseBlockY;
                        blockZ = Mth.floor(pz + (double) offsetY);
                    }

                    int idx = rowBase + col;
                    pos.set(blockX, blockY, blockZ);
                    state.lightCoordsPerBlock[idx] = LightCoordsUtil.getLightCoords(level, pos);

                    pos.set(blockX + backStepX, blockY + backStepY, blockZ + backStepZ);
                    BlockState back = level.getBlockState(pos);
                    boolean tileSolid = papi$isFaceOccluding(back, direction, solidLeaves);
                    if (tileSolid) {
                        if (idx < 64) {
                            solidBackMask |= (1L << idx);
                        } else if (largeMask != null) {
                            largeMask[idx] = true;
                        }
                    } else {
                        allBackSolid = false;
                        if (idx >= 64 && largeMask != null) {
                            largeMask[idx] = false;
                        }
                    }

                    if (horizontal) {
                        if (topSealed && row == height - 1) {
                            pos.set(blockX, blockY + 1, blockZ);
                            if (!papi$isFaceOccluding(level.getBlockState(pos), Direction.DOWN, solidLeaves)) {
                                topSealed = false;
                            }
                        }
                        if (bottomSealed && row == 0) {
                            pos.set(blockX, blockY - 1, blockZ);
                            if (!papi$isFaceOccluding(level.getBlockState(pos), Direction.UP, solidLeaves)) {
                                bottomSealed = false;
                            }
                        }
                        if (rightSealed && col == width - 1 && rightDir != null && leftDir != null) {
                            pos.set(blockX + rightDir.getStepX(), blockY, blockZ + rightDir.getStepZ());
                            if (!papi$isFaceOccluding(level.getBlockState(pos), leftDir, solidLeaves)) {
                                rightSealed = false;
                            }
                        }
                        if (leftSealed && col == 0 && leftDir != null && rightDir != null) {
                            pos.set(blockX + leftDir.getStepX(), blockY, blockZ + leftDir.getStepZ());
                            if (!papi$isFaceOccluding(level.getBlockState(pos), rightDir, solidLeaves)) {
                                leftSealed = false;
                            }
                        }
                    }
                }
            }
        } finally {
            BlockPosPool.reset(mark);
        }

        byte edgeMask = 0;
        if (topSealed) edgeMask |= EDGE_TOP;
        if (bottomSealed) edgeMask |= EDGE_BOTTOM;
        if (rightSealed) edgeMask |= EDGE_RIGHT;
        if (leftSealed) edgeMask |= EDGE_LEFT;

        decExt.papi$setBackOccluded(allBackSolid);
        paintExt.papi$setSolidBackMask(solidBackMask);
        paintExt.papi$setOccludedEdgesMask(edgeMask);
        if (largeMask != null) {
            paintExt.papi$setLargeSolidBack(largeMask);
        }
        ci.cancel();
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/PaintingRenderer;renderPainting(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;[IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
            )
    )
    private void papi$renderCulledPainting(
            PaintingRenderer instance,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            RenderType renderType,
            int[] lightCoords,
            int width,
            int height,
            TextureAtlasSprite frontSprite,
            TextureAtlasSprite backSprite,
            Operation<Void> original,
            @Local(argsOnly = true) PaintingRenderState state,
            @Local(argsOnly = true) CameraRenderState cameraRenderState
    ) {
        boolean shadow = PapiOcclusionEngine.isShadowPass();
        PapiCullableDecorationState decExt = (PapiCullableDecorationState) state;
        PapiPaintingCullState paintExt = (PapiPaintingCullState) state;
        byte edgeMask = paintExt.papi$getOccludedEdgesMask();

        boolean drawFront = true;
        boolean camBehindBack = true;
        boolean drawTop = (edgeMask & EDGE_TOP) == 0;
        boolean drawBottom = (edgeMask & EDGE_BOTTOM) == 0;
        boolean drawRight = (edgeMask & EDGE_RIGHT) == 0;
        boolean drawLeft = (edgeMask & EDGE_LEFT) == 0;

        if (!shadow && cameraRenderState != null && cameraRenderState.initialized) {
            Matrix4f m = poseStack.last().pose();
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

            if (Math.abs(det) > 1.0E-6F) {
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

                float localCamX = (bx * r0x + by * r0y + bz * r0z) * invDet;
                float localCamY = (bx * r1x + by * r1y + bz * r1z) * invDet;
                float localCamZ = (bx * r2x + by * r2y + bz * r2z) * invDet;

                float halfW = (float) width * 0.5F;
                float halfH = (float) height * 0.5F;

                drawFront = localCamZ < -0.03125F;
                camBehindBack = localCamZ > 0.03125F;
                drawTop = drawTop && localCamY > halfH;
                drawBottom = drawBottom && localCamY < -halfH;
                drawRight = drawRight && localCamX > halfW;
                drawLeft = drawLeft && localCamX < -halfW;
            }
        }

        boolean allBackSolid = decExt.papi$isBackOccluded();
        boolean anyBack = camBehindBack && !allBackSolid;

        if (!drawFront && !anyBack && !drawTop && !drawBottom && !drawRight && !drawLeft) {
            return;
        }

        long solidMask = paintExt.papi$getSolidBackMask();
        boolean[] largeMask = paintExt.papi$getLargeSolidBack();
        int faceFlags = (drawFront ? 1 : 0)
                | (anyBack ? 2 : 0)
                | (drawTop ? 4 : 0)
                | (drawBottom ? 8 : 0)
                | (drawRight ? 16 : 0)
                | (drawLeft ? 32 : 0);

        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            papi$emitPaintingQuads(
                    pose,
                    consumer,
                    width,
                    height,
                    frontSprite,
                    backSprite,
                    lightCoords,
                    faceFlags,
                    solidMask,
                    largeMask
            );
        });
    }

    @Unique
    private static void papi$emitPaintingQuads(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int width,
            int height,
            TextureAtlasSprite frontSprite,
            TextureAtlasSprite backSprite,
            int[] lightCoords,
            int faceFlags,
            long solidMask,
            boolean[] largeMask
    ) {
        boolean drawFront = (faceFlags & 1) != 0;
        boolean drawBack = (faceFlags & 2) != 0;
        boolean drawTop = (faceFlags & 4) != 0;
        boolean drawBottom = (faceFlags & 8) != 0;
        boolean drawRight = (faceFlags & 16) != 0;
        boolean drawLeft = (faceFlags & 32) != 0;
        boolean needBackAtlas = (faceFlags & ~1) != 0;

        float startX = (float) (-width) / 2.0F;
        float startY = (float) (-height) / 2.0F;
        float zThickness = 0.03125F;

        float backU0 = 0.0F;
        float backU1 = 0.0F;
        float backV0 = 0.0F;
        float backV1 = 0.0F;
        float tbV1 = 0.0F;
        float lrU1 = 0.0F;
        if (needBackAtlas) {
            backU0 = backSprite.getU0();
            backU1 = backSprite.getU1();
            backV0 = backSprite.getV0();
            backV1 = backSprite.getV1();
            if (drawTop || drawBottom) {
                tbV1 = backSprite.getV(0.0625F);
            }
            if (drawRight || drawLeft) {
                lrU1 = backSprite.getU(0.0625F);
            }
        }

        double invW = 1.0 / (double) width;
        double invH = 1.0 / (double) height;

        for (int col = 0; col < width; col++) {
            float x1 = startX + (float) (col + 1);
            float x0 = startX + (float) col;
            float fU0 = 0.0F;
            float fU1 = 0.0F;
            if (drawFront) {
                fU0 = frontSprite.getU((float) (invW * (double) (width - col)));
                fU1 = frontSprite.getU((float) (invW * (double) (width - (col + 1))));
            }

            for (int row = 0; row < height; row++) {
                float y1 = startY + (float) (row + 1);
                float y0 = startY + (float) row;
                int idx = col + row * width;
                int light = lightCoords[idx];

                if (drawFront) {
                    float fV0 = frontSprite.getV((float) (invH * (double) (height - row)));
                    float fV1 = frontSprite.getV((float) (invH * (double) (height - (row + 1))));
                    papi$vertex(pose, consumer, x1, y0, fU1, fV0, -zThickness, 0, 0, -1, light);
                    papi$vertex(pose, consumer, x0, y0, fU0, fV0, -zThickness, 0, 0, -1, light);
                    papi$vertex(pose, consumer, x0, y1, fU0, fV1, -zThickness, 0, 0, -1, light);
                    papi$vertex(pose, consumer, x1, y1, fU1, fV1, -zThickness, 0, 0, -1, light);
                }

                if (drawBack) {
                    boolean blocked = idx < 64
                            ? ((solidMask & (1L << idx)) != 0L)
                            : (largeMask != null && idx < largeMask.length && largeMask[idx]);
                    if (!blocked) {
                        papi$vertex(pose, consumer, x1, y1, backU1, backV0, zThickness, 0, 0, 1, light);
                        papi$vertex(pose, consumer, x0, y1, backU0, backV0, zThickness, 0, 0, 1, light);
                        papi$vertex(pose, consumer, x0, y0, backU0, backV1, zThickness, 0, 0, 1, light);
                        papi$vertex(pose, consumer, x1, y0, backU1, backV1, zThickness, 0, 0, 1, light);
                    }
                }

                if (drawTop && row == height - 1) {
                    papi$vertex(pose, consumer, x1, y1, backU0, backV0, -zThickness, 0, 1, 0, light);
                    papi$vertex(pose, consumer, x0, y1, backU1, backV0, -zThickness, 0, 1, 0, light);
                    papi$vertex(pose, consumer, x0, y1, backU1, tbV1, zThickness, 0, 1, 0, light);
                    papi$vertex(pose, consumer, x1, y1, backU0, tbV1, zThickness, 0, 1, 0, light);
                }

                if (drawBottom && row == 0) {
                    papi$vertex(pose, consumer, x1, y0, backU0, backV0, zThickness, 0, -1, 0, light);
                    papi$vertex(pose, consumer, x0, y0, backU1, backV0, zThickness, 0, -1, 0, light);
                    papi$vertex(pose, consumer, x0, y0, backU1, tbV1, -zThickness, 0, -1, 0, light);
                    papi$vertex(pose, consumer, x1, y0, backU0, tbV1, -zThickness, 0, -1, 0, light);
                }

                if (drawRight && col == width - 1) {
                    papi$vertex(pose, consumer, x1, y1, lrU1, backV0, zThickness, -1, 0, 0, light);
                    papi$vertex(pose, consumer, x1, y0, lrU1, backV1, zThickness, -1, 0, 0, light);
                    papi$vertex(pose, consumer, x1, y0, backU0, backV1, -zThickness, -1, 0, 0, light);
                    papi$vertex(pose, consumer, x1, y1, backU0, backV0, -zThickness, -1, 0, 0, light);
                }

                if (drawLeft && col == 0) {
                    papi$vertex(pose, consumer, x0, y1, lrU1, backV0, -zThickness, 1, 0, 0, light);
                    papi$vertex(pose, consumer, x0, y0, lrU1, backV1, -zThickness, 1, 0, 0, light);
                    papi$vertex(pose, consumer, x0, y0, backU0, backV1, zThickness, 1, 0, 0, light);
                    papi$vertex(pose, consumer, x0, y1, backU0, backV0, zThickness, 1, 0, 0, light);
                }
            }
        }
    }

    @Unique
    private static void papi$vertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float u,
            float v,
            float z,
            int nx,
            int ny,
            int nz,
            int light
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) nx, (float) ny, (float) nz);
    }
}
