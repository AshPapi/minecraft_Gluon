package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.ashpapi.papioptimization.physics.VoxelShapeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockCollisions.class, priority = 1500)
public abstract class BlockCollisionsMixin {

    @Shadow
    @Final
    @Mutable
    private VoxelShape entityShape;

    @Shadow
    @Final
    private AABB box;

    @Unique
    private ChunkAccess papi$lastChunk;

    @Unique
    private int papi$lastSectionY = Integer.MIN_VALUE;

    @Unique
    private LevelChunkSection papi$lastSection;

    @Unique
    private byte papi$boxTinyState;

    @Unique
    private boolean papi$isTargetBoxTiny() {
        byte s = this.papi$boxTinyState;
        if (s == 0) {
            s = (byte) (VoxelShapeHelper.isTiny(this.box) ? 2 : 1);
            this.papi$boxTinyState = s;
        }
        return s == 2;
    }

    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/AABB;ZLjava/util/function/BiFunction;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private VoxelShape papi$deferEntityShapeCreation(AABB aabb, Operation<VoxelShape> original) {
        return null;
    }

    @WrapOperation(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState papi$fastSectionBlockState(BlockGetter getter, BlockPos pos, Operation<BlockState> original) {
        if (getter instanceof ChunkAccess chunk) {
            int y = pos.getY();
            int secY = y >> 4;
            LevelChunkSection section;
            if (chunk == this.papi$lastChunk && secY == this.papi$lastSectionY) {
                section = this.papi$lastSection;
            } else {
                int idx = chunk.getSectionIndexFromSectionY(secY);
                LevelChunkSection[] sections = chunk.getSections();
                section = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
                this.papi$lastChunk = chunk;
                this.papi$lastSectionY = secY;
                this.papi$lastSection = section;
            }
            if (section == null || section.hasOnlyAir()) {
                return Blocks.AIR.defaultBlockState();
            }
            return section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15);
        }
        return original.call(getter, pos);
    }

    @WrapOperation(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
                    ordinal = 1
            )
    )
    private VoxelShape papi$skipMoveWhenDisjoint(VoxelShape blockShape, Vec3i pos, Operation<VoxelShape> original) {
        if (blockShape.isEmpty()) {
            return Shapes.empty();
        }
        if (blockShape instanceof PapiVoxelShapeExtension ext) {
            double px = (double) pos.getX();
            double py = (double) pos.getY();
            double pz = (double) pos.getZ();
            AABB cuboid = ext.papi$getCuboidBox();
            if (cuboid != null) {
                if (!this.papi$isTargetBoxTiny() && !VoxelShapeHelper.isTiny(cuboid)) {
                    if (!VoxelShapeHelper.shiftedBoxOverlapsStrict(cuboid, px, py, pz, this.box)) {
                        return Shapes.empty();
                    }
                    return original.call(blockShape, pos);
                }
            } else {
                AABB bounds = ext.papi$getBoundsBox();
                if (bounds != null && !VoxelShapeHelper.shiftedBoxOverlapsStrict(bounds, px, py, pz, this.box)) {
                    return Shapes.empty();
                }
            }
        }
        return original.call(blockShape, pos);
    }

    @WrapOperation(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
            )
    )
    private boolean papi$lazyJoinIsNotEmpty(
            VoxelShape movedBlockShape,
            VoxelShape currentEntityShape,
            BooleanOp op,
            Operation<Boolean> original
    ) {
        if (op == BooleanOp.AND && movedBlockShape instanceof PapiVoxelShapeExtension ext) {
            AABB cuboid = ext.papi$getCuboidBox();
            if (cuboid != null && !this.papi$isTargetBoxTiny() && !VoxelShapeHelper.isTiny(cuboid)) {
                return VoxelShapeHelper.boxesOverlapStrict(cuboid, this.box);
            }
        }
        if (currentEntityShape == null) {
            currentEntityShape = Shapes.create(this.box);
            this.entityShape = currentEntityShape;
        }
        return original.call(movedBlockShape, currentEntityShape, op);
    }
}
