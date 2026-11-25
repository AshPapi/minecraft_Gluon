package net.ashpapi.papioptimization.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PoseStack.class, priority = 1500)
public interface PoseStackAccessor {

    @Accessor("lastIndex")
    int papi$getLastIndex();
}
