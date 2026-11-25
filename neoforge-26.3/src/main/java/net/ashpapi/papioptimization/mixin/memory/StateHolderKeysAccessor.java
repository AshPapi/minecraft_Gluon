package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StateHolder.class, priority = 1500)
public interface StateHolderKeysAccessor {

    @Accessor("propertyKeys")
    Property<?>[] papi$getPropertyKeys();
}
