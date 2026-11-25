package net.ashpapi.papioptimization.mixin.entity;

import com.mojang.datafixers.util.Pair;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ashpapi.papioptimization.access.PapiBrainMemoryTracker;
import net.ashpapi.papioptimization.ai.PapiSleepableSensor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.schedule.Activity;
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

@Mixin(value = Brain.class, priority = 1500)
public abstract class BrainFastMixin<E extends LivingEntity> implements PapiBrainMemoryTracker {

    @Unique
    @SuppressWarnings("rawtypes")
    private static final BehaviorControl[] PAPI$EMPTY_BEHAVIORS = new BehaviorControl[0];

    @Unique
    private static final MemorySlot<?>[] PAPI$EMPTY_SLOTS = new MemorySlot<?>[0];

    @Unique
    @SuppressWarnings("rawtypes")
    private static final Sensor[] PAPI$EMPTY_SENSORS = new Sensor[0];

    @Shadow @Final @Mutable private Map<MemoryModuleType<?>, MemorySlot<?>> memories;
    @Shadow @Final @Mutable private Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors;
    @Shadow @Final private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;
    @Shadow @Final @Mutable private Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> activityRequirements;
    @Shadow @Final private Set<Activity> activeActivities;

    @Shadow public abstract <U> void eraseMemory(MemoryModuleType<U> memoryType);

    @Unique private long papi$memoryModCount = 1L;
    @Unique private MemorySlot<?> @Nullable [] papi$slotArray;
    @Unique private int papi$slotArraySourceSize = -1;
    @Unique private Sensor<? super E> @Nullable [] papi$sensorArray;
    @Unique private int papi$sensorArraySourceSize = -1;
    @Unique private BehaviorControl<? super E> @Nullable [] papi$allBehaviors;
    @Unique private BehaviorControl<? super E> @Nullable [] papi$activeBehaviors;
    @Unique private boolean papi$activeSetExposed;
    @Unique private int papi$lastActiveSetHash;
    @Unique private BehaviorControl<? super E> @Nullable [] papi$runningBuffer;
    @Unique private boolean papi$inRunningLoop;
    @Unique private byte papi$ageSyncState;
    @Unique private byte papi$goatItemsState;

    @Unique
    private void papi$bumpMemoryModCount() {
        long next = this.papi$memoryModCount + 1L;
        this.papi$memoryModCount = next == 0L ? 1L : next;
    }

    @Override
    public long papi$getMemoryModCount() {
        return this.papi$memoryModCount;
    }

    @Override
    public void papi$setSensorActive(SensorType<?> sensorType, boolean active) {
        Sensor<? super E> sensor = this.sensors.get(sensorType);
        if (sensor instanceof PapiSleepableSensor sleepable) {
            sleepable.papi$setSensorSleeping(!active);
        }
    }

    @Inject(
        method = {
            "<init>(Ljava/util/Collection;Ljava/util/Collection;Ljava/util/List;Lnet/minecraft/world/entity/ai/memory/MemoryMap;Lnet/minecraft/util/RandomSource;)V",
            "<init>()V"
        },
        at = @At("RETURN")
    )
    private void papi$optimizeCollectionsOnInit(CallbackInfo ci) {
        if (!(this.memories instanceof Reference2ObjectOpenHashMap)) {
            this.memories = new Reference2ObjectOpenHashMap<>(this.memories);
        }
        if (!(this.sensors instanceof Reference2ReferenceLinkedOpenHashMap)) {
            this.sensors = new Reference2ReferenceLinkedOpenHashMap<>(this.sensors);
        }
        if (!(this.activityRequirements instanceof Object2ObjectOpenHashMap)) {
            this.activityRequirements = new Object2ObjectOpenHashMap<>(this.activityRequirements);
        }
        this.papi$slotArray = null;
        this.papi$slotArraySourceSize = -1;
        this.papi$sensorArray = null;
        this.papi$sensorArraySourceSize = -1;
        this.papi$allBehaviors = null;
        this.papi$activeBehaviors = null;
        this.papi$bumpMemoryModCount();
    }

    @Inject(method = "registerMemory", at = @At("HEAD"), cancellable = true)
    private void papi$fastRegisterMemory(MemoryModuleType<?> type, CallbackInfo ci) {
        if (!this.memories.containsKey(type)) {
            this.memories.put(type, MemorySlot.create());
            this.papi$slotArray = null;
            this.papi$bumpMemoryModCount();
        }
        ci.cancel();
    }

