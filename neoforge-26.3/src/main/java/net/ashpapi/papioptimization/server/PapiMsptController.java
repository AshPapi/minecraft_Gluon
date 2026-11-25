package net.ashpapi.papioptimization.server;

import java.util.Arrays;
import net.ashpapi.papioptimization.config.PapiConfig;

public final class PapiMsptController {

    private static final int WINDOW = 100;
    private static final int EVAL_PERIOD_TICKS = 20;
    private static final double HYSTERESIS_MS = 2.0;

    private static final int[] AI_INTERVALS = {1, 2, 4, 8, 16};
    private static final double[] AI_OFFSETS = {-5.0, -1.0, 2.0, 6.0};

    private static final int[] VILLAGER_INTERVALS = {1, 4, 8, 12, 20};
    private static final double[] VILLAGER_OFFSETS = {-12.0, -6.0, -1.0, 3.0};

    private static final long[] RING = new long[WINDOW];
    private static final long[] SORT_BUF = new long[WINDOW];

    private static int cursor = 0;
    private static int samples = 0;
    private static int evalCounter = 0;
    private static int aiTier = 0;
    private static int villagerTier = 0;

    private static volatile double averageMspt = 0.0;
    private static volatile int currentAiInterval = 1;
    private static volatile int currentVillagerInterval = 1;

    private PapiMsptController() {}

    public static synchronized void recordTickNanos(long tickNanos) {
        if (tickNanos < 0L) {
            return;
        }
        RING[cursor] = tickNanos;
        if (samples < WINDOW) {
            samples++;
        }
        cursor = (cursor + 1) % WINDOW;

        if (++evalCounter >= EVAL_PERIOD_TICKS || samples <= 4) {
            evalCounter = 0;
            recomputeTrimmedMsptAndTiers();
        }
    }

    private static void recomputeTrimmedMsptAndTiers() {
        int count = samples;
        if (count <= 0) {
            averageMspt = 0.0;
            aiTier = 0;
            villagerTier = 0;
            currentAiInterval = 1;
            currentVillagerInterval = 1;
            return;
        }
        System.arraycopy(RING, 0, SORT_BUF, 0, count);
        Arrays.sort(SORT_BUF, 0, count);
        int keep = Math.max(1, count - (count / 10));
        long trimmedSum = 0L;
        for (int i = 0; i < keep; i++) {
            trimmedSum += SORT_BUF[i];
        }
        double mspt = ((double) trimmedSum / (double) keep) * 1.0E-6;
        averageMspt = mspt;

        double target = PapiConfig.rawMsptTarget();
        aiTier = stepTierWithHysteresis(aiTier, mspt, target, AI_OFFSETS);
        villagerTier = stepTierWithHysteresis(villagerTier, mspt, target, VILLAGER_OFFSETS);
        currentAiInterval = AI_INTERVALS[aiTier];
        currentVillagerInterval = VILLAGER_INTERVALS[villagerTier];
    }

    private static int stepTierWithHysteresis(int curTier, double mspt, double target, double[] offsets) {
        int nextTier = curTier;
        int maxTier = offsets.length;
        while (nextTier < maxTier && mspt >= target + offsets[nextTier] + HYSTERESIS_MS) {
            nextTier++;
        }
        while (nextTier > 0 && mspt <= target + offsets[nextTier - 1] - HYSTERESIS_MS) {
            nextTier--;
        }
        return nextTier;
    }

    public static synchronized void reset() {
        Arrays.fill(RING, 0L);
        Arrays.fill(SORT_BUF, 0L);
        cursor = 0;
        samples = 0;
        evalCounter = 0;
        aiTier = 0;
        villagerTier = 0;
        averageMspt = 0.0;
        currentAiInterval = 1;
        currentVillagerInterval = 1;
    }

    public static double getAverageMspt() {
        return averageMspt;
    }

    public static int getAiThrottleInterval() {
        return currentAiInterval;
    }

    public static int getVillagerLobotomyInterval() {
        return currentVillagerInterval;
    }

    public static int getAutosaveBatchSize(int queueSize) {
        double mspt = averageMspt;
        double target = PapiConfig.rawMsptTarget();
        if (mspt >= target + 1.0) {
            return Math.max(4, queueSize / 80);
        }
        if (mspt >= target - 10.0) {
            return Math.max(12, queueSize / 50);
        }
        return Math.max(16, queueSize / 35);
    }
}
