package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.core.ArmorStandSleepHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ArmorStand.class, priority = 1500)
public abstract class ArmorStandOptimizationMixin extends LivingEntity implements ArmorStandSleepHolder {

    @Shadow
    public abstract boolean isMarker();

    @Unique
    private volatile int papi$sleepTicks = 0;

    @Unique
    private double papi$sleepX = Double.NaN;

    @Unique
    private double papi$sleepY = Double.NaN;

    @Unique
    private double papi$sleepZ = Double.NaN;

    protected ArmorStandOptimizationMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public int papi$getSleepTicks() {
        return this.papi$sleepTicks;
    }

    @Override
    public void papi$setSleepTicks(int ticks) {
        this.papi$sleepTicks = ticks;
    }

    @Override
    public void papi$wakeUp() {
        this.papi$sleepTicks = 0;
    }

    @Override
    public boolean papi$isSleeping() {
        return this.papi$sleepTicks > 0;
    }

    @Override
    public boolean papi$hasMovedSinceSleep(double x, double y, double z) {
        return Math.abs(x - this.papi$sleepX) > 1.0E-4
                || Math.abs(y - this.papi$sleepY) > 1.0E-4
                || Math.abs(z - this.papi$sleepZ) > 1.0E-4;
    }

    @Override
    public void papi$recordSleepPos(double x, double y, double z) {
        this.papi$sleepX = x;
        this.papi$sleepY = y;
        this.papi$sleepZ = z;
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void papi$wakeUpOnHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        this.papi$wakeUp();
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void papi$wakeUpOnInteract(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        this.papi$wakeUp();
    }

    @Inject(method = "thunderHit", at = @At("HEAD"))
    private void papi$wakeUpOnLightning(ServerLevel level, LightningBolt lightningBolt, CallbackInfo ci) {
        this.papi$wakeUp();
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void papi$cancelPushEntities(CallbackInfo ci) {
        if (this.isMarker() || this.papi$isSleeping()) {
            ci.cancel();
        }
    }
}
