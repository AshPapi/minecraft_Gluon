package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigatingMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityNavigationMixin extends Entity {

    public LivingEntityNavigationMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "stopRiding", at = @At("RETURN"))
    private void papi$syncNavigationOnStopRiding(CallbackInfo ci) {
        if ((Object) this instanceof PapiTrackedNavigatingMob tracked && this.level() instanceof ServerLevel) {
            tracked.papi$syncNavigation();
        }
    }
}
