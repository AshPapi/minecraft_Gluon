package net.ashpapi.papioptimization.mixin.chunk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import net.ashpapi.papioptimization.chunk.PapiPalettedContainerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerCompactReadMixin<T> implements PaletteResize<T> {

    @SuppressWarnings("unchecked")
    @Inject(method = "read", at = @At("RETURN"))
    private void papi$compactUniformAfterRead(FriendlyByteBuf buffer, CallbackInfo ci) {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        MethodHandle createHandle = PapiPalettedContainerData.CREATE_OR_REUSE_DATA_HANDLE;
        if (dataHandle == null || createHandle == null) {
            return;
        }
        Object currentData = dataHandle.getAcquire(this);
        if (currentData == null) {
            return;
        }
        PalettedContainerDataAccessor<T> accessor = (PalettedContainerDataAccessor<T>) currentData;
        BitStorage storage = accessor.papi$getStorage();
        if (storage.getBits() <= 0) {
            return;
        }
        long[] raw = storage.getRaw();
        for (int i = 0, len = raw.length; i < len; i++) {
            if (raw[i] != 0L) {
                return;
            }
        }
        Palette<T> palette = accessor.papi$getPalette();
        if (palette.getSize() <= 0) {
            return;
        }
        T singleValue;
        try {
            singleValue = palette.valueFor(0);
        } catch (RuntimeException e) {
            return;
        }
        if (singleValue == null) {
            return;
        }
        try {
            Object compactData = createHandle.invoke((PalettedContainer<T>) (Object) this, null, 0);
            ((PalettedContainerDataAccessor<T>) compactData).papi$getPalette().idFor(singleValue, this);
            dataHandle.setRelease(this, compactData);
        } catch (Throwable ignored) {
        }
    }
}
