package net.ashpapi.papioptimization.mixin.particle;

import java.util.Map;
import java.util.Queue;
import net.ashpapi.papioptimization.access.PapiParticleEngineAccess;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ParticleEngine.class, priority = 1500)
public abstract class ParticleEngineMixin implements PapiParticleEngineAccess {

    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Shadow
    @Final
    private Queue<Particle> particlesToAdd;

    @Override
    public boolean papi$isSingleQuadsSaturated() {
        ParticleGroup<?> group = this.particles.get(ParticleRenderType.SINGLE_QUADS);
        int active = group != null ? group.size() : 0;
        return (active + this.particlesToAdd.size()) >= 12288;
    }
}
