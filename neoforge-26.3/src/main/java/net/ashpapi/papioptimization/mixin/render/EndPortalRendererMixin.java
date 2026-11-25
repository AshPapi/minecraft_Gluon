package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = AbstractEndPortalRenderer.class, priority = 1500)
public abstract class EndPortalRendererMixin {

    @Unique
    private static final int BIT_DOWN = 1 << Direction.DOWN.ordinal();

    @Unique
    private static final int BIT_UP = 1 << Direction.UP.ordinal();

    @Unique
    private static final int BIT_NORTH = 1 << Direction.NORTH.ordinal();

    @Unique
    private static final int BIT_SOUTH = 1 << Direction.SOUTH.ordinal();

    @Unique
    private static final int BIT_WEST = 1 << Direction.WEST.ordinal();

    @Unique
    private static final int BIT_EAST = 1 << Direction.EAST.ordinal();

    @Unique
    @SuppressWarnings("unchecked")
    private static final List<Direction>[] PAPI$FACE_SUBSETS = new List[64];

    static {
        Direction[] dirs = Direction.values();
        PAPI$FACE_SUBSETS[0] = List.of();
        for (int mask = 1; mask < 64; mask++) {
            List<Direction> list = new ArrayList<>(Integer.bitCount(mask));
            for (Direction d : dirs) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    list.add(d);
                }
            }
            PAPI$FACE_SUBSETS[mask] = List.copyOf(list);
        }
    }

    @WrapMethod(method = "submitCube(Ljava/util/Collection;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V")
    private static void papi$cullBackfacingPortalCubeFaces(
            Collection<Direction> faces,
            RenderType renderType,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Operation<Void> original
    ) {
        if (!(faces instanceof Set<?>) || faces.isEmpty() || PapiOcclusionEngine.isShadowPass()) {
            original.call(faces, renderType, poseStack, collector);
            return;
        }

        Matrix4f m = poseStack.last().pose();
        float c0x = m.m00();
        float c0y = m.m01();
        float c0z = m.m02();
        float c1x = m.m10();
        float c1y = m.m11();
        float c1z = m.m12();
        float c2x = m.m20();
        float c2y = m.m21();
        float c2z = m.m22();

        float r0x = c1y * c2z - c1z * c2y;
        float r0y = c1z * c2x - c1x * c2z;
        float r0z = c1x * c2y - c1y * c2x;
        float det = c0x * r0x + c0y * r0y + c0z * r0z;
        if (Math.abs(det) <= 1.0E-6F) {
            original.call(faces, renderType, poseStack, collector);
            return;
        }

        float invDet = 1.0F / det;
        float bx = -m.m30();
        float by = -m.m31();
        float bz = -m.m32();

        float r1x = c2y * c0z - c2z * c0y;
        float r1y = c2z * c0x - c2x * c0z;
        float r1z = c2x * c0y - c2y * c0x;

        float r2x = c0y * c1z - c0z * c1y;
        float r2y = c0z * c1x - c0x * c1z;
        float r2z = c0x * c1y - c0y * c1x;

        float cx = (bx * r0x + by * r0y + bz * r0z) * invDet;
        float cy = (bx * r1x + by * r1y + bz * r1z) * invDet;
        float cz = (bx * r2x + by * r2y + bz * r2z) * invDet;

        int visMask = 0;
        if (cy < 1.0E-3F && faces.contains(Direction.DOWN)) visMask |= BIT_DOWN;
        if (cy > 0.999F && faces.contains(Direction.UP)) visMask |= BIT_UP;
        if (cz < 1.0E-3F && faces.contains(Direction.NORTH)) visMask |= BIT_NORTH;
        if (cz > 0.999F && faces.contains(Direction.SOUTH)) visMask |= BIT_SOUTH;
        if (cx < 1.0E-3F && faces.contains(Direction.WEST)) visMask |= BIT_WEST;
        if (cx > 0.999F && faces.contains(Direction.EAST)) visMask |= BIT_EAST;

        if (visMask == 0) {
            return;
        }
        original.call(PAPI$FACE_SUBSETS[visMask], renderType, poseStack, collector);
    }
}
