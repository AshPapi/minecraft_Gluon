package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = StringSplitter.class, priority = 1500)
public abstract class FontSplitterFastMixin {
    @Shadow @Final private StringSplitter.WidthProvider widthProvider;

    @WrapMethod(method = "stringWidth(Ljava/lang/String;)F")
    private float papi$fastStringWidth(String str, Operation<Float> original) {
        if (str == null || str.isEmpty()) {
            return 0.0f;
        }
        int len = str.length();
        float total = 0.0f;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '\u00a7' || Character.isSurrogate(c)) {
                return original.call(str);
            }
            total += this.widthProvider.getWidth(c, Style.EMPTY);
        }
        return total;
    }
}
