package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.SubShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubShape.class, priority = 1500)
public interface SubShapeAccessor {

    @Accessor("parent")
    DiscreteVoxelShape papi$getParent();

    @Accessor("parent")
    @Mutable
    void papi$setParent(DiscreteVoxelShape parent);

    @Accessor("startX")
    int papi$getStartX();

    @Accessor("startY")
    int papi$getStartY();

    @Accessor("startZ")
    int papi$getStartZ();

    @Accessor("endX")
    int papi$getEndX();

    @Accessor("endY")
    int papi$getEndY();

    @Accessor("endZ")
    int papi$getEndZ();
}
