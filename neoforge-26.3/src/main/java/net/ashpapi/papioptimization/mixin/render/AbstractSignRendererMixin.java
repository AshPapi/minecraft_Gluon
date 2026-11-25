package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import java.util.function.Function;
import net.ashpapi.papioptimization.access.PapiSignTextCache;
import net.ashpapi.papioptimization.client.PapiSignRenderCache;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = AbstractSignRenderer.class, priority = 1500)
public abstract class AbstractSignRendererMixin {

    @Unique
    private static boolean papi$hasVisibleMessage(@Nullable SignText signText, boolean filtered) {
        if (signText == null) {
            return false;
        }
        return signText instanceof PapiSignTextCache cache
                ? cache.papi$hasVisibleMessage(filtered)
                : PapiSignTextCache.papi$computeVisibleMessage(signText, filtered);
    }

    @Unique
    private static boolean papi$isFaceVisible(@Nullable Transformation transform, BlockPos pos, Vec3 cameraPos) {
        if (transform == null) {
            return true;
        }
        Matrix4fc m = transform.getMatrix();
        float dx = (float) (cameraPos.x - ((double) pos.getX() + (double) m.m30()));
        float dy = (float) (cameraPos.y - ((double) pos.getY() + (double) m.m31()));
        float dz = (float) (cameraPos.z - ((double) pos.getZ() + (double) m.m32()));
        return Math.fma(m.m20(), dx, Math.fma(m.m21(), dy, m.m22() * dz)) > 0.0F;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("RETURN")
    )
    private void papi$cullSignText(
            SignBlockEntity sign,
            SignRenderState state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        boolean filtered = mc != null && mc.isTextFilteringEnabled();
        if (state.frontText != null && !papi$hasVisibleMessage(state.frontText, filtered)) {
            state.frontText = null;
        }
        if (state.backText != null && !papi$hasVisibleMessage(state.backText, filtered)) {
            state.backText = null;
        }
        if ((state.frontText == null && state.backText == null) || cameraPos == null || PapiOcclusionEngine.isShadowPass()) {
            return;
        }
        BlockPos pos = sign.getBlockPos();
        double cx = (double) pos.getX() + 0.5;
        double cy = (double) pos.getY() + 0.5;
        double cz = (double) pos.getZ() + 0.5;
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - cy;
        double dz = cameraPos.z - cz;

        boolean scoping = mc != null && mc.player != null && mc.options.getCameraType().isFirstPerson() && mc.player.isScoping();
        if (!scoping && mc != null && mc.options != null) {
            int fov = mc.options.fov().get();
            double limitSqr = (fov > 0 && fov < 70) ? 4096.0 * (4900.0 / (double) (fov * fov)) : 4096.0;
            if (dx * dx + dy * dy + dz * dz > limitSqr) {
                state.frontText = null;
                state.backText = null;
                return;
            }
        }

        if (state.transformations != null && state.transformations != SignRenderState.SignTransformations.IDENTITY) {
            if (state.frontText != null && !papi$isFaceVisible(state.transformations.frontText(), pos, cameraPos)) {
                state.frontText = null;
            }
            if (state.backText != null && !papi$isFaceVisible(state.transformations.backText(), pos, cameraPos)) {
                state.backText = null;
            }
        }
    }

    @WrapOperation(
            method = "submitSignText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/SignText;getRenderMessages(ZLjava/util/function/Function;)[Lnet/minecraft/util/FormattedCharSequence;"
            )
    )
    private FormattedCharSequence[] papi$wrapSignRenderMessages(
            SignText signText,
            boolean filtered,
            Function<Component, FormattedCharSequence> splitter,
            Operation<FormattedCharSequence[]> original
    ) {
        FormattedCharSequence[] raw = original.call(signText, filtered, splitter);
        if (raw != null && signText instanceof PapiSignTextCache cache) {
            return PapiSignRenderCache.getOrWrap(cache, raw, filtered);
        }
        return raw;
    }

    @WrapOperation(
            method = "submitSignText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/util/FormattedCharSequence;)I"
            )
    )
    private int papi$fastCachedSignLineWidth(
            Font font,
            FormattedCharSequence sequence,
            Operation<Integer> original
    ) {
        if (sequence == null || sequence == FormattedCharSequence.EMPTY) {
            return 0;
        }
        if (sequence instanceof PapiSignRenderCache.PreparedLine line) {
            return line.getOrComputeWidth(font, original);
        }
        return original.call(font, sequence);
    }

    @WrapOperation(
            method = "submitSignText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
            )
    )
    private void papi$skipEmptySignLineSubmit(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            float x,
            float y,
            FormattedCharSequence sequence,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int lightCoords,
            int color,
            int backgroundColor,
            int outlineColor,
            Operation<Void> original
    ) {
        if ((backgroundColor & 0xFF000000) == 0) {
            if (sequence == null || sequence == FormattedCharSequence.EMPTY) {
                return;
            }
            if (sequence instanceof PapiSignRenderCache.PreparedLine line && line.isKnownEmpty()) {
                return;
            }
        }
        original.call(collector, poseStack, x, y, sequence, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @WrapMethod(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
    )
    private void papi$submitVisibleSignSides(
            SignRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            Operation<Void> original
    ) {
        SignText origFront = state.frontText;
        SignText origBack = state.backText;
        if (origFront == null && origBack == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean filtered = mc != null && mc.isTextFilteringEnabled();
        SignText useFront = papi$hasVisibleMessage(origFront, filtered) ? origFront : null;
        SignText useBack = papi$hasVisibleMessage(origBack, filtered) ? origBack : null;

        if ((useFront != null || useBack != null)
                && !PapiOcclusionEngine.isShadowPass()
                && state.transformations != null
                && state.transformations != SignRenderState.SignTransformations.IDENTITY
                && state.blockPos != null
                && cameraRenderState != null
                && cameraRenderState.pos != null) {
            if (useFront != null && !papi$isFaceVisible(state.transformations.frontText(), state.blockPos, cameraRenderState.pos)) {
                useFront = null;
            }
            if (useBack != null && !papi$isFaceVisible(state.transformations.backText(), state.blockPos, cameraRenderState.pos)) {
                useBack = null;
            }
        }

        if (useFront == null && useBack == null) {
            return;
        }
        state.frontText = useFront;
        state.backText = useBack;
        try {
            original.call(state, poseStack, submitNodeCollector, cameraRenderState);
        } finally {
            state.frontText = origFront;
            state.backText = origBack;
        }
    }
}
