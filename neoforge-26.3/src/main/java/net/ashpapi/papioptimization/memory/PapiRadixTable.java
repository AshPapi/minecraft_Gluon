package net.ashpapi.papioptimization.memory;

import org.jspecify.annotations.Nullable;

public final class PapiRadixTable<S> {
    public final S[] states;
    public final int[] strides;
    public final int[] valueCounts;
    public final boolean bitMode;
    public final int @Nullable [] keepMasks;
    public final int @Nullable [] shifts;

    public PapiRadixTable(
            S[] states,
            int[] strides,
            int[] valueCounts,
            boolean bitMode,
            int @Nullable [] keepMasks,
            int @Nullable [] shifts
    ) {
        this.states = states;
        this.strides = strides;
        this.valueCounts = valueCounts;
        this.bitMode = bitMode;
        this.keepMasks = keepMasks;
        this.shifts = shifts;
    }

    public static int computeTotalBits(int[] valueCounts, int totalCount) {
        int totalBits = 0;
        for (int vc : valueCounts) {
            if (vc > 1) {
                totalBits += 32 - Integer.numberOfLeadingZeros(vc - 1);
            }
            if (totalBits > 30) {
                return -1;
            }
        }
        int bitCapacity = 1 << totalBits;
        if (bitCapacity > Math.max(64, totalCount * 2)) {
            return -1;
        }
        return totalBits;
    }

    public static void fillShiftsAndMasks(int[] valueCounts, int[] shifts, int[] keepMasks) {
        int propCount = valueCounts.length;
        int shift = 0;
        for (int k = propCount - 1; k >= 0; k--) {
            shifts[k] = shift;
            int vc = valueCounts[k];
            int bits = vc > 1 ? (32 - Integer.numberOfLeadingZeros(vc - 1)) : 0;
            int fieldMask = bits == 0 ? 0 : (((1 << bits) - 1) << shift);
            keepMasks[k] = ~fieldMask;
            shift += bits;
        }
    }
}
