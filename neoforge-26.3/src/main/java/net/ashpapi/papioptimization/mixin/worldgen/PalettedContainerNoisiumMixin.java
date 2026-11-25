package net.ashpapi.papioptimization.mixin.worldgen;

import java.lang.invoke.VarHandle;
import net.ashpapi.papioptimization.access.PapiNoisiumPalette;
import net.ashpapi.papioptimization.chunk.PapiPalettedContainerData;
import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerNoisiumMixin<T> implements PapiNoisiumPalette, PaletteResize<T> {

    @SuppressWarnings("unchecked")
    @Override
    public BlockState papi$setDirectNoisium(int index, BlockState state, NoisiumPaletteCursor cursor) {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        if (dataHandle == null) {
            return ((PalettedContainer<BlockState>) (Object) this).getAndSetUnchecked(
                    index & 15, (index >>> 8) & 15, (index >>> 4) & 15, state);
        }
        Object curData = dataHandle.getAcquire(this);
        if (curData == null) {
            return ((PalettedContainer<BlockState>) (Object) this).getAndSetUnchecked(
                    index & 15, (index >>> 8) & 15, (index >>> 4) & 15, state);
        }
        if (cursor.dataRef != curData) {
            cursor.resetPalette();
            cursor.dataRef = curData;
        }

        int id;
        if (state == cursor.lastState && cursor.lastId >= 0) {
            id = cursor.lastId;
        } else if (state == cursor.defaultState && cursor.defaultId >= 0) {
            id = cursor.defaultId;
        } else {
            PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
            id = acc.papi$getPalette().idFor(state, (PaletteResize<BlockState>) this);
            Object afterData = dataHandle.getAcquire(this);
            if (afterData != curData) {
                curData = afterData;
                cursor.resetPalette();
                cursor.dataRef = curData;
            }
            int flags = NoisiumPaletteCursor.computeFlags(state);
            if (cursor.defaultState == null && state != Blocks.AIR.defaultBlockState()) {
                cursor.defaultState = state;
                cursor.defaultId = id;
                cursor.defaultStateFlags = flags;
            }
            cursor.lastState = state;
            cursor.lastId = id;
            cursor.lastStateFlags = flags;
        }

        PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
        BitStorage storage = acc.papi$getStorage();
        int prevId = storage.getAndSet(index, id);
        if (prevId == id) {
            return state;
        }
        if (prevId == cursor.airId) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState prev = acc.papi$getPalette().valueFor(prevId);
        if (prev == Blocks.AIR.defaultBlockState()) {
            cursor.airId = prevId;
        }
        return prev;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void papi$setWriteOnlyNoisium(int index, BlockState state, NoisiumPaletteCursor cursor) {
        VarHandle dataHandle = PapiPalettedContainerData.DATA_HANDLE;
        if (dataHandle == null) {
            ((PalettedContainer<BlockState>) (Object) this).getAndSetUnchecked(
                    index & 15, (index >>> 8) & 15, (index >>> 4) & 15, state);
            cursor.recordWrite(index, NoisiumPaletteCursor.computeFlags(state));
            return;
        }
        Object curData = dataHandle.getAcquire(this);
        if (curData == null) {
            ((PalettedContainer<BlockState>) (Object) this).getAndSetUnchecked(
                    index & 15, (index >>> 8) & 15, (index >>> 4) & 15, state);
            cursor.recordWrite(index, NoisiumPaletteCursor.computeFlags(state));
            return;
        }
        if (cursor.dataRef != curData) {
            cursor.resetPalette();
            cursor.dataRef = curData;
        }

        int id;
        int flags;
        if (state == cursor.lastState && cursor.lastId >= 0) {
            id = cursor.lastId;
            flags = cursor.lastStateFlags;
        } else if (state == cursor.defaultState && cursor.defaultId >= 0) {
            id = cursor.defaultId;
            flags = cursor.defaultStateFlags;
        } else {
            PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
            id = acc.papi$getPalette().idFor(state, (PaletteResize<BlockState>) this);
            Object afterData = dataHandle.getAcquire(this);
            if (afterData != curData) {
                curData = afterData;
                cursor.resetPalette();
                cursor.dataRef = curData;
            }
            flags = NoisiumPaletteCursor.computeFlags(state);
            if (cursor.defaultState == null && state != Blocks.AIR.defaultBlockState()) {
                cursor.defaultState = state;
                cursor.defaultId = id;
                cursor.defaultStateFlags = flags;
            }
            cursor.lastState = state;
            cursor.lastId = id;
            cursor.lastStateFlags = flags;
        }

        PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
        acc.papi$getStorage().set(index, id);
        cursor.recordWrite(index, flags);
    }
}
