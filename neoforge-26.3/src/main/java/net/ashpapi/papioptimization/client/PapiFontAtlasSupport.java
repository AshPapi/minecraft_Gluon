package net.ashpapi.papioptimization.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.Optional;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiFontAtlasSupport {

    private static final int VANILLA_SIZE = 256;
    private static final int TARGET_SIZE = 1024;
    private static final Identifier TEXT_VSH = Identifier.withDefaultNamespace("shaders/core/text.vsh");
    private static final Identifier TEXT_FSH = Identifier.withDefaultNamespace("shaders/core/text.fsh");

    private static volatile boolean allowExpandedAtlas = true;

    private PapiFontAtlasSupport() {}

    public static void updateFromResourceManager(@Nullable ResourceManager resourceManager) {
        if (resourceManager == null || !PapiConfig.rawFontAtlasResizing()) {
            allowExpandedAtlas = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        VanillaPackResources vanillaHolder = mc != null ? mc.getVanillaPackResources() : null;
        PackResources vanillaPack = vanillaHolder != null ? vanillaHolder.fullResources() : null;
        allowExpandedAtlas = isVanillaShader(resourceManager, TEXT_VSH, vanillaPack)
                && isVanillaShader(resourceManager, TEXT_FSH, vanillaPack);
    }

    private static boolean isVanillaShader(ResourceManager manager, Identifier id, @Nullable PackResources vanillaPack) {
        try {
            Optional<Resource> opt = manager.getResource(id);
            if (opt.isEmpty()) {
                return true;
            }
            Resource res = opt.get();
            PackResources src = res.source();
            if (vanillaPack != null && src == vanillaPack) {
                return true;
            }
            return "vanilla".equals(res.sourcePackId());
        } catch (Throwable t) {
            return false;
        }
    }

    public static int computeAtlasSize(boolean colored) {
        if (!PapiConfig.rawFontAtlasResizing() || !allowExpandedAtlas) {
            return VANILLA_SIZE;
        }
        try {
            GpuDevice device = RenderSystem.getDevice();
            GpuFormat format = colored ? GpuFormat.RGBA8_UNORM : GpuFormat.R8_UNORM;
            int maxTex = device.getDeviceInfo().limits().maxTextureSizeForFormat(format);
            int clamped = Math.min(TARGET_SIZE, maxTex);
            if (clamped < VANILLA_SIZE) {
                return VANILLA_SIZE;
            }
            return Integer.highestOneBit(clamped);
        } catch (Throwable t) {
            return VANILLA_SIZE;
        }
    }
}
