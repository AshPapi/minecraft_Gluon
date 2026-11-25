package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.material.FlowingFluid$BlockStatePairKey", priority = 1500)
public abstract class BlockStatePairKeyHashMixin {

    @Shadow @Final private BlockState first;
    @Shadow @Final private BlockState second;
    @Shadow @Final private Direction direction;

    @WrapMethod(method = "hashCode")
    private int papi$fastHashCode(Operation<Integer> original) {
        int h = HashCommon.mix(System.identityHashCode(this.first));
        h = HashCommon.mix(h ^ System.identityHashCode(this.second));
        return h ^ this.direction.ordinal();
    }
}
