package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = VertexConsumer.class, priority = 1500)
public interface VertexConsumerFastMixin {

    @Shadow
    VertexConsumer addVertex(float x, float y, float z);

    @WrapMethod(method = "addVertexWith2DPose")
    default VertexConsumer papi$fastAddVertexWith2DPose(
            Matrix3x2fc pose,
            float x,
            float y,
            Operation<VertexConsumer> original
    ) {
        float tx = Math.fma(pose.m00(), x, Math.fma(pose.m10(), y, pose.m20()));
        float ty = Math.fma(pose.m01(), x, Math.fma(pose.m11(), y, pose.m21()));
        return this.addVertex(tx, ty, 0.0F);
    }
}
