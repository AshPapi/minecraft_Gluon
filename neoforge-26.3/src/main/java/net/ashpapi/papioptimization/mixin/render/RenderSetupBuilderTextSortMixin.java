package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.PapiTextSortBypass;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderSetup.RenderSetupBuilder.class, priority = 1500)
public abstract class RenderSetupBuilderTextSortMixin {

    @WrapMethod(method = "sortOnUpload")
    private RenderSetup.RenderSetupBuilder papi$skipWorldTextSortOnUpload(Operation<RenderSetup.RenderSetupBuilder> original) {
        if (PapiTextSortBypass.isActive() && PapiConfig.skipTextTranslucencySorting()) {
            return (RenderSetup.RenderSetupBuilder) (Object) this;
        }
        return original.call();
    }
}
