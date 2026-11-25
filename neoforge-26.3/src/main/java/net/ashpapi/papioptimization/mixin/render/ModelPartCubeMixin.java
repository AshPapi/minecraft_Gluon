package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ModelPart.Cube.class, priority = 1500)
public abstract class ModelPartCubeMixin {

    @Shadow
    @Final
    public ModelPart.Polygon[] polygons;

    @Unique
    private static final ThreadLocal<Vector3f> PAPI$NORMAL = ThreadLocal.withInitial(Vector3f::new);

    @WrapMethod(method = "compile")
    private void papi$fastCompile(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color,
            Operation<Void> original
    ) {
        ModelPart.Polygon[] polys = this.polygons;
        int polyCount = polys.length;
        if (polyCount == 0) {
            return;
        }
        Matrix4f m = pose.pose();
        float m00 = m.m00();
        float m01 = m.m01();
        float m02 = m.m02();
        float m10 = m.m10();
        float m11 = m.m11();
        float m12 = m.m12();
        float m20 = m.m20();
        float m21 = m.m21();
        float m22 = m.m22();
        float m30 = m.m30();
        float m31 = m.m31();
        float m32 = m.m32();
        Vector3f norm = PAPI$NORMAL.get();
        for (int p = 0; p < polyCount; p++) {
            ModelPart.Polygon poly = polys[p];
            pose.transformNormal(poly.normal(), norm);
            float nx = norm.x;
            float ny = norm.y;
            float nz = norm.z;
            ModelPart.Vertex[] verts = poly.vertices();
            for (int v = 0, vLen = verts.length; v < vLen; v++) {
                ModelPart.Vertex vert = verts[v];
                float wx = vert.worldX();
                float wy = vert.worldY();
                float wz = vert.worldZ();
                float tx = Math.fma(m00, wx, Math.fma(m10, wy, Math.fma(m20, wz, m30)));
                float ty = Math.fma(m01, wx, Math.fma(m11, wy, Math.fma(m21, wz, m31)));
                float tz = Math.fma(m02, wx, Math.fma(m12, wy, Math.fma(m22, wz, m32)));
                consumer.addVertex(tx, ty, tz, color, vert.u(), vert.v(), packedOverlay, packedLight, nx, ny, nz);
            }
        }
    }
}
