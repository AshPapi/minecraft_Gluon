package net.ashpapi.papioptimization.mixin.explosion;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.ashpapi.papioptimization.core.ExplosionOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerExplosion.class, priority = 1500)
public abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Vec3 center;

    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private boolean fire;

    @Shadow
    @Final
    private ExplosionDamageCalculator damageCalculator;

    @Unique
    private int papi$skippedAirCount;

    @Inject(method = "calculateExplodedPositions", at = @At("HEAD"), cancellable = true)
    private void papi$fastCalculateExplodedPositions(CallbackInfoReturnable<List<BlockPos>> cir) {
        int[] skippedAir = new int[1];
        List<BlockPos> blocks = ExplosionOptimizer.calculateExplodedPositions(
                (ServerExplosion) (Object) this,
                this.level,
                this.center,
                this.radius,
                this.damageCalculator,
                this.fire,
                skippedAir
        );
        this.papi$skippedAirCount = skippedAir[0];
        cir.setReturnValue(blocks);
    }

    @ModifyReturnValue(method = "explode", at = @At("RETURN"))
    private int papi$restoreSkippedAirCount(int blockCount) {
        int extra = this.papi$skippedAirCount;
        this.papi$skippedAirCount = 0;
        return blockCount + extra;
    }

    @WrapMethod(method = "hurtEntities(Ljava/util/List;)V")
    private void papi$scopedHurtEntities(List<BlockPos> blocks, Operation<Void> original) {
        ExplosionOptimizer.startExplosionContext();
        try {
            original.call(blocks);
        } finally {
            ExplosionOptimizer.endExplosionContext();
        }
    }

    @WrapMethod(method = "getSeenPercent")
    private static float papi$fastGetSeenPercent(Vec3 center, Entity entity, Operation<Float> original) {
        float fast = ExplosionOptimizer.computeSeenPercentFast(center, entity);
        if (fast >= 0.0F) {
            return fast;
        }
        float vanilla = original.call(center, entity);
        ExplosionOptimizer.putCachedExposure(center, entity.getBoundingBox(), vanilla);
        return vanilla;
    }

    @Inject(method = "interactWithBlocks", at = @At("HEAD"), cancellable = true)
    private void papi$fastInteractWithBlocks(List<BlockPos> targetBlocks, CallbackInfo ci) {
        if (targetBlocks.isEmpty()) {
            ci.cancel();
            return;
        }
        ExplosionOptimizer.ConsolidatedDropManager dropManager = new ExplosionOptimizer.ConsolidatedDropManager();
        Util.shuffle(targetBlocks, this.level.getRandom());
        for (int i = 0, len = targetBlocks.size(); i < len; i++) {
            BlockPos pos = targetBlocks.get(i);
            BlockState state = this.level.getBlockState(pos);
            if (!state.isAir()) {
                state.onExplosionHit(
                        this.level, pos, (Explosion) (Object) this,
                        dropManager::addStack
                );
            }
        }
        dropManager.popAll(this.level);
        ci.cancel();
    }
}
