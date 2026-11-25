package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Block.class, priority = 1500)
public abstract class BlockFullShapeCacheMixin {

    @WrapMethod(method = "isShapeFullBlock")
    private static boolean papi$fastIsShapeFullBlock(VoxelShape shape, Operation<Boolean> original) {
        if (shape instanceof PapiVoxelShapeExtension ext) {
            return ext.papi$isFullBlock();
        }
        return original.call(shape);
    }
}
