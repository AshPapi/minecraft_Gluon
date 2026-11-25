package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BannerBlockEntity.class, priority = 1500)
public abstract class BannerEbeClientMixin {

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void papi$rebuildBannerOnClientDataLoaded(ValueInput input, CallbackInfo ci) {
        PapiEbeRuntime.onClientBlockEntityDataLoaded((BlockEntity) (Object) this);
    }
}
