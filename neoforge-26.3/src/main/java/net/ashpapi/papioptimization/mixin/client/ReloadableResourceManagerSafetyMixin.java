package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ReloadableResourceManager.class, priority = 1500)
public abstract class ReloadableResourceManagerSafetyMixin {

    @Shadow
    @Final
    private PackType type;

    @Unique
    private static final Logger PAPI$LOGGER = LoggerFactory.getLogger("Gluon/ResourceSafety");

    @Unique
    private static final Set<Class<?>> PAPI$WARNED_LISTENER_CLASSES = ConcurrentHashMap.newKeySet();

    @WrapMethod(method = "registerReloadListener")
    private void papi$ensureRenderThreadRegisterListener(
            PreparableReloadListener listener,
            Operation<Void> original
    ) {
        if (!PapiConfig.modernFix() || listener == null || this.type != PackType.CLIENT_RESOURCES) {
            original.call(listener);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread() && !RenderSystem.isOnRenderThread()) {
            Class<?> listenerClass = listener.getClass();
            if (PAPI$WARNED_LISTENER_CLASSES.add(listenerClass)) {
                PAPI$LOGGER.warn(
                        "[Gluon] Off-thread ReloadableResourceManager.registerReloadListener({}) on thread '{}', marshalling to client thread",
                        listenerClass.getName(),
                        Thread.currentThread().getName()
                );
            }
            ReloadableResourceManager self = (ReloadableResourceManager) (Object) this;
            mc.execute(() -> self.registerReloadListener(listener));
            return;
        }
        original.call(listener);
    }
}
