package net.ashpapi.papioptimization.mixin.chunk;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.Consumer;
import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.ashpapi.papioptimization.chunk.PapiPalettedContainerAccess;
import net.ashpapi.papioptimization.chunk.PapiPalettedContainerData;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerCountMixin<T> implements PapiPalettedContainerAccess<T> {

    @Unique
    private static final ThreadLocal<int[]> PAPI$COUNT_SCRATCH = ThreadLocal.withInitial(() -> new int[4096]);

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable PalettedContainerDataAccessor<T> papi$getDataAccessor() {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        if (dataHandle == null) {
            return null;
        }
        return (PalettedContainerDataAccessor<T>) dataHandle.getAcquire(this);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getAll", at = @At("HEAD"), cancellable = true)
    private void papi$fastSingleGetAll(Consumer<T> consumer, CallbackInfo ci) {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        if (dataHandle == null) {
            return;
        }
        PalettedContainerDataAccessor<T> accessor = (PalettedContainerDataAccessor<T>) dataHandle.getAcquire(this);
        if (accessor == null) {
            return;
        }
        Palette<T> palette = accessor.papi$getPalette();
        if (palette.getSize() == 1) {
            consumer.accept(palette.valueFor(0));
            ci.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void papi$count(PalettedContainer.CountConsumer<T> output, CallbackInfo ci) {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        if (dataHandle == null) {
            return;
        }
        PalettedContainerDataAccessor<T> accessor = (PalettedContainerDataAccessor<T>) dataHandle.getAcquire(this);
        if (accessor == null) {
            return;
        }
        Palette<T> palette = accessor.papi$getPalette();
        BitStorage storage = accessor.papi$getStorage();

        int paletteSize = palette.getSize();
        if (paletteSize == 1) {
            output.accept(palette.valueFor(0), storage.getSize());
            ci.cancel();
            return;
        }

        int bits = storage.getBits();
        if (bits < 0) {
            return;
        }
        if (bits > 12 || paletteSize > 4096) {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap();
            storage.getAll(id -> map.addTo(id, 1));
            for (Int2IntMap.Entry entry : map.int2IntEntrySet()) {
                output.accept(palette.valueFor(entry.getIntKey()), entry.getIntValue());
            }
            ci.cancel();
            return;
        }

        int maxCapacity = Math.max(paletteSize, 1 << bits);
        if (maxCapacity > 4096) {
            return;
        }

        int[] counts = PAPI$COUNT_SCRATCH.get();
        Arrays.fill(counts, 0, maxCapacity, 0);
        if (storage instanceof FastBitStorageCounts fastStorage) {
            fastStorage.papi$countInto(counts);
        } else {
            storage.getAll(id -> {
                if (Integer.compareUnsigned(id, maxCapacity) < 0) {
                    counts[id]++;
                }
            });
        }

        int limit = Math.min(paletteSize, maxCapacity);
        for (int id = 0; id < limit; id++) {
            int count = counts[id];
            if (count > 0) {
                output.accept(palette.valueFor(id), count);
            }
        }
        ci.cancel();
    }
}
