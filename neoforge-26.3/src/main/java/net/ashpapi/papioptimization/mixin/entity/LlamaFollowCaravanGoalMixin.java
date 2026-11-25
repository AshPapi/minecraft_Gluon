package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LlamaFollowCaravanGoal.class, priority = 1500)
public abstract class LlamaFollowCaravanGoalMixin {

    @Shadow
    @Final
    public Llama llama;

    @Shadow
    private boolean firstIsLeashed(Llama llama, int count) {
        throw new AssertionError();
    }

    @WrapMethod(method = "canUse")
    private boolean papi$fastCanUse(Operation<Boolean> original) {
        Llama self = this.llama;
        if (self.isLeashed() || self.inCaravan()) {
            return false;
        }

        AABB box = self.getBoundingBox().inflate(9.0, 4.0, 9.0);
        Llama[] bestRefs = new Llama[2];
        double[] bestDists = new double[]{Double.MAX_VALUE, Double.MAX_VALUE};

        ((LevelEntityGetterAccessor) self.level()).papi$getEntityGetter().get(SpatialEntityQueryHelper.LLAMA_TEST, box, candidate -> {
            if (candidate == self) {
                return Continuation.CONTINUE;
            }
            EntityType<?> type = candidate.getType();
            if (type != EntityTypes.LLAMA && type != EntityTypes.TRADER_LLAMA) {
                return Continuation.CONTINUE;
            }
            if (candidate.hasCaravanTail()) {
                return Continuation.CONTINUE;
            }
            if (candidate.inCaravan()) {
                double d = self.distanceToSqr(candidate);
                if (d <= bestDists[0]) {
                    bestDists[0] = d;
                    bestRefs[0] = candidate;
                }
            } else if (bestRefs[0] == null && candidate.isLeashed()) {
                double d = self.distanceToSqr(candidate);
                if (d <= bestDists[1]) {
                    bestDists[1] = d;
                    bestRefs[1] = candidate;
                }
            }
            return Continuation.CONTINUE;
        });

        Llama chosen = bestRefs[0];
        double chosenDist = bestDists[0];
        if (chosen == null) {
            chosen = bestRefs[1];
            chosenDist = bestDists[1];
        }
        if (chosen == null || chosenDist < 4.0) {
            return false;
        }
        if (!chosen.isLeashed() && !this.firstIsLeashed(chosen, 1)) {
            return false;
        }
        self.joinCaravan(chosen);
        return true;
    }
}
