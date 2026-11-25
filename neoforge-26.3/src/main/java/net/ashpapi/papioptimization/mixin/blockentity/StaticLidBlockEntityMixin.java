package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ChestLidController.class, priority = 1500)
public abstract class StaticLidBlockEntityMixin {

    @Shadow
    private boolean shouldBeOpen;

    @Shadow
    private float openness;

    @Shadow
    private float oOpenness;

    @WrapMethod(method = "tickLid")
    private void papi$skipIdleLidTick(Operation<Void> original) {
        if (!this.shouldBeOpen && this.openness == 0.0F && this.oOpenness == 0.0F) {
            return;
        }
        if (this.shouldBeOpen && this.openness == 1.0F && this.oOpenness == 1.0F) {
            return;
        }
        original.call();
    }
}
