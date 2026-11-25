package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.Continuation;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EntitySection.class, priority = 1500)
public abstract class EntitySectionIterationMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/Continuation;")
    private Continuation papi$fastGetEntities(
            AABB box,
            AbortableIterationConsumer<T> consumer,
            Operation<Continuation> original
    ) {
        List<T> all = ((ClassInstanceMultiMapAccessor<T>) this.storage).papi$getAllInstances();
        int initialSize = all.size();
        if (initialSize == 0) {
            return Continuation.CONTINUE;
        }
        if (all instanceof RandomAccess) {
            boolean enteredCrowded = initialSize >= 24 && SpatialEntityQueryHelper.enterCrowdedSectionIfPushQuery();
            try {
                for (int i = 0; i < all.size(); i++) {
                    T entity = all.get(i);
                    if (entity.getBoundingBox().intersects(box) && consumer.accept(entity).shouldAbort()) {
                        return Continuation.ABORT;
                    }
                }
                return Continuation.CONTINUE;
            } finally {
                if (enteredCrowded) {
                    SpatialEntityQueryHelper.exitCrowdedSection();
                }
            }
        }
        return original.call(box, consumer);
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/Continuation;")
    private <U extends T> Continuation papi$fastGetEntitiesTyped(
            EntityTypeTest<T, U> typeTest,
            AABB box,
            AbortableIterationConsumer<? super U> consumer,
            Operation<Continuation> original
    ) {
        Class<? extends T> baseClass = typeTest.getBaseClass();
        Map<Class<?>, List<T>> byClass = ((ClassInstanceMultiMapAccessor<T>) this.storage).papi$getByClass();
        List<T> list = byClass.get(baseClass);
        if (list == null) {
            Collection<? extends T> created = this.storage.find(baseClass);
            if (created.isEmpty()) {
                return Continuation.CONTINUE;
            }
            list = byClass.get(baseClass);
            if (list == null) {
                return original.call(typeTest, box, consumer);
            }
        }
        if (list.isEmpty()) {
            return Continuation.CONTINUE;
        }
        if (list instanceof RandomAccess) {
            for (int i = 0; i < list.size(); i++) {
                T raw = list.get(i);
                U cast = typeTest.tryCast(raw);
                if (cast != null && raw.getBoundingBox().intersects(box) && consumer.accept(cast).shouldAbort()) {
                    return Continuation.ABORT;
                }
            }
            return Continuation.CONTINUE;
        }
        return original.call(typeTest, box, consumer);
    }
}
