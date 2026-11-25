package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = DecoratedPotBlockEntity.class, priority = 1500)
public abstract class DecoratedPotEbeClientMixin {

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void papi$rebuildPotOnClientDataLoaded(ValueInput input, CallbackInfo ci) {
        PapiEbeRuntime.onClientBlockEntityDataLoaded((BlockEntity) (Object) this);
    }

    @Inject(method = "triggerEvent", at = @At("RETURN"))
    private void papi$syncPotWobbleStart(int id, int type, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            PapiEbeRuntime.onDecoratedPotWobbleStart((DecoratedPotBlockEntity) (Object) this);
        }
    }
}