    @Inject(
        method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V",
        at = @At("HEAD")
    )
    private <U> void papi$beforeSetMemoryTtl(
            MemoryModuleType<U> type,
            @Nullable U value,
            long ttl,
            CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Share("hadVal") com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef hadValRef
    ) {
        MemorySlot<?> slot = this.memories.get(type);
        hadValRef.set(slot != null && slot.hasValue());
    }

    @Inject(
        method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V",
        at = @At("RETURN")
    )
    private <U> void papi$afterSetMemoryTtl(
            MemoryModuleType<U> type,
            @Nullable U value,
            long ttl,
            CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Share("hadVal") com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef hadValRef
    ) {
        MemorySlot<?> slot = this.memories.get(type);
        if (slot != null && slot.hasValue() != hadValRef.get()) {
            this.papi$bumpMemoryModCount();
        }
    }

    @Inject(
        method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
        at = @At("HEAD")
    )
    private <U> void papi$beforeSetMemory(
            MemoryModuleType<U> type,
            @Nullable U value,
            CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Share("hadVal") com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef hadValRef
    ) {
        MemorySlot<?> slot = this.memories.get(type);
        hadValRef.set(slot != null && slot.hasValue());
    }

    @Inject(
        method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
        at = @At("RETURN")
    )
    private <U> void papi$afterSetMemory(
            MemoryModuleType<U> type,
            @Nullable U value,
            CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Share("hadVal") com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef hadValRef
    ) {
        MemorySlot<?> slot = this.memories.get(type);
        if (slot != null && slot.hasValue() != hadValRef.get()) {
            this.papi$bumpMemoryModCount();
        }
    }

    @Inject(method = "eraseMemory", at = @At("HEAD"), cancellable = true)
    private <U> void papi$fastEraseMemory(MemoryModuleType<U> type, CallbackInfo ci) {
        MemorySlot<?> slot = this.memories.get(type);
        if (slot != null && slot.hasValue()) {
            slot.clear();
            this.papi$bumpMemoryModCount();
        }
        ci.cancel();
    }

