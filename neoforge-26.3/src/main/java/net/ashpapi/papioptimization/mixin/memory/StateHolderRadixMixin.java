package net.ashpapi.papioptimization.mixin.memory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiRadixStateHolder;
import net.ashpapi.papioptimization.memory.PapiRadixTable;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = StateHolder.class, priority = 1500)
public abstract class StateHolderRadixMixin<O, S> implements PapiRadixStateHolder<S> {

    @Shadow @Final protected O owner;
    @Shadow @Final private Comparable<?>[] propertyValues;
    @Shadow private S[][] neighbors;

    @Unique
    private PapiRadixTable<S> papi$radixTable;

    @Unique
    private int papi$stateIndex;

    @Override
    public void papi$attachRadix(PapiRadixTable<S> table, int stateIndex) {
        this.papi$radixTable = table;
        this.papi$stateIndex = stateIndex;
        this.neighbors = null;
    }

    @Override
    public boolean papi$hasRadix() {
        return this.papi$radixTable != null;
    }

    @WrapMethod(method = "initializeNeighbors")
    private void papi$skipNeighborsWhenRadixPresent(S[][] neighbors, Operation<Void> original) {
        if (this.papi$radixTable == null) {
            original.call((Object) neighbors);
        }
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "setValueInternal")
    private <T extends Comparable<T>, V extends T> S papi$fastSetValueInternal(
            Property<T> property,
            int propIdx,
            V value,
            Operation<S> original
    ) {
        PapiRadixTable<S> table = this.papi$radixTable;
        if (table == null) {
            return original.call(property, propIdx, value);
        }
        if (Integer.compareUnsigned(propIdx, table.valueCounts.length) >= 0) {
            throw new IllegalArgumentException(
                    "Cannot set property " + property + " as it does not exist in " + this.owner
            );
        }
        if (this.propertyValues[propIdx] == value) {
            return (S) (Object) this;
        }
        int newInternal = property.getInternalIndex(value);
        int count = table.valueCounts[propIdx];
        if (Integer.compareUnsigned(newInternal, count) >= 0) {
            throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value"
            );
        }
        int curIdx = this.papi$stateIndex;
        if (table.bitMode) {
            int targetIdx = (curIdx & table.keepMasks[propIdx]) | (newInternal << table.shifts[propIdx]);
            return table.states[targetIdx];
        }
        int stride = table.strides[propIdx];
        int oldInternal = (curIdx / stride) % count;
        return table.states[curIdx + (newInternal - oldInternal) * stride];
    }
}
