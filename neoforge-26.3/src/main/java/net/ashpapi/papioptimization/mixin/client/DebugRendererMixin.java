package net.ashpapi.papioptimization.mixin.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = DebugRenderer.class, priority = 1500)
public abstract class DebugRendererMixin {

    @Shadow
    @Final
    private List<DebugRenderer.SimpleDebugRenderer> renderers;

    @Shadow
    private long lastDebugEntriesVersion;

    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void papi$fastGizmosExit(Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (!this.renderers.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.debugEntries == null) {
            return;
        }
        if (mc.debugEntries.getCurrentlyEnabledVersion() == this.lastDebugEntriesVersion) {
            ci.cancel();
        }
    }
}
