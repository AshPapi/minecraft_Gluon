package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CampfireBlockEntity.class, priority = 1500)
public abstract class CampfireBlockEntityMixin {

    @Shadow
    private NonNullList<ItemStack> items;

    @Shadow
    private int[] cookingProgress;

    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCookTickSleep(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            CampfireBlockEntity entity,
            RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache,
            CallbackInfo ci
    ) {
        CampfireBlockEntityMixin access = (CampfireBlockEntityMixin) (Object) entity;
        NonNullList<ItemStack> slots = access.items;
        for (int i = 0, len = slots.size(); i < len; i++) {
            if (!slots.get(i).isEmpty()) {
                return;
            }
        }
        ci.cancel();
    }

    @Inject(method = "cooldownTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCooldownTickSleep(
            Level level,
            BlockPos pos,
            BlockState state,
            CampfireBlockEntity entity,
            CallbackInfo ci
    ) {
        CampfireBlockEntityMixin access = (CampfireBlockEntityMixin) (Object) entity;
        int[] progress = access.cookingProgress;
        for (int i = 0, len = progress.length; i < len; i++) {
            if (progress[i] > 0) {
                return;
            }
        }
        ci.cancel();
    }
}
