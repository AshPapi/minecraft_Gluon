package net.ashpapi.papioptimization.client.occlusion;

import net.minecraft.world.phys.Vec3;

public final class PapiSkeletalLod {

    public static final double ENTITY_CULL_MIN_DIST_SQR = 12.25;
    public static final double BLOCK_ENTITY_CULL_MIN_DIST_SQR = 12.25;
    public static final double LAYER_LOD_DIST_SQR = 2304.0;

    private PapiSkeletalLod() {}

    public static boolean isBeyondLayerLod(Vec3 camPos, double ex, double ey, double ez, float fovDegrees) {
        double dx = ex - camPos.x;
        double dy = ey - camPos.y;
        double dz = ez - camPos.z;
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (fovDegrees > 0.0F && fovDegrees < 70.0F) {
            double zoomScale = fovDegrees / 70.0;
            distSqr *= zoomScale * zoomScale;
        }
        return distSqr > LAYER_LOD_DIST_SQR;
    }
}
