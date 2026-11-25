package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityCollisionContext.class, priority = 1500)
public abstract class EntityCollisionContextMixin {

    @Shadow
    @Final
    private boolean descending;

    @Shadow
    @Final
    @Mutable
    private ItemStack heldItem;

    @Shadow
    @Final
    private @Nullable Entity entity;

    @Unique
    private byte papi$descendingState;

    @Unique
    private boolean papi$heldItemDeferred;

    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isDescending()Z")
    )
    private static boolean papi$deferDescending(Entity entity, Operation<Boolean> original) {
        return false;
    }

    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;")
    )
    private static ItemStack papi$deferMainHandItem(LivingEntity living, Operation<ItemStack> original) {
        return ItemStack.EMPTY;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V", at = @At("RETURN"))
    private void papi$markDeferred(Entity entity, boolean alwaysCollideWithFluid, boolean placement, CallbackInfo ci) {
        this.papi$descendingState = 1;
        if (entity instanceof LivingEntity) {
            this.papi$heldItemDeferred = true;
        }
    }

    @WrapMethod(method = "isDescending")
    private boolean papi$lazyIsDescending(Operation<Boolean> original) {
        byte state = this.papi$descendingState;
        if (state == 0) {
            return this.descending;
        }
        if (state == 1) {
            boolean d = this.entity != null && this.entity.isDescending();
            this.papi$descendingState = (byte) (d ? 3 : 2);
            return d;
        }
        return state == 3;
    }

    @WrapMethod(method = "isHoldingItem")
    private boolean papi$lazyIsHoldingItem(Item item, Operation<Boolean> original) {
        if (this.papi$heldItemDeferred) {
            this.papi$heldItemDeferred = false;
            if (this.entity instanceof LivingEntity living) {
                this.heldItem = living.getMainHandItem();
            }
        }
        return this.heldItem.is(item);
    }
}
