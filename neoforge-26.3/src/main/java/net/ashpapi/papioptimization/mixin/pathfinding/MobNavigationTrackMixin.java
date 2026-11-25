package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigatingMob;
import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Mob.class, priority = 1500)
public abstract class MobNavigationTrackMixin extends LivingEntity implements PapiTrackedNavigatingMob {

    @Shadow
    protected PathNavigation navigation;

    @Unique
    private boolean papi$trackedInServerLevel;

    protected MobNavigationTrackMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean papi$isTrackedInServerLevel() {
        return this.papi$trackedInServerLevel;
    }

    @Override
    public void papi$setTrackedInServerLevel(boolean tracked) {
        this.papi$trackedInServerLevel = tracked;
        if (tracked) {
            papi$syncNavigation();
        } else {
            papi$unregisterNavigation();
        }
    }

    @Override
    public @Nullable PathNavigation papi$getRawNavigation() {
        return this.navigation;
    }

    @Override
    public void papi$syncNavigation() {
        PathNavigation nav = this.navigation;
        if (nav instanceof PapiTrackedNavigation tracked) {
            tracked.papi$syncActiveState();
        }
    }

    @Override
    public void papi$unregisterNavigation() {
        PathNavigation nav = this.navigation;
        if (nav instanceof PapiTrackedNavigation tracked) {
            tracked.papi$forceUnregister();
        }
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("RETURN"))
    private void papi$syncNavigationOnStartRiding(Entity vehicle, boolean force, boolean sendGameEvent, CallbackInfoReturnable<Boolean> cir) {
        if (this.level() instanceof ServerLevel) {
            this.papi$syncNavigation();
        }
    }
}
