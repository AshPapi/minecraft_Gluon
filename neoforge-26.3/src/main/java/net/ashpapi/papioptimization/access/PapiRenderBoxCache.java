package net.ashpapi.papioptimization.access;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public interface PapiRenderBoxCache {
    AABB papi$getOrComputeChestBox(BlockPos pos);
}
