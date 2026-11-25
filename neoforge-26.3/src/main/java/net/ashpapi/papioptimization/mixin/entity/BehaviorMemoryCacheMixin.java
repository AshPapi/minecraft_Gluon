package net.ashpapi.papioptimization.mixin.entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import net.ashpapi.papioptimization.access.PapiBrainMemoryTracker;
import net.ashpapi.papioptimization.core.PapiConditionSnapshot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Behavior.class, priority = 1500)
public abstract class BehaviorMemoryCacheMixin<E extends LivingEntity> {

    @Shadow @Final @Mutable
    protected Map<MemoryModuleType<?>, MemoryStatus> entryCondition;

    @Unique
    private volatile PapiConditionSnapshot papi$snapshot = PapiConditionSnapshot.EMPTY;

    @Unique
    private @Nullable Brain<?> papi$lastBrain;

    @Unique
    private long papi$lastModCount;

    @Unique
    private boolean papi$lastResult;

    @Inject(method = "<init>(Ljava/util/Map;II)V", at = @At("RETURN"))
    private void papi$initConditionArrays(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int minDuration, int maxDuration, CallbackInfo ci) {
        if (this.entryCondition != null && !this.entryCondition.isEmpty()) {
            if (!(this.entryCondition instanceof Reference2ObjectOpenHashMap)) {
                this.entryCondition = new Reference2ObjectOpenHashMap<>(this.entryCondition);
            }
            this.papi$rebuildSnapshot();
        }
    }

    @Unique
    private PapiConditionSnapshot papi$rebuildSnapshot() {
        int size = this.entryCondition.size();
        if (size == 0) {
            this.papi$snapshot = PapiConditionSnapshot.EMPTY;
            this.papi$lastModCount = 0L;
            return PapiConditionSnapshot.EMPTY;
        }
        MemoryModuleType<?>[] keys = new MemoryModuleType<?>[size];
        MemoryStatus[] statuses = new MemoryStatus[size];
        int i = 0;
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            keys[i] = entry.getKey();
            statuses[i] = entry.getValue();
            i++;
        }
        PapiConditionSnapshot snap = new PapiConditionSnapshot(keys.length, keys, statuses);
        this.papi$snapshot = snap;
        this.papi$lastModCount = 0L;
        return snap;
    }

    @Inject(method = "hasRequiredMemories", at = @At("HEAD"), cancellable = true)
    private void papi$fastHasRequiredMemories(E body, CallbackInfoReturnable<Boolean> cir) {
        int currentSize = this.entryCondition.size();
        if (currentSize == 0) {
            cir.setReturnValue(true);
            return;
        }

        PapiConditionSnapshot snap = this.papi$snapshot;
        if (snap.sourceSize() != currentSize) {
            snap = this.papi$rebuildSnapshot();
        }

        Brain<?> brain = body.getBrain();
        long modCount = 0L;
        if (brain instanceof PapiBrainMemoryTracker tracker) {
            modCount = tracker.papi$getMemoryModCount();
            if (modCount != 0L && this.papi$lastBrain == brain && this.papi$lastModCount == modCount) {
                cir.setReturnValue(this.papi$lastResult);
                return;
            }
        }

        MemoryModuleType<?>[] keys = snap.keys();
        MemoryStatus[] statuses = snap.statuses();
        boolean result = true;
        for (int i = 0, len = keys.length; i < len; i++) {
            if (!brain.checkMemory(keys[i], statuses[i])) {
                result = false;
                break;
            }
        }

        if (modCount != 0L) {
            this.papi$lastBrain = brain;
            this.papi$lastModCount = modCount;
            this.papi$lastResult = result;
        }
        cir.setReturnValue(result);
    }
}
