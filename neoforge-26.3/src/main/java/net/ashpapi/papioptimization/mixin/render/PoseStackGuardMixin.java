package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.mixin.client.PoseStackAccessor;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = {LivingEntityRenderer.class, AvatarRenderer.class}, priority = 1500)
public abstract class PoseStackGuardMixin {

    @WrapOperation(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
            ordinal = 0
        )
    )
    private Event papi$guardPreEventPoseStack(
            IEventBus bus,
            Event event,
            Operation<Event> original,
            @Local(argsOnly = true) PoseStack poseStack
    ) {
        if (!PapiConfig.modernFix() || !(poseStack instanceof PoseStackAccessor accessor)) {
            return original.call(bus, event);
        }
        int expectedIndex = accessor.papi$getLastIndex();
        Event result = original.call(bus, event);
        boolean canceled = (result instanceof ICancellableEvent rc && rc.isCanceled())
                || (event instanceof ICancellableEvent ec && ec.isCanceled());
        if (canceled) {
            int current = accessor.papi$getLastIndex();
            while (current > expectedIndex) {
                poseStack.popPose();
                current--;
            }
            while (current < expectedIndex) {
                poseStack.pushPose();
                current++;
            }
        }
        return result;
    }
}
