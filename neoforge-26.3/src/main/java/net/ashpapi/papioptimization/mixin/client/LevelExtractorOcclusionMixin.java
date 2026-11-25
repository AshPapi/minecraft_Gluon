package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LevelExtractor.class, priority = 1500)
public abstract class LevelExtractorOcclusionMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @WrapMethod(method = "extract")
    private void papi$wrapOcclusionFrame(
            DeltaTracker deltaTracker,
            Camera camera,
            float partialTick,
            Operation<Void> original
    ) {
        PapiOcclusionEngine.beginFrame(this.level, camera);
        try {
            original.call(deltaTracker, camera, partialTick);
        } finally {
            PapiOcclusionEngine.endFrame();
        }
    }

    @WrapOperation(
            method = "extract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;getCullFrustum()Lnet/minecraft/client/renderer/culling/Frustum;"
            )
    )
    private Frustum papi$captureExtractPassFrustum(Camera instance, Operation<Frustum> original) {
        Frustum frustum = original.call(instance);
        PapiOcclusionEngine.setCurrentPassFrustum(instance, frustum);
        return frustum;
    }
}
