package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.ashpapi.papioptimization.physics.CubePointRangePool;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CubeVoxelShape.class, priority = 1500)
public abstract class CubeVoxelShapeMixin extends VoxelShape {

    @Unique
    private DoubleList papi$coordsX;

    @Unique
    private DoubleList papi$coordsY;

    @Unique
    private DoubleList papi$coordsZ;

    protected CubeVoxelShapeMixin(DiscreteVoxelShape shape) {
        super(shape);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initPooledCoords(DiscreteVoxelShape discrete, CallbackInfo ci) {
        this.papi$coordsX = CubePointRangePool.get(discrete.getSize(Direction.Axis.X));
        this.papi$coordsY = CubePointRangePool.get(discrete.getSize(Direction.Axis.Y));
        this.papi$coordsZ = CubePointRangePool.get(discrete.getSize(Direction.Axis.Z));
    }

    @WrapMethod(method = "getCoords")
    private DoubleList papi$fastGetCoords(Direction.Axis axis, Operation<DoubleList> original) {
        return switch (axis.ordinal()) {
            case 0 -> {
                DoubleList cx = this.papi$coordsX;
                if (cx == null) {
                    cx = CubePointRangePool.get(this.shape.getSize(Direction.Axis.X));
                    this.papi$coordsX = cx;
                }
                yield cx;
            }
            case 1 -> {
                DoubleList cy = this.papi$coordsY;
                if (cy == null) {
                    cy = CubePointRangePool.get(this.shape.getSize(Direction.Axis.Y));
                    this.papi$coordsY = cy;
                }
                yield cy;
            }
            default -> {
                DoubleList cz = this.papi$coordsZ;
                if (cz == null) {
                    cz = CubePointRangePool.get(this.shape.getSize(Direction.Axis.Z));
                    this.papi$coordsZ = cz;
                }
                yield cz;
            }
        };
    }
}
