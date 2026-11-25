package net.ashpapi.papioptimization.mixin.pathfinding;

import java.util.Set;
import net.ashpapi.papioptimization.access.PapiActiveNavigationHost;
import net.ashpapi.papioptimization.pathfinding.PapiPathTypeHelper;
import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigatingMob;
import net.ashpapi.papioptimization.pathfinding.PapiTrackedNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathNavigation.class, priority = 1500)
public abstract class PathNavigationMixin implements PapiTrackedNavigation {

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected @Nullable Path path;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Unique
    private boolean papi$registeredActive;

    @Unique
    private boolean papi$computeShouldBeActive() {
        if (!(this.level instanceof ServerLevel)) {
            return false;
        }
        PapiTrackedNavigatingMob trackedMob = (PapiTrackedNavigatingMob) this.mob;
        if (!trackedMob.papi$isTrackedInServerLevel()) {
            return false;
        }
        if (trackedMob.papi$getRawNavigation() != (Object) this) {
            return false;
        }
        if (this.mob.getControlledVehicle() instanceof Mob) {
            return false;
        }
        if (PapiPathTypeHelper.hasCustomShouldRecomputePath(this.getClass())) {
            return true;
        }
        if (this.hasDelayedRecomputation) {
            return true;
        }
        Path p = this.path;
        return p != null && !p.isDone();
    }

    @Override
    public void papi$syncActiveState() {
        boolean want = papi$computeShouldBeActive();
        if (want != this.papi$registeredActive) {
            this.papi$registeredActive = want;
            if (this.level instanceof PapiActiveNavigationHost host) {
                PathNavigation self = (PathNavigation) (Object) this;
                if (want) {
                    host.papi$addActiveNavigation(self);
                } else {
                    host.papi$removeActiveNavigation(self);
                }
            }
        }
    }

    @Override
    public void papi$forceUnregister() {
        if (this.papi$registeredActive) {
            this.papi$registeredActive = false;
            if (this.level instanceof PapiActiveNavigationHost host) {
                host.papi$removeActiveNavigation((PathNavigation) (Object) this);
            }
        }
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void papi$fastExitEmptyTargets(Set<BlockPos> targets, int reachRange, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<@Nullable Path> cir) {
        if (targets.isEmpty()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("RETURN"))
    private void papi$onMoveTo(Path newPath, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (this.level instanceof ServerLevel) {
            this.papi$syncActiveState();
        }
    }

    @Inject(method = "stop", at = @At("RETURN"))
    private void papi$onStop(CallbackInfo ci) {
        if (this.papi$registeredActive) {
            this.papi$forceUnregister();
        }
    }

    @Inject(method = "recomputePath", at = @At("RETURN"))
    private void papi$onRecomputePath(CallbackInfo ci) {
        if (this.level instanceof ServerLevel) {
            this.papi$syncActiveState();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void papi$onTickEnd(CallbackInfo ci) {
        if (this.level instanceof ServerLevel) {
            this.papi$syncActiveState();
        }
    }
}
