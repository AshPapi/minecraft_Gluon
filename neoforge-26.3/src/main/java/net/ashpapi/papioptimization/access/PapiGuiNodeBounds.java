package net.ashpapi.papioptimization.access;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PapiGuiNodeBounds {

    int PAPI$CONTOUR_ELEMENTS = 0;
    int PAPI$CONTOUR_ITEMS = 1;
    int PAPI$CONTOUR_TEXTS = 2;
    int PAPI$CONTOUR_PIP = 3;

    int papi$checkIntersection(int kind, int actualListSize, int left, int top, int right, int bottom);

    static long papi$computeTileMask(int left, int top, int right, int bottom) {
        int x0 = Math.clamp(left >> 6, 0, 7);
        int x1 = Math.clamp((right - 1) >> 6, 0, 7);
        int y0 = Math.clamp(top >> 6, 0, 7);
        int y1 = Math.clamp((bottom - 1) >> 6, 0, 7);
        long rowBits = (2L << x1) - (1L << x0);
        long yMask = (-1L >>> ((7 - y1) << 3)) & (-1L << (y0 << 3));
        return (0x0101010101010101L * rowBits) & yMask;
    }
}
