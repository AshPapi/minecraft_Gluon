package net.ashpapi.papioptimization.network;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.ashpapi.papioptimization.config.PapiConfig;

public final class PapiCompressionPool {

    private static final int MAX_POOL_SIZE = 32;

    private static final ConcurrentLinkedQueue<Deflater> DEFLATERS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger DEFLATER_COUNT = new AtomicInteger(0);

    private static final ConcurrentLinkedQueue<Inflater> INFLATERS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger INFLATER_COUNT = new AtomicInteger(0);

    private static final FastThreadLocal<LocalSlot> LOCAL_SLOT = new FastThreadLocal<>() {
        @Override
        protected LocalSlot initialValue() {
            return new LocalSlot();
        }

        @Override
        protected void onRemoval(LocalSlot value) {
            if (value != null) {
                value.close();
            }
        }
    };

    private PapiCompressionPool() {
    }

    private static int configuredLevel() {
        int lvl = PapiConfig.rawNetworkCompressionLevel();
        if (lvl < -1 || lvl > 9) {
            return 4;
        }
        return lvl;
    }

    public static Deflater acquireDeflater() {
        int targetLevel = configuredLevel();
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            LocalSlot slot = LOCAL_SLOT.get();
            if (!slot.deflaterInUse) {
                slot.deflaterInUse = true;
                Deflater d = slot.deflater;
                if (d == null) {
                    d = new Deflater(targetLevel);
                    slot.deflater = d;
                    slot.deflaterLevel = targetLevel;
                } else if (slot.deflaterLevel != targetLevel) {
                    d.setLevel(targetLevel);
                    slot.deflaterLevel = targetLevel;
                }
                return d;
            }
        }
        Deflater d = DEFLATERS.poll();
        if (d != null) {
            DEFLATER_COUNT.decrementAndGet();
            d.setLevel(targetLevel);
            return d;
        }
        return new Deflater(targetLevel);
    }

    public static void releaseDeflater(Deflater d) {
        if (d == null) {
            return;
        }
        d.reset();
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            LocalSlot slot = LOCAL_SLOT.getIfExists();
            if (slot != null && slot.deflater == d) {
                slot.deflaterInUse = false;
                return;
            }
        }
        if (DEFLATER_COUNT.incrementAndGet() <= MAX_POOL_SIZE) {
            DEFLATERS.offer(d);
        } else {
            DEFLATER_COUNT.decrementAndGet();
            d.end();
        }
    }

    public static Inflater acquireInflater() {
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            LocalSlot slot = LOCAL_SLOT.get();
            if (!slot.inflaterInUse) {
                slot.inflaterInUse = true;
                Inflater inf = slot.inflater;
                if (inf == null) {
                    inf = new Inflater();
                    slot.inflater = inf;
                }
                return inf;
            }
        }
        Inflater inf = INFLATERS.poll();
        if (inf != null) {
            INFLATER_COUNT.decrementAndGet();
            return inf;
        }
        return new Inflater();
    }

    public static void releaseInflater(Inflater inf) {
        if (inf == null) {
            return;
        }
        inf.reset();
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            LocalSlot slot = LOCAL_SLOT.getIfExists();
            if (slot != null && slot.inflater == inf) {
                slot.inflaterInUse = false;
                return;
            }
        }
        if (INFLATER_COUNT.incrementAndGet() <= MAX_POOL_SIZE) {
            INFLATERS.offer(inf);
        } else {
            INFLATER_COUNT.decrementAndGet();
            inf.end();
        }
    }

    private static final class LocalSlot {
        Deflater deflater;
        int deflaterLevel = -2;
        boolean deflaterInUse;
        Inflater inflater;
        boolean inflaterInUse;

        void close() {
            Deflater d = this.deflater;
            this.deflater = null;
            if (d != null) {
                d.end();
            }
            Inflater inf = this.inflater;
            this.inflater = null;
            if (inf != null) {
                inf.end();
            }
        }
    }
}
