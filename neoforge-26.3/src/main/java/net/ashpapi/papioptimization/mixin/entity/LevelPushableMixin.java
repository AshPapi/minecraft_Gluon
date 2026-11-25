package net.ashpapi.papioptimization.mixin.entity;

import com.google.common.base.Predicates;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Level.class, priority = 1500)
public abstract class LevelPushableMixin {

    @Shadow
    public abstract List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate);

    @WrapMethod(method = "getPushableEntities")
    private List<Entity> papi$fastGetPushableEntities(Entity except, AABB box, Operation<List<Entity>> original) {
        Predicate<Entity> predicate = EntitySelector.pushableBy(except);
        if (predicate == (Predicate<?>) Predicates.alwaysFalse()) {
            return List.of();
        }
        SpatialEntityQueryHelper.enterPushableQuery();
        try {
            return this.getEntities(except, box, predicate);
        } finally {
            SpatialEntityQueryHelper.exitPushableQuery();
        }
    }
}
