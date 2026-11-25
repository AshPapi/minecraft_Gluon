package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class LivingEntityRendererLayerSafetyMixin {

    @Unique
    private static final Logger PAPI$LOGGER = LoggerFactory.getLogger("Gluon/RenderSafety");

    @Unique
    private static final Set<Class<?>> PAPI$WARNED_LAYER_CLASSES = ConcurrentHashMap.newKeySet();

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapMethod(method = "addLayer")
    private boolean papi$ensureRenderThreadAddLayer(RenderLayer<?, ?> layer, Operation<Boolean> original) {
        if (!PapiConfig.modernFix() || layer == null) {
            return original.call(layer);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread() && !RenderSystem.isOnRenderThread()) {
            Class<?> layerClass = layer.getClass();
            if (PAPI$WARNED_LAYER_CLASSES.add(layerClass)) {
                PAPI$LOGGER.warn(
                        "[Gluon] Off-thread LivingEntityRenderer.addLayer({}) on thread '{}', marshalling to client thread",
                        layerClass.getName(),
                        Thread.currentThread().getName()
                );
            }
            LivingEntityRenderer self = (LivingEntityRenderer) (Object) this;
            mc.execute(() -> self.addLayer(layer));
            return true;
        }
        return original.call(layer);
    }
}
