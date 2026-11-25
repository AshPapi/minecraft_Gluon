package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPos.class, priority = 1500)
public abstract class BlockPosMixin extends Vec3i {

    protected BlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    @Shadow
    public abstract BlockPos immutable();

    @Inject(method = "betweenClosed(Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastBetweenClosedAabb(AABB aabb, CallbackInfoReturnable<Iterable<BlockPos>> cir) {
        int x1 = Mth.floor(aabb.minX);
        int y1 = Mth.floor(aabb.minY);
        int z1 = Mth.floor(aabb.minZ);
        int x2 = Mth.floor(aabb.maxX);
        int y2 = Mth.floor(aabb.maxY);
        int z2 = Mth.floor(aabb.maxZ);
        cir.setReturnValue(BlockPos.betweenClosed(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        ));
    }

    @WrapMethod(method = "above()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastAbove(Operation<BlockPos> original) {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
    }

    @WrapMethod(method = "above(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastAboveDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY() + distance, this.getZ());
    }

    @WrapMethod(method = "below()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastBelow(Operation<BlockPos> original) {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
    }

    @WrapMethod(method = "below(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastBelowDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY() - distance, this.getZ());
    }

    @WrapMethod(method = "north()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastNorth(Operation<BlockPos> original) {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
    }

    @WrapMethod(method = "north(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastNorthDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY(), this.getZ() - distance);
    }

    @WrapMethod(method = "south()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastSouth(Operation<BlockPos> original) {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
    }

    @WrapMethod(method = "south(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastSouthDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY(), this.getZ() + distance);
    }

    @WrapMethod(method = "west()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastWest(Operation<BlockPos> original) {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
    }

    @WrapMethod(method = "west(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastWestDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX() - distance, this.getY(), this.getZ());
    }

    @WrapMethod(method = "east()Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastEast(Operation<BlockPos> original) {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
    }

    @WrapMethod(method = "east(I)Lnet/minecraft/core/BlockPos;")
    private BlockPos papi$fastEastDist(int distance, Operation<BlockPos> original) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX() + distance, this.getY(), this.getZ());
    }
}
