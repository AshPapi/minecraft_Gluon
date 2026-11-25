package net.ashpapi.papioptimization.mixin.startup;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixerBuilder;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.ashpapi.papioptimization.startup.PapiLazyDfuBridge;
import net.minecraft.util.datafix.DataFixers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataFixers.class, priority = 1500)
public abstract class DataFixersMixin {

    @Shadow @Final private static DataFixerBuilder.Result DATA_FIXER;

    @Inject(method = "optimize", at = @At("HEAD"), cancellable = true)
    private static void papi$skipEagerDfuOptimization(
            Set<DSL.TypeReference> types,
            CallbackInfoReturnable<CompletableFuture<?>> cir
    ) {
        PapiLazyDfuBridge.registerFixer(DATA_FIXER.fixer());
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
