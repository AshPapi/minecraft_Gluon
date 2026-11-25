package net.ashpapi.papioptimization.mixin.memory;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import net.ashpapi.papioptimization.access.PapiRadixStateHolder;
import net.ashpapi.papioptimization.memory.PapiRadixTable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StateDefinition.class, priority = 1500)
public abstract class StateDefinitionRadixMixin<O, S extends StateHolder<O, S>> {

    @Unique
    private static final long PAPI$MAX_STATES = 1_048_576L;

    @Shadow @Final private ImmutableList<S> states;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapMethod(
        method = "createSinglePropertyStates(Ljava/lang/Object;Lnet/minecraft/world/level/block/state/StateDefinition$Factory;Lnet/minecraft/world/level/block/state/properties/Property;)Lcom/google/common/collect/ImmutableList;"
    )
    private static <O, S extends StateHolder<O, S>, T extends Comparable<T>> ImmutableList<S> papi$createSingleStatesWithoutNeighbors(
            O owner,
            StateDefinition.Factory<O, S> factory,
            Property<T> property,
            Operation<ImmutableList<S>> original
    ) {
        List<T> possible = property.getPossibleValues();
        int size = possible.size();
        if (size <= 0 || size > PAPI$MAX_STATES) {
            return original.call(owner, factory, property);
        }
        Comparable<?>[] vals = new Comparable<?>[size];
        for (int v = 0; v < size; v++) {
            T val = possible.get(v);
            if (property.getInternalIndex(val) != v) {
                return original.call(owner, factory, property);
            }
            vals[v] = val;
        }
        Property<?>[] keys = new Property<?>[]{property};
        int[] valueCounts = new int[]{size};
        int[] strides = new int[]{1};
        int totalBits = PapiRadixTable.computeTotalBits(valueCounts, size);
        boolean bitMode = totalBits >= 0;
        int[] shifts = bitMode ? new int[1] : null;
        int[] keepMasks = bitMode ? new int[1] : null;
        if (bitMode) {
            PapiRadixTable.fillShiftsAndMasks(valueCounts, shifts, keepMasks);
        }
        int tableSize = bitMode ? (1 << totalBits) : size;
        S[] lookupStates = (S[]) new StateHolder[tableSize];
        S[] denseStates = bitMode ? (S[]) new StateHolder[size] : lookupStates;
        PapiRadixTable<S> table = new PapiRadixTable<>(lookupStates, strides, valueCounts, bitMode, keepMasks, shifts);
        for (int i = 0; i < size; i++) {
            S state = factory.create(owner, keys, new Comparable<?>[]{vals[i]});
            denseStates[i] = state;
            lookupStates[i] = state;
            ((PapiRadixStateHolder<S>) state).papi$attachRadix(table, i);
        }
        VarHandle.releaseFence();
        return ImmutableList.copyOf(denseStates);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapMethod(method = "createMultiPropertyStates")
    private static <O, S extends StateHolder<O, S>> ImmutableList<S> papi$createStatesWithoutNeighborMatrix(
            O owner,
            StateDefinition.Factory<O, S> factory,
            Map<String, Property<?>> propertiesByName,
            Operation<ImmutableList<S>> original
    ) {
        int propCount = propertiesByName.size();
        if (propCount <= 1) {
            return original.call(owner, factory, propertiesByName);
        }

        Property<?>[] keys = propertiesByName.values().toArray(new Property<?>[propCount]);
        Comparable<?>[][] valueLists = new Comparable<?>[propCount][];
        long totalLong = 1L;
        for (int k = 0; k < propCount; k++) {
            Property p = keys[k];
            List<?> possible = p.getPossibleValues();
            int size = possible.size();
            if (size <= 0) {
                return original.call(owner, factory, propertiesByName);
            }
            if (totalLong > PAPI$MAX_STATES / size) {
                return original.call(owner, factory, propertiesByName);
            }
            totalLong *= (long) size;
            if (totalLong > PAPI$MAX_STATES) {
                return original.call(owner, factory, propertiesByName);
            }
            Comparable<?>[] vals = new Comparable<?>[size];
            for (int v = 0; v < size; v++) {
                Comparable val = (Comparable) possible.get(v);
                if (p.getInternalIndex(val) != v) {
                    return original.call(owner, factory, propertiesByName);
                }
                vals[v] = val;
            }
            valueLists[k] = vals;
        }

        int totalCount = (int) totalLong;
        int[] valueCounts = new int[propCount];
        for (int k = 0; k < propCount; k++) {
            valueCounts[k] = valueLists[k].length;
        }
        int[] strides = new int[propCount];
        strides[propCount - 1] = 1;
        for (int k = propCount - 2; k >= 0; k--) {
            strides[k] = strides[k + 1] * valueCounts[k + 1];
        }

        int totalBits = PapiRadixTable.computeTotalBits(valueCounts, totalCount);
        boolean bitMode = totalBits >= 0;
        int[] shifts = bitMode ? new int[propCount] : null;
        int[] keepMasks = bitMode ? new int[propCount] : null;
        if (bitMode) {
            PapiRadixTable.fillShiftsAndMasks(valueCounts, shifts, keepMasks);
        }

        S[] denseStates = (S[]) new StateHolder[totalCount];
        S[] lookupStates = bitMode ? (S[]) new StateHolder[1 << totalBits] : denseStates;
        PapiRadixTable<S> table = new PapiRadixTable<>(lookupStates, strides, valueCounts, bitMode, keepMasks, shifts);
        int[] odometer = new int[propCount];

        for (int i = 0; i < totalCount; i++) {
            Comparable<?>[] stateValues = new Comparable<?>[propCount];
            int stateIdx = i;
            if (bitMode) {
                stateIdx = 0;
                for (int k = 0; k < propCount; k++) {
                    int odo = odometer[k];
                    stateValues[k] = valueLists[k][odo];
                    stateIdx |= odo << shifts[k];
                }
            } else {
                for (int k = 0; k < propCount; k++) {
                    stateValues[k] = valueLists[k][odometer[k]];
                }
            }
            S state = factory.create(owner, keys, stateValues);
            denseStates[i] = state;
            lookupStates[stateIdx] = state;
            ((PapiRadixStateHolder<S>) state).papi$attachRadix(table, stateIdx);

            for (int k = propCount - 1; k >= 0; k--) {
                int next = odometer[k] + 1;
                if (next < valueLists[k].length) {
                    odometer[k] = next;
                    break;
                }
                odometer[k] = 0;
            }
        }

        VarHandle.releaseFence();
        return ImmutableList.copyOf(denseStates);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$buildRadixTable(CallbackInfo ci) {
        int count = this.states.size();
        if (count <= 1 || count > PAPI$MAX_STATES) {
            return;
        }
        S first = this.states.get(0);
        if (((PapiRadixStateHolder<S>) first).papi$hasRadix()) {
            return;
        }
        Property<?>[] keys = ((StateHolderKeysAccessor) first).papi$getPropertyKeys();
        int propCount = keys.length;
        if (propCount == 0) {
            return;
        }

        int[] valueCounts = new int[propCount];
        long expectedLong = 1L;
        for (int k = 0; k < propCount; k++) {
            int vc = keys[k].getPossibleValues().size();
            if (vc <= 0 || expectedLong > PAPI$MAX_STATES / vc) {
                return;
            }
            valueCounts[k] = vc;
            expectedLong *= vc;
        }
        if (expectedLong != count) {
            return;
        }

        int[] strides = new int[propCount];
        strides[propCount - 1] = 1;
        for (int k = propCount - 2; k >= 0; k--) {
            strides[k] = strides[k + 1] * valueCounts[k + 1];
        }

        int totalBits = PapiRadixTable.computeTotalBits(valueCounts, count);
        boolean bitMode = totalBits >= 0;
        int[] shifts = bitMode ? new int[propCount] : null;
        int[] keepMasks = bitMode ? new int[propCount] : null;
        if (bitMode) {
            PapiRadixTable.fillShiftsAndMasks(valueCounts, shifts, keepMasks);
        }

        int lookupLen = bitMode ? (1 << totalBits) : count;
        S[] lookupStates = (S[]) new StateHolder[lookupLen];
        int[] computedIndices = new int[count];
        for (int i = 0; i < count; i++) {
            S st = this.states.get(i);
            int idx = 0;
            for (int k = 0; k < propCount; k++) {
                Property p = keys[k];
                Comparable val = st.getValue(p);
                int internalIdx = p.getInternalIndex(val);
                if (Integer.compareUnsigned(internalIdx, valueCounts[k]) >= 0) {
                    return;
                }
                if (bitMode) {
                    idx |= internalIdx << shifts[k];
                } else {
                    idx += internalIdx * strides[k];
                }
            }
            if (Integer.compareUnsigned(idx, lookupLen) >= 0 || lookupStates[idx] != null) {
                return;
            }
            lookupStates[idx] = st;
            computedIndices[i] = idx;
        }

        PapiRadixTable<S> table = new PapiRadixTable<>(lookupStates, strides, valueCounts, bitMode, keepMasks, shifts);
        for (int i = 0; i < count; i++) {
            ((PapiRadixStateHolder<S>) this.states.get(i)).papi$attachRadix(table, computedIndices[i]);
        }
        VarHandle.releaseFence();
    }
}
