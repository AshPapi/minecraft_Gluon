package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SortedArraySet.class, priority = 1500)
public abstract class SortedArraySetFastMixin<T> extends AbstractSet<T> {

    @Shadow
    @Final
    private Comparator<T> comparator;

    @Shadow
    private T[] contents;

    @Shadow
    private int size;

    @Unique
    private boolean papi$isNaturalOrder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$detectNaturalOrder(int initialCapacity, Comparator<T> comparator, CallbackInfo ci) {
        this.papi$isNaturalOrder = comparator == Comparator.naturalOrder();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapMethod(method = "findIndex")
    private int papi$fastFindIndex(T key, Operation<Integer> original) {
        T[] a = this.contents;
        int high = this.size - 1;
        if (this.papi$isNaturalOrder && key instanceof Comparable compKey) {
            int low = 0;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int cmp = ((Comparable) a[mid]).compareTo(compKey);
                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            }
            return -(low + 1);
        }
        Comparator<T> cmpFn = this.comparator;
        int low = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = cmpFn.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter);
        T[] a = this.contents;
        int n = this.size;
        int i = 0;
        while (i < n && !filter.test(a[i])) {
            i++;
        }
        if (i == n) {
            return false;
        }
        int writeIdx = i;
        int readIdx = i + 1;
        try {
            while (readIdx < n) {
                T elem = a[readIdx++];
                if (!filter.test(elem)) {
                    a[writeIdx++] = elem;
                }
            }
        } finally {
            if (readIdx < n) {
                int remaining = n - readIdx;
                System.arraycopy(a, readIdx, a, writeIdx, remaining);
                writeIdx += remaining;
            }
            Arrays.fill(a, writeIdx, n, null);
            this.size = writeIdx;
        }
        return true;
    }
}
