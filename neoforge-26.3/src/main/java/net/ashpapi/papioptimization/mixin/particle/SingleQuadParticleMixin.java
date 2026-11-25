package net.ashpapi.papioptimization.mixin.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SingleQuadParticle.class, priority = 1500)
public abstract class SingleQuadParticleMixin extends Particle {

    @Shadow
    protected float roll;

    @Shadow
    protected float oRoll;

    @Shadow
    public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();

    @Shadow
    protected abstract void extractRotatedQuad(
            QuadParticleRenderState state,
            Camera camera,
            Quaternionf rotation,
            float partialTick
    );

    @Unique
    private static final Quaternionf PAPI$RENDER_QUAT = new Quaternionf();

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void papi$fastExtract(
            QuadParticleRenderState state,
            Camera camera,
            float partialTick,
            CallbackInfo ci
    ) {
        Quaternionf q = RenderSystem.isOnRenderThread() ? PAPI$RENDER_QUAT : new Quaternionf();
        SingleQuadParticle.FacingCameraMode mode = this.getFacingCameraMode();
        if (mode == SingleQuadParticle.FacingCameraMode.LOOKAT_XYZ) {
            q.set(camera.rotation());
        } else if (mode == SingleQuadParticle.FacingCameraMode.LOOKAT_Y) {
            Quaternionf cr = camera.rotation();
            q.set(0.0F, cr.y, 0.0F, cr.w);
        } else {
            q.identity();
            mode.setRotation(q, camera, partialTick);
        }
        if (this.roll != 0.0F) {
            q.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
        }
        this.extractRotatedQuad(state, camera, q, partialTick);
        ci.cancel();
    }
}
