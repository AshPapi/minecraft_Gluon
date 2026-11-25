package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Level.class, priority = 1500)
public interface LevelEntityGetterAccessor {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> papi$getEntityGetter();
}
