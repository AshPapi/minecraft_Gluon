package net.ashpapi.papioptimization.mixin.entity;

import java.util.Arrays;
import java.util.Optional;
import net.ashpapi.papioptimization.ai.ItemMergeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public abstract class NearestItemSensorMixin {

    @Unique
    private ItemEntity[] papi$candidates = new ItemEntity[8];

    @Unique
    private double[] papi$distances = new double[8];

    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V", at = @At("HEAD"), cancellable = true)
    private void papi$fastDoTick(ServerLevel level, Mob body, CallbackInfo ci) {
        ci.cancel();
        Brain<?> brain = body.getBrain();
        AABB box = body.getBoundingBox().inflate(32.0, 16.0, 32.0);
        int[] countHolder = new int[1];

        level.getEntities().get(ItemMergeHelper.ITEM_ENTITY_TYPE, box, item -> {
            double distSqr = item.distanceToSqr(body);
            if (distSqr < 1024.0 && body.wantsToPickUp(level, item.getItem())) {
                int idx = countHolder[0];
                if (idx == this.papi$candidates.length) {
                    int newLen = idx << 1;
                    this.papi$candidates = Arrays.copyOf(this.papi$candidates, newLen);
                    this.papi$distances = Arrays.copyOf(this.papi$distances, newLen);
                }
                this.papi$candidates[idx] = item;
                this.papi$distances[idx] = distSqr;
                countHolder[0] = idx + 1;
            }
            return Continuation.CONTINUE;
        });

        int count = countHolder[0];
        if (count == 0) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
            return;
        }

        ItemEntity[] items = this.papi$candidates;
        double[] dists = this.papi$distances;
        ItemEntity found = null;
        try {
            if (count == 1) {
                ItemEntity only = items[0];
                if (body.hasLineOfSight(only)) {
                    found = only;
                }
            } else {
                for (int i = 1; i < count; i++) {
                    ItemEntity curItem = items[i];
                    double curDist = dists[i];
                    int j = i - 1;
                    while (j >= 0 && dists[j] > curDist) {
                        items[j + 1] = items[j];
                        dists[j + 1] = dists[j];
                        j--;
                    }
                    items[j + 1] = curItem;
                    dists[j + 1] = curDist;
                }
                for (int i = 0; i < count; i++) {
                    ItemEntity candidate = items[i];
                    if (body.hasLineOfSight(candidate)) {
                        found = candidate;
                        break;
                    }
                }
            }
        } finally {
            Arrays.fill(items, 0, count, null);
        }

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, Optional.ofNullable(found));
    }
}
