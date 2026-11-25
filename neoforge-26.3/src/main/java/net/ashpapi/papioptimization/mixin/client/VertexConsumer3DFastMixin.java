package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ashpapi.papioptimization.client.PapiVertexScratch;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = VertexConsumer.class, priority = 1500)
public interface VertexConsumer3DFastMixin {

    @Shadow
    VertexConsumer addVertex(float x, float y, float z);

    @Shadow
    VertexConsumer setNormal(float x, float y, float z);

    @WrapMethod(method = "addVertex(Lorg/joml/Matrix4fc;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    default VertexConsumer papi$fastAddVertex3D(
            Matrix4fc pose,
            float x,
            float y,
            float z,
            Operation<VertexConsumer> original
    ) {
        float tx = Math.fma(pose.m00(), x, Math.fma(pose.m10(), y, Math.fma(pose.m20(), z, pose.m30())));
        float ty = Math.fma(pose.m01(), x, Math.fma(pose.m11(), y, Math.fma(pose.m21(), z, pose.m31())));
        float tz = Math.fma(pose.m02(), x, Math.fma(pose.m12(), y, Math.fma(pose.m22(), z, pose.m32())));
        return this.addVertex(tx, ty, tz);
    }

    @WrapMethod(method = "setNormal(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    default VertexConsumer papi$fastSetNormal3D(
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            Operation<VertexConsumer> original
    ) {
        Vector3f normal = PapiVertexScratch.NORMAL.get();
        pose.transformNormal(x, y, z, normal);
        return this.setNormal(normal.x, normal.y, normal.z);
    }
}
