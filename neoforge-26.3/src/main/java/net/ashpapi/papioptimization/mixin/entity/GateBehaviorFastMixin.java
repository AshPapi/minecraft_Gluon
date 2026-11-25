package net.ashpapi.papioptimization.mixin.entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GateBehavior.class, priority = 1500)
public abstract class GateBehaviorFastMixin<E extends LivingEntity> {

    @Shadow @Final @Mutable private Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    @Shadow @Final private Set<MemoryModuleType<?>> exitErasedMemories;
    @Shadow @Final private GateBehavior.OrderPolicy orderPolicy;
    @Shadow @Final private GateBehavior.RunningPolicy runningPolicy;
    @Shadow @Final private ShufflingList<BehaviorControl<? super E>> behaviors;
    @Shadow private Behavior.Status status;

    @Shadow protected abstract boolean hasRequiredMemories(E body);
    @Shadow public abstract void doStop(ServerLevel level, E body, long timestamp);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$optimizeEntryConditionMap(CallbackInfo ci) {
        if (!this.entryCondition.isEmpty() && !(this.entryCondition instanceof Reference2ObjectOpenHashMap)) {
            this.entryCondition = new Reference2ObjectOpenHashMap<>(this.entryCondition);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "tryStart", at = @At("HEAD"), cancellable = true)
    private void papi$fastTryStart(ServerLevel level, E body, long timestamp, CallbackInfoReturnable<Boolean> cir) {
        if (!this.hasRequiredMemories(body)) {
            cir.setReturnValue(false);
            return;
        }
        this.status = Behavior.Status.RUNNING;
        this.orderPolicy.apply(this.behaviors);
        GateBehavior.RunningPolicy policy = this.runningPolicy;
        if (policy != GateBehavior.RunningPolicy.RUN_ONE && policy != GateBehavior.RunningPolicy.TRY_ALL) {
            policy.apply(this.behaviors.stream(), level, body, timestamp);
            cir.setReturnValue(true);
            return;
        }
        List<ShufflingList.WeightedEntry<BehaviorControl<? super E>>> entries =
                ((ShufflingListAccessor<BehaviorControl<? super E>>) this.behaviors).papi$getEntries();
        boolean runOne = policy == GateBehavior.RunningPolicy.RUN_ONE;
        if (entries instanceof RandomAccess) {
            for (int i = 0, size = entries.size(); i < size; i++) {
                BehaviorControl<? super E> behavior = entries.get(i).getData();
                if (behavior.getStatus() == Behavior.Status.STOPPED) {
                    if (behavior.tryStart(level, body, timestamp) && runOne) {
                        break;
                    }
                }
            }
        } else {
            for (ShufflingList.WeightedEntry<BehaviorControl<? super E>> entry : entries) {
                BehaviorControl<? super E> behavior = entry.getData();
                if (behavior.getStatus() == Behavior.Status.STOPPED) {
                    if (behavior.tryStart(level, body, timestamp) && runOne) {
                        break;
                    }
                }
            }
        }
        cir.setReturnValue(true);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "tickOrStop", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickOrStop(ServerLevel level, E body, long timestamp, CallbackInfo ci) {
        List<ShufflingList.WeightedEntry<BehaviorControl<? super E>>> entries =
                ((ShufflingListAccessor<BehaviorControl<? super E>>) this.behaviors).papi$getEntries();
        boolean anyStillRunning = false;
        if (entries instanceof RandomAccess) {
            for (int i = 0, size = entries.size(); i < size; i++) {
                BehaviorControl<? super E> behavior = entries.get(i).getData();
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    behavior.tickOrStop(level, body, timestamp);
                    if (behavior.getStatus() == Behavior.Status.RUNNING) {
                        anyStillRunning = true;
                    }
                }
            }
        } else {
            for (ShufflingList.WeightedEntry<BehaviorControl<? super E>> entry : entries) {
                BehaviorControl<? super E> behavior = entry.getData();
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    behavior.tickOrStop(level, body, timestamp);
                    if (behavior.getStatus() == Behavior.Status.RUNNING) {
                        anyStillRunning = true;
                    }
                }
            }
        }
        if (!anyStillRunning) {
            this.doStop(level, body, timestamp);
        }
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "doStop", at = @At("HEAD"), cancellable = true)
    private void papi$fastDoStop(ServerLevel level, E body, long timestamp, CallbackInfo ci) {
        this.status = Behavior.Status.STOPPED;
        List<ShufflingList.WeightedEntry<BehaviorControl<? super E>>> entries =
                ((ShufflingListAccessor<BehaviorControl<? super E>>) this.behaviors).papi$getEntries();
        if (entries instanceof RandomAccess) {
            for (int i = 0, size = entries.size(); i < size; i++) {
                BehaviorControl<? super E> behavior = entries.get(i).getData();
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    behavior.doStop(level, body, timestamp);
                }
            }
        } else {
            for (ShufflingList.WeightedEntry<BehaviorControl<? super E>> entry : entries) {
                BehaviorControl<? super E> behavior = entry.getData();
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    behavior.doStop(level, body, timestamp);
                }
            }
        }
        Brain<?> brain = body.getBrain();
        for (MemoryModuleType<?> memory : this.exitErasedMemories) {
            brain.eraseMemory(memory);
        }
        ci.cancel();
    }
}
