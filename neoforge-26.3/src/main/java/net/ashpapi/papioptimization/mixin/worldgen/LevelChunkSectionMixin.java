package net.ashpapi.papioptimization.mixin.worldgen;

import net.ashpapi.papioptimization.access.PapiNoisiumPalette;
import net.ashpapi.papioptimization.access.PapiNoisiumSection;
import net.ashpapi.papioptimization.access.PapiSectionFlags;
import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunkSection.class, priority = 1500)
public abstract class LevelChunkSectionMixin implements PapiNoisiumSection, PapiSectionFlags {

    @Shadow private short nonEmptyBlockCount;
    @Shadow private short fluidCount;
    @Shadow private short tickingBlockCount;
    @Shadow private short tickingFluidCount;
    @Shadow @Final private PalettedContainer<BlockState> states;
    @Shadow private PalettedContainerRO<Holder<Biome>> biomes;

    @Shadow public abstract boolean isRandomlyTicking();

    @Unique
    private long[] papi$randomTickBits;

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetEmptyFluidState(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        if (this.fluidCount == 0) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(
        method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN")
    )
    private void papi$onSetBlockStateReturn(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            boolean useLocks,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (!this.isRandomlyTicking()) {
            this.papi$randomTickBits = null;
            return;
        }
        long[] bits = this.papi$randomTickBits;
        if (bits != null) {
            int idx = (sectionY << 8) | (sectionZ << 4) | sectionX;
            long mask = 1L << (idx & 63);
            if (state.isRandomlyTicking() || state.getFluidState().isRandomlyTicking()) {
                bits[idx >>> 6] |= mask;
            } else {
                bits[idx >>> 6] &= ~mask;
            }
        }
    }

    @Inject(method = "recalcBlockCounts", at = @At("RETURN"))
    private void papi$onRecalcCountsReturn(CallbackInfo ci) {
        this.papi$randomTickBits = null;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void papi$onReadReturn(FriendlyByteBuf buffer, CallbackInfo ci) {
        this.papi$randomTickBits = null;
    }

    @Inject(method = "fillBiomesFromNoise", at = @At("HEAD"), cancellable = true)
    private void papi$fastFixedBiomeFill(BiomeResolver resolver, int quartX, int quartY, int quartZ, CallbackInfo ci) {
        if (resolver instanceof FixedBiomeSource) {
            Holder<Biome> fixed = resolver.getNoiseBiome(quartX, quartY, quartZ);
            PalettedContainer<Holder<Biome>> container = this.biomes.recreate();
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        container.getAndSetUnchecked(x, y, z, fixed);
                    }
                }
            }
            this.biomes = container;
            ci.cancel();
        }
    }

    @Override
    public boolean papi$hasRandomTickBitset() {
        return this.isRandomlyTicking();
    }

    @Override
    public boolean papi$isRandomTickingAt(int index) {
        if (!this.isRandomlyTicking()) {
            return false;
        }
        long[] bits = this.papi$randomTickBits;
        if (bits == null) {
            bits = this.papi$buildRandomTickBits();
            this.papi$randomTickBits = bits;
        }
        return (bits[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private long[] papi$buildRandomTickBits() {
        long[] bits = new long[64];
        PalettedContainerDataAccessor<BlockState> dataAcc =
                (this.states instanceof net.ashpapi.papioptimization.chunk.PapiPalettedContainerAccess<?> acc)
                        ? (PalettedContainerDataAccessor<BlockState>) acc.papi$getDataAccessor()
                        : null;
        if (dataAcc != null) {
            Palette<BlockState> palette = dataAcc.papi$getPalette();
            BitStorage storage = dataAcc.papi$getStorage();
            int pSize = palette.getSize();
            if (pSize > 0 && pSize <= 256) {
                long[] palTickMask = new long[4];
                boolean anyTick = false;
                for (int id = 0; id < pSize; id++) {
                    BlockState st = palette.valueFor(id);
                    if (st != null && (st.isRandomlyTicking() || st.getFluidState().isRandomlyTicking())) {
                        palTickMask[id >>> 6] |= 1L << (id & 63);
                        anyTick = true;
                    }
                }
                if (!anyTick) {
                    return bits;
                }
                for (int idx = 0; idx < 4096; idx++) {
                    int id = storage.get(idx);
                    if (id >= 0 && id < 256 && (palTickMask[id >>> 6] & (1L << (id & 63))) != 0L) {
                        bits[idx >>> 6] |= 1L << (idx & 63);
                    }
                }
                return bits;
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState st = this.states.get(x, y, z);
                    if (st.isRandomlyTicking() || st.getFluidState().isRandomlyTicking()) {
                        int idx = (y << 8) | (z << 4) | x;
                        bits[idx >>> 6] |= 1L << (idx & 63);
                    }
                }
            }
        }
        return bits;
    }

    @Override
    public void papi$setBlockStateNoisium(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            NoisiumPaletteCursor cursor
    ) {
        if (!(this.states instanceof PapiNoisiumPalette noisiumPalette)) {
            ((LevelChunkSection) (Object) this).setBlockState(sectionX, sectionY, sectionZ, state, false);
            return;
        }
        this.papi$randomTickBits = null;
        int index = (sectionY << 8) | (sectionZ << 4) | sectionX;
        BlockState previous = noisiumPalette.papi$setDirectNoisium(index, state, cursor);
        if (previous == state) {
            return;
        }
        if (previous != Blocks.AIR.defaultBlockState() && !previous.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount - 1);
            if (previous.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount - 1);
            }
            FluidState previousFluid = previous.getFluidState();
            if (!previousFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount - 1);
                if (previousFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount - 1);
                }
            }
        }
        if (!state.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount + 1);
            if (state.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount + 1);
            }
            FluidState currentFluid = state.getFluidState();
            if (!currentFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount + 1);
                if (currentFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount + 1);
                }
            }
        }
    }

    @Override
    public void papi$setWriteOnlyNoisium(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            NoisiumPaletteCursor cursor
    ) {
        if (!(this.states instanceof PapiNoisiumPalette noisiumPalette)) {
            ((LevelChunkSection) (Object) this).setBlockState(sectionX, sectionY, sectionZ, state, false);
            return;
        }
        int index = (sectionY << 8) | (sectionZ << 4) | sectionX;
        noisiumPalette.papi$setWriteOnlyNoisium(index, state, cursor);
    }

    @Override
    public void papi$flushNoisiumCounts(NoisiumPaletteCursor cursor) {
        this.nonEmptyBlockCount = (short) cursor.nonEmptyCount;
        this.tickingBlockCount = (short) cursor.tickingBlockCount;
        this.fluidCount = (short) cursor.fluidCount;
        this.tickingFluidCount = (short) cursor.tickingFluidCount;
        this.papi$randomTickBits = cursor.randomTickBits;
        cursor.randomTickBits = null;
    }
}
