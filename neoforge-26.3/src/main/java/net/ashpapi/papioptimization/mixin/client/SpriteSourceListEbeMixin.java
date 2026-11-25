package net.ashpapi.papioptimization.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.ashpapi.papioptimization.client.ebe.PapiEbeSpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SpriteSourceList.class, priority = 1500)
public abstract class SpriteSourceListEbeMixin {

    @Shadow
    @Final
    @Mutable
    private List<SpriteSource> sources;

    @Inject(
        method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/atlas/SpriteSourceList;",
        at = @At("RETURN")
    )
    private static void papi$appendBlockEntitySpritesToBlockAtlas(
            ResourceManager resourceManager,
            Identifier location,
            CallbackInfoReturnable<SpriteSourceList> cir
    ) {
        if (!AtlasIds.BLOCKS.equals(location)) {
            return;
        }
        SpriteSourceList list = cir.getReturnValue();
        if (list == null) {
            return;
        }
        SpriteSourceListEbeMixin acc = (SpriteSourceListEbeMixin) (Object) list;
        List<SpriteSource> src = acc.sources;
        if (!(src instanceof ArrayList<SpriteSource>)) {
            src = new ArrayList<>(src);
            acc.sources = src;
        }
        src.add(PapiEbeSpriteSource.INSTANCE);
        PapiEbeRuntime.markAtlasPatched();
    }
}
