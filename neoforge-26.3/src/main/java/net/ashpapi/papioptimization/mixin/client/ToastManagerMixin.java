package net.ashpapi.papioptimization.mixin.client;

import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ToastManager.class, priority = 1500)
public abstract class ToastManagerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private List<?> visibleToasts;

    @Shadow
    @Final
    private Deque<Toast> queued;

    @Shadow
    @Final
    private Set<SoundEvent> playedToastSounds;

    @Unique
    private boolean papi$hasNowPlayingToast;

    @Inject(method = {"initializeMusicToast", "setMusicToastDisplayState"}, at = @At("RETURN"))
    private void papi$trackNowPlayingToastState(MusicToastDisplayState state, CallbackInfo ci) {
        this.papi$hasNowPlayingToast = state != null && state != MusicToastDisplayState.NEVER;
    }

    @Unique
    private boolean papi$isMusicToastActiveForCurrentUi() {
        if (!this.papi$hasNowPlayingToast) {
            return false;
        }
        if (this.minecraft.options.musicToast().get().renderToast()) {
            return true;
        }
        return this.minecraft.gui.screen() instanceof PauseScreen ps && ps.rendersNowPlayingToast();
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void papi$skipIdleToastUpdate(CallbackInfo ci) {
        if (this.visibleToasts.isEmpty() && this.queued.isEmpty() && !papi$isMusicToastActiveForCurrentUi()) {
            if (!this.playedToastSounds.isEmpty()) {
                this.playedToastSounds.clear();
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void papi$skipIdleToastRender(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.minecraft.gui.hud.isHidden()) {
            ci.cancel();
            return;
        }
        if (this.visibleToasts.isEmpty() && !papi$isMusicToastActiveForCurrentUi()) {
            ci.cancel();
        }
    }
}
