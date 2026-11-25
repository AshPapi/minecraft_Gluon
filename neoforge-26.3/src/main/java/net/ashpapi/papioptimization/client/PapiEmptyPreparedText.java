package net.ashpapi.papioptimization.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiEmptyPreparedText implements Font.PreparedText {

    public static final Font.PreparedText INSTANCE = new PapiEmptyPreparedText();

    private PapiEmptyPreparedText() {
    }

    @Override
    public void visit(Font.GlyphVisitor visitor) {
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return null;
    }
}
