package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.ashpapi.papioptimization.ai.PapiEquipmentTracker;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityFastTickMixin extends Entity {

    @Shadow @Final protected EntityEquipment equipment;
    @Shadow protected int fallFlyTicks;
    @Shadow private Optional<BlockPos> lastClimbablePos;

    public LivingEntityFastTickMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract boolean isDeadOrDying();
    @Shadow public abstract boolean isFallFlying();

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void papi$skipDeadOrSpectatorPush(CallbackInfo ci) {
        if (this.isSpectator() || this.isDeadOrDying()) {
            ci.cancel();
        }
    }

    @Inject(method = "tryAddFrost", at = @At("HEAD"), cancellable = true)
    private void papi$skipFrostWhenNotFrozen(CallbackInfo ci) {
        if (this.getTicksFrozen() <= 0) {
            ci.cancel();
        }
    }

    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void papi$skipIdleFallFlying(CallbackInfo ci) {
        if (!this.isFallFlying() && this.fallFlyTicks == 0) {
            this.checkFallDistanceAccumulation();
            ci.cancel();
        }
    }

    @WrapMethod(method = "onClimbable")
    private boolean papi$fastCrowdedOnClimbable(Operation<Boolean> original) {
        int cached = SpatialEntityQueryHelper.checkCrowdedClimbable((LivingEntity) (Object) this);
        if (cached == 0) {
            return false;
        }
        if (cached == 1) {
            this.lastClimbablePos = Optional.of(this.blockPosition());
            return true;
        }
        return original.call();
    }

    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;tickEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
        )
    )
    private void papi$skipTickEffectsWhenNoTickableEnchantments(
            ServerLevel serverLevel,
            LivingEntity entity,
            Operation<Void> original
    ) {
        if (this.equipment instanceof PapiEquipmentTracker tracker
                && tracker.papi$isTrackable()
                && PapiEquipmentTracker.usesStandardEquipment(entity)
                && !tracker.papi$hasTickableEnchantments()) {
            return;
        }
        original.call(serverLevel, entity);
    }
}
