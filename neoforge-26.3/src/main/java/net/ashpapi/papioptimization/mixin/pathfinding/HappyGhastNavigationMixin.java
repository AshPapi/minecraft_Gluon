package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigatingMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HappyGhast.class, priority = 1500)
public abstract class HappyGhastNavigationMixin extends Animal {

    protected HappyGhastNavigationMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = {"adultGhastSetup", "babyGhastSetup"}, at = @At("HEAD"))
    private void papi$beforeGhastNavigationSwap(CallbackInfo ci) {
        if (this.level() instanceof ServerLevel) {
            ((PapiTrackedNavigatingMob) this).papi$unregisterNavigation();
        }
    }

    @Inject(method = {"adultGhastSetup", "babyGhastSetup"}, at = @At("RETURN"))
    private void papi$afterGhastNavigationSwap(CallbackInfo ci) {
        if (this.level() instanceof ServerLevel) {
            ((PapiTrackedNavigatingMob) this).papi$syncNavigation();
        }
    }
}
