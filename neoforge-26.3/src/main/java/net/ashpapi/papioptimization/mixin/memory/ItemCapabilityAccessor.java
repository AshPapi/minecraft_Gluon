package net.ashpapi.papioptimization.mixin.memory;

import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ItemCapability.class, priority = 1500, remap = false)
public interface ItemCapabilityAccessor<T, C> {

    @Accessor("providers")
    Map<Item, List<ICapabilityProvider<ItemStack, C, T>>> papi$getProviders();
}
