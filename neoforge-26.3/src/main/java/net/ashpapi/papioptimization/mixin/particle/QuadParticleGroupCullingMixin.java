package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = QuadParticleGroup.class, priority = 1500)
public abstract class QuadParticleGroupCullingMixin {

    @Unique
    private boolean papi$frameShadow;

    @Unique
    private @Nullable ClientLevel papi$frameLevel;

    @Unique
    private @Nullable Vec3 papi$frameCamPos;

    @Unique
    private double papi$maxDirectRenderDistSqr;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void papi$beginParticleGroupExtract(
            Frustum frustum,
            Camera camera,
            float partialTick,
            CallbackInfoReturnable<ParticleGroupRenderState> cir
    ) {
        this.papi$frameShadow = PapiOcclusionEngine.shouldSkipParticleCulling(frustum);
        Minecraft mc = Minecraft.getInstance();
        this.papi$frameLevel = mc != null ? mc.level : null;
        this.papi$frameCamPos = camera != null ? camera.position() : null;
        double lodSqr = PapiConfig.particleLodDistanceSqr();
        int renderChunks = (mc != null && mc.options != null) ? mc.options.getEffectiveRenderDistance() : 12;
        double chunkLim = renderChunks * 16.0;
        this.papi$maxDirectRenderDistSqr = Math.max(lodSqr, chunkLim * chunkLim);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void papi$endParticleGroupExtract(
            Frustum frustum,
            Camera camera,
            float partialTick,
            CallbackInfoReturnable<ParticleGroupRenderState> cir
    ) {
        this.papi$frameLevel = null;
        this.papi$frameCamPos = null;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"
        )
    )
    private boolean papi$cullOccludedParticle(
            Frustum frustum,
            double x,
            double y,
            double z,
            Operation<Boolean> original,
            @Local SingleQuadParticle particle,
            @Local(argsOnly = true) float partialTick
    ) {
        boolean shadow = this.papi$frameShadow;
        Vec3 camPos = this.papi$frameCamPos;
        if (!shadow && camPos != null) {
            double dx = x - camPos.x;
            double dy = y - camPos.y;
            double dz = z - camPos.z;
            if (dx * dx + dy * dy + dz * dz > this.papi$maxDirectRenderDistSqr) {
                PapiOcclusionEngine.recordFrustumCulledParticle();
                return false;
            }
        }
        if (original.call(frustum, x, y, z)) {
            if (shadow) {
                return true;
            }
            ClientLevel level = this.papi$frameLevel;
            if (level == null || camPos == null) {
                return true;
            }
            float quadSize = particle != null ? particle.getQuadSize(partialTick) : 0.0F;
            if (quadSize > 3.0F) {
                return true;
            }
            if (quadSize > 0.5F) {
                return !PapiOcclusionEngine.isLargeParticleOccluded(level, camPos, x, y, z, Math.min(quadSize, 8.0F));
            }
            return !PapiOcclusionEngine.isParticleOccluded(level, camPos, x, y, z);
        }

        float quadSize = (!shadow && particle != null) ? particle.getQuadSize(partialTick) : 0.0F;
        if (quadSize > 0.5F) {
            double r = Math.min(quadSize, 8.0F);
            if (PapiOcclusionEngine.isBoxInFrustum(frustum, x - r, y - r, z - r, x + r, y + r, z + r)) {
                if (shadow || quadSize > 3.0F) {
                    return true;
                }
                ClientLevel level = this.papi$frameLevel;
                if (level == null || camPos == null) {
                    return true;
                }
                return !PapiOcclusionEngine.isLargeParticleOccluded(level, camPos, x, y, z, r);
            }
        }

        if (!shadow) {
            PapiOcclusionEngine.recordFrustumCulledParticle();
        }
        return false;
    }
}
