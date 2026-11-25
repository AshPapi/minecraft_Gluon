package net.ashpapi.papioptimization.memory;

import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.ReportedException;
import net.minecraft.util.ThreadingDetector;

public final class PapiCompactThreadingDetector {

    public static final byte UNLOCKED = 0;
    public static final byte LOCKED = 1;
    public static final byte CRASHING = 2;

    private static final long WAIT_TIMEOUT_NANOS = 200_000_000L;
    private static final Map<Object, ContentionInfo> ACTIVE_CONTENTION = new ConcurrentHashMap<>();

    private PapiCompactThreadingDetector() {
    }

    public static void handleAcquireContention(Object container, VarHandle stateHandle, String name) {
        ContentionInfo info = ACTIVE_CONTENTION.computeIfAbsent(container, k -> new ContentionInfo(Thread.currentThread()));
        stateHandle.setVolatile(container, CRASHING);
        long deadline = System.nanoTime() + WAIT_TIMEOUT_NANOS;
        while (info.ownerThread == null && System.nanoTime() - deadline < 0L) {
            LockSupport.parkNanos(1_000_000L);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ACTIVE_CONTENTION.remove(container, info);
        Thread other = info.ownerThread != null ? info.ownerThread : info.contendingThread;
        ReportedException crash = ThreadingDetector.makeThreadingException(name, other);
        info.crash = crash;
        throw crash;
    }

    public static void handleReleaseContention(Object container, VarHandle stateHandle, String name) {
        ContentionInfo info = ACTIVE_CONTENTION.get(container);
        if (info != null) {
            info.ownerThread = Thread.currentThread();
            LockSupport.unpark(info.contendingThread);
            long deadline = System.nanoTime() + WAIT_TIMEOUT_NANOS;
            while (info.crash == null && System.nanoTime() - deadline < 0L) {
                LockSupport.parkNanos(1_000_000L);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            ACTIVE_CONTENTION.remove(container, info);
            if (info.crash != null) {
                throw info.crash;
            }
        }
        stateHandle.setVolatile(container, UNLOCKED);
        throw ThreadingDetector.makeThreadingException(name, null);
    }

    private static final class ContentionInfo {
        final Thread contendingThread;
        volatile Thread ownerThread;
        volatile ReportedException crash;

        ContentionInfo(Thread contendingThread) {
            this.contendingThread = contendingThread;
        }
    }
}
