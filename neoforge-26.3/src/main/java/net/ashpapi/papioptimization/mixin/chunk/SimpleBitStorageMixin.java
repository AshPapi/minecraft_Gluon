package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.IntConsumer;
import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleBitStorage.class, priority = 1500)
public abstract class SimpleBitStorageMixin implements FastBitStorageCounts {

    @Shadow @Final private long[] data;
    @Shadow @Final private int bits;
    @Shadow @Final private long mask;
    @Shadow @Final private int size;
    @Shadow @Final private int valuesPerLong;
    @Shadow @Final private int divideMul;
    @Shadow @Final private int divideAdd;
    @Shadow @Final private int divideShift;

    @WrapMethod(method = "get")
    private int papi$fastGet(int index, Operation<Integer> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0) {
            return original.call(index);
        }
        int cellIdx = (int) ((index * Integer.toUnsignedLong(this.divideMul) + Integer.toUnsignedLong(this.divideAdd)) >>> 32 >>> this.divideShift);
        int bitOffset = (index - cellIdx * this.valuesPerLong) * this.bits;
        return (int) ((this.data[cellIdx] >>> bitOffset) & this.mask);
    }

    @WrapMethod(method = "set")
    private void papi$fastSet(int index, int value, Operation<Void> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0 || Long.compareUnsigned(value, this.mask) > 0) {
            original.call(index, value);
            return;
        }
        int cellIdx = (int) ((index * Integer.toUnsignedLong(this.divideMul) + Integer.toUnsignedLong(this.divideAdd)) >>> 32 >>> this.divideShift);
        long word = this.data[cellIdx];
        int bitOffset = (index - cellIdx * this.valuesPerLong) * this.bits;
        long m = this.mask;
        this.data[cellIdx] = (word & ~(m << bitOffset)) | (((long) value & m) << bitOffset);
    }

    @WrapMethod(method = "getAndSet")
    private int papi$fastGetAndSet(int index, int value, Operation<Integer> original) {
        if (Integer.compareUnsigned(index, this.size) >= 0 || Long.compareUnsigned(value, this.mask) > 0) {
            return original.call(index, value);
        }
        int cellIdx = (int) ((index * Integer.toUnsignedLong(this.divideMul) + Integer.toUnsignedLong(this.divideAdd)) >>> 32 >>> this.divideShift);
        long word = this.data[cellIdx];
        int bitOffset = (index - cellIdx * this.valuesPerLong) * this.bits;
        long m = this.mask;
        int prev = (int) ((word >>> bitOffset) & m);
        this.data[cellIdx] = (word & ~(m << bitOffset)) | (((long) value & m) << bitOffset);
        return prev;
    }

    @Inject(method = "getAll", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetAll(IntConsumer output, CallbackInfo ci) {
        long[] words = this.data;
        int len = words.length;
        if (len == 0) {
            ci.cancel();
            return;
        }
        int valsPerLong = this.valuesPerLong;
        int b = this.bits;
        long m = this.mask;
        int limit = len - 1;
        for (int i = 0; i < limit; i++) {
            long word = words[i];
            for (int j = 0; j < valsPerLong; j++) {
                output.accept((int) (word & m));
                word >>>= b;
            }
        }
        int rem = this.size - limit * valsPerLong;
        long lastWord = words[limit];
        for (int j = 0; j < rem; j++) {
            output.accept((int) (lastWord & m));
            lastWord >>>= b;
        }
        ci.cancel();
    }

    @Override
    public void papi$countInto(int[] counts) {
        long[] words = this.data;
        int len = words.length;
        if (len == 0) {
            return;
        }
        int countLen = counts.length;
        int valsPerLong = this.valuesPerLong;
        int b = this.bits;
        long m = this.mask;
        int limit = len - 1;
        for (int i = 0; i < limit; i++) {
            long word = words[i];
            for (int j = 0; j < valsPerLong; j++) {
                int id = (int) (word & m);
                if (Integer.compareUnsigned(id, countLen) < 0) {
                    counts[id]++;
                }
                word >>>= b;
            }
        }
        int rem = this.size - limit * valsPerLong;
        long lastWord = words[limit];
        for (int j = 0; j < rem; j++) {
            int id = (int) (lastWord & m);
            if (Integer.compareUnsigned(id, countLen) < 0) {
                counts[id]++;
            }
            lastWord >>>= b;
        }
    }
}
