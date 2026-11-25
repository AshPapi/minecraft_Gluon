package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.access.PapiParticleEngineAccess;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelParticleSpawnMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyConstant(method = "doAddParticle", constant = @Constant(doubleValue = 1024.0D))
    private double papi$configuredParticleSpawnDistanceSqr(double vanillaDistSqr) {
        return PapiConfig.particleLodDistanceSqr();
    }

    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void papi$filterDecorativeParticleSpawn(
            ParticleOptions options,
            boolean overrideLimiter,
            boolean alwaysShow,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            CallbackInfo ci
    ) {
        if (overrideLimiter || alwaysShow) {
            return;
        }
        ClientLevel self = (ClientLevel) (Object) this;
        if (!PapiOcclusionEngine.isWithinParticleLod(self, x, y, z)) {
            ci.cancel();
            return;
        }
        if (this.minecraft != null
                && this.minecraft.particleEngine instanceof PapiParticleEngineAccess access
                && access.papi$isSingleQuadsSaturated()
                && PapiOcclusionEngine.isPointOutsideLastFrustum(x, y, z, 2.0)) {
            ci.cancel();
        }
    }
}
