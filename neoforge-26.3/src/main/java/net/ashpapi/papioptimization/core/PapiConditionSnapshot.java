package net.ashpapi.papioptimization.core;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public record PapiConditionSnapshot(
        int sourceSize,
        MemoryModuleType<?>[] keys,
        MemoryStatus[] statuses
) {
    public static final PapiConditionSnapshot EMPTY = new PapiConditionSnapshot(
            0,
            new MemoryModuleType<?>[0],
            new MemoryStatus[0]
    );
}
