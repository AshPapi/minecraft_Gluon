package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.BlockEntityTypeRendererCache;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.client.occlusion.PapiSkeletalLod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 1500)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    private Vec3 cameraPos;

    @Shadow
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;createRenderState()Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"
        ),
        cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$occlusionCullBlockEntity(
            E blockEntity,
            float partialTick,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
            boolean renderOffScreen,
            @Nullable Frustum frustum,
            CallbackInfoReturnable<@Nullable S> cir
    ) {
        Class<?> cls = blockEntity.getClass();
        if (crumblingOverlay == null && cls == ShelfBlockEntity.class && ((ShelfBlockEntity) blockEntity).isEmpty()) {
            cir.setReturnValue(null);
            return;
        }
        if (renderOffScreen
                || PapiOcclusionEngine.isShadowPass()
                || PapiOcclusionEngine.isShadowFrustum(frustum)
                || !(blockEntity.getLevel() instanceof ClientLevel cl)
                || ((BlockEntityTypeRendererCache) blockEntity.getType()).papi$isCullWhitelisted()) {
            return;
        }
        if (blockEntity instanceof BeaconBeamOwner
                || blockEntity instanceof BoundingBoxRenderable
                || blockEntity instanceof BeaconBlockEntity
                || blockEntity instanceof StructureBlockEntity
                || blockEntity instanceof JigsawBlockEntity
                || blockEntity instanceof TheEndPortalBlockEntity
                || blockEntity instanceof TheEndGatewayBlockEntity
                || blockEntity instanceof PistonMovingBlockEntity) {
            return;
        }
        BlockEntityRenderer<E, S> renderer = this.getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        if (camera == null || this.cameraPos == null) {
            return;
        }
        Vec3 mainCamPos = camera.position();
        if (this.cameraPos.distanceToSqr(mainCamPos) > 1.0E-4) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();

        double dcx = (px + 0.5) - mainCamPos.x;
        double dcy = (py + 0.5) - mainCamPos.y;
        double dcz = (pz + 0.5) - mainCamPos.z;
        if (dcx * dcx + dcy * dcy + dcz * dcz > 512.0 * 512.0) {
            return;
        }

        double minX = px;
        double minY = py;
        double minZ = pz;
        double maxX = px + 1.0;
        double maxY = py + 1.0;
        double maxZ = pz + 1.0;

        if (cls == ChestBlockEntity.class || cls == TrappedChestBlockEntity.class) {
            BlockState state = blockEntity.getBlockState();
            if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    Direction conn = ChestBlock.getConnectedDirection(state);
                    int sx = conn.getStepX();
                    int sz = conn.getStepZ();
                    if (sx < 0) minX += sx; else if (sx > 0) maxX += sx;
                    if (sz < 0) minZ += sz; else if (sz > 0) maxZ += sz;
                }
            }
            if (((LidBlockEntity) blockEntity).getOpenNess(partialTick) > 0.01F) {
                maxY = py + 1.375;
            }
        } else if (cls == EnderChestBlockEntity.class) {
            if (((EnderChestBlockEntity) blockEntity).getOpenNess(partialTick) > 0.01F) {
                maxY = py + 1.375;
            }
        } else if (cls == ShulkerBoxBlockEntity.class) {
            ShulkerBoxBlockEntity shulker = (ShulkerBoxBlockEntity) blockEntity;
            float prog = shulker.getProgress(partialTick);
            if (prog > 0.001F) {
                BlockState state = shulker.getBlockState();
                Direction dir = state.hasProperty(ShulkerBoxBlock.FACING) ? state.getValue(ShulkerBoxBlock.FACING) : Direction.UP;
                double ext = 0.5 * prog;
                int sx = dir.getStepX();
                int sy = dir.getStepY();
                int sz = dir.getStepZ();
                if (sx < 0) minX += sx * ext; else if (sx > 0) maxX += sx * ext;
                if (sy < 0) minY += sy * ext; else if (sy > 0) maxY += sy * ext;
                if (sz < 0) minZ += sz * ext; else if (sz > 0) maxZ += sz * ext;
            }
        } else if (cls == BannerBlockEntity.class) {
            if (blockEntity.getBlockState().getBlock() instanceof BannerBlock) {
                maxY = py + 2.0;
            } else {
                minY = py - 1.0;
            }
        } else if (cls == EnchantingTableBlockEntity.class) {
            maxY = py + 1.35;
        } else if (cls == ConduitBlockEntity.class) {
            minY = py - 0.25;
            maxY = py + 1.25;
        } else if (cls == BrushableBlockEntity.class) {
            minX = px - 0.25;
            minY = py - 0.25;
            minZ = pz - 0.25;
            maxX = px + 1.25;
            maxY = py + 1.5;
            maxZ = pz + 1.25;
        } else if (cls == CopperGolemStatueBlockEntity.class || cls == LecternBlockEntity.class) {
            maxY = py + 1.5;
        } else if (cls == DecoratedPotBlockEntity.class) {
            maxY = py + 1.3;
        } else if (cls == SkullBlockEntity.class) {
            if (blockEntity.getBlockState().getBlock() instanceof AbstractSkullBlock sb
                    && sb.getType() == SkullBlock.Types.DRAGON) {
                minX = px - 0.75;
                minY = py - 0.35;
                minZ = pz - 0.75;
                maxX = px + 1.75;
                maxZ = pz + 1.75;
            }
        } else if (cls == SignBlockEntity.class) {
            if (blockEntity.getBlockState().getBlock() instanceof StandingSignBlock) {
                maxY = py + 1.125;
            }
        } else if (cls != HangingSignBlockEntity.class
                && cls != CampfireBlockEntity.class
                && cls != BellBlockEntity.class
                && cls != SpawnerBlockEntity.class
                && cls != TrialSpawnerBlockEntity.class
                && cls != VaultBlockEntity.class
                && cls != ShelfBlockEntity.class) {
            AABB box = renderer.getRenderBoundingBox(blockEntity);
            if (box == null || box.isInfinite() || box.hasNaN()) {
                return;
            }
            minX = box.minX;
            minY = box.minY;
            minZ = box.minZ;
            maxX = box.maxX;
            maxY = box.maxY;
            maxZ = box.maxZ;
        }

        if (frustum == null && !PapiOcclusionEngine.isBoxInActiveFrustum(
                minX - 0.75, minY - 0.75, minZ - 0.75,
                maxX + 0.75, maxY + 0.75, maxZ + 0.75
        )) {
            cir.setReturnValue(null);
            return;
        }

        if (PapiOcclusionEngine.isBoxOccluded(
                cl,
                mainCamPos,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                PapiOcclusionEngine.TARGET_BLOCK_ENTITY,
                pos.asLong(),
                PapiSkeletalLod.BLOCK_ENTITY_CULL_MIN_DIST_SQR
        )) {
            cir.setReturnValue(null);
        }
    }
}
