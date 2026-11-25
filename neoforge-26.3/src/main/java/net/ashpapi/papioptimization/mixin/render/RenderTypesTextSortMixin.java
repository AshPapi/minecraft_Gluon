package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.PapiTextSortBypass;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderTypes.class, priority = 1500)
public abstract class RenderTypesTextSortMixin {

    @WrapMethod(method = "textPolygonOffset")
    private static RenderType papi$bypassSortTextPolygonOffset(Identifier location, Operation<RenderType> original) {
        PapiTextSortBypass.enter();
        try {
            return original.call(location);
        } finally {
            PapiTextSortBypass.exit();
        }
    }

    @WrapMethod(method = "textGrayscalePolygonOffset")
    private static RenderType papi$bypassSortTextGrayscalePolygonOffset(Identifier location, Operation<RenderType> original) {
        PapiTextSortBypass.enter();
        try {
            return original.call(location);
        } finally {
            PapiTextSortBypass.exit();
        }
    }

    @WrapMethod(method = "textGrayscaleSeeThrough")
    private static RenderType papi$bypassSortTextGrayscaleSeeThrough(Identifier location, Operation<RenderType> original) {
        PapiTextSortBypass.enter();
        try {
            return original.call(location);
        } finally {
            PapiTextSortBypass.exit();
        }
    }
}
