package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.ashpapi.papioptimization.physics.FastBoxCollision;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.ashpapi.papioptimization.physics.VoxelShapeHelper;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VoxelShape.class, priority = 1500)
public abstract class VoxelShapeCacheMixin implements PapiVoxelShapeExtension {

    @Shadow
    @Final
    protected DiscreteVoxelShape shape;

    @Shadow
    public abstract DoubleList getCoords(Direction.Axis axis);

    @Shadow
    protected abstract double get(Direction.Axis axis, int index);

    @Shadow
    protected abstract int findIndex(Direction.Axis axis, double position);

    @Shadow
    public abstract boolean isEmpty();

    @Unique
    private volatile @Nullable List<AABB> papi$cachedAabbs;

    @Unique
    private volatile @Nullable AABB papi$cuboidBox;

    @Unique
    private volatile @Nullable AABB papi$boundsBox;

    @Unique
    private volatile byte papi$boxState;

    @Unique
    private volatile byte papi$fullBlockState;

    @Unique
    private volatile @Nullable AtomicReferenceArray<VoxelShape> papi$pistonOffsets;

    @Unique
    private byte papi$ensureAnalyzed() {
        byte s = this.papi$boxState;
        if (s == VoxelShapeHelper.STATE_UNINITIALIZED) {
            VoxelShapeHelper.analyzeShape((VoxelShape) (Object) this, this.shape);
            s = this.papi$boxState;
        }
        return s;
    }

    @Override
    public void papi$initMovedState(byte state, @Nullable AABB singleOrCuboidBox, @Nullable AABB boundsBox) {
        this.papi$cuboidBox = singleOrCuboidBox;
        this.papi$boundsBox = boundsBox;
        this.papi$boxState = state;
    }

    @Override
    public @Nullable AABB papi$getSingleBox() {
        if (this.papi$ensureAnalyzed() == VoxelShapeHelper.STATE_SINGLE_SEGMENT_BOX) {
            return this.papi$cuboidBox;
        }
        return null;
    }

    @Override
    public boolean papi$isSingleBox() {
        return this.papi$ensureAnalyzed() == VoxelShapeHelper.STATE_SINGLE_SEGMENT_BOX;
    }

    @Override
    public @Nullable AABB papi$getCuboidBox() {
        byte s = this.papi$ensureAnalyzed();
        if (s == VoxelShapeHelper.STATE_SINGLE_SEGMENT_BOX || s == VoxelShapeHelper.STATE_MULTI_SEGMENT_CUBOID) {
            return this.papi$cuboidBox;
        }
        return null;
    }

    @Override
    public @Nullable AABB papi$getBoundsBox() {
        this.papi$ensureAnalyzed();
        return this.papi$boundsBox;
    }

    @Override
    public boolean papi$isFullBlock() {
        byte s = this.papi$fullBlockState;
        if (s == 0) {
            boolean full = VoxelShapeHelper.computeIsFullBlock((VoxelShape) (Object) this);
            s = (byte) (full ? 2 : 1);
            this.papi$fullBlockState = s;
        }
        return s == 2;
    }

    @Override
    public VoxelShape papi$getDisplacedPistonShape(float absOffset, Direction direction) {
        VoxelShape self = (VoxelShape) (Object) this;
        if (self.isEmpty()) {
            return self;
        }
        int slot;
        if (absOffset == 0.0F) {
            slot = 0;
        } else if (absOffset == 0.5F) {
            slot = 1 + direction.ordinal();
        } else if (absOffset == 1.0F) {
            slot = 7 + direction.ordinal();
        } else {
            return self.move(
                    (double) ((float) direction.getStepX() * absOffset),
                    (double) ((float) direction.getStepY() * absOffset),
                    (double) ((float) direction.getStepZ() * absOffset)
            );
        }

        AtomicReferenceArray<VoxelShape> arr = this.papi$pistonOffsets;
        if (arr == null) {
            AtomicReferenceArray<VoxelShape> created = new AtomicReferenceArray<>(13);
            synchronized (this) {
                arr = this.papi$pistonOffsets;
                if (arr == null) {
                    arr = created;
                    this.papi$pistonOffsets = arr;
                }
            }
        }
        VoxelShape cached = arr.get(slot);
        if (cached != null) {
            return cached;
        }
        VoxelShape computed = slot == 0
                ? self.move(0.0, 0.0, 0.0)
                : self.move(
                        (double) ((float) direction.getStepX() * absOffset),
                        (double) ((float) direction.getStepY() * absOffset),
                        (double) ((float) direction.getStepZ() * absOffset)
                );
        arr.compareAndSet(slot, null, computed);
        return computed;
    }

    @WrapMethod(method = "bounds")
    private AABB papi$fastBounds(Operation<AABB> original) {
        byte s = this.papi$ensureAnalyzed();
        AABB b = this.papi$boundsBox;
        if (s != VoxelShapeHelper.STATE_EMPTY && b != null) {
            return b;
        }
        return original.call();
    }

    @WrapMethod(method = "toAabbs")
    private List<AABB> papi$fastToAabbs(Operation<List<AABB>> original) {
        List<AABB> cached = this.papi$cachedAabbs;
        if (cached != null) {
            return cached;
        }
        byte s = this.papi$ensureAnalyzed();
        if (s == VoxelShapeHelper.STATE_EMPTY) {
            List<AABB> empty = List.of();
            this.papi$cachedAabbs = empty;
            return empty;
        }
        if (s == VoxelShapeHelper.STATE_SINGLE_SEGMENT_BOX || s == VoxelShapeHelper.STATE_MULTI_SEGMENT_CUBOID) {
            AABB box = this.papi$cuboidBox;
            if (box != null) {
                List<AABB> single = List.of(box);
                this.papi$cachedAabbs = single;
                return single;
            }
        }
        List<AABB> raw = original.call();
        List<AABB> immutable = List.copyOf(raw);
        this.papi$cachedAabbs = immutable;
        return immutable;
    }

