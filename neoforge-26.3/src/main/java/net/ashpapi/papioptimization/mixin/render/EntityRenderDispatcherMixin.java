package net.ashpapi.papioptimization.mixin.render;

import java.util.List;
import java.util.Map;
import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.ashpapi.papioptimization.client.PapiEntityCullingBox;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.client.occlusion.PapiSkeletalLod;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;

    @Shadow
    public Camera camera;

    @Shadow
    public abstract <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity);

    @Shadow
    public abstract double distanceToSqr(Entity entity);

    @Inject(method = "shouldRender", at = @At("HEAD"))
    private <E extends Entity> void papi$resetCapturedBoxBeforeShouldRender(
            E entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        PapiOcclusionEngine.resetCapturedEntityBox();
        PapiOcclusionEngine.resetPendingNameTagEntity();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void papi$occlusionCullEntity(
            E entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        AABB capturedBox = PapiOcclusionEngine.consumeCapturedEntityBox(entity != null ? entity.getId() : Integer.MIN_VALUE);
        if (entity == null
                || !cir.getReturnValueZ()
                || this.camera == null
                || PapiOcclusionEngine.shouldSkipEntityCulling()
                || PapiOcclusionEngine.isShadowFrustum(frustum)
                || !(entity.level() instanceof ClientLevel cl)) {
            return;
        }
        Vec3 mainCamPos = this.camera.position();
        double dcx = camX - mainCamPos.x;
        double dcy = camY - mainCamPos.y;
        double dcz = camZ - mainCamPos.z;
        if (dcx * dcx + dcy * dcy + dcz * dcz > 1.0E-4) {
            return;
        }

        double distSq = this.distanceToSqr(entity);
        if (distSq > 512.0 * 512.0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Entity camEnt = mc.getCameraEntity();
        if (entity == camEnt
                || entity == mc.player
                || mc.shouldEntityAppearGlowing(entity)
                || entity instanceof EnderDragon
                || mc.crosshairPickEntity == entity
                || ((EntityTypeRendererCache) entity.getType()).papi$isCullWhitelisted()) {
            return;
        }

        if (entity.isVehicle() || entity.isPassenger()) {
            Entity root = entity.getRootVehicle();
            if (root == mc.player
                    || root == camEnt
                    || (mc.player != null && mc.player.getRootVehicle() == root)
                    || (camEnt != null && camEnt.getRootVehicle() == root)) {
                return;
            }
        }

        EntityRenderer<? super E, ?> renderer = this.getRenderer(entity);
        if (renderer == null) {
            return;
        }
        PapiEntityCullingBox boxProvider = renderer instanceof PapiEntityCullingBox bp ? bp : null;
        if (boxProvider != null && !boxProvider.papi$isAffectedByCulling(entity)) {
            return;
        }

        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;

        if (entity instanceof ArmorStand stand && stand.isMarker()) {
            double ex = Mth.lerp(partialTick, stand.xOld, stand.getX());
            double ey = Mth.lerp(partialTick, stand.yOld, stand.getY());
            double ez = Mth.lerp(partialTick, stand.zOld, stand.getZ());
            double scale = stand.getScale();
            double hw = (stand.isSmall() ? 0.125 : 0.25) * scale;
            double h = (stand.isSmall() ? 0.9875 : 1.975) * scale;
            minX = ex - hw;
            minY = ey;
            minZ = ez - hw;
            maxX = ex + hw;
            maxY = ey + h;
            maxZ = ez + hw;
        } else {
            AABB cullBox = capturedBox;
            if (cullBox == null) {
                cullBox = boxProvider != null
                        ? boxProvider.papi$getCullingBox(entity, partialTick)
                        : entity.getBoundingBox();
            }
            if (cullBox == null || cullBox.hasNaN() || cullBox.isInfinite()) {
                return;
            }
            minX = cullBox.minX;
            minY = cullBox.minY;
            minZ = cullBox.minZ;
            maxX = cullBox.maxX;
            maxY = cullBox.maxY;
            maxZ = cullBox.maxZ;
            if ((maxX - minX) + (maxY - minY) + (maxZ - minZ) < 1.0E-4) {
                double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
                double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                minX = ex - 0.5;
                minY = ey - 0.5;
                minZ = ez - 0.5;
                maxX = ex + 0.5;
                maxY = ey + 0.5;
                maxZ = ez + 0.5;
            }
        }

        if (entity instanceof WitherBoss) {
            minX -= 0.75; minY -= 0.75; minZ -= 0.75;
            maxX += 0.75; maxY += 0.75; maxZ += 0.75;
        } else if (entity instanceof Warden) {
            minX -= 0.25; minY -= 0.25; minZ -= 0.25;
            maxX += 0.25; maxY += 0.25; maxZ += 0.25;
        }

        if (entity.isMultipartEntity()) {
            PartEntity<?>[] parts = entity.getParts();
            if (parts != null) {
                for (int i = 0; i < parts.length; i++) {
                    PartEntity<?> part = parts[i];
                    if (part != null) {
                        AABB pb = part.getBoundingBox();
                        if (pb != null && !pb.hasNaN() && !pb.isInfinite()) {
                            if (pb.minX < minX) minX = pb.minX;
                            if (pb.minY < minY) minY = pb.minY;
                            if (pb.minZ < minZ) minZ = pb.minZ;
                            if (pb.maxX > maxX) maxX = pb.maxX;
                            if (pb.maxY > maxY) maxY = pb.maxY;
                            if (pb.maxZ > maxZ) maxZ = pb.maxZ;
                        }
                    }
                }
            }
        }

        if (entity.isPassenger()) {
            Entity veh = entity.getVehicle();
            if (veh != null) {
                AABB vb = veh.getBoundingBox();
                if (vb != null && !vb.hasNaN() && !vb.isInfinite()) {
                    if (vb.minX < minX) minX = vb.minX;
                    if (vb.minY < minY) minY = vb.minY;
                    if (vb.minZ < minZ) minZ = vb.minZ;
                    if (vb.maxX > maxX) maxX = vb.maxX;
                    if (vb.maxY > maxY) maxY = vb.maxY;
                    if (vb.maxZ > maxZ) maxZ = vb.maxZ;
                }
            }
        }

        if (entity.isVehicle()) {
            List<Entity> passengers = entity.getPassengers();
            for (int i = 0, n = passengers.size(); i < n; i++) {
                Entity pass = passengers.get(i);
                if (pass != null) {
                    AABB pb = pass.getBoundingBox();
                    if (pb != null && !pb.hasNaN() && !pb.isInfinite()) {
                        if (pb.minX < minX) minX = pb.minX;
                        if (pb.minY < minY) minY = pb.minY;
                        if (pb.minZ < minZ) minZ = pb.minZ;
                        if (pb.maxX > maxX) maxX = pb.maxX;
                        if (pb.maxY > maxY) maxY = pb.maxY;
                        if (pb.maxZ > maxZ) maxZ = pb.maxZ;
                    }
                }
            }
        }

        if (entity instanceof Leashable leashable) {
            Entity holder = leashable.getLeashHolder();
            if (holder != null) {
                if (holder == camEnt || holder == mc.player) {
                    return;
                }
                AABB hb = holder.getBoundingBox();
                if (hb != null && !hb.hasNaN() && !hb.isInfinite()) {
                    if (hb.minX - 0.15 < minX) minX = hb.minX - 0.15;
                    if (hb.minY - 0.15 < minY) minY = hb.minY - 0.15;
                    if (hb.minZ - 0.15 < minZ) minZ = hb.minZ - 0.15;
                    if (hb.maxX + 0.15 > maxX) maxX = hb.maxX + 0.15;
                    if (hb.maxY + 0.15 > maxY) maxY = hb.maxY + 0.15;
                    if (hb.maxZ + 0.15 > maxZ) maxZ = hb.maxZ + 0.15;
                }
            }
        }

        if (entity instanceof Guardian guardian) {
            LivingEntity target = guardian.getActiveAttackTarget();
            if (target != null) {
                if (target == camEnt || target == mc.player) {
                    return;
                }
                AABB tb = target.getBoundingBox();
                if (tb != null && !tb.hasNaN() && !tb.isInfinite()) {
                    if (tb.minX - 0.5 < minX) minX = tb.minX - 0.5;
                    if (tb.minY - 0.5 < minY) minY = tb.minY - 0.5;
                    if (tb.minZ - 0.5 < minZ) minZ = tb.minZ - 0.5;
                    if (tb.maxX + 0.5 > maxX) maxX = tb.maxX + 0.5;
                    if (tb.maxY + 0.5 > maxY) maxY = tb.maxY + 0.5;
                    if (tb.maxZ + 0.5 > maxZ) maxZ = tb.maxZ + 0.5;
                }
            }
        }

        if (entity instanceof EndCrystal crystal) {
            BlockPos beam = crystal.getBeamTarget();
            if (beam != null) {
                double bMinX = beam.getX() - 0.5;
                double bMinY = beam.getY() - 0.5;
                double bMinZ = beam.getZ() - 0.5;
                double bMaxX = beam.getX() + 1.5;
                double bMaxY = beam.getY() + 1.5;
                double bMaxZ = beam.getZ() + 1.5;
                if (bMinX < minX) minX = bMinX;
                if (bMinY < minY) minY = bMinY;
                if (bMinZ < minZ) minZ = bMinZ;
                if (bMaxX > maxX) maxX = bMaxX;
                if (bMaxY > maxY) maxY = bMaxY;
                if (bMaxZ > maxZ) maxZ = bMaxZ;
                if (!PapiOcclusionEngine.isBoxInFrustum(frustum, minX, minY, minZ, maxX, maxY, maxZ)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        if (entity instanceof Shulker shulker) {
            Vec3 rp = shulker.getRenderPosition(partialTick);
            if (rp != null) {
                double sx = Mth.lerp(partialTick, shulker.xOld, shulker.getX()) + rp.x;
                double sy = Mth.lerp(partialTick, shulker.yOld, shulker.getY()) + rp.y;
                double sz = Mth.lerp(partialTick, shulker.zOld, shulker.getZ()) + rp.z;
                double hw = Math.max(0.5, 0.5 * shulker.getBbWidth());
                double sh = Math.max(1.0, shulker.getBbHeight());
                if (sx - hw < minX) minX = sx - hw;
                if (sy < minY) minY = sy;
                if (sz - hw < minZ) minZ = sz - hw;
                if (sx + hw > maxX) maxX = sx + hw;
                if (sy + sh > maxY) maxY = sy + sh;
                if (sz + hw > maxZ) maxZ = sz + hw;
            }
        }

        Vec3 vel = entity.getDeltaMovement();
        double stepDx = entity.getX() - entity.xOld;
        double stepDy = entity.getY() - entity.yOld;
        double stepDz = entity.getZ() - entity.zOld;
        double mx = Math.abs(stepDx) > Math.abs(vel.x) ? stepDx : vel.x;
        double my = Math.abs(stepDy) > Math.abs(vel.y) ? stepDy : vel.y;
        double mz = Math.abs(stepDz) > Math.abs(vel.z) ? stepDz : vel.z;

        if (Math.abs(mx) > 0.12) {
            double vx = Mth.clamp(mx * 2.5, -6.0, 6.0);
            if (vx < 0.0) minX += vx; else maxX += vx;
        }
        if ((!entity.onGround() || Math.abs(stepDy) > 0.12) && Math.abs(my) > 0.12) {
            double vy = Mth.clamp(my * 2.5, -6.0, 6.0);
            if (vy < 0.0) minY += vy; else maxY += vy;
        }
        if (Math.abs(mz) > 0.12) {
            double vz = Mth.clamp(mz * 2.5, -6.0, 6.0);
            if (vz < 0.0) minZ += vz; else maxZ += vz;
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
                PapiOcclusionEngine.TARGET_ENTITY,
                entity.getId(),
                PapiSkeletalLod.ENTITY_CULL_MIN_DIST_SQR
        )) {
            if (PapiConfig.renderNametagsThroughWalls()
                    && !entity.isDiscrete()
                    && mc.gui != null
                    && mc.gui.hud != null
                    && !mc.gui.hud.isHidden()) {
                double maxName = 64.0;
                double maxBelow = 10.0;
                if (entity instanceof LivingEntity living) {
                    maxName = living.getAttributeValue(Attributes.NAME_TAG_DISTANCE);
                    maxBelow = living.getAttributeValue(Attributes.BELOW_NAME_DISTANCE);
                }
                boolean hasName = distSq < maxName * maxName
                        && boxProvider != null
                        && boxProvider.papi$shouldShowName(entity, distSq);
                if (hasName) {
                    PapiOcclusionEngine.markPendingNameTagEntity(entity.getId(), distSq, maxName, maxBelow);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void papi$extractNameTagOnlyWhenOccluded(
            E entity,
            float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        if (entity == null || !PapiOcclusionEngine.consumePendingNameTagEntity(entity.getId())) {
            return;
        }
        EntityRenderer<? super E, ?> actualRenderer = this.getRenderer(entity);
        EntityRenderer<?, ?> noopRenderer = this.renderers != null ? this.renderers.get(EntityTypes.INTERACTION) : null;
        if (actualRenderer == null || noopRenderer == null) {
            return;
        }

        double distSq = PapiOcclusionEngine.getPendingNameTagDistSq();
        double maxName = PapiOcclusionEngine.getPendingNameTagMaxDist();
        double maxBelow = PapiOcclusionEngine.getPendingBelowNameMaxDist();

        EntityRenderState state = PapiOcclusionEngine.acquirePooledNameTagState();
        state.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        state.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        state.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        state.partialTick = partialTick;
        state.distanceToCameraSq = distSq;
        state.lightCoords = actualRenderer.getPackedLightCoords(entity, partialTick);

        PapiEntityCullingBox boxProvider = actualRenderer instanceof PapiEntityCullingBox bp ? bp : null;
        Component rawName = boxProvider != null ? boxProvider.papi$getNameTag(entity) : entity.getDisplayName();
        RenderNameTagEvent.CanRender event = new RenderNameTagEvent.CanRender(
                entity,
                state,
                rawName,
                noopRenderer,
                partialTick
        );
        NeoForge.EVENT_BUS.post(event);
        TriState canRender = event.canRender();
        boolean showName = canRender.isTrue()
                || (canRender.isDefault()
                && distSq < maxName * maxName
                && boxProvider != null
                && boxProvider.papi$shouldShowName(entity, distSq));
        if (showName) {
            state.nameTag = event.getContent();
            if (distSq < maxBelow * maxBelow) {
                state.scoreText = entity.belowNameDisplay();
            }
            if (state.nameTag != null || state.scoreText != null) {
                Vec3 att = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
                if (att != null && entity instanceof ClientAvatarEntity cae && cae.showExtraEars()) {
                    att = new Vec3(att.x, att.y + 0.25, att.z);
                }
                state.nameTagAttachment = att;
            }
        }
        cir.setReturnValue(state);
    }
}
