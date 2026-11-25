package net.ashpapi.papioptimization.mixin.entity;

import java.util.List;
import java.util.Optional;
import net.ashpapi.papioptimization.ai.FastLongJumpChoiceList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LongJumpToRandomPos.class, priority = 1500)
public abstract class LongJumpToRandomPosFastMixin<E extends Mob> {

    @Shadow @Final protected int maxLongJumpHeight;
    @Shadow @Final protected int maxLongJumpWidth;
    @Shadow protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates;
    @Shadow protected Optional<Vec3> initialPosition;
    @Shadow protected @Nullable Vec3 chosenJump;
    @Shadow protected int findJumpTries;

    @Inject(
        method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastStart(ServerLevel level, E body, long timestamp, CallbackInfo ci) {
        this.chosenJump = null;
        this.findJumpTries = 20;
        this.initialPosition = Optional.of(body.position());
        this.jumpCandidates = FastLongJumpChoiceList.create(body.blockPosition(), this.maxLongJumpWidth, this.maxLongJumpHeight);
        ci.cancel();
    }

    @Inject(method = "getJumpCandidate", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetJumpCandidate(ServerLevel level, CallbackInfoReturnable<Optional<LongJumpToRandomPos.PossibleJump>> cir) {
        if (this.jumpCandidates instanceof FastLongJumpChoiceList fastList) {
            cir.setReturnValue(fastList.pickAndRemove(level.getRandom()));
        }
    }
}
