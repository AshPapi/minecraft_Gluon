package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.BooleanSupplier;
import net.ashpapi.papioptimization.server.PapiAutosaveState;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1500)
public abstract class MinecraftServerMsptMixin extends ReentrantBlockableEventLoop<TickTask> {

    @Unique
    private long papi$tickStartNanos;

    @Unique
    private volatile boolean papi$inStopSaving;

    @Unique
    private volatile boolean papi$finishedSaving;

    protected MinecraftServerMsptMixin(String name) {
        super(name, false);
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void papi$resetMsptOnServerStart(CallbackInfo ci) {
        this.papi$inStopSaving = false;
        this.papi$finishedSaving = false;
        PapiMsptController.reset();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void papi$beginStopServerSaving(CallbackInfo ci) {
        this.papi$finishedSaving = false;
        this.papi$inStopSaving = true;
    }

    @Inject(
            method = "stopServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z",
                    shift = At.Shift.AFTER
            )
    )
    private void papi$markFinishedSavingWorlds(CallbackInfo ci) {
        this.papi$finishedSaving = true;
        this.papi$inStopSaving = false;
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void papi$finishStopServerSaving(CallbackInfo ci) {
        this.papi$finishedSaving = true;
        this.papi$inStopSaving = false;
    }

    @WrapMethod(method = "scheduleExecutables")
    private boolean papi$keepScheduleExecutablesUntilSaved(Operation<Boolean> original) {
        if (original.call()) {
            return true;
        }
        return this.papi$inStopSaving && !this.papi$finishedSaving && super.scheduleExecutables();
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void papi$markServerTickStart(BooleanSupplier haveTime, CallbackInfo ci) {
        this.papi$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void papi$recordServerTickDuration(BooleanSupplier haveTime, CallbackInfo ci) {
        long start = this.papi$tickStartNanos;
        if (start != 0L) {
            PapiMsptController.recordTickNanos(System.nanoTime() - start);
        }
    }

    @WrapMethod(method = "autoSave")
    private void papi$markAutoSaveContext(Operation<Void> original) {
        boolean prev = PapiAutosaveState.isInAutoSave();
        PapiAutosaveState.setInAutoSave(true);
        try {
            original.call();
        } finally {
            PapiAutosaveState.setInAutoSave(prev);
        }
    }
}
