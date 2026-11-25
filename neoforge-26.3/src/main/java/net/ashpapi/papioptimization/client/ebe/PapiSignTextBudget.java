package net.ashpapi.papioptimization.client.ebe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiSignTextBudget {

    private static final int SMART_SOFT_CAP = 48;
    private static final double MIN_SMART_DIST_SQ = 16.0 * 16.0;
    private static final double BASE_MAX_DIST_SQ = 64.0 * 64.0;

    private static int renderedThisFrame = 0;
    private static int renderedLastFrame = 0;
    private static double effectiveLimitSq = BASE_MAX_DIST_SQ;
    private static boolean scopingThisFrame = false;

    private PapiSignTextBudget() {}

    public static void beginFrame() {
        renderedLastFrame = renderedThisFrame;
        renderedThisFrame = 0;

        Minecraft mc = Minecraft.getInstance();
        boolean scoping = mc != null
                && mc.player != null
                && mc.options.getCameraType().isFirstPerson()
                && mc.player.isScoping();
        scopingThisFrame = scoping;
        if (scoping) {
            effectiveLimitSq = Double.MAX_VALUE;
            return;
        }
        double limit = BASE_MAX_DIST_SQ;
        if (mc != null && mc.options != null) {
            int fov = mc.options.fov().get();
            if (fov > 0 && fov < 70) {
                limit = BASE_MAX_DIST_SQ * (4900.0 / (double) (fov * fov));
            }
        }
        if (renderedLastFrame > SMART_SOFT_CAP) {
            double ratio = (double) SMART_SOFT_CAP / (double) renderedLastFrame;
            limit = Math.max(MIN_SMART_DIST_SQ, limit * ratio);
        }
        effectiveLimitSq = limit;
    }

    public static boolean shouldSkipSignBeforeExtract(
            SignBlockEntity sign,
            @Nullable Vec3 cameraPos,
            boolean hasFront,
            boolean hasBack
    ) {
        if (!hasFront && !hasBack) {
            return true;
        }
        if (cameraPos == null) {
            renderedThisFrame++;
            return false;
        }
        BlockPos pos = sign.getBlockPos();
        double dx = cameraPos.x - (pos.getX() + 0.5);
        double dy = cameraPos.y - (pos.getY() + 0.5);
        double dz = cameraPos.z - (pos.getZ() + 0.5);

        if (!scopingThisFrame) {
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > effectiveLimitSq) {
                return true;
            }
        }

        if (!hasFront || !hasBack) {
            BlockState blockState = sign.getBlockState();
            if (blockState.getBlock() instanceof SignBlock signBlock) {
                float rad = signBlock.getYRotationDegrees(blockState) * ((float) Math.PI / 180.0F);
                double nx = -Mth.sin(rad);
                double nz = Mth.cos(rad);
                double adjDx = dx;
                double adjDz = dz;
                if (signBlock instanceof WallSignBlock) {
                    adjDx += nx * 0.4375;
                    adjDz += nz * 0.4375;
                }
                double dot = adjDx * nx + adjDz * nz;
                if (!hasBack && dot < -0.1) {
                    return true;
                }
                if (!hasFront && dot > 0.1) {
                    return true;
                }
            }
        }

        renderedThisFrame++;
        return false;
    }

    public static boolean shouldSkipSignBeforeExtract(SignBlockEntity sign, @Nullable Vec3 cameraPos) {
        return shouldSkipSignBeforeExtract(sign, cameraPos, true, true);
    }
}
