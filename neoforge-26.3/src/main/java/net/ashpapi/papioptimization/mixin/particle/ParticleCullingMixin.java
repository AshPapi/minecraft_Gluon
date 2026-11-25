package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Particle.class, priority = 1500)
public abstract class ParticleCullingMixin {

    @Shadow
    @Final
    protected ClientLevel level;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    protected double xd;

    @Shadow
    protected double zd;

    @Shadow
    protected boolean hasPhysics;

    @Shadow
    protected boolean onGround;

    @Shadow
    private boolean stoppedByCollision;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setBoundingBox(AABB bb);

    @Shadow
    protected abstract void setLocationFromBoundingbox();

    @WrapMethod(method = "move")
    private void papi$fastParticleMove(double dx, double dy, double dz, Operation<Void> original) {
        if (this.stoppedByCollision) {
            return;
        }
        ClientLevel lvl = this.level;
        if (!this.hasPhysics
                || lvl == null
                || (dx == 0.0 && dy == 0.0 && dz == 0.0)
                || (dx * dx + dy * dy + dz * dz >= 10000.0)) {
            original.call(dx, dy, dz);
            return;
        }

        AABB box = this.getBoundingBox();
        if (box == null) {
            original.call(dx, dy, dz);
            return;
        }

        double bMinX = dx < 0.0 ? box.minX + dx : box.minX;
        double bMaxX = dx > 0.0 ? box.maxX + dx : box.maxX;
        double bMinY = dy < 0.0 ? box.minY + dy : box.minY;
        double bMaxY = dy > 0.0 ? box.maxY + dy : box.maxY;
        double bMinZ = dz < 0.0 ? box.minZ + dz : box.minZ;
        double bMaxZ = dz > 0.0 ? box.maxZ + dz : box.maxZ;

        int minX = Mth.floor(bMinX - 1.0E-7);
        int maxX = Mth.floor(bMaxX + 1.0E-7);
        int minY = Mth.floor(bMinY - 1.0E-7);
        int maxY = Mth.floor(bMaxY + 1.0E-7);
        int minZ = Mth.floor(bMinZ - 1.0E-7);
        int maxZ = Mth.floor(bMaxZ + 1.0E-7);

        if ((maxX - minX) > 2 || (maxY - minY) > 2 || (maxZ - minZ) > 2) {
            original.call(dx, dy, dz);
            return;
        }

        boolean checkFenceBelow = (bMinY - minY) < 0.5000001;
        int cubeCount = 0;
        long c0 = 0L;
        long c1 = 0L;
        long c2 = 0L;
        long c3 = 0L;

        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        LevelChunk cachedChunk = null;
        LevelChunkSection[] cachedSections = null;

        for (int bx = minX; bx <= maxX; bx++) {
            int cx = bx >> 4;
            for (int bz = minZ; bz <= maxZ; bz++) {
                int cz = bz >> 4;
                if (cx != lastCx || cz != lastCz) {
                    lastCx = cx;
                    lastCz = cz;
                    cachedChunk = lvl.getChunkSource().getChunk(cx, cz, false);
                    cachedSections = cachedChunk != null ? cachedChunk.getSections() : null;
                }
                if (cachedChunk == null || cachedSections == null) {
                    continue;
                }
                if (checkFenceBelow) {
                    int belowY = minY - 1;
                    int belowIdx = cachedChunk.getSectionIndexFromSectionY(belowY >> 4);
                    if (belowIdx >= 0 && belowIdx < cachedSections.length) {
                        LevelChunkSection bSec = cachedSections[belowIdx];
                        if (bSec != null && !bSec.hasOnlyAir()) {
                            BlockState belowState = bSec.getBlockState(bx & 15, belowY & 15, bz & 15);
                            if (belowState.hasLargeCollisionShape()) {
                                original.call(dx, dy, dz);
                                return;
                            }
                        }
                    }
                }

                int lastSecIdx = Integer.MIN_VALUE;
                LevelChunkSection sec = null;
                boolean secEmpty = true;
                for (int by = minY; by <= maxY; by++) {
                    int secIdx = cachedChunk.getSectionIndexFromSectionY(by >> 4);
                    if (secIdx != lastSecIdx) {
                        lastSecIdx = secIdx;
                        sec = (secIdx >= 0 && secIdx < cachedSections.length) ? cachedSections[secIdx] : null;
                        secEmpty = (sec == null || sec.hasOnlyAir());
                    }
                    if (secEmpty) {
                        continue;
                    }
                    BlockState st = sec.getBlockState(bx & 15, by & 15, bz & 15);
                    if (st.hasLargeCollisionShape()) {
                        original.call(dx, dy, dz);
                        return;
                    }
                    if (st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                        continue;
                    }
                    if (!st.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || cubeCount >= 4) {
                        original.call(dx, dy, dz);
                        return;
                    }
                    long packed = BlockPos.asLong(bx, by, bz);
                    if (cubeCount == 0) c0 = packed;
                    else if (cubeCount == 1) c1 = packed;
                    else if (cubeCount == 2) c2 = packed;
                    else c3 = packed;
                    cubeCount++;
                }
            }
        }

        if (cubeCount == 0) {
            this.setBoundingBox(box.move(dx, dy, dz));
            this.setLocationFromBoundingbox();
            this.onGround = false;
            return;
        }

        double origDx = dx;
        double origDy = dy;
        double origDz = dz;

        double curMinX = box.minX;
        double curMaxX = box.maxX;
        double curMinY = box.minY;
        double curMaxY = box.maxY;
        double curMinZ = box.minZ;
        double curMaxZ = box.maxZ;

        if (dy != 0.0) {
            dy = papi$clipY(dy, curMinX, curMaxX, curMinY, curMaxY, curMinZ, curMaxZ, cubeCount, c0, c1, c2, c3);
            curMinY += dy;
            curMaxY += dy;
        }

        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0.0) {
            dz = papi$clipZ(dz, curMinX, curMaxX, curMinY, curMaxY, curMinZ, curMaxZ, cubeCount, c0, c1, c2, c3);
            curMinZ += dz;
            curMaxZ += dz;
        }
        if (dx != 0.0) {
            dx = papi$clipX(dx, curMinX, curMaxX, curMinY, curMaxY, curMinZ, curMaxZ, cubeCount, c0, c1, c2, c3);
            curMinX += dx;
            curMaxX += dx;
        }
        if (!zFirst && dz != 0.0) {
            dz = papi$clipZ(dz, curMinX, curMaxX, curMinY, curMaxY, curMinZ, curMaxZ, cubeCount, c0, c1, c2, c3);
        }

        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            this.setBoundingBox(box.move(dx, dy, dz));
            this.setLocationFromBoundingbox();
        }
        if (Math.abs(origDy) >= 1.0E-5F && Math.abs(dy) < 1.0E-5F) {
            this.stoppedByCollision = true;
        }
        this.onGround = origDy != dy && origDy < 0.0;
        if (origDx != dx) {
            this.xd = 0.0;
        }
        if (origDz != dz) {
            this.zd = 0.0;
        }
    }

    @Unique
    private static double papi$clipY(
            double dy,
            double minX, double maxX,
            double minY, double maxY,
            double minZ, double maxZ,
            int cnt, long c0, long c1, long c2, long c3
    ) {
        for (int i = 0; i < cnt; i++) {
            long p = i == 0 ? c0 : (i == 1 ? c1 : (i == 2 ? c2 : c3));
            int bx = BlockPos.getX(p);
            int by = BlockPos.getY(p);
            int bz = BlockPos.getZ(p);
            if (maxX > bx + 1.0E-7 && minX < bx + 1.0 - 1.0E-7 && maxZ > bz + 1.0E-7 && minZ < bz + 1.0 - 1.0E-7) {
                if (dy > 0.0 && maxY <= by + 1.0E-7) {
                    double d = by - maxY;
                    if (d < dy) dy = d;
                } else if (dy < 0.0 && minY >= by + 1.0 - 1.0E-7) {
                    double d = (by + 1.0) - minY;
                    if (d > dy) dy = d;
                }
            }
        }
        return dy;
    }

    @Unique
    private static double papi$clipX(
            double dx,
            double minX, double maxX,
            double minY, double maxY,
            double minZ, double maxZ,
            int cnt, long c0, long c1, long c2, long c3
    ) {
        for (int i = 0; i < cnt; i++) {
            long p = i == 0 ? c0 : (i == 1 ? c1 : (i == 2 ? c2 : c3));
            int bx = BlockPos.getX(p);
            int by = BlockPos.getY(p);
            int bz = BlockPos.getZ(p);
            if (maxY > by + 1.0E-7 && minY < by + 1.0 - 1.0E-7 && maxZ > bz + 1.0E-7 && minZ < bz + 1.0 - 1.0E-7) {
                if (dx > 0.0 && maxX <= bx + 1.0E-7) {
                    double d = bx - maxX;
                    if (d < dx) dx = d;
                } else if (dx < 0.0 && minX >= bx + 1.0 - 1.0E-7) {
                    double d = (bx + 1.0) - minX;
                    if (d > dx) dx = d;
                }
            }
        }
        return dx;
    }

    @Unique
    private static double papi$clipZ(
            double dz,
            double minX, double maxX,
            double minY, double maxY,
            double minZ, double maxZ,
            int cnt, long c0, long c1, long c2, long c3
    ) {
        for (int i = 0; i < cnt; i++) {
            long p = i == 0 ? c0 : (i == 1 ? c1 : (i == 2 ? c2 : c3));
            int bx = BlockPos.getX(p);
            int by = BlockPos.getY(p);
            int bz = BlockPos.getZ(p);
            if (maxX > bx + 1.0E-7 && minX < bx + 1.0 - 1.0E-7 && maxY > by + 1.0E-7 && minY < by + 1.0 - 1.0E-7) {
                if (dz > 0.0 && maxZ <= bz + 1.0E-7) {
                    double d = bz - maxZ;
                    if (d < dz) dz = d;
                } else if (dz < 0.0 && minZ >= bz + 1.0 - 1.0E-7) {
                    double d = (bz + 1.0) - minZ;
                    if (d > dz) dz = d;
                }
            }
        }
        return dz;
    }

    @WrapMethod(method = "getLightCoords")
    private int papi$fastCachedParticleLight(float partialTick, Operation<Integer> original) {
        ClientLevel lvl = this.level;
        if (lvl != null) {
            return PapiOcclusionEngine.getCachedParticleLight(lvl, this.x, this.y, this.z);
        }
        return original.call(partialTick);
    }
}
