package net.ashpapi.papioptimization.mixin.particle;

import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ParticleEngine.class, priority = 1500)
public abstract class ParticleEngineExtractFastMixin {

    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Shadow
    @Final
    private List<ParticleRenderType> particleRenderOrder;

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyParticleExtract(
            ParticlesRenderState state,
            Frustum frustum,
            Camera camera,
            float partialTick,
            CallbackInfo ci
    ) {
        if (this.particles.isEmpty()) {
            ci.cancel();
            return;
        }
        List<ParticleRenderType> order = this.particleRenderOrder;
        for (int i = 0, n = order.size(); i < n; i++) {
            ParticleGroup<?> group = this.particles.get(order.get(i));
            if (group != null && !group.isEmpty()) {
                return;
            }
        }
        ci.cancel();
    }
}
