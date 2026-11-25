package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = TemptingSensor.class, priority = 1500)
public abstract class TemptingSensorMixin {

    @Shadow @Final private static TargetingConditions TEMPT_TARGETING;

    @Shadow protected abstract boolean playerHoldingTemptation(PathfinderMob mob, Player player);

    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;)V", at = @At("HEAD"), cancellable = true)
    private void papi$fastDoTick(ServerLevel level, PathfinderMob body, CallbackInfo ci) {
        Brain<?> brain = body.getBrain();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            ci.cancel();
            return;
        }
        double range = body.getAttributeValue(Attributes.TEMPT_RANGE);
        double maxDistSqr = range * range;
        TargetingConditions targeting = null;
        ServerPlayer nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;

        for (int i = 0, size = players.size(); i < size; i++) {
            ServerPlayer player = players.get(i);
            if (!EntitySelector.NO_SPECTATORS.test(player)) continue;
            double distSqr = body.distanceToSqr(player);
            if (distSqr > maxDistSqr || distSqr >= nearestDistSqr) continue;
            if (body.hasPassenger(player)) continue;
            if (!this.playerHoldingTemptation(body, player)) continue;
            if (targeting == null) {
                targeting = TEMPT_TARGETING.copy().range(range);
            }
            if (targeting.test(level, body, player)) {
                nearest = player;
                nearestDistSqr = distSqr;
            }
        }

        if (nearest != null) {
            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, nearest);
        } else {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }
        ci.cancel();
    }
}
