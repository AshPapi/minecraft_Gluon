package net.ashpapi.papioptimization.mixin.block;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.LiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LiquidBlock.class, priority = 1500)
public abstract class LiquidBlockRandomTickMixin {

    @ModifyReturnValue(method = "isRandomlyTicking", at = @At("RETURN"))
    private boolean papi$deduplicateVanillaLiquidRandomTick(boolean original) {
        if (original && ((Object) this).getClass() == LiquidBlock.class) {
            return false;
        }
        return original;
    }
}
