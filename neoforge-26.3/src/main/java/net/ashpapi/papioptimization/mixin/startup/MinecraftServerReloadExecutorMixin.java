package net.ashpapi.papioptimization.mixin.startup;

import java.util.concurrent.Executor;
import net.ashpapi.papioptimization.startup.PapiReloadExecutor;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ReloadableServerResources.class, priority = 1500)
public abstract class MinecraftServerReloadExecutorMixin {

    @ModifyVariable(method = "loadResources", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Executor papi$useDedicatedReloadExecutor(Executor backgroundExecutor) {
        return PapiReloadExecutor.executor();
    }
}
