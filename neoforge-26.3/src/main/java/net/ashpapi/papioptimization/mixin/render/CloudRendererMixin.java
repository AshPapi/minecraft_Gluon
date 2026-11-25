package net.ashpapi.papioptimization.mixin.render;

import java.nio.ByteBuffer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = CloudRenderer.class, priority = 1500)
public abstract class CloudRendererMixin {

    @Shadow
    private boolean needsRebuild;

    @Unique
    private boolean papi$nearCloudLayer = true;

    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"))
    private void papi$trackNearCloudBoundary(
            int color,
            CloudStatus cloudStatus,
            float cloudHeight,
            int cloudRange,
            Vec3 cameraPos,
            long ticks,
            float partialTick,
            CallbackInfo ci
    ) {
        if (cameraPos == null) {
            return;
        }
        double relY = (double) cloudHeight - cameraPos.y;
        boolean near = relY >= -4.5 && relY <= 0.5;
        if (near != this.papi$nearCloudLayer) {
            this.papi$nearCloudLayer = near;
            this.needsRebuild = true;
        }
    }

    @Inject(
        method = "buildExtrudedCell",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"
        ),
        cancellable = true
    )
    private void papi$skipInvisibleInsideCloudFaces(
            @Coerce Enum<?> relativeCameraPos,
            ByteBuffer buffer,
            int cellX,
            int cellZ,
            long cellData,
            CallbackInfo ci
    ) {
        if (!this.papi$nearCloudLayer && relativeCameraPos.ordinal() != 1) {
            ci.cancel();
        }
    }
}
