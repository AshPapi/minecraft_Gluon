package net.ashpapi.papioptimization.physics;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import net.minecraft.world.phys.shapes.IndexMerger;
import org.jspecify.annotations.Nullable;

public final class FlatIndexMerger implements IndexMerger {
    private static final double EPSILON = 1.0E-7;

    private final double[] result;
    private final int[] firstIndices;
    private final int[] secondIndices;
    private final int resultLength;
    private @Nullable DoubleList listView;

    public FlatIndexMerger(DoubleList first, DoubleList second, boolean firstOnly, boolean secondOnly) {
        int s1 = first.size();
        int s2 = second.size();
        int capacity = s1 + s2;
        double[] out = new double[capacity];
        int[] outFirst = new int[capacity];
        int[] outSecond = new int[capacity];

        double[] raw1 = extractRaw(first, s1);
        double[] raw2 = extractRaw(second, s2);

        boolean skipFirstTail = !firstOnly;
        boolean skipSecondTail = !secondOnly;
        double lastVal = Double.NaN;
        int outIdx = 0;
        int i1 = 0;
        int i2 = 0;

        while (true) {
            boolean end1 = i1 >= s1;
            boolean end2 = i2 >= s2;
            if (end1 && end2) {
                this.result = out;
                this.firstIndices = outFirst;
                this.secondIndices = outSecond;
                this.resultLength = Math.max(1, outIdx);
                return;
            }

            boolean pick1;
            if (end1) {
                pick1 = false;
            } else if (end2) {
                pick1 = true;
            } else {
                double v1 = raw1 != null ? raw1[i1] : first.getDouble(i1);
                double v2 = raw2 != null ? raw2[i2] : second.getDouble(i2);
                pick1 = v1 < v2 + EPSILON;
            }

            if (pick1) {
                i1++;
                if (skipFirstTail && (i2 == 0 || end2)) {
                    continue;
                }
            } else {
                i2++;
                if (skipSecondTail && (i1 == 0 || end1)) {
                    continue;
                }
            }

            int fIdx = i1 - 1;
            int sIdx = i2 - 1;
            double val;
            if (pick1) {
                val = raw1 != null ? raw1[fIdx] : first.getDouble(fIdx);
            } else {
                val = raw2 != null ? raw2[sIdx] : second.getDouble(sIdx);
            }

            if (!(lastVal >= val - EPSILON)) {
                outFirst[outIdx] = fIdx;
                outSecond[outIdx] = sIdx;
                out[outIdx] = val;
                outIdx++;
                lastVal = val;
            } else {
                int prev = outIdx - 1;
                outFirst[prev] = fIdx;
                outSecond[prev] = sIdx;
            }
        }
    }

    private static double @Nullable [] extractRaw(DoubleList list, int expectedSize) {
        if (list instanceof DoubleArrayList dal) {
            double[] elems = dal.elements();
            if (elems.length >= expectedSize) {
                return elems;
            }
        } else if (list instanceof PapiDoubleArrayBacked backed) {
            double[] pts = backed.papi$getRawPoints();
            if (pts != null && pts.length >= expectedSize) {
                return pts;
            }
        }
        return null;
    }

    @Override
    public boolean forMergedIndexes(IndexConsumer consumer) {
        int limit = this.resultLength - 1;
        int[] f = this.firstIndices;
        int[] s = this.secondIndices;
        for (int i = 0; i < limit; i++) {
            if (!consumer.merge(f[i], s[i], i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int size() {
        return this.resultLength;
    }

    @Override
    public DoubleList getList() {
        if (this.resultLength <= 1) {
            return DoubleLists.EMPTY_LIST;
        }
        DoubleList view = this.listView;
        if (view == null) {
            view = DoubleArrayList.wrap(this.result, this.resultLength);
            this.listView = view;
        }
        return view;
    }
}
