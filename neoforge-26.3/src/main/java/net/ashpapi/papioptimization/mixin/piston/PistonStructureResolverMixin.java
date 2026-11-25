package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collections;
import java.util.List;
import net.ashpapi.papioptimization.util.DirectionConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PistonStructureResolver.class, priority = 1500)
public abstract class PistonStructureResolverMixin {
    @Shadow
    @Final
    private List<BlockPos> toPush;

    @WrapMethod(method = "reorderListAtCollision")
    private void papi$fastReorderListAtCollision(int blocksAdded, int collisionPos, Operation<Void> original) {
        Collections.rotate(this.toPush.subList(collisionPos, this.toPush.size()), blocksAdded);
    }

    @WrapOperation(
            method = "addBranchingBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;")
    )
    private Direction[] papi$sharedDirections(Operation<Direction[]> original) {
        return DirectionConstants.ALL;
    }
}
