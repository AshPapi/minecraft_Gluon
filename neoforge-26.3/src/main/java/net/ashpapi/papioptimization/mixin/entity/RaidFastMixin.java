package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Raid.class, priority = 1500)
public abstract class RaidFastMixin {

    @Shadow @Final private ServerBossEvent raidEvent;
    @Shadow private float totalHealth;

    @Shadow public abstract float getHealthOfLivingRaiders();
    @Shadow public abstract boolean isStopped();

    @Unique
    private boolean papi$barDirty;

    @Inject(method = "updateBossbar", at = @At("HEAD"), cancellable = true)
    private void papi$deferUpdateBossbar(CallbackInfo ci) {
        this.papi$barDirty = true;
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void papi$flushBossbarBeforeTick(ServerLevel level, CallbackInfo ci) {
        if (this.papi$barDirty && !this.isStopped()) {
            this.papi$flushBossbarNow();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void papi$flushBossbarAfterTick(ServerLevel level, CallbackInfo ci) {
        if (this.papi$barDirty && !this.isStopped()) {
            this.papi$flushBossbarNow();
        }
    }

    @Unique
    private void papi$flushBossbarNow() {
        this.papi$barDirty = false;
        float progress = this.totalHealth <= 0.0F
                ? 0.0F
                : Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F);
        this.raidEvent.setProgress(progress);
    }
}
