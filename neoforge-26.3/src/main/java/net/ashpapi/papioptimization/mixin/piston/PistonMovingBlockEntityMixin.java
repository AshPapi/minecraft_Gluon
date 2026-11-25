package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.ashpapi.papioptimization.physics.PistonShapeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMath;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = PistonMovingBlockEntity.class, priority = 1500)
public abstract class PistonMovingBlockEntityMixin {
    @Shadow
    @Final
    private static ThreadLocal<Direction> NOCLIP;

    @Shadow
    private float progress;

    @Shadow
    private BlockState movedState;

    @Shadow
    private Direction direction;

    @Shadow
    private boolean extending;

    @Shadow
    private boolean isSourcePiston;

    @Shadow
    public abstract boolean isSourcePiston();

    @Shadow
    public abstract Direction getMovementDirection();

    @Shadow
    private native BlockState getCollisionRelatedBlockState();

    @Shadow
    private native float getExtendedProgress(float progress);

    @Shadow
    private static native void moveEntityByPiston(Direction pistonDirection, Entity entity, double delta, Direction movement);

    @Shadow
    private static native void fixEntityWithinPistonBase(BlockPos pos, Entity entity, Direction direction, double deltaProgress);

    @Shadow
    private static native double getMovement(AABB aabbToBeOutsideOf, Direction movement, AABB aabb);

    @Shadow
    private static native AABB moveByPositionAndProgress(BlockPos pos, AABB aabb, PistonMovingBlockEntity entity);

