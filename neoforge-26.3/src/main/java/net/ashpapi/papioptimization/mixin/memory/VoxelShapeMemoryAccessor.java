package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VoxelShape.class, priority = 1500)
public interface VoxelShapeMemoryAccessor {

    @Accessor("shape")
    DiscreteVoxelShape papi$getShape();

    @Accessor("shape")
    @Mutable
    void papi$setShape(DiscreteVoxelShape shape);

    @Accessor("faces")
    VoxelShape @Nullable [] papi$getFaces();

    @Accessor("faces")
    @Mutable
    void papi$setFaces(VoxelShape @Nullable [] faces);
}
