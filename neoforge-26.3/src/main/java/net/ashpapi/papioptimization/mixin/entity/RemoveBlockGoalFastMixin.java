package net.ashpapi.papioptimization.mixin.entity;

import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.FastBlockSearchHelper;
import net.ashpapi.papioptimization.ai.PapiPaletteGoalTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = RemoveBlockGoal.class, priority = 1500)
public abstract class RemoveBlockGoalFastMixin implements PapiPaletteGoalTarget {

    @Shadow
    @Final
    private Block blockToRemove;

    @Shadow
    @Final
    private Mob removerMob;

    @Unique
    private @Nullable Predicate<BlockState> papi$cachedPredicate;

    @Unique
    private byte papi$vanillaCheckState;

    @Override
    public @Nullable Predicate<BlockState> papi$getPalettePredicate() {
        byte st = this.papi$vanillaCheckState;
        if (st == 0) {
            boolean vanilla = FastBlockSearchHelper.isVanillaRemoveBlockGoal(((Object) this).getClass());
            st = vanilla ? (byte) 1 : (byte) 2;
            this.papi$vanillaCheckState = st;
        }
        if (st != 1) {
            return null;
        }
        Predicate<BlockState> pred = this.papi$cachedPredicate;
        if (pred == null) {
            Block target = this.blockToRemove;
            pred = state -> state.is(target);
            this.papi$cachedPredicate = pred;
        }
        return pred;
    }

    @Override
    public boolean papi$isValidCachedTarget(
            ChunkAccess cachedChunk,
            LevelReader level,
            BlockPos.MutableBlockPos scratchPos,
            int x,
            int y,
            int z
    ) {
        if (!FastBlockSearchHelper.getSectionState(cachedChunk, x, y, z).is(this.blockToRemove)) {
            return false;
        }
        if (!FastBlockSearchHelper.getSectionState(cachedChunk, x, y + 1, z).isAir()) {
            return false;
        }
        return FastBlockSearchHelper.getSectionState(cachedChunk, x, y + 2, z).isAir();
    }
}
