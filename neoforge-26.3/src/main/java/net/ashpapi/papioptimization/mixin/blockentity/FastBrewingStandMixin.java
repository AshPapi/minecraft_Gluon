package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BrewingStandBlockEntity.class, priority = 1500)
public abstract class FastBrewingStandMixin {

    @Shadow
    private NonNullList<ItemStack> items;

    @Shadow
    private int brewTime;

    @Shadow
    private int fuel;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastBrewingCheck(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            BrewingStandBlockEntity stand,
            CallbackInfo ci
    ) {
        FastBrewingStandMixin access = (FastBrewingStandMixin) (Object) stand;

        if (access.brewTime > 0 || access.items.size() < 5 || !(state.getBlock() instanceof BrewingStandBlock)) {
            return;
        }

        if (access.fuel <= 0 && !access.items.get(4).isEmpty()) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (state.getValueOrElse(BrewingStandBlock.HAS_BOTTLE[i], Boolean.FALSE) != !access.items.get(i).isEmpty()) {
                return;
            }
        }

        if (access.items.get(3).isEmpty()) {
            ci.cancel();
            return;
        }

        if (access.fuel <= 0 && access.items.get(4).isEmpty()) {
            ci.cancel();
            return;
        }

        if (access.items.get(0).isEmpty() && access.items.get(1).isEmpty() && access.items.get(2).isEmpty()) {
            ci.cancel();
        }
    }
}
