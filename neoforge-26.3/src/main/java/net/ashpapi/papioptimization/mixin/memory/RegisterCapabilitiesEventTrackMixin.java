package net.ashpapi.papioptimization.mixin.memory;

import net.ashpapi.papioptimization.memory.PapiCapabilityDeduplicator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RegisterCapabilitiesEvent.class, priority = 1500, remap = false)
public abstract class RegisterCapabilitiesEventTrackMixin {

    @Inject(method = "registerBlock", at = @At("HEAD"))
    private <T, C> void papi$trackBlock(
            BlockCapability<T, C> capability,
            IBlockCapabilityProvider<T, C> provider,
            Block[] blocks,
            CallbackInfo ci
    ) {
        PapiCapabilityDeduplicator.track(capability);
    }

    @Inject(method = "registerBlockEntity", at = @At("HEAD"))
    private <T, C, BE extends BlockEntity> void papi$trackBlockEntity(
            BlockCapability<T, C> capability,
            BlockEntityType<BE> type,
            ICapabilityProvider<? super BE, C, T> provider,
            CallbackInfo ci
    ) {
        PapiCapabilityDeduplicator.track(capability);
    }

    @Inject(method = "registerEntity", at = @At("HEAD"))
    private <T, C, E extends Entity> void papi$trackEntity(
            EntityCapability<T, C> capability,
            EntityType<E> type,
            ICapabilityProvider<? super E, C, T> provider,
            CallbackInfo ci
    ) {
        PapiCapabilityDeduplicator.track(capability);
    }

    @Inject(method = "registerItem", at = @At("HEAD"))
    private <T, C> void papi$trackItem(
            ItemCapability<T, C> capability,
            ICapabilityProvider<ItemStack, C, T> provider,
            ItemLike[] items,
            CallbackInfo ci
    ) {
        PapiCapabilityDeduplicator.track(capability);
    }
}
