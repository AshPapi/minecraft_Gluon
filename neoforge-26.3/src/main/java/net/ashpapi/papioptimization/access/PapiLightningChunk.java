package net.ashpapi.papioptimization.access;

import net.minecraft.util.RandomSource;

public interface PapiLightningChunk {

    boolean papi$shouldDoLightning(RandomSource random, int chance);
}