    @Inject(method = "move(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"))
    private void papi$propagateMovedState(double x, double y, double z, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape moved = cir.getReturnValue();
        if (moved != null && moved != (Object) this && moved instanceof PapiVoxelShapeExtension ext) {
            byte s = this.papi$boxState;
            if (s != VoxelShapeHelper.STATE_UNINITIALIZED) {
                AABB cuboid = this.papi$cuboidBox;
                AABB bounds = this.papi$boundsBox;
                AABB movedCuboid = cuboid != null ? cuboid.move(x, y, z) : null;
                AABB movedBounds = bounds != null ? (bounds == cuboid ? movedCuboid : bounds.move(x, y, z)) : null;
                ext.papi$initMovedState(s, movedCuboid, movedBounds);
            }
        }
    }

    @WrapMethod(method = "findIndex")
    private int papi$fastFindIndex(Direction.Axis axis, double coord, Operation<Integer> original) {
        DoubleList coords = this.getCoords(axis);
        int from = 0;
        int len = this.shape.getSize(axis) + 1;
        while (len > 0) {
            int half = len >> 1;
            int mid = from + half;
            if (coord < coords.getDouble(mid)) {
                len = half;
            } else {
                from = mid + 1;
                len -= half + 1;
            }
        }
        return from - 1;
    }

    @WrapMethod(method = "collide")
    private double papi$fastCollide(Direction.Axis axis, AABB box, double distance, Operation<Double> original) {
        if (Math.abs(distance) < 1.0E-7) {
            return 0.0;
        }
        byte s = this.papi$ensureAnalyzed();
        if (s == VoxelShapeHelper.STATE_EMPTY) {
            return distance;
        }
        if (s == VoxelShapeHelper.STATE_SINGLE_SEGMENT_BOX) {
            AABB single = this.papi$cuboidBox;
            if (single != null) {
                return FastBoxCollision.collideSingleBox(axis, box, single, distance);
            }
        }
        return original.call(axis, box, distance);
    }

    @WrapMethod(method = "collideX")
    private double papi$fastCollideX(AxisCycle cycle, AABB box, double distance, Operation<Double> original) {
        if (this.isEmpty()) {
            return distance;
        }
        if (Math.abs(distance) < 1.0E-7) {
            return 0.0;
        }
        AxisCycle inverse = cycle.inverse();
        Direction.Axis axisX = inverse.cycle(Direction.Axis.X);
        Direction.Axis axisY = inverse.cycle(Direction.Axis.Y);
        Direction.Axis axisZ = inverse.cycle(Direction.Axis.Z);
        int sizeX = this.shape.getSize(axisX);

        if (distance > 0.0) {
            double maxX = box.max(axisX);
            int startX = this.findIndex(axisX, maxX - 1.0E-7) + 1;
            if (startX >= sizeX) {
                return distance;
            }
            int minY = Math.max(0, this.findIndex(axisY, box.min(axisY) + 1.0E-7));
            int maxY = Math.min(this.shape.getSize(axisY), this.findIndex(axisY, box.max(axisY) - 1.0E-7) + 1);
            if (minY >= maxY) {
                return distance;
            }
            int minZ = Math.max(0, this.findIndex(axisZ, box.min(axisZ) + 1.0E-7));
            int maxZ = Math.min(this.shape.getSize(axisZ), this.findIndex(axisZ, box.max(axisZ) - 1.0E-7) + 1);
            if (minZ >= maxZ) {
                return distance;
            }
            for (int ix = startX; ix < sizeX; ix++) {
                for (int iy = minY; iy < maxY; iy++) {
                    for (int iz = minZ; iz < maxZ; iz++) {
                        if (this.shape.isFullWide(inverse, ix, iy, iz)) {
                            double diff = this.get(axisX, ix) - maxX;
                            if (diff >= -1.0E-7) {
                                distance = Math.min(distance, diff);
                            }
                            return distance;
                        }
                    }
                }
            }
        } else if (distance < 0.0) {
            double minX = box.min(axisX);
            int startX = this.findIndex(axisX, minX + 1.0E-7) - 1;
            if (startX < 0) {
                return distance;
            }
            int minY = Math.max(0, this.findIndex(axisY, box.min(axisY) + 1.0E-7));
            int maxY = Math.min(this.shape.getSize(axisY), this.findIndex(axisY, box.max(axisY) - 1.0E-7) + 1);
            if (minY >= maxY) {
                return distance;
            }
            int minZ = Math.max(0, this.findIndex(axisZ, box.min(axisZ) + 1.0E-7));
            int maxZ = Math.min(this.shape.getSize(axisZ), this.findIndex(axisZ, box.max(axisZ) - 1.0E-7) + 1);
            if (minZ >= maxZ) {
                return distance;
            }
            for (int ix = startX; ix >= 0; ix--) {
                for (int iy = minY; iy < maxY; iy++) {
                    for (int iz = minZ; iz < maxZ; iz++) {
                        if (this.shape.isFullWide(inverse, ix, iy, iz)) {
                            double diff = this.get(axisX, ix + 1) - minX;
                            if (diff <= 1.0E-7) {
                                distance = Math.max(distance, diff);
                            }
                            return distance;
                        }
                    }
                }
            }
        }
        return distance;
    }
}
