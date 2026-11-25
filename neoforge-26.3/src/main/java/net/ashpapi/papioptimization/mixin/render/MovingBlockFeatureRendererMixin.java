package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MovingBlockFeatureRenderer.class, priority = 1500)
public abstract class MovingBlockFeatureRendererMixin {

    @Unique
    private static final float PAPI$AO_MAX_DIST_SQ = 22.0F * 22.0F;

    @Shadow
    @Final
    private PoseStack poseStack;

    @Unique
    private boolean papi$aoEnabled;

    @Unique
    private BlockColors papi$currentBlockColors;

    @Unique
    private BlockColors papi$flatRendererColors;

    @Unique
    private ModelBlockRenderer papi$flatRenderer;

    @Inject(method = "buildGroup", at = @At("HEAD"))
    private void papi$captureFrameContext(
            FeatureFrameContext context,
            List<MovingBlockFeatureRenderer.Submit> submits,
            CallbackInfo ci
    ) {
        this.papi$aoEnabled = context.options().ambientOcclusion;
        this.papi$currentBlockColors = context.blockColors();
    }

    @Inject(method = "buildGroup", at = @At("RETURN"))
    private void papi$clearFrameContext(
            FeatureFrameContext context,
            List<MovingBlockFeatureRenderer.Submit> submits,
            CallbackInfo ci
    ) {
        this.papi$currentBlockColors = null;
    }

    @WrapOperation(
            method = "buildGroup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"
            )
    )
    private void papi$skipDistantMovingBlockAo(
            ModelBlockRenderer instance,
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter getter,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            Operation<Void> original
    ) {
        ModelBlockRenderer target = instance;
        if (this.papi$aoEnabled) {
            Matrix4f pose = this.poseStack.last().pose();
            float dx = Math.fma(pose.m00(), 0.5F, Math.fma(pose.m10(), 0.5F, Math.fma(pose.m20(), 0.5F, pose.m30())));
            float dy = Math.fma(pose.m01(), 0.5F, Math.fma(pose.m11(), 0.5F, Math.fma(pose.m21(), 0.5F, pose.m31())));
            float dz = Math.fma(pose.m02(), 0.5F, Math.fma(pose.m12(), 0.5F, Math.fma(pose.m22(), 0.5F, pose.m32())));
            if (dx * dx + dy * dy + dz * dz > PAPI$AO_MAX_DIST_SQ) {
                BlockColors colors = this.papi$currentBlockColors;
                if (colors != null) {
                    if (this.papi$flatRenderer == null || this.papi$flatRendererColors != colors) {
                        this.papi$flatRenderer = new ModelBlockRenderer(false, false, colors);
                        this.papi$flatRendererColors = colors;
                    }
                    target = this.papi$flatRenderer;
                }
            }
        }
        original.call(target, output, x, y, z, getter, pos, state, model, seed);
    }
}
