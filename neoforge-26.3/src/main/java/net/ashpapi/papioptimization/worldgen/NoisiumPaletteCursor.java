package net.ashpapi.papioptimization.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public final class NoisiumPaletteCursor {
    public @Nullable Object dataRef;
    public @Nullable BlockState lastState;
    public int lastId = -1;
    public int lastStateFlags;
    public @Nullable BlockState defaultState;
    public int defaultId = -1;
    public int defaultStateFlags;
    public int airId = -1;
    public int nonEmptyCount;
    public int tickingBlockCount;
    public int fluidCount;
    public int tickingFluidCount;
    public long[] randomTickBits;

    public static int computeFlags(BlockState state) {
        if (state.isEmpty()) {
            return 0;
        }
        int flags = 1;
        if (state.isRandomlyTicking()) {
            flags |= 2;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            flags |= 4;
            if (fluid.isRandomlyTicking()) {
                flags |= 8;
            }
        }
        return flags;
    }

    public void recordWrite(int index, int flags) {
        this.nonEmptyCount += flags & 1;
        int tb = (flags >>> 1) & 1;
        int tf = (flags >>> 3) & 1;
        this.tickingBlockCount += tb;
        this.fluidCount += (flags >>> 2) & 1;
        this.tickingFluidCount += tf;
        if ((tb | tf) != 0) {
            long[] bits = this.randomTickBits;
            if (bits == null) {
                bits = new long[64];
                this.randomTickBits = bits;
            }
            bits[index >>> 6] |= 1L << (index & 63);
        }
    }

    public void resetPalette() {
        this.dataRef = null;
        this.lastState = null;
        this.lastId = -1;
        this.lastStateFlags = 0;
        this.defaultState = null;
        this.defaultId = -1;
        this.defaultStateFlags = 0;
        this.airId = -1;
    }

    public void resetAll() {
        this.resetPalette();
        this.nonEmptyCount = 0;
        this.tickingBlockCount = 0;
        this.fluidCount = 0;
        this.tickingFluidCount = 0;
        this.randomTickBits = null;
    }
}
