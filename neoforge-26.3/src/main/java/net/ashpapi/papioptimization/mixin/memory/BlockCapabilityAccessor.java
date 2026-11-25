package net.ashpapi.papioptimization.mixin.memory;

import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BlockCapability.class, priority = 1500, remap = false)
public interface BlockCapabilityAccessor<T, C> {

    @Accessor("providers")
    Map<Block, List<IBlockCapabilityProvider<T, C>>> papi$getProviders();
}
