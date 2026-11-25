package net.ashpapi.papioptimization.mixin.startup;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixerUpper;
import com.mojang.serialization.Dynamic;
import net.ashpapi.papioptimization.startup.PapiLazyDfuBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataFixerUpper.class, priority = 1500, remap = false)
public abstract class DataFixerUpperMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private <T> void papi$fastTrackOrSkipUpdate(
            DSL.TypeReference type,
            Dynamic<T> input,
            int version,
            int newVersion,
            CallbackInfoReturnable<Dynamic<T>> cir
    ) {
        if (version >= newVersion) {
            cir.setReturnValue(input);
            return;
        }
        PapiLazyDfuBridge.recordLegacyUpdate(this);
    }
}
