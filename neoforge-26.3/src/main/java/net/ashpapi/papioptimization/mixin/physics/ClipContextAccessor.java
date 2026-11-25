package net.ashpapi.papioptimization.mixin.physics;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClipContext.class, priority = 1500)
public interface ClipContextAccessor {
    @Accessor("fluid")
    ClipContext.Fluid papi$getFluid();
}
