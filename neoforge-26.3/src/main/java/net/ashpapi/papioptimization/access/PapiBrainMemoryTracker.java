package net.ashpapi.papioptimization.access;

import net.minecraft.world.entity.ai.sensing.SensorType;

public interface PapiBrainMemoryTracker {

    long papi$getMemoryModCount();

    void papi$setSensorActive(SensorType<?> sensorType, boolean active);
}
