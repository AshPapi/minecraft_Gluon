package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SliceShape.class, priority = 1500)
public interface SliceShapeAccessor {

    @Accessor("delegate")
    VoxelShape papi$getDelegate();

    @Accessor("delegate")
    @Mutable
    void papi$setDelegate(VoxelShape delegate);

    @Accessor("axis")
    Direction.Axis papi$getAxis();
}
