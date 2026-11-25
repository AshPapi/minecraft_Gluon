package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiReorderableGroupState;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderTypeFeatureRenderer.class, priority = 1500)
public abstract class RenderTypeFeatureRendererStateMixin implements PapiReorderableGroupState {

    @Unique
    private boolean papi$currentGroupCanReorder;

    @WrapMethod(method = "prepareGroup")
    private void papi$trackGroupReorderState(
            FeatureFrameContext context,
            List<?> submits,
            boolean keepOrder,
            Operation<Void> original
    ) {
        boolean prev = this.papi$currentGroupCanReorder;
        this.papi$currentGroupCanReorder = !keepOrder;
        try {
            original.call(context, submits, keepOrder);
        } finally {
            this.papi$currentGroupCanReorder = prev;
        }
    }

    @Override
    public boolean papi$isCurrentGroupCanReorder() {
        return this.papi$currentGroupCanReorder;
    }
}
