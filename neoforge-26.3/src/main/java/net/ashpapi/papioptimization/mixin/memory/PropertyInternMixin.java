package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Property.class, priority = 1500)
public abstract class PropertyInternMixin<T extends Comparable<T>> {

    @Shadow @Final @Mutable private String name;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$internName(String name, Class<T> clazz, CallbackInfo ci) {
        if (this.name != null) {
            this.name = this.name.intern();
        }
    }
}
