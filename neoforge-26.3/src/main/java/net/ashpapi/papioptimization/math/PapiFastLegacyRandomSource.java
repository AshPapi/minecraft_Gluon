package net.ashpapi.papioptimization.math;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public final class PapiFastLegacyRandomSource implements BitRandomSource {
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private static final long MODULUS_MASK = 281474976710655L;

    private long seed;
    private MarsagliaPolarGaussian gaussianSource;

    public PapiFastLegacyRandomSource(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
    }

    @Override
    public RandomSource fork() {
        return new PapiFastLegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
        if (this.gaussianSource != null) {
            this.gaussianSource.reset();
        }
    }

    @Override
    public int next(int bits) {
        long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = nextSeed;
        return (int) (nextSeed >>> (48 - bits));
    }

    @Override
    public int nextInt() {
        long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = nextSeed;
        return (int) (nextSeed >>> 16);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & (bound - 1)) == 0) {
            long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
            this.seed = nextSeed;
            return (int) ((bound * (nextSeed >>> 17)) >> 31);
        }
        int bits;
        int val;
        do {
            long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
            this.seed = nextSeed;
            bits = (int) (nextSeed >>> 17);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    @Override
    public long nextLong() {
        long s1 = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        long s2 = (s1 * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = s2;
        return ((long) ((int) (s1 >>> 16)) << 32) + (int) (s2 >>> 16);
    }

    @Override
    public boolean nextBoolean() {
        long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = nextSeed;
        return (nextSeed >>> 47) != 0L;
    }

    @Override
    public float nextFloat() {
        long nextSeed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = nextSeed;
        return (float) ((int) (nextSeed >>> 24)) * FLOAT_MULTIPLIER;
    }

    @Override
    public double nextDouble() {
        long s1 = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        long s2 = (s1 * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        this.seed = s2;
        long combined = (((long) ((int) (s1 >>> 22))) << 27) + (int) (s2 >>> 21);
        return (double) combined * DOUBLE_MULTIPLIER;
    }

    @Override
    public double nextGaussian() {
        MarsagliaPolarGaussian g = this.gaussianSource;
        if (g == null) {
            g = new MarsagliaPolarGaussian(this);
            this.gaussianSource = g;
        }
        return g.nextGaussian();
    }
}
