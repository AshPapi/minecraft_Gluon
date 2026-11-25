package net.ashpapi.papioptimization.mixin.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = QuadParticleRenderState.class, priority = 1500)
public abstract class QuadParticleRenderStateMixin {

    @Inject(method = "renderRotatedQuad", at = @At("HEAD"), cancellable = true)
    private void papi$zeroAllocRenderRotatedQuad(
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            float qx,
            float qy,
            float qz,
            float qw,
            float size,
            float u0,
            float u1,
            float v0,
            float v1,
            int color,
            int light,
            CallbackInfo ci
    ) {
        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float ww = qw * qw;
        float normSq = xx + yy + zz + ww;
        float s = normSq > 1.0E-12F ? (size / normSq) : 0.0F;

        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float xw = qx * qw;
        float yw = qy * qw;
        float zw = qz * qw;

        float ux = (ww + xx - yy - zz) * s;
        float uy = 2.0F * (xy + zw) * s;
        float uz = 2.0F * (xz - yw) * s;

        float vx = 2.0F * (xy - zw) * s;
        float vy = (ww - xx + yy - zz) * s;
        float vz = 2.0F * (yz + xw) * s;

        consumer.addVertex(x + ux - vx, y + uy - vy, z + uz - vz).setUv(u1, v1).setColor(color).setLight(light);
        consumer.addVertex(x + ux + vx, y + uy + vy, z + uz + vz).setUv(u1, v0).setColor(color).setLight(light);
        consumer.addVertex(x - ux + vx, y - uy + vy, z - uz + vz).setUv(u0, v0).setColor(color).setLight(light);
        consumer.addVertex(x - ux - vx, y - uy - vy, z - uz - vz).setUv(u0, v1).setColor(color).setLight(light);
        ci.cancel();
    }
}
