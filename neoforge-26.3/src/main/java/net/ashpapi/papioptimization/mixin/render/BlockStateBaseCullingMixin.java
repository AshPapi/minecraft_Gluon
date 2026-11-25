package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiCullableBlockState;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500)
public abstract class BlockStateBaseCullingMixin implements PapiCullableBlockState {

    @Shadow
    protected abstract BlockState asState();

    @Unique
    private volatile int papi$cullFlags;

    @Unique
    private volatile long[] @Nullable [] papi$faceMasks;

    @Override
    public int papi$getCullFlags() {
        return this.papi$cullFlags;
    }

    @Override
    public void papi$setCullFlags(int flags) {
        this.papi$cullFlags = flags;
    }

    @Override
    public void papi$setCullData(int flags, long[] @Nullable [] faceMasks) {
        this.papi$faceMasks = faceMasks;
        this.papi$cullFlags = flags;
    }

    @Override
    public long @Nullable [] papi$getFaceMask(int slot) {
        long[][] table = this.papi$faceMasks;
        return (table != null && slot >= 0 && slot < table.length) ? table[slot] : null;
    }

    @WrapMethod(method = "skipRendering")
    private boolean papi$cullByBakedStateMasks(
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (PapiBlockCullingCache.shouldSkipFaceLocal(this, this.asState(), adjacentState, direction)) {
            return true;
        }
        return original.call(adjacentState, direction);
    }
}
