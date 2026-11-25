package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = AbstractClientPlayer.class, priority = 1500)
public abstract class AbstractClientPlayerFovMixin {

    @WrapMethod(method = "getFieldOfViewModifier(ZF)F")
    private float papi$fastZeroScaleFovModifier(boolean isFirstPerson, float fovEffectScale, Operation<Float> original) {
        if (fovEffectScale == 0.0F) {
            Player self = (Player) (Object) this;
            if (isFirstPerson && self.isScoping()) {
                return 0.1F;
            }
            return ClientHooks.getFieldOfViewModifier(self, 1.0F, 0.0F);
        }
        return original.call(isFirstPerson, fovEffectScale);
    }
}
