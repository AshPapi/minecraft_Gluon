package net.ashpapi.papioptimization.mixin.memory;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ArrayVoxelShape.class, priority = 1500)
public interface ArrayVoxelShapeAccessor {

    @Accessor("xs")
    DoubleList papi$getXs();

    @Accessor("xs")
    @Mutable
    void papi$setXs(DoubleList xs);

    @Accessor("ys")
    DoubleList papi$getYs();

    @Accessor("ys")
    @Mutable
    void papi$setYs(DoubleList ys);

    @Accessor("zs")
    DoubleList papi$getZs();

    @Accessor("zs")
    @Mutable
    void papi$setZs(DoubleList zs);
}
