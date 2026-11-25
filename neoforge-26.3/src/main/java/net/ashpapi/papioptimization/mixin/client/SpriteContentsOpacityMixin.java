package net.ashpapi.papioptimization.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Optional;
import net.ashpapi.papioptimization.access.PapiSpriteOpacity;
import net.ashpapi.papioptimization.client.culling.PapiSpriteOpacityTracker;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SpriteContents.class, priority = 1500)
public abstract class SpriteContentsOpacityMixin implements PapiSpriteOpacity {

    @Unique
    private long papi$opW0 = -1L;

    @Unique
    private long papi$opW1 = -1L;

    @Unique
    private long papi$opW2 = -1L;

    @Unique
    private long papi$opW3 = -1L;

    @Unique
    private byte papi$opFlags = PapiSpriteOpacityTracker.FLAG_FULLY_OPAQUE;

    @Inject(
            method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
            at = @At("RETURN")
    )
    private void papi$analyzeSpriteOpacity(
            Identifier name,
            FrameSize frameSize,
            NativeImage originalImage,
            Optional<AnimationMetadataSection> animationMeta,
            List<MetadataSectionType.WithValue<?>> additionalMeta,
            Optional<TextureMetadataSection> textureMeta,
            CallbackInfo ci
    ) {
        PapiSpriteOpacityTracker.analyze((SpriteContents) (Object) this, originalImage);
    }

    @Override
    public long papi$getOpacityWord(int index) {
        return switch (index) {
            case 0 -> this.papi$opW0;
            case 1 -> this.papi$opW1;
            case 2 -> this.papi$opW2;
            default -> this.papi$opW3;
        };
    }

    @Override
    public byte papi$getOpacityFlags() {
        return this.papi$opFlags;
    }

    @Override
    public void papi$setOpacityData(long w0, long w1, long w2, long w3, byte flags) {
        this.papi$opW0 = w0;
        this.papi$opW1 = w1;
        this.papi$opW2 = w2;
        this.papi$opW3 = w3;
        this.papi$opFlags = flags;
    }
}
