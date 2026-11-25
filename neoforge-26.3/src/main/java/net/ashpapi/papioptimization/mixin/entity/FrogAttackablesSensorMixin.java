package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = FrogAttackablesSensor.class, priority = 1500)
public abstract class FrogAttackablesSensorMixin {

    @Shadow
    private boolean isUnreachableAttackTarget(LivingEntity attacker, LivingEntity target) {
        throw new AssertionError();
    }

    @WrapMethod(method = "isMatchingEntity")
    private boolean papi$checkCanEatBeforeRaycast(
            ServerLevel level,
            LivingEntity attacker,
            LivingEntity target,
            Operation<Boolean> original
    ) {
        if (!Frog.canEat(target) || !target.closerThan(attacker, 10.0) || this.isUnreachableAttackTarget(attacker, target)) {
            return false;
        }
        return Sensor.isEntityAttackable(level, attacker, target);
    }
}
