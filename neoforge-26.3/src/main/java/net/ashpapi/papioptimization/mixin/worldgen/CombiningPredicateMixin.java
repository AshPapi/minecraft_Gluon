package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.blockpredicates.AllOfPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.AnyOfPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {AllOfPredicate.class, AnyOfPredicate.class}, priority = 1500)
public abstract class CombiningPredicateMixin extends CombiningPredicate {

    @Unique
    private BlockPredicate[] papi$predicateArray;

    @Unique
    private boolean papi$isAllOf;

    protected CombiningPredicateMixin(List<BlockPredicate> predicates) {
        super(predicates);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initPredicateArray(List<BlockPredicate> predicates, CallbackInfo ci) {
        this.papi$predicateArray = predicates.toArray(new BlockPredicate[0]);
        this.papi$isAllOf = ((Object) this) instanceof AllOfPredicate;
    }

    @WrapMethod(method = "test(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z")
    private boolean papi$fastTest(LevelAccessor level, BlockPos pos, Operation<Boolean> original) {
        BlockPredicate[] arr = this.papi$predicateArray;
        if (arr == null) {
            return original.call(level, pos);
        }
        if (this.papi$isAllOf) {
            for (int i = 0; i < arr.length; i++) {
                if (!arr[i].test(level, pos)) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].test(level, pos)) {
                return true;
            }
        }
        return false;
    }
}
