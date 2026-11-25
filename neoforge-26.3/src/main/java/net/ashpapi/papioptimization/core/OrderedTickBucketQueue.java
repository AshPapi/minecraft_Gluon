package net.ashpapi.papioptimization.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.Nullable;

public final class OrderedTickBucketQueue<T> extends AbstractQueue<ScheduledTick<T>> {
    private final Long2ObjectOpenHashMap<Bucket<T>> buckets = new Long2ObjectOpenHashMap<>(8);
    private int totalSize;
    private long minBucketKey = Long.MAX_VALUE;
    private @Nullable Bucket<T> minBucket;

    private static long bucketKey(ScheduledTick<?> tick) {
        return (tick.triggerTick() << 3) | (long) (tick.priority().ordinal() & 7);
    }

    @Override
    public boolean offer(ScheduledTick<T> tick) {
        if (tick == null) {
            throw new NullPointerException();
        }
        long key = bucketKey(tick);
        Bucket<T> bucket = this.buckets.get(key);
        if (bucket == null) {
            bucket = new Bucket<>();
            this.buckets.put(key, bucket);
        }
        bucket.add(tick);
        this.totalSize++;
        if (key < this.minBucketKey || (key == this.minBucketKey && this.minBucket == null)) {
            this.minBucketKey = key;
            this.minBucket = bucket;
        }
        return true;
    }

    @Override
    public @Nullable ScheduledTick<T> peek() {
        if (this.totalSize == 0) {
            return null;
        }
        Bucket<T> b = this.resolveMinBucket();
        return b != null ? b.peek() : null;
    }

    @Override
    public @Nullable ScheduledTick<T> poll() {
        if (this.totalSize == 0) {
            return null;
        }
        Bucket<T> b = this.resolveMinBucket();
        if (b == null) {
            return null;
        }
        ScheduledTick<T> out = b.poll();
        this.totalSize--;
        if (b.isEmpty()) {
            this.buckets.remove(this.minBucketKey);
            this.minBucket = null;
            this.minBucketKey = Long.MAX_VALUE;
        }
        return out;
    }

    private @Nullable Bucket<T> resolveMinBucket() {
        Bucket<T> cur = this.minBucket;
        if (cur != null && !cur.isEmpty()) {
            return cur;
        }
        long bestKey = Long.MAX_VALUE;
        Bucket<T> best = null;
        var iter = this.buckets.long2ObjectEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Bucket<T> candidate = entry.getValue();
            if (!candidate.isEmpty()) {
                long k = entry.getLongKey();
                if (best == null || k < bestKey) {
                    bestKey = k;
                    best = candidate;
                }
            }
        }
        this.minBucketKey = bestKey;
        this.minBucket = best;
        return best;
    }

    @Override
    public boolean removeIf(Predicate<? super ScheduledTick<T>> filter) {
        if (this.totalSize == 0) {
            return false;
        }
        int removedTotal = 0;
        var iter = this.buckets.long2ObjectEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Bucket<T> b = entry.getValue();
            int rem = b.removeIf(filter);
            if (rem > 0) {
                removedTotal += rem;
                if (b.isEmpty()) {
                    if (b == this.minBucket) {
                        this.minBucket = null;
                        this.minBucketKey = Long.MAX_VALUE;
                    }
                    iter.remove();
                }
            }
        }
        this.totalSize -= removedTotal;
        return removedTotal > 0;
    }

    @Override
    public void clear() {
        this.buckets.clear();
        this.totalSize = 0;
        this.minBucket = null;
        this.minBucketKey = Long.MAX_VALUE;
    }

    @Override
    public int size() {
        return this.totalSize;
    }

    @Override
    public boolean isEmpty() {
        return this.totalSize == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<ScheduledTick<T>> iterator() {
        if (this.totalSize == 0) {
            return Collections.emptyIterator();
        }
        long[] keys = this.buckets.keySet().toLongArray();
        Arrays.sort(keys);
        ScheduledTick<T>[] snapshot = (ScheduledTick<T>[]) new ScheduledTick<?>[this.totalSize];
        int pos = 0;
        for (int i = 0; i < keys.length; i++) {
            Bucket<T> b = this.buckets.get(keys[i]);
            if (b != null && !b.isEmpty()) {
                pos = b.copySortedInto(snapshot, pos);
            }
        }
        final int len = pos;
        return new Iterator<>() {
            private int idx;

            @Override
            public boolean hasNext() {
                return this.idx < len;
            }

            @Override
            public ScheduledTick<T> next() {
                if (this.idx >= len) {
                    throw new NoSuchElementException();
                }
                return snapshot[this.idx++];
            }
        };
    }

    private static final class Bucket<T> {
        @SuppressWarnings("unchecked")
        private ScheduledTick<T>[] items = (ScheduledTick<T>[]) new ScheduledTick<?>[4];
        private int head;
        private int tail;
        private boolean unsorted;
        private long lastSubTick = Long.MIN_VALUE;

        void add(ScheduledTick<T> tick) {
            if (this.tail == this.items.length) {
                if (this.head > 0) {
                    int count = this.tail - this.head;
                    System.arraycopy(this.items, this.head, this.items, 0, count);
                    Arrays.fill(this.items, count, this.tail, null);
                    this.head = 0;
                    this.tail = count;
                } else {
                    this.items = Arrays.copyOf(this.items, this.items.length << 1);
                }
            }
            long sub = tick.subTickOrder();
            if (this.tail > this.head && sub < this.lastSubTick) {
                this.unsorted = true;
            } else {
                this.lastSubTick = sub;
            }
            this.items[this.tail++] = tick;
        }

        private void ensureSorted() {
            if (this.unsorted) {
                Arrays.sort(this.items, this.head, this.tail, ScheduledTick.INTRA_TICK_DRAIN_ORDER);
                this.unsorted = false;
                this.lastSubTick = this.tail > this.head ? this.items[this.tail - 1].subTickOrder() : Long.MIN_VALUE;
            }
        }

        ScheduledTick<T> peek() {
            this.ensureSorted();
            return this.items[this.head];
        }

        ScheduledTick<T> poll() {
            this.ensureSorted();
            ScheduledTick<T> out = this.items[this.head];
            this.items[this.head++] = null;
            if (this.head == this.tail) {
                this.head = 0;
                this.tail = 0;
                this.lastSubTick = Long.MIN_VALUE;
            }
            return out;
        }

        boolean isEmpty() {
            return this.head == this.tail;
        }

        int removeIf(Predicate<? super ScheduledTick<T>> filter) {
            int write = this.head;
            int removed = 0;
            for (int r = this.head; r < this.tail; r++) {
                ScheduledTick<T> item = this.items[r];
                if (filter.test(item)) {
                    this.items[r] = null;
                    removed++;
                } else {
                    if (write != r) {
                        this.items[write] = item;
                        this.items[r] = null;
                    }
                    write++;
                }
            }
            this.tail = write;
            if (this.head == this.tail) {
                this.head = 0;
                this.tail = 0;
                this.unsorted = false;
                this.lastSubTick = Long.MIN_VALUE;
            } else if (!this.unsorted) {
                this.lastSubTick = this.items[this.tail - 1].subTickOrder();
            }
            return removed;
        }

        int copySortedInto(ScheduledTick<T>[] dst, int dstPos) {
            this.ensureSorted();
            int len = this.tail - this.head;
            System.arraycopy(this.items, this.head, dst, dstPos, len);
            return dstPos + len;
        }
    }
}
