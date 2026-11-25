package net.ashpapi.papioptimization.mixin.memory;

import java.util.BitSet;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BitSetDiscreteVoxelShape.class, priority = 1500)
public interface BitSetDiscreteVoxelShapeAccessor {

    @Accessor("storage")
    BitSet papi$getStorage();

    @Accessor("xMin")
    int papi$getXMin();

    @Accessor("yMin")
    int papi$getYMin();

    @Accessor("zMin")
    int papi$getZMin();

    @Accessor("xMax")
    int papi$getXMax();

    @Accessor("yMax")
    int papi$getYMax();

    @Accessor("zMax")
    int papi$getZMax();
}