    @WrapMethod(method = "getCollisionShape")
    private VoxelShape papi$fastGetCollisionShape(BlockGetter level, BlockPos pos, Operation<VoxelShape> original) {
        VoxelShape baseShape;
        boolean vanillaPistonBase = false;
        if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
            Block block = this.movedState.getBlock();
            if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
                Direction facing = this.movedState.getValue(PistonBaseBlock.FACING);
                baseShape = PistonShapeHelper.getExtendedBase(facing);
                vanillaPistonBase = facing == this.direction;
            } else {
                baseShape = this.movedState.setValue(PistonBaseBlock.EXTENDED, Boolean.TRUE).getCollisionShape(level, pos);
            }
        } else {
            baseShape = Shapes.empty();
        }

        Direction noClipDir = NOCLIP.get();
        if ((double) this.progress < 1.0 && noClipDir == this.getMovementDirection()) {
            return baseShape;
        }

        if (vanillaPistonBase && this.isSourcePiston()) {
            VoxelShape precomputed = PistonShapeHelper.getRetractingDiscrete(this.direction, this.progress);
            if (precomputed != null) {
                return precomputed;
            }
        }

        VoxelShape movingShape;
        if (this.isSourcePiston()) {
            boolean isShort = this.extending != (1.0F - this.progress < 0.25F);
            movingShape = PistonShapeHelper.getHead(this.direction, isShort);
        } else {
            movingShape = this.movedState.getCollisionShape(level, pos);
        }

        if (movingShape.isEmpty()) {
            return baseShape;
        }

        float ext = this.getExtendedProgress(this.progress);
        float absOffset;
        Direction dispDir;
        if (ext >= 0.0F) {
            absOffset = ext;
            dispDir = this.direction;
        } else {
            absOffset = -ext;
            dispDir = this.direction.getOpposite();
        }

        VoxelShape displaced;
        if (movingShape instanceof PapiVoxelShapeExtension shapeExt) {
            displaced = shapeExt.papi$getDisplacedPistonShape(absOffset, dispDir);
        } else {
            displaced = movingShape.move(
                    (double) ((float) this.direction.getStepX() * ext),
                    (double) ((float) this.direction.getStepY() * ext),
                    (double) ((float) this.direction.getStepZ() * ext)
            );
        }

        if (baseShape.isEmpty()) {
            return displaced;
        }
        return Shapes.or(baseShape, displaced);
    }

    @WrapMethod(method = "moveCollidedEntities")
    private static void papi$moveCollidedEntities(
            Level level,
            BlockPos pos,
            float newProgress,
            PistonMovingBlockEntity self,
            Operation<Void> original
    ) {
        PistonMovingBlockEntityMixin selfMixin = (PistonMovingBlockEntityMixin) (Object) self;
        Direction movement = self.getMovementDirection();
        double deltaProgress = (double) (newProgress - selfMixin.progress);
        VoxelShape shape = selfMixin.getCollisionRelatedBlockState().getCollisionShape((BlockGetter) level, pos);

        if (shape.isEmpty()) {
            return;
        }

        AABB bounds = shape.bounds();
        AABB sweptAabb = papi$computeSweptAabb(pos, bounds, selfMixin, movement, deltaProgress);
        List<Entity> entities = level.getEntities(null, sweptAabb);
        if (entities.isEmpty()) {
            return;
        }

        List<AABB> shapeAabbs = shape.toAabbs();
        boolean causeBounce = selfMixin.movedState.isSlimeBlock();

        for (int idx = 0, len = entities.size(); idx < len; idx++) {
            Entity entity = entities.get(idx);
            if (entity.getPistonPushReaction() == PushReaction.IGNORE_ENTITY) {
                continue;
            }

            if (causeBounce && entity.canSimulateMovement()) {
                Vec3 deltaMovement = entity.getDeltaMovement();
                double dx = deltaMovement.x;
                double dy = deltaMovement.y;
                double dz = deltaMovement.z;
                Direction.Axis axis = movement.getAxis();
                if (axis == Direction.Axis.X) {
                    dx = movement.getStepX();
                } else if (axis == Direction.Axis.Y) {
                    dy = movement.getStepY();
                } else {
                    dz = movement.getStepZ();
                }
                entity.setDeltaMovement(dx, dy, dz);
            }

            double delta = 0.0;
            AABB entityAabb = entity.getBoundingBox();
            int shapeCount = shapeAabbs.size();

            for (int j = 0; j < shapeCount; j++) {
                AABB shapeAabb = shapeAabbs.get(j);
                AABB movingAABB = PistonMath.getMovementArea(moveByPositionAndProgress(pos, shapeAabb, self), movement, deltaProgress);
                if (movingAABB.intersects(entityAabb)) {
                    delta = Math.max(delta, getMovement(movingAABB, movement, entityAabb));
                    if (delta >= deltaProgress) {
                        break;
                    }
                }
            }

            if (delta <= 0.0) {
                continue;
            }

            delta = Math.min(delta, deltaProgress) + 0.01;
            moveEntityByPiston(movement, entity, delta, movement);
            if (!selfMixin.extending && selfMixin.isSourcePiston) {
                fixEntityWithinPistonBase(pos, entity, movement, deltaProgress);
            }
        }
    }

    @Unique
    private static AABB papi$computeSweptAabb(
            BlockPos pos,
            AABB bounds,
            PistonMovingBlockEntityMixin selfMixin,
            Direction movement,
            double deltaProgress
    ) {
        double currentPosition = selfMixin.getExtendedProgress(selfMixin.progress);
        double ox = (double) pos.getX() + currentPosition * (double) selfMixin.direction.getStepX();
        double oy = (double) pos.getY() + currentPosition * (double) selfMixin.direction.getStepY();
        double oz = (double) pos.getZ() + currentPosition * (double) selfMixin.direction.getStepZ();

        double minX = bounds.minX + ox;
        double maxX = bounds.maxX + ox;
        double minY = bounds.minY + oy;
        double maxY = bounds.maxY + oy;
        double minZ = bounds.minZ + oz;
        double maxZ = bounds.maxZ + oz;

        double stepX = (double) movement.getStepX() * deltaProgress;
        double stepY = (double) movement.getStepY() * deltaProgress;
        double stepZ = (double) movement.getStepZ() * deltaProgress;

        if (stepX < 0.0) {
            minX += stepX;
        } else {
            maxX += stepX;
        }
        if (stepY < 0.0) {
            minY += stepY;
        } else {
            maxY += stepY;
        }
        if (stepZ < 0.0) {
            minZ += stepZ;
        } else {
            maxZ += stepZ;
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
