package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiMaskedSubmitCollector;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = OrderedSubmitNodeCollector.class, priority = 1500)
public interface OrderedSubmitNodeCollectorMixin extends PapiMaskedSubmitCollector {
}
