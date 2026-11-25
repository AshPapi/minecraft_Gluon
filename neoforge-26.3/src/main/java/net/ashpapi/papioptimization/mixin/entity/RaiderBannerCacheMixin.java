package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.ai.PapiRaidBannerCache;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Raider.class, priority = 1500)
public abstract class RaiderBannerCacheMixin {

    @WrapOperation(
        method = {
            "isCaptain",
            "pickUpItem",
            "lambda$static$0"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/raid/Raid;getOminousBannerInstance(Lnet/minecraft/core/HolderGetter;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private static ItemStack papi$useCachedOminousBanner(
            HolderGetter<BannerPattern> getter,
            Operation<ItemStack> original
    ) {
        return PapiRaidBannerCache.getReadOnly(getter);
    }
}
