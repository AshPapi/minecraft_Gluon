package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.access.PapiClientLivingTick;
import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.ashpapi.papioptimization.client.PapiEntityCullingBox;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelTickCullingMixin {

    @WrapOperation(
        method = "tickNonPassenger",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V"
        )
    )
    private void papi$cullOccludedClientEntityTick(Entity entity, Operation<Void> original) {
        ClientLevel self = (ClientLevel) (Object) this;
        long gameTime = self.getGameTime();
        if (!PapiConfig.clientTickCulling()
                || entity == null
                || entity.tickCount < 10
                || entity.isPassenger()
                || entity.isVehicle()
                || entity.isMultipartEntity()) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }
        EntityTypeRendererCache typeCache = (EntityTypeRendererCache) entity.getType();
        if (!typeCache.papi$isTickCullAllowed(entity)) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?, ?> cachedRenderer = typeCache.papi$getRenderer();
        if (cachedRenderer == null && mc != null && mc.getEntityRenderDispatcher() != null) {
            cachedRenderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        }
        if (cachedRenderer instanceof PapiEntityCullingBox bp && !bp.papi$isAffectedByCulling(entity)) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }
        if (entity instanceof LivingEntity living && (living.hurtTime > 0 || living.deathTime > 0 || living.isDeadOrDying())) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }
        if (entity instanceof Leashable leashable && leashable.getLeashHolder() != null) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }
        if (mc != null && (entity == mc.getCameraEntity() || entity == mc.player || mc.shouldEntityAppearGlowing(entity))) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }

        double minDist = PapiConfig.tickCullingMinDistance();
        if (!PapiOcclusionEngine.isEntityOccludedForClientTick(self, entity, minDist * minDist)) {
            PapiOcclusionEngine.recordClientEntityTick(gameTime, false);
            original.call(entity);
            return;
        }

        PapiOcclusionEngine.recordClientEntityTick(gameTime, true);
        if (entity instanceof PapiClientLivingTick livingTick) {
            livingTick.papi$clientMinimalLivingStep();
        }
    }
}
