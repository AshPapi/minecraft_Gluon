package net.ashpapi.papioptimization.ai;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.mixin.entity.LevelEntityGetterAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

public final class ProjectileRayHelper {

    private static final ClassValue<Boolean> STANDARD_LEVEL_GET_ENTITIES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("getEntities", Entity.class, AABB.class, Predicate.class);
                return m.getDeclaringClass() == Level.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    private static final ClassValue<Boolean> STANDARD_PROJECTILE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Projectile.class.isAssignableFrom(type)) {
                return Boolean.FALSE;
            }
            for (Class<?> cur = type; cur != null && cur != Projectile.class; cur = cur.getSuperclass()) {
                try {
                    Method m = cur.getDeclaredMethod("canHitEntity", Entity.class);
                    if (m.getDeclaringClass() != AbstractArrow.class) {
                        return Boolean.FALSE;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    private static final ClassValue<Boolean> VANILLA_PROJECTILE_PREDICATE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("net.minecraft.world.entity.projectile.");
        }
    };

    private static final ClassValue<Boolean> NEVER_HITTABLE_ENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Entity.class.isAssignableFrom(type)) {
                return Boolean.FALSE;
            }
            try {
                Method mHit = type.getMethod("canBeHitByProjectile");
                if (mHit.getDeclaringClass() != Entity.class) {
                    return Boolean.FALSE;
                }
                Method mPick = type.getMethod("isPickable");
                return mPick.getDeclaringClass() == Entity.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    private static final ThreadLocal<ScanVisitor> SCAN_VISITOR = ThreadLocal.withInitial(ScanVisitor::new);

    private ProjectileRayHelper() {}

    public static @Nullable EntityHitResult findNearestEntityHit(
            Level level,
            @Nullable Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            float entityMargin
    ) {
        if (targetSearchArea == null
                || targetSearchArea.getXsize() < 0.0
                || targetSearchArea.getYsize() < 0.0
                || targetSearchArea.getZsize() < 0.0
                || targetSearchArea.hasNaN()) {
            return null;
        }

        double fromX = from.x;
        double fromY = from.y;
        double fromZ = from.z;
        double dx = to.x - fromX;
        double dy = to.y - fromY;
        double dz = to.z - fromZ;
        double margin = (double) entityMargin;

        if (!STANDARD_LEVEL_GET_ENTITIES.get(level.getClass())) {
            return scanList(
                    level.getEntities(source, targetSearchArea, matching),
                    fromX, fromY, fromZ, dx, dy, dz, margin
            );
        }

        boolean skipNonHittable = source != null
                && STANDARD_PROJECTILE.get(source.getClass())
                && VANILLA_PROJECTILE_PREDICATE.get(matching.getClass());

        ScanVisitor visitor = SCAN_VISITOR.get();
        boolean borrowed = !visitor.inUse;
        if (!borrowed) {
            visitor = new ScanVisitor();
        }
        visitor.init(source, matching, skipNonHittable, fromX, fromY, fromZ, dx, dy, dz, margin);
        try {
            ((LevelEntityGetterAccessor) level).papi$getEntityGetter().get(targetSearchArea, visitor);

            Collection<? extends PartEntity<?>> dragonParts = level.dragonParts();
            if (!dragonParts.isEmpty()) {
                for (PartEntity<?> part : dragonParts) {
                    if (part != source
                            && part.getParent() != source
                            && (!skipNonHittable || !NEVER_HITTABLE_ENTITY.get(part.getClass()))
                            && matching.test(part)
                            && targetSearchArea.intersects(part.getBoundingBox())) {
                        visitor.testCandidate(part);
                    }
                }
            }

            Entity hitEntity = visitor.nearestEntity;
            if (hitEntity == null) {
                return null;
            }
            double s = visitor.nearestScale;
            Vec3 hitPos = s == 0.0 ? from : new Vec3(fromX + s * dx, fromY + s * dy, fromZ + s * dz);
            return new EntityHitResult(hitEntity, hitPos);
        } finally {
            visitor.reset(borrowed);
        }
    }

    private static @Nullable EntityHitResult scanList(
            List<Entity> entities,
            double fromX, double fromY, double fromZ,
            double dx, double dy, double dz,
            double margin
    ) {
        double nearestScale = 1.0;
        boolean hasHit = false;
        Entity nearestEntity = null;

        for (int i = 0, len = entities.size(); i < len; i++) {
            Entity entity = entities.get(i);
            AABB bb = entity.getBoundingBox();
            double minX = bb.minX - margin;
            double minY = bb.minY - margin;
            double minZ = bb.minZ - margin;
            double maxX = bb.maxX + margin;
            double maxY = bb.maxY + margin;
            double maxZ = bb.maxZ + margin;
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                continue;
            }
            if (minX <= fromX && fromX <= maxX && minY <= fromY && fromY <= maxY && minZ <= fromZ && fromZ <= maxZ) {
                if (nearestScale >= 0.0) {
                    nearestEntity = entity;
                    nearestScale = 0.0;
                    hasHit = true;
                }
                continue;
            }
            double scale = clipRayScale(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    fromX, fromY, fromZ, dx, dy, dz,
                    nearestScale
            );
            if (scale >= 0.0 && (!hasHit || scale < nearestScale)) {
                nearestEntity = entity;
                nearestScale = scale;
                hasHit = true;
            }
        }

        if (nearestEntity == null) {
            return null;
        }
        Vec3 hitPos = nearestScale == 0.0
                ? new Vec3(fromX, fromY, fromZ)
                : new Vec3(fromX + nearestScale * dx, fromY + nearestScale * dy, fromZ + nearestScale * dz);
        return new EntityHitResult(nearestEntity, hitPos);
    }

    public static double clipRayScale(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double fromX, double fromY, double fromZ,
            double dx, double dy, double dz,
            double maxScale
    ) {
        double scale = maxScale;
        boolean hit = false;

        if (dx > 1.0E-7) {
            double s = (minX - fromX) / dx;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double py = fromY + s * dy;
                double pz = fromZ + s * dz;
                if (minY - 1.0E-7 <= py && py <= maxY + 1.0E-7 && minZ - 1.0E-7 <= pz && pz <= maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dx < -1.0E-7) {
            double s = (maxX - fromX) / dx;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double py = fromY + s * dy;
                double pz = fromZ + s * dz;
                if (minY - 1.0E-7 <= py && py <= maxY + 1.0E-7 && minZ - 1.0E-7 <= pz && pz <= maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        if (dy > 1.0E-7) {
            double s = (minY - fromY) / dy;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double px = fromX + s * dx;
                double pz = fromZ + s * dz;
                if (minX - 1.0E-7 <= px && px <= maxX + 1.0E-7 && minZ - 1.0E-7 <= pz && pz <= maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dy < -1.0E-7) {
            double s = (maxY - fromY) / dy;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double px = fromX + s * dx;
                double pz = fromZ + s * dz;
                if (minX - 1.0E-7 <= px && px <= maxX + 1.0E-7 && minZ - 1.0E-7 <= pz && pz <= maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        if (dz > 1.0E-7) {
            double s = (minZ - fromZ) / dz;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double px = fromX + s * dx;
                double py = fromY + s * dy;
                if (minX - 1.0E-7 <= px && px <= maxX + 1.0E-7 && minY - 1.0E-7 <= py && py <= maxY + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dz < -1.0E-7) {
            double s = (maxZ - fromZ) / dz;
            if (s >= 0.0 && (hit ? s < scale : s <= scale)) {
                double px = fromX + s * dx;
                double py = fromY + s * dy;
                if (minX - 1.0E-7 <= px && px <= maxX + 1.0E-7 && minY - 1.0E-7 <= py && py <= maxY + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        return hit ? scale : -1.0;
    }

    private static final class ScanVisitor implements Consumer<Entity> {
        boolean inUse;
        @Nullable Entity source;
        Predicate<Entity> matching;
        boolean skipNonHittable;
        double fromX;
        double fromY;
        double fromZ;
        double dx;
        double dy;
        double dz;
        double margin;
        double nearestScale;
        boolean hasHit;
        @Nullable Entity nearestEntity;

        void init(
                @Nullable Entity source,
                Predicate<Entity> matching,
                boolean skipNonHittable,
                double fromX, double fromY, double fromZ,
                double dx, double dy, double dz,
                double margin
        ) {
            this.inUse = true;
            this.source = source;
            this.matching = matching;
            this.skipNonHittable = skipNonHittable;
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromZ = fromZ;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.margin = margin;
            this.nearestScale = 1.0;
            this.hasHit = false;
            this.nearestEntity = null;
        }

        void reset(boolean borrowed) {
            this.source = null;
            this.matching = null;
            this.nearestEntity = null;
            if (borrowed) {
                this.inUse = false;
            }
        }

        @Override
        public void accept(Entity entity) {
            if (entity == this.source) {
                return;
            }
            if (this.skipNonHittable && NEVER_HITTABLE_ENTITY.get(entity.getClass())) {
                return;
            }
            if (!this.matching.test(entity)) {
                return;
            }
            this.testCandidate(entity);
        }

        void testCandidate(Entity entity) {
            AABB bb = entity.getBoundingBox();
            double m = this.margin;
            double minX = bb.minX - m;
            double minY = bb.minY - m;
            double minZ = bb.minZ - m;
            double maxX = bb.maxX + m;
            double maxY = bb.maxY + m;
            double maxZ = bb.maxZ + m;
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                return;
            }
            double fx = this.fromX;
            double fy = this.fromY;
            double fz = this.fromZ;
            if (minX <= fx && fx <= maxX && minY <= fy && fy <= maxY && minZ <= fz && fz <= maxZ) {
                if (this.nearestScale >= 0.0) {
                    this.nearestEntity = entity;
                    this.nearestScale = 0.0;
                    this.hasHit = true;
                }
                return;
            }
            double scale = clipRayScale(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    fx, fy, fz, this.dx, this.dy, this.dz,
                    this.nearestScale
            );
            if (scale >= 0.0 && (!this.hasHit || scale < this.nearestScale)) {
                this.nearestEntity = entity;
                this.nearestScale = scale;
                this.hasHit = true;
            }
        }
    }
}
