package net.ashpapi.papioptimization.client;

import net.minecraft.client.gui.render.GuiItemAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface PapiAnimatedItemAtlasHook {

    void papi$setAnimatedDelegate(@Nullable GuiItemAtlas delegate);

    void papi$markAsAnimatedAtlas(boolean needsClearThisFrame);
}
