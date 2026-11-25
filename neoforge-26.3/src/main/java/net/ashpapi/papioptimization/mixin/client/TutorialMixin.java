package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.client.tutorial.TutorialStepInstance;
import net.minecraft.client.tutorial.TutorialSteps;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Tutorial.class, priority = 1500)
public abstract class TutorialMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private @Nullable TutorialStepInstance instance;

    @Shadow
    public abstract void stop();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$skipCompletedTutorialTick(CallbackInfo ci) {
        if (!this.minecraft.isDemo() && this.minecraft.options.tutorialStep == TutorialSteps.NONE) {
            if (this.minecraft.level == null && this.instance != null) {
                this.stop();
            }
            ci.cancel();
        }
    }
}
