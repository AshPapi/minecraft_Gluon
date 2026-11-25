package net.ashpapi.papioptimization.worldgen;

import java.util.Arrays;

public final class PapiWorldgenScratch {
    private static final ThreadLocal<PapiWorldgenScratch> LOCAL = ThreadLocal.withInitial(PapiWorldgenScratch::new);

    private NoisiumPaletteCursor[] sectionCursors = new NoisiumPaletteCursor[32];
    private boolean cursorsInUse;

    private PapiWorldgenScratch() {
        for (int i = 0; i < this.sectionCursors.length; i++) {
            this.sectionCursors[i] = new NoisiumPaletteCursor();
        }
    }

    public static PapiWorldgenScratch get() {
        return LOCAL.get();
    }

    public NoisiumPaletteCursor[] acquireCursors(int sectionCount) {
        if (this.cursorsInUse) {
            NoisiumPaletteCursor[] temp = new NoisiumPaletteCursor[sectionCount];
            for (int i = 0; i < sectionCount; i++) {
                temp[i] = new NoisiumPaletteCursor();
            }
            return temp;
        }
        this.cursorsInUse = true;
        if (this.sectionCursors.length < sectionCount) {
            int oldLen = this.sectionCursors.length;
            this.sectionCursors = Arrays.copyOf(this.sectionCursors, Math.max(sectionCount, oldLen * 2));
            for (int i = oldLen; i < this.sectionCursors.length; i++) {
                this.sectionCursors[i] = new NoisiumPaletteCursor();
            }
        }
        for (int i = 0; i < sectionCount; i++) {
            this.sectionCursors[i].resetAll();
        }
        return this.sectionCursors;
    }

    public void releaseCursors(NoisiumPaletteCursor[] cursors, int sectionCount) {
        if (cursors == this.sectionCursors) {
            for (int i = 0; i < sectionCount; i++) {
                cursors[i].resetAll();
            }
            this.cursorsInUse = false;
        }
    }
}
