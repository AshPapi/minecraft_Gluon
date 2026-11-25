package net.ashpapi.papioptimization.mixin.memory;

import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntityCapability.class, priority = 1500, remap = false)
public interface EntityCapabilityAccessor<T, C> {

    @Accessor("providers")
    Map<EntityType<?>, List<ICapabilityProvider<Entity, C, T>>> papi$getProviders();
}
