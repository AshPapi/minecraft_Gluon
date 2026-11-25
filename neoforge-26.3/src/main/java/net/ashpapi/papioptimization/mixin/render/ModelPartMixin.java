package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ModelPart.class, priority = 1500)
public abstract class ModelPartMixin {

    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Shadow
    public boolean visible;

    @Shadow
    public boolean skipDraw;

    @Shadow
    public abstract void translateAndRotate(PoseStack poseStack);

    @Shadow
    private void compile(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {
    }

    @WrapMethod(method = "compile")
    private void papi$fastCompile(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color,
            Operation<Void> original
    ) {
        List<ModelPart.Cube> cubeList = this.cubes;
        for (int i = 0, n = cubeList.size(); i < n; i++) {
            cubeList.get(i).compile(pose, consumer, packedLight, packedOverlay, color);
        }
    }

    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    private void papi$fastRender(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color,
            Operation<Void> original
    ) {
        if (!this.visible) {
            return;
        }
        boolean hasCubes = !this.cubes.isEmpty();
        boolean hasChildren = !this.children.isEmpty();
        if (!hasCubes && !hasChildren) {
            return;
        }
        poseStack.pushPose();
        this.translateAndRotate(poseStack);
        if (!this.skipDraw && hasCubes) {
            this.compile(poseStack.last(), consumer, packedLight, packedOverlay, color);
        }
        if (hasChildren) {
            for (ModelPart child : this.children.values()) {
                child.render(poseStack, consumer, packedLight, packedOverlay, color);
            }
        }
        poseStack.popPose();
    }
}
