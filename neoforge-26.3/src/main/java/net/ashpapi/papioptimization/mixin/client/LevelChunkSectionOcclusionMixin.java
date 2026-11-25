package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiOcclusionSection;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LevelChunkSection.class, priority = 2000)
public abstract class LevelChunkSectionOcclusionMixin implements PapiOcclusionSection {

    @Unique
    private volatile int papi$occlusionEpoch = PapiOcclusionEngine.nextSectionEpoch();

    @Override
    public int papi$getOcclusionEpoch() {
        return this.papi$occlusionEpoch;
    }

    @Override
    public void papi$bumpOcclusionEpoch() {
        this.papi$occlusionEpoch = PapiOcclusionEngine.nextSectionEpoch();
    }

    @WrapMethod(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState papi$wrapSetBlockStateForOcclusion(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            boolean useLocks,
            Operation<BlockState> original
    ) {
        BlockState prev = original.call(sectionX, sectionY, sectionZ, state, useLocks);
        if (prev != state) {
            this.papi$occlusionEpoch = PapiOcclusionEngine.nextSectionEpoch();
        }
        return prev;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void papi$onReadPacket(FriendlyByteBuf buffer, CallbackInfo ci) {
        this.papi$occlusionEpoch = PapiOcclusionEngine.nextSectionEpoch();
    }

    @Inject(method = "recalcBlockCounts", at = @At("RETURN"))
    private void papi$onRecalcBlockCounts(CallbackInfo ci) {
        this.papi$occlusionEpoch = PapiOcclusionEngine.nextSectionEpoch();
    }
}
