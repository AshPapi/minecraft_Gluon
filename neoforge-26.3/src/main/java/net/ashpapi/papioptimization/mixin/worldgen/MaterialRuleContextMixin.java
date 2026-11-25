package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Function;
import net.ashpapi.papioptimization.worldgen.PapiDensityBufferArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaterialRuleContext.class, priority = 1500)
public abstract class MaterialRuleContextMixin {

    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Shadow private Holder<Biome> biome;
    @Shadow private int blockX;
    @Shadow private int blockY;
    @Shadow private int blockZ;

    @Unique
    private int papi$lastBlockX = Integer.MIN_VALUE;

    @Unique
    private int papi$lastBlockY = Integer.MIN_VALUE;

    @Unique
    private int papi$lastBlockZ = Integer.MIN_VALUE;

    @Unique
    private Holder<Biome> papi$lastBlockBiome;

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBiome(CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> current = this.biome;
        if (current != null) {
            cir.setReturnValue(current);
            return;
        }
        int bx = this.blockX;
        int by = this.blockY;
        int bz = this.blockZ;
        if (this.papi$lastBlockBiome != null && bx == this.papi$lastBlockX && by == this.papi$lastBlockY && bz == this.papi$lastBlockZ) {
            this.biome = this.papi$lastBlockBiome;
            cir.setReturnValue(this.papi$lastBlockBiome);
            return;
        }
        Holder<Biome> resolved = this.biomeGetter.apply(this.pos.set(bx, by, bz));
        this.biome = resolved;
        this.papi$lastBlockX = bx;
        this.papi$lastBlockY = by;
        this.papi$lastBlockZ = bz;
        this.papi$lastBlockBiome = resolved;
        cir.setReturnValue(resolved);
    }

    @WrapOperation(
        method = {"getMinSurfaceLevel", "getDensitiesInChunk"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"
        )
    )
    private DensityBuffer papi$poolDensityBuffer(int size, Operation<DensityBuffer> original) {
        return PapiDensityBufferArena.acquireUnpooled(size);
    }
}
