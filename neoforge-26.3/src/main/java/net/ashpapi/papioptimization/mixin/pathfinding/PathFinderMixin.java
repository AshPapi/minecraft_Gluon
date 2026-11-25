package net.ashpapi.papioptimization.mixin.pathfinding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathFinder.class, priority = 1500)
public abstract class PathFinderMixin {

    @Shadow
    @Final
    private NodeEvaluator nodeEvaluator;

    @Shadow
    @Final
    private BinaryHeap openSet;

    @Shadow
    private @Nullable Path findPath(
            Node start,
            Map<Target, BlockPos> targets,
            float maxRange,
            int accuracy,
            float searchDepthMultiplier
    ) {
        return null;
    }

    @WrapMethod(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;")
    private @Nullable Path papi$fastStreamlessFindPath(
            PathNavigationRegion region,
            Mob mob,
            Set<BlockPos> targets,
            float maxRange,
            int accuracy,
            float searchDepthMultiplier,
            Operation<@Nullable Path> original
    ) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(region, mob);
        try {
            Node start = this.nodeEvaluator.getStart();
            if (start == null) {
                return null;
            }
            int size = targets.size();
            Map<Target, BlockPos> targetMap = size <= 4
                    ? new Object2ObjectArrayMap<>(size)
                    : new Object2ObjectOpenHashMap<>(size);
            for (BlockPos pos : targets) {
                targetMap.put(
                        this.nodeEvaluator.getTarget((double) pos.getX(), (double) pos.getY(), (double) pos.getZ()),
                        pos
                );
            }
            return this.findPath(start, targetMap, maxRange, accuracy, searchDepthMultiplier);
        } finally {
            if (((NodeEvaluatorAccessor) this.nodeEvaluator).papi$getMob() != null) {
                this.nodeEvaluator.done();
            }
        }
    }

    @Inject(method = "reconstructPath", at = @At("HEAD"), cancellable = true)
    private void papi$fastReconstructPath(Node from, BlockPos target, boolean reached, CallbackInfoReturnable<Path> cir) {
        int count = 1;
        Node curr = from;
        while (curr.cameFrom != null) {
            count++;
            curr = curr.cameFrom;
        }
        Node[] nodes = new Node[count];
        curr = from;
        for (int i = count - 1; i >= 0; i--) {
            nodes[i] = curr;
            curr = curr.cameFrom;
        }
        cir.setReturnValue(new Path(ObjectArrayList.wrap(nodes), target, reached));
    }
}
