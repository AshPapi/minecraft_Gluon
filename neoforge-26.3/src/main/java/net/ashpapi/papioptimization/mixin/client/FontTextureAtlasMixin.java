package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.PapiFontAtlasSupport;
import net.minecraft.client.gui.font.FontTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@OnlyIn(Dist.CLIENT)
@Mixin(value = FontTexture.class, priority = 1500)
public abstract class FontTextureAtlasMixin {

    @Unique
    private int papi$atlasSize;

    @Unique
    private float papi$atlasSizeFloat = 256.0F;

    @Unique
    private int papi$getOrInitAtlasSize(boolean colored) {
        int sz = this.papi$atlasSize;
        if (sz <= 0) {
            sz = PapiFontAtlasSupport.computeAtlasSize(colored);
            this.papi$atlasSize = sz;
            this.papi$atlasSizeFloat = (float) sz;
        }
        return sz;
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicAtlasTree;<init>(IIII)V"
        ),
        index = 2
    )
    private int papi$expandTreeWidth(int width, @Local(argsOnly = true) boolean colored) {
        return papi$getOrInitAtlasSize(colored);
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicAtlasTree;<init>(IIII)V"
        ),
        index = 3
    )
    private int papi$expandTreeHeight(int height, @Local(argsOnly = true) boolean colored) {
        return papi$getOrInitAtlasSize(colored);
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;"
        ),
        index = 3
    )
    private int papi$expandGpuTextureWidth(int width, @Local(argsOnly = true) boolean colored) {
        return papi$getOrInitAtlasSize(colored);
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;"
        ),
        index = 4
    )
    private int papi$expandGpuTextureHeight(int height, @Local(argsOnly = true) boolean colored) {
        return papi$getOrInitAtlasSize(colored);
    }

    @ModifyConstant(method = "add", constant = @Constant(floatValue = 256.0F))
    private float papi$scaleGlyphUvDivisor(float constant) {
        return this.papi$atlasSizeFloat;
    }
}
