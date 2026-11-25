package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiReorderableGroupState;
import net.ashpapi.papioptimization.client.PapiTextBatchContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = TextFeatureRenderer.class, priority = 1500)
public abstract class TextFeatureRendererBatchMixin {

    @Unique
    private static final Comparator<TextFeatureRenderer.Submit> PAPI$TEXT_BATCH_ORDER = (a, b) -> {
        boolean aOutlined = a.content() instanceof TextFeatureRenderer.Content.Text t && t.outlineColor() != 0;
        boolean bOutlined = b.content() instanceof TextFeatureRenderer.Content.Text t && t.outlineColor() != 0;
        int aMode = aOutlined ? Font.DisplayMode.NORMAL.ordinal() : a.displayMode().ordinal();
        int bMode = bOutlined ? Font.DisplayMode.NORMAL.ordinal() : b.displayMode().ordinal();
        int cmp = Integer.compare(aMode, bMode);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(aOutlined ? 1 : 0, bOutlined ? 1 : 0);
    };

    @Unique
    private final ArrayList<TextFeatureRenderer.Submit> papi$sortedSubmits = new ArrayList<>(64);

    @WrapMethod(method = "buildGroup")
    private void papi$batchTextSubmitsByMode(
            FeatureFrameContext context,
            List<TextFeatureRenderer.Submit> submits,
            Operation<Void> original
    ) {
        int prevGen = PapiTextBatchContext.pushBuildGroup();
        try {
            if (submits == null || submits.size() <= 2) {
                original.call(context, submits);
                return;
            }
            if (!((Object) this instanceof PapiReorderableGroupState state) || !state.papi$isCurrentGroupCanReorder()) {
                original.call(context, submits);
                return;
            }
            for (int i = 0, len = submits.size(); i < len; i++) {
                TextFeatureRenderer.Submit submit = submits.get(i);
                boolean outlined = submit.content() instanceof TextFeatureRenderer.Content.Text t && t.outlineColor() != 0;
                if (!outlined && submit.displayMode() == Font.DisplayMode.SEE_THROUGH) {
                    original.call(context, submits);
                    return;
                }
            }
            ArrayList<TextFeatureRenderer.Submit> sorted = this.papi$sortedSubmits;
            sorted.clear();
            try {
                sorted.addAll(submits);
                sorted.sort(PAPI$TEXT_BATCH_ORDER);
                original.call(context, sorted);
            } finally {
                sorted.clear();
            }
        } finally {
            PapiTextBatchContext.popBuildGroup(prevGen);
        }
    }
}
