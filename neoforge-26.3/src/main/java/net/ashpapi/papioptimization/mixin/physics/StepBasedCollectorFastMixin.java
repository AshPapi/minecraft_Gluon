package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = InsideBlockEffectApplier.StepBasedCollector.class, priority = 1500)
public abstract class StepBasedCollectorFastMixin {

    @Shadow
    @Final
    private static InsideBlockEffectType[] APPLY_ORDER;

    @Shadow
    @Final
    @Mutable
    private List<Consumer<Entity>> finalEffects;

    @Shadow
    private int lastStep;

    @Unique
    private int papi$stepMask;

    @Unique
    private List<Consumer<Entity>> @Nullable [] papi$beforeLists;

    @Unique
    private List<Consumer<Entity>> @Nullable [] papi$afterLists;

    @Unique
    private boolean papi$hasBeforeOrAfter;

    @WrapOperation(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Ljava/util/EnumSet;noneOf(Ljava/lang/Class;)Ljava/util/EnumSet;")
    )
    private <E extends Enum<E>> EnumSet<E> papi$skipEnumSet(Class<E> elementType, Operation<EnumSet<E>> original) {
        return null;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;makeEnumMap(Ljava/lang/Class;Ljava/util/function/Function;)Ljava/util/Map;")
    )
    private <K extends Enum<K>, V> Map<K, V> papi$skipEnumMaps(Class<K> clazz, Function<K, V> supplier, Operation<Map<K, V>> original) {
        return Map.of();
    }

    @WrapOperation(
            method = "<init>",
            at = @At(value = "NEW", target = "()Ljava/util/ArrayList;")
    )
    private ArrayList<Consumer<Entity>> papi$skipFinalEffectsAlloc(Operation<ArrayList<Consumer<Entity>>> original) {
        return null;
    }

    @WrapMethod(method = "apply")
    private void papi$fastApply(InsideBlockEffectType type, Operation<Void> original) {
        this.papi$stepMask |= (1 << type.ordinal());
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "runBefore")
    private void papi$fastRunBefore(InsideBlockEffectType type, Consumer<Entity> effect, Operation<Void> original) {
        List<Consumer<Entity>>[] arr = this.papi$beforeLists;
        if (arr == null) {
            arr = (List<Consumer<Entity>>[]) new List<?>[APPLY_ORDER.length];
            this.papi$beforeLists = arr;
        }
        int idx = type.ordinal();
        List<Consumer<Entity>> list = arr[idx];
        if (list == null) {
            list = new ArrayList<>(2);
            arr[idx] = list;
        }
        list.add(effect);
        this.papi$hasBeforeOrAfter = true;
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "runAfter")
    private void papi$fastRunAfter(InsideBlockEffectType type, Consumer<Entity> effect, Operation<Void> original) {
        List<Consumer<Entity>>[] arr = this.papi$afterLists;
        if (arr == null) {
            arr = (List<Consumer<Entity>>[]) new List<?>[APPLY_ORDER.length];
            this.papi$afterLists = arr;
        }
        int idx = type.ordinal();
        List<Consumer<Entity>> list = arr[idx];
        if (list == null) {
            list = new ArrayList<>(2);
            arr[idx] = list;
        }
        list.add(effect);
        this.papi$hasBeforeOrAfter = true;
    }

    @WrapMethod(method = "flushStep")
    private void papi$fastFlushStep(Operation<Void> original) {
        int mask = this.papi$stepMask;
        boolean hasExtra = this.papi$hasBeforeOrAfter;
        if (mask == 0 && !hasExtra) {
            return;
        }

        List<Consumer<Entity>> finals = this.finalEffects;
        if (finals == null) {
            finals = new ArrayList<>(4);
            this.finalEffects = finals;
        }

        List<Consumer<Entity>>[] beforeArr = hasExtra ? this.papi$beforeLists : null;
        List<Consumer<Entity>>[] afterArr = hasExtra ? this.papi$afterLists : null;

        for (int i = 0, len = APPLY_ORDER.length; i < len; i++) {
            InsideBlockEffectType type = APPLY_ORDER[i];
            int idx = type.ordinal();
            if (beforeArr != null) {
                List<Consumer<Entity>> before = beforeArr[idx];
                if (before != null && !before.isEmpty()) {
                    finals.addAll(before);
                    before.clear();
                }
            }
            if ((mask & (1 << idx)) != 0) {
                finals.add(type.effect());
            }
            if (afterArr != null) {
                List<Consumer<Entity>> after = afterArr[idx];
                if (after != null && !after.isEmpty()) {
                    finals.addAll(after);
                    after.clear();
                }
            }
        }

        this.papi$stepMask = 0;
        this.papi$hasBeforeOrAfter = false;
    }

    @WrapMethod(method = "applyAndClear")
    private void papi$fastApplyAndClear(Entity entity, Operation<Void> original) {
        if (this.papi$stepMask != 0 || this.papi$hasBeforeOrAfter) {
            this.papi$fastFlushStep(original);
        }
        List<Consumer<Entity>> finals = this.finalEffects;
        if (finals != null && !finals.isEmpty()) {
            for (int i = 0, len = finals.size(); i < len; i++) {
                if (!entity.isAlive()) {
                    break;
                }
                finals.get(i).accept(entity);
            }
            finals.clear();
        }
        this.lastStep = -1;
    }
}
