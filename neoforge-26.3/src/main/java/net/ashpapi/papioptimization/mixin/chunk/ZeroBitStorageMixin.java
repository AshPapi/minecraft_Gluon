package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.minecraft.util.ZeroBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ZeroBitStorage.class, priority = 1500)
public abstract class ZeroBitStorageMixin implements FastBitStorageCounts {

    @Shadow @Final private int size;

    @WrapMethod(method = "get")
    private int papi$fastGet(int index, Operation<Integer> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0) {
            return original.call(index);
        }
        return 0;
    }

    @WrapMethod(method = "set")
    private void papi$fastSet(int index, int value, Operation<Void> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0 || value != 0) {
            original.call(index, value);
        }
    }

    @WrapMethod(method = "getAndSet")
    private int papi$fastGetAndSet(int index, int value, Operation<Integer> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0 || value != 0) {
            return original.call(index, value);
        }
        return 0;
    }

    @Override
    public void papi$countInto(int[] counts) {
        if (counts.length > 0) {
            counts[0] += this.size;
        }
    }
}
