package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CollisionGetter.class, priority = 1500)
public interface CollisionGetterFreePosMixin {

    @WrapMethod(method = "findFreePosition")
    private Optional<Vec3> papi$fastFindFreePosition(
            @Nullable Entity entity,
            VoxelShape shape,
            Vec3 target,
            double x,
            double y,
            double z,
            Operation<Optional<Vec3>> original
    ) {
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        CollisionGetter self = (CollisionGetter) this;
        AABB searchBox = shape.bounds().inflate(x, y, z);
        WorldBorder border = self.getWorldBorder();
        double hx = x * 0.5;
        double hy = y * 0.5;
        double hz = z * 0.5;

        VoxelShape freeSpace = shape;
        AABB freeBounds = freeSpace.bounds();
        boolean modified = false;

        for (VoxelShape collider : self.getBlockCollisions(entity, searchBox)) {
            if (collider.isEmpty()) {
                continue;
            }
            if (border != null && !border.isWithinBounds(collider.bounds())) {
                continue;
            }
            List<AABB> boxes = collider.toAabbs();
            for (int i = 0, len = boxes.size(); i < len; i++) {
                AABB b = boxes.get(i);
                double minX = b.minX - hx;
                double minY = b.minY - hy;
                double minZ = b.minZ - hz;
                double maxX = b.maxX + hx;
                double maxY = b.maxY + hy;
                double maxZ = b.maxZ + hz;

                if (maxX <= freeBounds.minX + 1.0E-7 || minX >= freeBounds.maxX - 1.0E-7
                        || maxY <= freeBounds.minY + 1.0E-7 || minY >= freeBounds.maxY - 1.0E-7
                        || maxZ <= freeBounds.minZ + 1.0E-7 || minZ >= freeBounds.maxZ - 1.0E-7) {
                    continue;
                }

                if (minX <= freeBounds.minX + 1.0E-7 && maxX >= freeBounds.maxX - 1.0E-7
                        && minY <= freeBounds.minY + 1.0E-7 && maxY >= freeBounds.maxY - 1.0E-7
                        && minZ <= freeBounds.minZ + 1.0E-7 && maxZ >= freeBounds.maxZ - 1.0E-7) {
                    return Optional.empty();
                }

                VoxelShape inflatedShape = Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
                freeSpace = Shapes.joinUnoptimized(freeSpace, inflatedShape, BooleanOp.ONLY_FIRST);
                if (freeSpace.isEmpty()) {
                    return Optional.empty();
                }
                freeBounds = freeSpace.bounds();
                modified = true;
            }
        }

        if (modified) {
            freeSpace = freeSpace.optimize();
        }
        return freeSpace.closestPointTo(target);
    }
}
