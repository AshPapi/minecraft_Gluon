package net.ashpapi.papioptimization.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface PapiVoxelShapeExtension {
    @Nullable AABB papi$getSingleBox();

    boolean papi$isSingleBox();

    @Nullable AABB papi$getCuboidBox();

    @Nullable AABB papi$getBoundsBox();

    void papi$initMovedState(byte state, @Nullable AABB singleOrCuboidBox, @Nullable AABB boundsBox);

    boolean papi$isFullBlock();

    VoxelShape papi$getDisplacedPistonShape(float absOffset, Direction direction);
}
