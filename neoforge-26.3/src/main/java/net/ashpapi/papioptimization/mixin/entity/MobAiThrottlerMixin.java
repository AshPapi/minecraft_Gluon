package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mob.class, priority = 1500)
public abstract class MobAiThrottlerMixin extends LivingEntity {

    protected MobAiThrottlerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract LivingEntity getTarget();

    @Shadow
    public abstract boolean isAggressive();

    @Shadow
    public abstract PathNavigation getNavigation();

    @Shadow
    public abstract MoveControl getMoveControl();

    @Shadow
    public abstract boolean isPersistenceRequired();

    @Shadow
    public abstract boolean requiresCustomPersistence();

    @Unique
    private byte papi$mobFlags = 0;

    @SuppressWarnings("deprecation")
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void papi$adaptiveAiThrottling(CallbackInfo ci) {
        int interval = PapiMsptController.getAiThrottleInterval();
        if (interval <= 1 || Math.floorMod(this.tickCount + this.getId(), interval) == 0) {
            return;
        }

        byte flags = this.papi$mobFlags;
        if (flags == 0) {
            boolean eligible = this.getClass().getName().startsWith("net.minecraft.")
                    && !this.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
            flags = eligible ? (byte) 1 : (byte) 2;
            this.papi$mobFlags = flags;
        }
        if (flags != 1) {
            return;
        }

        if ((Object) this instanceof AbstractVillager
                || (Object) this instanceof Raider
                || (Object) this instanceof Warden
                || (Object) this instanceof AbstractGolem
                || (Object) this instanceof Bee
                || (Object) this instanceof Allay
                || (Object) this instanceof AbstractPiglin
                || ((Object) this instanceof TamableAnimal tamable && tamable.isTame())
                || ((Object) this instanceof Animal animal && animal.isInLove())) {
            return;
        }

        if (this.isPersistenceRequired() || this.requiresCustomPersistence() || ((Leashable) (Object) this).isLeashed()) {
            return;
        }
        if (this.getTarget() != null || this.isAggressive()) {
            return;
        }

        PathNavigation nav = this.getNavigation();
        if (nav != null && nav.isInProgress()) {
            return;
        }
        if (this.getMoveControl().hasWanted()) {
            return;
        }

        if (this.hurtTime > 0 || this.getLastHurtByMob() != null || this.isOnFire()) {
            return;
        }
        if (this.isInWater() || this.isInLava()) {
            return;
        }
        if (!this.onGround()) {
            return;
        }
        if (this.isPassenger() || this.isVehicle()) {
            return;
        }
        if (this.hasCustomName()) {
            return;
        }
        if (this.isBaby()) {
            return;
        }

        if (this.level().hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), 16.0)) {
            return;
        }

        ci.cancel();
    }
}
