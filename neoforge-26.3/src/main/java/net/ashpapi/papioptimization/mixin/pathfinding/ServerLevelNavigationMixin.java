package net.ashpapi.papioptimization.mixin.pathfinding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.ashpapi.papioptimization.access.PapiActiveNavigationHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelNavigationMixin implements PapiActiveNavigationHost {

    @Shadow
    @Final
    @Mutable
    private Set<Mob> navigatingMobs;

    @Shadow
    private volatile boolean isUpdatingNavigations;

    @Unique
    private final ReferenceOpenHashSet<PathNavigation> papi$activeNavigations = new ReferenceOpenHashSet<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$useReferenceNavigatingMobs(CallbackInfo ci) {
        if (!(this.navigatingMobs instanceof ReferenceOpenHashSet)) {
            ReferenceOpenHashSet<Mob> replacement = new ReferenceOpenHashSet<>(this.navigatingMobs.size());
            replacement.addAll(this.navigatingMobs);
            this.navigatingMobs = replacement;
        }
    }

    @Override
    public void papi$addActiveNavigation(PathNavigation navigation) {
        this.papi$activeNavigations.add(navigation);
    }

    @Override
    public void papi$removeActiveNavigation(PathNavigation navigation) {
        this.papi$activeNavigations.remove(navigation);
    }

    @Override
    public Set<PathNavigation> papi$getActiveNavigations() {
        return this.papi$activeNavigations;
    }

    @WrapOperation(
        method = "sendBlockUpdated",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
        )
    )
    private boolean papi$dispatchOnlyActiveNavigations(
            VoxelShape oldShape,
            VoxelShape newShape,
            BooleanOp op,
            Operation<Boolean> original,
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            int flags
    ) {
        if (this.papi$activeNavigations.isEmpty()) {
            return false;
        }
        if (!original.call(oldShape, newShape, op)) {
            return false;
        }
        ObjectArrayList<PathNavigation> toRecompute = null;
        for (PathNavigation nav : this.papi$activeNavigations) {
            if (nav.shouldRecomputePath(pos)) {
                if (toRecompute == null) {
                    toRecompute = new ObjectArrayList<>(4);
                }
                toRecompute.add(nav);
            }
        }
        if (toRecompute != null) {
            try {
                this.isUpdatingNavigations = true;
                for (int i = 0, len = toRecompute.size(); i < len; i++) {
                    toRecompute.get(i).recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }
        }
        return false;
    }
}
