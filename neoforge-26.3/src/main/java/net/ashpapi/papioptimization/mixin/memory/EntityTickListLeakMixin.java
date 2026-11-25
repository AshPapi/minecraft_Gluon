package net.ashpapi.papioptimization.mixin.memory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityTickList.class, priority = 1500)
public abstract class EntityTickListLeakMixin {

    @Shadow
    private Int2ObjectMap<Entity> active;

    @Shadow
    private Int2ObjectMap<Entity> passive;

    @Shadow
    private Int2ObjectMap<Entity> iterated;

    @Inject(method = "ensureActiveIsNotIterated", at = @At("HEAD"), cancellable = true)
    private void papi$fastCloneActiveWhenIterated(CallbackInfo ci) {
        if (this.active instanceof Int2ObjectLinkedOpenHashMap<Entity> linkedActive) {
            if (this.iterated == this.active) {
                this.active = linkedActive.clone();
            }
            if (this.passive != this.active && !this.passive.isEmpty()) {
                this.passive.clear();
            }
            ci.cancel();
        }
    }

    @WrapMethod(method = "forEach")
    private void papi$clearPassiveAfterForEach(Consumer<Entity> output, Operation<Void> original) {
        try {
            original.call(output);
        } finally {
            if (this.passive != this.active && !this.passive.isEmpty()) {
                this.passive.clear();
                if (this.passive instanceof Int2ObjectLinkedOpenHashMap<?> linkedPassive) {
                    linkedPassive.trim(0);
                }
            }
        }
    }
}
