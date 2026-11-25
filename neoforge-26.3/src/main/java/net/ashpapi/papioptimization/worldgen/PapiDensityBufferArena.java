package net.ashpapi.papioptimization.worldgen;

import java.util.Arrays;
import net.ashpapi.papioptimization.mixin.worldgen.DensityBufferAccessor;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;

public final class PapiDensityBufferArena {
    private static final int MIN_SHIFT = 8;
    private static final int MAX_SHIFT = 20;
    private static final int BUCKET_COUNT = MAX_SHIFT - MIN_SHIFT + 1;
    private static final int MAX_PER_BUCKET = 4;

    private static final ThreadLocal<PapiDensityBufferArena> LOCAL = ThreadLocal.withInitial(PapiDensityBufferArena::new);

    private final DensityBuffer[][] buckets = new DensityBuffer[BUCKET_COUNT][MAX_PER_BUCKET];
    private final int[] bucketCounts = new int[BUCKET_COUNT];
    private DensityBuffer[] activeStack = new DensityBuffer[16];
    private int[] scopeStarts = new int[8];
    private int activeCount;
    private int scopeDepth;

    private PapiDensityBufferArena() {
    }

    public static void enterScope() {
        PapiDensityBufferArena arena = LOCAL.get();
        int d = arena.scopeDepth;
        if (d == arena.scopeStarts.length) {
            arena.scopeStarts = Arrays.copyOf(arena.scopeStarts, d * 2);
        }
        arena.scopeStarts[d] = arena.activeCount;
        arena.scopeDepth = d + 1;
    }

    public static void exitScope() {
        PapiDensityBufferArena arena = LOCAL.get();
        int d = arena.scopeDepth - 1;
        if (d < 0) {
            arena.scopeDepth = 0;
            return;
        }
        arena.scopeDepth = d;
        int target = arena.scopeStarts[d];
        while (arena.activeCount > target) {
            int idx = --arena.activeCount;
            DensityBuffer buf = arena.activeStack[idx];
            arena.activeStack[idx] = null;
            arena.returnToBucket(buf);
        }
    }

    public static DensityBuffer acquireUnpooled(int requestedSize) {
        if (requestedSize <= 0) {
            return DensityBuffer.createUnpooled(requestedSize);
        }
        PapiDensityBufferArena arena = LOCAL.get();
        if (arena.scopeDepth <= 0) {
            return DensityBuffer.createUnpooled(requestedSize);
        }
        int bucketIdx = bucketFor(requestedSize);
        if (bucketIdx < 0) {
            return DensityBuffer.createUnpooled(requestedSize);
        }
        DensityBuffer buf;
        int count = arena.bucketCounts[bucketIdx];
        if (count > 0) {
            int nextCount = count - 1;
            arena.bucketCounts[bucketIdx] = nextCount;
            buf = arena.buckets[bucketIdx][nextCount];
            arena.buckets[bucketIdx][nextCount] = null;
            ((DensityBufferAccessor) buf).papi$setSize(requestedSize);
            Arrays.fill(((DensityBufferAccessor) buf).papi$getValues(), 0, requestedSize, 0.0F);
        } else {
            int allocCap = 1 << (bucketIdx + MIN_SHIFT);
            buf = DensityBuffer.createUnpooled(allocCap);
            ((DensityBufferAccessor) buf).papi$setSize(requestedSize);
        }
        if (arena.activeCount == arena.activeStack.length) {
            arena.activeStack = Arrays.copyOf(arena.activeStack, arena.activeCount * 2);
        }
        arena.activeStack[arena.activeCount++] = buf;
        return buf;
    }

    private void returnToBucket(DensityBuffer buf) {
        int cap = buf.capacity();
        if (cap <= 0 || (cap & (cap - 1)) != 0) {
            return;
        }
        int shift = 31 - Integer.numberOfLeadingZeros(cap);
        int bucketIdx = shift - MIN_SHIFT;
        if (bucketIdx < 0 || bucketIdx >= BUCKET_COUNT) {
            return;
        }
        int count = this.bucketCounts[bucketIdx];
        if (count < MAX_PER_BUCKET) {
            this.buckets[bucketIdx][count] = buf;
            this.bucketCounts[bucketIdx] = count + 1;
        }
    }

    private static int bucketFor(int size) {
        int shift = 32 - Integer.numberOfLeadingZeros(Math.max(size - 1, 1));
        if (shift < MIN_SHIFT) {
            shift = MIN_SHIFT;
        }
        if (shift > MAX_SHIFT) {
            return -1;
        }
        return shift - MIN_SHIFT;
    }
}
