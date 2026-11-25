package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.ashpapi.papioptimization.client.GpuMemoryLeakHandler;
import net.ashpapi.papioptimization.client.PapiClientLevelLeakCleaner;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Minecraft.class, priority = 1500)
public abstract class MemoryLeakFixMixin {

    @Shadow
    public @Nullable ClientLevel level;

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void papi$onDisconnectCleanup(Screen screen, boolean transfer, boolean isDisconnectingFromWorld, CallbackInfo ci) {
        GpuMemoryLeakHandler.getInstance().onDisconnect();
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"))
    private void papi$onSetLevelHead(ClientLevel newLevel, CallbackInfo ci, @Share("oldLevel") LocalRef<ClientLevel> oldLevelRef) {
        oldLevelRef.set(this.level);
        GpuMemoryLeakHandler.getInstance().onLevelChange();
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("TAIL"))
    private void papi$onSetLevelTail(ClientLevel newLevel, CallbackInfo ci, @Share("oldLevel") LocalRef<ClientLevel> oldLevelRef) {
        ClientLevel oldLevel = oldLevelRef.get();
        if (oldLevel != null && oldLevel != this.level && PapiConfig.modernFix() && oldLevel instanceof PapiClientLevelLeakCleaner cleaner) {
            cleaner.papi$clearRetainedLevelReferences();
        }
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void papi$onClearClientLevelHead(Screen screen, CallbackInfo ci, @Share("oldLevel") LocalRef<ClientLevel> oldLevelRef) {
        oldLevelRef.set(this.level);
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void papi$onClearClientLevelCleanup(Screen screen, CallbackInfo ci, @Share("oldLevel") LocalRef<ClientLevel> oldLevelRef) {
        ClientLevel oldLevel = oldLevelRef.get();
        if (oldLevel != null && oldLevel != this.level && PapiConfig.modernFix() && oldLevel instanceof PapiClientLevelLeakCleaner cleaner) {
            cleaner.papi$clearRetainedLevelReferences();
        }
        GpuMemoryLeakHandler.getInstance().onDisconnect();
    }
}
