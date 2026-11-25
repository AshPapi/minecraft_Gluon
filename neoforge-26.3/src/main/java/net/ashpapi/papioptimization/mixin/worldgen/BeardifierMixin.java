package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Beardifier.class, priority = 1500)
public abstract class BeardifierMixin {

    @Shadow @Final protected List<Beardifier.Rigid> pieces;
    @Shadow @Final protected List<JigsawJunction> junctions;
    @Shadow @Final private BoundingBox affectedBox;

    @Shadow
    protected static float getBuryContribution(float dx, float dy, float dz) {
        return 0.0F;
    }

    @Shadow
    protected static float getBeardContribution(int dx, int dy, int dz, int dyRaw) {
        return 0.0F;
    }

    @Unique private int[] papi$pieceCoords;
    @Unique private byte[] papi$pieceTypes;
    @Unique private int papi$pieceCount;
    @Unique private int[] papi$junctionCoords;
    @Unique private int papi$junctionCount;
    @Unique private boolean papi$useSoA;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$buildSoAArrays(List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions, BoundingBox affectedBox, CallbackInfo ci) {
        if (affectedBox == null || (pieces.isEmpty() && junctions.isEmpty())) {
            this.papi$useSoA = true;
            return;
        }
        int pSize = pieces.size();
        int[] pCoords = new int[pSize * 7];
        byte[] pTypes = new byte[pSize];
        for (int i = 0; i < pSize; i++) {
            Beardifier.Rigid r = pieces.get(i);
            BoundingBox b = r.box();
            int base = i * 7;
            pCoords[base] = b.minX();
            pCoords[base + 1] = b.minY();
            pCoords[base + 2] = b.minZ();
            pCoords[base + 3] = b.maxX();
            pCoords[base + 4] = b.maxY();
            pCoords[base + 5] = b.maxZ();
            pCoords[base + 6] = r.groundLevelDelta();
            TerrainAdjustment adj = r.terrainAdjustment();
            byte code;
            if (adj == TerrainAdjustment.NONE) {
                code = 0;
            } else if (adj == TerrainAdjustment.BURY) {
                code = 1;
            } else if (adj == TerrainAdjustment.BEARD_THIN) {
                code = 2;
            } else if (adj == TerrainAdjustment.BEARD_BOX) {
                code = 3;
            } else if (adj == TerrainAdjustment.ENCAPSULATE) {
                code = 4;
            } else {
                this.papi$useSoA = false;
                return;
            }
            pTypes[i] = code;
        }

        int jSize = junctions.size();
        int[] jCoords = new int[jSize * 3];
        for (int i = 0; i < jSize; i++) {
            JigsawJunction j = junctions.get(i);
            int base = i * 3;
            jCoords[base] = j.getSourceX();
            jCoords[base + 1] = j.getSourceGroundY();
            jCoords[base + 2] = j.getSourceZ();
        }

        this.papi$pieceCoords = pCoords;
        this.papi$pieceTypes = pTypes;
        this.papi$pieceCount = pSize;
        this.papi$junctionCoords = jCoords;
        this.papi$junctionCount = jSize;
        this.papi$useSoA = true;
    }

    @WrapMethod(method = "sampleValueUnchecked")
    private float papi$fastSampleValueUnchecked(int x, int y, int z, Operation<Float> original) {
        if (!this.papi$useSoA) {
            return original.call(x, y, z);
        }
        float total = 0.0F;
        int pCount = this.papi$pieceCount;
        int[] pCoords = this.papi$pieceCoords;
        byte[] pTypes = this.papi$pieceTypes;
        for (int i = 0; i < pCount; i++) {
            byte code = pTypes[i];
            if (code == 0) {
                continue;
            }
            int base = i * 7;
            int minX = pCoords[base];
            int minY = pCoords[base + 1];
            int minZ = pCoords[base + 2];
            int maxX = pCoords[base + 3];
            int maxY = pCoords[base + 4];
            int maxZ = pCoords[base + 5];
            int delta = pCoords[base + 6];

            int dx = Math.max(0, Math.max(minX - x, x - maxX));
            int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            int groundY = minY + delta;
            int dyFromGround = y - groundY;

            switch (code) {
                case 1 -> total += getBuryContribution((float) dx, (float) dyFromGround * 0.5F, (float) dz);
                case 2 -> {
                    if (dx <= 12 && dz <= 12 && dyFromGround >= -12 && dyFromGround <= 12) {
                        total += getBeardContribution(dx, dyFromGround, dz, dyFromGround) * 0.8F;
                    }
                }
                case 3 -> {
                    int dyBox = Math.max(0, Math.max(groundY - y, y - maxY));
                    if (dx <= 12 && dz <= 12 && dyBox <= 12) {
                        total += getBeardContribution(dx, dyBox, dz, dyFromGround) * 0.8F;
                    }
                }
                case 4 -> {
                    int dyEnc = Math.max(0, Math.max(minY - y, y - maxY));
                    total += getBuryContribution((float) dx * 0.5F, (float) dyEnc * 0.5F, (float) dz * 0.5F) * 0.8F;
                }
                default -> {
                }
            }
        }

        int jCount = this.papi$junctionCount;
        int[] jCoords = this.papi$junctionCoords;
        for (int i = 0; i < jCount; i++) {
            int base = i * 3;
            int dx = x - jCoords[base];
            int dy = y - jCoords[base + 1];
            int dz = z - jCoords[base + 2];
            if (dx >= -12 && dx <= 12 && dy >= -12 && dy <= 12 && dz >= -12 && dz <= 12) {
                total += getBeardContribution(dx, dy, dz, dy) * 0.4F;
            }
        }
        return total;
    }
}
