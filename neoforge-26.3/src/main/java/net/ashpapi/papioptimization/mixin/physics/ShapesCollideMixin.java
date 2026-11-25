package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.ashpapi.papioptimization.physics.FastBoxCollision;
import net.ashpapi.papioptimization.physics.FlatIndexMerger;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.ashpapi.papioptimization.physics.VoxelShapeHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Shapes.class, priority = 1500)
public abstract class ShapesCollideMixin {

    @WrapMethod(method = "collide")
    private static double papi$fastCollide(
            Direction.Axis axis,
            AABB moving,
            Iterable<VoxelShape> shapes,
            double distance,
            Operation<Double> original
    ) {
        if (Math.abs(distance) < 1.0E-7) {
            return 0.0;
        }

        for (VoxelShape shape : shapes) {
            if (shape instanceof PapiVoxelShapeExtension ext && ext.papi$isSingleBox()) {
                AABB box = ext.papi$getSingleBox();
                if (box != null) {
                    distance = FastBoxCollision.collideSingleBox(axis, moving, box, distance);
                } else {
                    distance = shape.collide(axis, moving, distance);
                }
            } else {
                distance = shape.collide(axis, moving, distance);
            }
            if (Math.abs(distance) < 1.0E-7) {
                return 0.0;
            }
        }
        return distance;
    }

    @WrapMethod(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    private static boolean papi$fastJoinIsNotEmpty(
            VoxelShape first,
            VoxelShape second,
            BooleanOp op,
            Operation<Boolean> original
    ) {
        if (op.apply(false, false)) {
            throw Util.pauseInIde(new IllegalArgumentException());
        }
        boolean empty1 = first.isEmpty();
        boolean empty2 = second.isEmpty();
        if (empty1 || empty2) {
            return op.apply(!empty1, !empty2);
        }
        if (first == second) {
            return op.apply(true, true);
        }
        if (first instanceof PapiVoxelShapeExtension ext1 && second instanceof PapiVoxelShapeExtension ext2) {
            AABB b1 = ext1.papi$getCuboidBox();
            AABB b2 = ext2.papi$getCuboidBox();
            if (b1 != null && b2 != null) {
                if (!VoxelShapeHelper.isTiny(b1) && !VoxelShapeHelper.isTiny(b2)) {
                    boolean overlap = VoxelShapeHelper.boxesOverlapStrict(b1, b2);
                    if (op == BooleanOp.AND) {
                        return overlap;
                    }
                    boolean onlyFirst = op.apply(true, false);
                    boolean onlySecond = op.apply(false, true);
                    if (!overlap) {
                        return onlyFirst || onlySecond;
                    }
                    if (op.apply(true, true)) {
                        return true;
                    }
                }
            } else {
                AABB bounds1 = ext1.papi$getBoundsBox();
                AABB bounds2 = ext2.papi$getBoundsBox();
                if (bounds1 != null && bounds2 != null && !VoxelShapeHelper.boxesOverlapStrict(bounds1, bounds2)) {
                    return op.apply(true, false) || op.apply(false, true);
                }
            }
        }
        return original.call(first, second, op);
    }

    @Inject(
            method = "createIndexMerger",
            at = @At(value = "NEW", target = "net/minecraft/world/phys/shapes/IndirectMerger"),
            cancellable = true
    )
    private static void papi$useFlatIndexMerger(
            int size,
            DoubleList first,
            DoubleList second,
            boolean firstOnly,
            boolean secondOnly,
            CallbackInfoReturnable<IndexMerger> cir
    ) {
        cir.setReturnValue(new FlatIndexMerger(first, second, firstOnly, secondOnly));
    }

    @WrapMethod(method = "or(Lnet/minecraft/world/phys/shapes/VoxelShape;[Lnet/minecraft/world/phys/shapes/VoxelShape;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    private static VoxelShape papi$fastOrArray(
            VoxelShape first,
            VoxelShape[] tail,
            Operation<VoxelShape> original
    ) {
        if (tail == null || tail.length == 0) {
            return first;
        }
        VoxelShape fullBlock = Shapes.block();
        if (first == fullBlock) {
            return fullBlock;
        }
        int totalCap = 1 + tail.length;
        VoxelShape[] buf = new VoxelShape[totalCap];
        int count = 0;
        if (first != null && !first.isEmpty()) {
            buf[count++] = first;
        }
        for (int i = 0; i < tail.length; i++) {
            VoxelShape s = tail[i];
            if (s == fullBlock) {
                return fullBlock;
            }
            if (s != null && !s.isEmpty()) {
                buf[count++] = s;
            }
        }
        if (count == 0) {
            return Shapes.empty();
        }
        if (count == 1) {
            return buf[0];
        }
        while (count > 1) {
            int next = 0;
            for (int i = 0; i + 1 < count; i += 2) {
                VoxelShape merged = Shapes.or(buf[i], buf[i + 1]);
                if (merged == fullBlock) {
                    return fullBlock;
                }
                buf[next++] = merged;
            }
            if ((count & 1) != 0) {
                buf[next++] = buf[count - 1];
            }
            count = next;
        }
        return buf[0];
    }

    @Inject(method = "faceShapeOccludes", at = @At("HEAD"), cancellable = true)
    private static void papi$fastFaceShapeOccludes(
            VoxelShape shape1, VoxelShape shape2, CallbackInfoReturnable<Boolean> cir
    ) {
        if (shape1 == Shapes.block() || shape2 == Shapes.block()) {
            cir.setReturnValue(true);
        } else if (shape1.isEmpty() && shape2.isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mergedFaceOccludes", at = @At("HEAD"), cancellable = true)
    private static void papi$fastMergedFaceOccludes(
            VoxelShape shape1, VoxelShape shape2, Direction direction, CallbackInfoReturnable<Boolean> cir
    ) {
        if (shape1 == Shapes.block() || shape2 == Shapes.block()) {
            cir.setReturnValue(true);
        } else if (shape1.isEmpty() && shape2.isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
