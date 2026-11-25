package net.ashpapi.papioptimization.mixin.memory;

import com.mojang.serialization.Lifecycle;
import java.util.Map;
import net.ashpapi.papioptimization.memory.PapiSparseRegistrationMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MappedRegistry.class, priority = 1500)
public abstract class MappedRegistryCompactMixin<T> {

    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

    @Inject(
        method = "<init>(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;Z)V",
        at = @At("RETURN")
    )
    private void papi$useSparseRegistrationMap(
            ResourceKey<? extends Registry<T>> key,
            Lifecycle lifecycle,
            boolean intrusive,
            CallbackInfo ci
    ) {
        if (this.registrationInfos.isEmpty()) {
            this.registrationInfos = new PapiSparseRegistrationMap<>(this.byKey);
        }
    }
}