    @Inject(method = "clearMemories", at = @At("HEAD"), cancellable = true)
    private void papi$fastClearMemories(CallbackInfo ci) {
        MemorySlot<?>[] slots = this.papi$getSlotArray();
        boolean changed = false;
        for (int i = 0, len = slots.length; i < len; i++) {
            MemorySlot<?> slot = slots[i];
            if (slot.hasValue()) {
                slot.clear();
                changed = true;
            }
        }
        if (changed) {
            this.papi$bumpMemoryModCount();
        }
        ci.cancel();
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"), cancellable = true)
    private void papi$fastForgetOutdatedMemories(CallbackInfo ci) {
        MemorySlot<?>[] slots = this.papi$getSlotArray();
        boolean expiredAny = false;
        for (int i = 0, len = slots.length; i < len; i++) {
            MemorySlot<?> slot = slots[i];
            if (slot.canExpire() && slot.hasValue()) {
                slot.tick();
                if (!slot.hasValue()) {
                    expiredAny = true;
                }
            }
        }
        if (expiredAny) {
            this.papi$bumpMemoryModCount();
        }
        ci.cancel();
    }

    @Unique
    private MemorySlot<?>[] papi$getSlotArray() {
        int size = this.memories.size();
        MemorySlot<?>[] arr = this.papi$slotArray;
        if (arr != null && this.papi$slotArraySourceSize == size) {
            return arr;
        }
        arr = size == 0 ? PAPI$EMPTY_SLOTS : this.memories.values().toArray(PAPI$EMPTY_SLOTS);
        this.papi$slotArray = arr;
        this.papi$slotArraySourceSize = size;
        return arr;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private Sensor<? super E>[] papi$getSensorArray() {
        int size = this.sensors.size();
        Sensor<? super E>[] arr = this.papi$sensorArray;
        if (arr != null && this.papi$sensorArraySourceSize == size) {
            return arr;
        }
        arr = size == 0 ? PAPI$EMPTY_SENSORS : this.sensors.values().toArray(PAPI$EMPTY_SENSORS);
        this.papi$sensorArray = arr;
        this.papi$sensorArraySourceSize = size;
        return arr;
    }

    @Inject(method = "tickSensors", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickSensors(ServerLevel level, E body, CallbackInfo ci) {
        if (body instanceof AgeableMob ageable) {
            byte curAgeState = ageable.isBaby() ? (byte) 1 : (byte) 2;
            if (this.papi$ageSyncState != curAgeState) {
                this.papi$ageSyncState = curAgeState;
                boolean baby = curAgeState == 1;
                this.papi$setSensorActive(SensorType.NEAREST_ADULT, baby);
                this.papi$setSensorActive(SensorType.VILLAGER_BABIES, baby);
                if (!baby) {
                    this.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
                    this.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
                }
            }
        }
        if (body.getClass() == Goat.class) {
            byte desiredGoatState = ((Goat) body).canPickUpLoot() ? (byte) 1 : (byte) 2;
            if (this.papi$goatItemsState != desiredGoatState) {
                this.papi$goatItemsState = desiredGoatState;
                boolean wantItems = desiredGoatState == 1;
                this.papi$setSensorActive(SensorType.NEAREST_ITEMS, wantItems);
                if (!wantItems) {
                    this.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
                }
            }
        }

        Sensor<? super E>[] arr = this.papi$getSensorArray();
        for (int i = 0, len = arr.length; i < len; i++) {
            Sensor<? super E> sensor = arr[i];
            if (sensor instanceof PapiSleepableSensor sleepable && sleepable.papi$isSensorSleeping()) {
                continue;
            }
            sensor.tick(level, body);
        }
        ci.cancel();
    }

    @Inject(method = "addActivity", at = @At("RETURN"))
    private void papi$invalidateOnAddActivity(
            Activity activity,
            ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> pairs,
            Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions,
            Set<MemoryModuleType<?>> memoriesToErase,
            CallbackInfo ci
    ) {
        this.papi$allBehaviors = null;
        this.papi$activeBehaviors = null;
    }

    @Inject(method = "removeAllBehaviors", at = @At("RETURN"))
    private void papi$invalidateOnRemoveAllBehaviors(CallbackInfo ci) {
        this.papi$allBehaviors = null;
        this.papi$activeBehaviors = null;
    }

    @Inject(method = "setActiveActivity", at = @At("RETURN"))
    private void papi$invalidateOnSetActiveActivity(Activity activity, CallbackInfo ci) {
        this.papi$activeBehaviors = null;
    }

    @Inject(method = "setCoreActivities", at = @At("RETURN"))
    private void papi$invalidateOnSetCoreActivities(Set<Activity> core, CallbackInfo ci) {
        this.papi$activeBehaviors = null;
    }

    @Inject(method = "getActiveActivities", at = @At("HEAD"))
    private void papi$markActiveSetExposed(CallbackInfoReturnable<Set<Activity>> cir) {
        this.papi$activeSetExposed = true;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private BehaviorControl<? super E>[] papi$getAllBehaviors() {
        BehaviorControl<? super E>[] arr = this.papi$allBehaviors;
        if (arr != null) {
            return arr;
        }
        int total = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : byActivity.values()) {
                total += set.size();
            }
        }
        if (total == 0) {
            this.papi$allBehaviors = PAPI$EMPTY_BEHAVIORS;
            return PAPI$EMPTY_BEHAVIORS;
        }
        arr = new BehaviorControl[total];
        int idx = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : byActivity.values()) {
                for (BehaviorControl<? super E> b : set) {
                    arr[idx++] = b;
                }
            }
        }
        if (idx != total) {
            arr = Arrays.copyOf(arr, idx);
        }
        this.papi$allBehaviors = arr;
        return arr;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private BehaviorControl<? super E>[] papi$getActiveBehaviors() {
        BehaviorControl<? super E>[] arr = this.papi$activeBehaviors;
        if (arr != null) {
            if (!this.papi$activeSetExposed) {
                return arr;
            }
            if (this.activeActivities.hashCode() == this.papi$lastActiveSetHash) {
                return arr;
            }
        }
        int total = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> entry : byActivity.entrySet()) {
                if (this.activeActivities.contains(entry.getKey())) {
                    total += entry.getValue().size();
                }
            }
        }
        if (total == 0) {
            this.papi$activeBehaviors = PAPI$EMPTY_BEHAVIORS;
            if (this.papi$activeSetExposed) {
                this.papi$lastActiveSetHash = this.activeActivities.hashCode();
            }
            return PAPI$EMPTY_BEHAVIORS;
        }
        arr = new BehaviorControl[total];
        int idx = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> entry : byActivity.entrySet()) {
                if (this.activeActivities.contains(entry.getKey())) {
                    for (BehaviorControl<? super E> b : entry.getValue()) {
                        arr[idx++] = b;
                    }
                }
            }
        }
        if (idx != total) {
            arr = Arrays.copyOf(arr, idx);
        }
        this.papi$activeBehaviors = arr;
        if (this.papi$activeSetExposed) {
            this.papi$lastActiveSetHash = this.activeActivities.hashCode();
        }
        return arr;
    }

    @Inject(method = "getRunningBehaviors", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetRunningBehaviors(CallbackInfoReturnable<List<BehaviorControl<? super E>>> cir) {
        BehaviorControl<? super E>[] all = this.papi$getAllBehaviors();
        ObjectArrayList<BehaviorControl<? super E>> running = new ObjectArrayList<>();
        for (int i = 0, len = all.length; i < len; i++) {
            BehaviorControl<? super E> behavior = all[i];
            if (behavior.getStatus() == Behavior.Status.RUNNING) {
                running.add(behavior);
            }
        }
        cir.setReturnValue(running);
    }

    @Inject(method = "startEachNonRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void papi$fastStartEachNonRunningBehavior(ServerLevel level, E body, CallbackInfo ci) {
        long timestamp = level.getGameTime();
        BehaviorControl<? super E>[] active = this.papi$getActiveBehaviors();
        for (int i = 0, len = active.length; i < len; i++) {
            BehaviorControl<? super E> behavior = active[i];
            if (behavior.getStatus() == Behavior.Status.STOPPED) {
                behavior.tryStart(level, body, timestamp);
            }
        }
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "tickEachRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickEachRunningBehavior(ServerLevel level, E body, CallbackInfo ci) {
        ci.cancel();
        BehaviorControl<? super E>[] all = this.papi$getAllBehaviors();
        int total = all.length;
        if (total == 0) {
            return;
        }
        long timestamp = level.getGameTime();
        boolean reentrant = this.papi$inRunningLoop;
        BehaviorControl<? super E>[] buf;
        if (reentrant) {
            buf = new BehaviorControl[Math.min(total, 16)];
        } else {
            this.papi$inRunningLoop = true;
            buf = this.papi$runningBuffer;
            if (buf == null) {
                buf = new BehaviorControl[16];
                this.papi$runningBuffer = buf;
            }
        }
        int count = 0;
        try {
            for (int i = 0; i < total; i++) {
                BehaviorControl<? super E> behavior = all[i];
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    if (count == buf.length) {
                        buf = Arrays.copyOf(buf, buf.length << 1);
                        if (!reentrant) {
                            this.papi$runningBuffer = buf;
                        }
                    }
                    buf[count++] = behavior;
                }
            }
            for (int i = 0; i < count; i++) {
                BehaviorControl<? super E> behavior = buf[i];
                buf[i] = null;
                behavior.tickOrStop(level, body, timestamp);
            }
        } finally {
            if (!reentrant) {
                Arrays.fill(buf, 0, count, null);
                this.papi$inRunningLoop = false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "stopAll", at = @At("HEAD"), cancellable = true)
    private void papi$fastStopAll(ServerLevel level, E body, CallbackInfo ci) {
        ci.cancel();
        BehaviorControl<? super E>[] all = this.papi$getAllBehaviors();
        int total = all.length;
        if (total == 0) {
            return;
        }
        long timestamp = body.level().getGameTime();
        boolean reentrant = this.papi$inRunningLoop;
        BehaviorControl<? super E>[] buf;
        if (reentrant) {
            buf = new BehaviorControl[Math.min(total, 16)];
        } else {
            this.papi$inRunningLoop = true;
            buf = this.papi$runningBuffer;
            if (buf == null) {
                buf = new BehaviorControl[16];
                this.papi$runningBuffer = buf;
            }
        }
        int count = 0;
        try {
            for (int i = 0; i < total; i++) {
                BehaviorControl<? super E> behavior = all[i];
                if (behavior.getStatus() == Behavior.Status.RUNNING) {
                    if (count == buf.length) {
                        buf = Arrays.copyOf(buf, buf.length << 1);
                        if (!reentrant) {
                            this.papi$runningBuffer = buf;
                        }
                    }
                    buf[count++] = behavior;
                }
            }
            for (int i = 0; i < count; i++) {
                BehaviorControl<? super E> behavior = buf[i];
                buf[i] = null;
                behavior.doStop(level, body, timestamp);
            }
        } finally {
            if (!reentrant) {
                Arrays.fill(buf, 0, count, null);
                this.papi$inRunningLoop = false;
            }
        }
    }
}
