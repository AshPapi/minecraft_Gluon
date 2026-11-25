package net.ashpapi.papioptimization.mixin.entity;

import java.util.Objects;
import java.util.UUID;
import net.ashpapi.papioptimization.ai.ItemMergeHelper;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemEntity.class, priority = 1500)
public abstract class ItemEntityMergeMixin extends Entity {

    @Shadow
    private @Nullable UUID target;

    public ItemEntityMergeMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    private boolean isMergable() {
        throw new AssertionError();
    }

    @Shadow
    private void tryToMerge(ItemEntity other) {
    }

    @Inject(method = "areMergable", at = @At("HEAD"), cancellable = true)
    private static void papi$fastAreMergable(ItemStack stack1, ItemStack stack2, CallbackInfoReturnable<Boolean> cir) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        if (stack1.getItem() != stack2.getItem()) {
            cir.setReturnValue(false);
            return;
        }
        int limit = Math.min(stack2.getMaxStackSize(), 64);
        if (stack2.getCount() + stack1.getCount() > limit) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(ItemStack.isSameItemSameComponents(stack1, stack2));
    }

    @Inject(method = "mergeWithNeighbours", at = @At("HEAD"), cancellable = true)
    private void papi$lazyMergeWithNeighbours(CallbackInfo ci) {
        ci.cancel();
        if (!this.isMergable()) {
            return;
        }
        ItemStack selfStack = this.getItem();
        if (selfStack.isEmpty()) {
            return;
        }
        int maxCap = Math.min(selfStack.getMaxStackSize(), 64);
        int startCount = selfStack.getCount();
        if (startCount >= maxCap) {
            return;
        }

        ItemEntity self = (ItemEntity) (Object) this;
        UUID selfTarget = this.target;
        AABB box = this.getBoundingBox().inflate(0.5, 0.0, 0.5);
        ItemMergeHelper.MergeBuffer buf = ItemMergeHelper.acquireBuffer();
        buf.projectedCount = startCount;
        try {
            ((LevelEntityGetterAccessor) this.level()).papi$getEntityGetter().get(ItemMergeHelper.ITEM_ENTITY_TYPE, box, other -> {
                if (other == self || !((ItemEntityMergeMixin) (Object) other).isMergable()) {
                    return Continuation.CONTINUE;
                }
                if (!Objects.equals(selfTarget, ((ItemEntityMergeMixin) (Object) other).target)) {
                    return Continuation.CONTINUE;
                }
                ItemStack otherStack = other.getItem();
                if (otherStack.isEmpty() || otherStack.getItem() != selfStack.getItem()) {
                    return Continuation.CONTINUE;
                }
                int otherCount = otherStack.getCount();
                if (buf.projectedCount + otherCount > maxCap) {
                    return Continuation.CONTINUE;
                }
                if (!ItemStack.isSameItemSameComponents(selfStack, otherStack)) {
                    return Continuation.CONTINUE;
                }
                buf.add(other);
                if (otherCount >= buf.projectedCount) {
                    return Continuation.ABORT;
                }
                buf.projectedCount += otherCount;
                return buf.projectedCount >= maxCap ? Continuation.ABORT : Continuation.CONTINUE;
            });

            ItemEntity first = buf.first;
            if (first != null) {
                if (((ItemEntityMergeMixin) (Object) first).isMergable()) {
                    this.tryToMerge(first);
                }
                int extras = buf.extraCount;
                for (int i = 0; i < extras && !this.isRemoved(); i++) {
                    ItemEntity next = buf.getExtra(i);
                    if (((ItemEntityMergeMixin) (Object) next).isMergable()) {
                        this.tryToMerge(next);
                    }
                }
            }
        } finally {
            buf.release();
        }
    }
}
