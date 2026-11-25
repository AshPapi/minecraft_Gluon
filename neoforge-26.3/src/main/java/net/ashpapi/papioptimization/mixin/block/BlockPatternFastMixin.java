package net.ashpapi.papioptimization.mixin.block;

import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.SectionBlockCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPattern.class, priority = 1500)
public abstract class BlockPatternFastMixin {

    @Shadow
    @Final
    private int depth;

    @Shadow
    @Final
    private int height;

    @Shadow
    @Final
    private int width;

    @Unique
    private SectionBlockCounter.@Nullable PatternRequirement papi$requirement;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$analyzePatternOnInit(Predicate<BlockInWorld>[][][] pattern, CallbackInfo ci) {
        this.papi$requirement = SectionBlockCounter.analyzePattern(pattern);
    }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private void papi$fastSectionPreCheck(
            LevelReader level,
            BlockPos pos,
            CallbackInfoReturnable<BlockPattern.@Nullable BlockPatternMatch> cir
    ) {
        SectionBlockCounter.PatternRequirement req = this.papi$requirement;
        if (req != null) {
            int maxDim = Math.max(Math.max(this.width, this.height), this.depth);
            if (!SectionBlockCounter.hasEnoughBlocks(level, pos, maxDim, req)) {
                cir.setReturnValue(null);
            }
        }
    }
}
