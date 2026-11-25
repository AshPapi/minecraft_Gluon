package net.ashpapi.papioptimization.mixin.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntitySectionStorage.class, priority = 1500)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow @Final private Long2ObjectMap<EntitySection<T>> sections;

    @Inject(
        method = "forEachAccessibleNonEmptySection",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastForEachAccessibleNonEmptySection(
            AABB box,
            AbortableIterationConsumer<EntitySection<T>> consumer,
            CallbackInfo ci
    ) {
        if (this.sections.isEmpty()) {
            ci.cancel();
            return;
        }
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0);
        int minY = SectionPos.posToSectionCoord(box.minY - 4.0);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0);
        int maxY = SectionPos.posToSectionCoord(box.maxY);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0);

        long dx = (long) maxX - (long) minX + 1L;
        long dy = (long) maxY - (long) minY + 1L;
        long dz = (long) maxZ - (long) minZ + 1L;

        if (dx <= 0L || dy <= 0L || dz <= 0L) {
            ci.cancel();
            return;
        }

        if (dx <= 5L && dz <= 5L && dy <= 16L) {
            int posStartZ = Math.max(minZ, 0);
            int negEndZ = Math.min(maxZ, -1);
            int posStartY = Math.max(minY, 0);
            int negEndY = Math.min(maxY, -1);

            for (int x = minX; x <= maxX; ++x) {
                for (int z = posStartZ; z <= maxZ; ++z) {
                    if (this.papi$visitColumnY(x, z, posStartY, maxY, minY, negEndY, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
                for (int z = minZ; z <= negEndZ; ++z) {
                    if (this.papi$visitColumnY(x, z, posStartY, maxY, minY, negEndY, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
            }
            ci.cancel();
        }
    }

    @Unique
    private boolean papi$visitColumnY(
            int x,
            int z,
            int posStartY,
            int maxY,
            int minY,
            int negEndY,
            AbortableIterationConsumer<EntitySection<T>> consumer
    ) {
        for (int y = posStartY; y <= maxY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (consumer.accept(section).shouldAbort()) {
                    return true;
                }
            }
        }
        for (int y = minY; y <= negEndY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (consumer.accept(section).shouldAbort()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastGetEntities(
            AABB box,
            AbortableIterationConsumer<T> consumer,
            CallbackInfo ci
    ) {
        if (this.sections.isEmpty()) {
            ci.cancel();
            return;
        }
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0);
        int minY = SectionPos.posToSectionCoord(box.minY - 4.0);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0);
        int maxY = SectionPos.posToSectionCoord(box.maxY);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0);

        long dx = (long) maxX - (long) minX + 1L;
        long dy = (long) maxY - (long) minY + 1L;
        long dz = (long) maxZ - (long) minZ + 1L;

        if (dx <= 0L || dy <= 0L || dz <= 0L) {
            ci.cancel();
            return;
        }

        if (dx <= 5L && dz <= 5L && dy <= 16L) {
            int posStartZ = Math.max(minZ, 0);
            int negEndZ = Math.min(maxZ, -1);
            int posStartY = Math.max(minY, 0);
            int negEndY = Math.min(maxY, -1);

            for (int x = minX; x <= maxX; ++x) {
                for (int z = posStartZ; z <= maxZ; ++z) {
                    if (this.papi$visitEntitiesY(x, z, posStartY, maxY, minY, negEndY, box, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
                for (int z = minZ; z <= negEndZ; ++z) {
                    if (this.papi$visitEntitiesY(x, z, posStartY, maxY, minY, negEndY, box, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
            }
            ci.cancel();
        }
    }

    @Unique
    private boolean papi$visitEntitiesY(
            int x,
            int z,
            int posStartY,
            int maxY,
            int minY,
            int negEndY,
            AABB box,
            AbortableIterationConsumer<T> consumer
    ) {
        for (int y = posStartY; y <= maxY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (section.getEntities(box, consumer).shouldAbort()) {
                    return true;
                }
            }
        }
        for (int y = minY; y <= negEndY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (section.getEntities(box, consumer).shouldAbort()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <U extends T> void papi$fastGetEntitiesTyped(
            EntityTypeTest<T, U> typeTest,
            AABB box,
            AbortableIterationConsumer<U> consumer,
            CallbackInfo ci
    ) {
        if (this.sections.isEmpty()) {
            ci.cancel();
            return;
        }
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0);
        int minY = SectionPos.posToSectionCoord(box.minY - 4.0);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0);
        int maxY = SectionPos.posToSectionCoord(box.maxY);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0);

        long dx = (long) maxX - (long) minX + 1L;
        long dy = (long) maxY - (long) minY + 1L;
        long dz = (long) maxZ - (long) minZ + 1L;

        if (dx <= 0L || dy <= 0L || dz <= 0L) {
            ci.cancel();
            return;
        }

        if (dx <= 5L && dz <= 5L && dy <= 16L) {
            int posStartZ = Math.max(minZ, 0);
            int negEndZ = Math.min(maxZ, -1);
            int posStartY = Math.max(minY, 0);
            int negEndY = Math.min(maxY, -1);

            for (int x = minX; x <= maxX; ++x) {
                for (int z = posStartZ; z <= maxZ; ++z) {
                    if (this.papi$visitEntitiesTypedY(x, z, posStartY, maxY, minY, negEndY, typeTest, box, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
                for (int z = minZ; z <= negEndZ; ++z) {
                    if (this.papi$visitEntitiesTypedY(x, z, posStartY, maxY, minY, negEndY, typeTest, box, consumer)) {
                        ci.cancel();
                        return;
                    }
                }
            }
            ci.cancel();
        }
    }

    @Unique
    private <U extends T> boolean papi$visitEntitiesTypedY(
            int x,
            int z,
            int posStartY,
            int maxY,
            int minY,
            int negEndY,
            EntityTypeTest<T, U> typeTest,
            AABB box,
            AbortableIterationConsumer<U> consumer
    ) {
        for (int y = posStartY; y <= maxY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (section.getEntities(typeTest, box, consumer).shouldAbort()) {
                    return true;
                }
            }
        }
        for (int y = minY; y <= negEndY; ++y) {
            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
            if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                if (section.getEntities(typeTest, box, consumer).shouldAbort()) {
                    return true;
                }
            }
        }
        return false;
    }
}
