package net.ashpapi.papioptimization.worldgen;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FastJigsawSpace extends VoxelShape {
    private static final CubePointRange COORDS = new CubePointRange(1);

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private final List<BoundingBox> placedBoxes = new ArrayList<>();
    private Long2ObjectOpenHashMap<ObjectArrayList<BoundingBox>> chunkBuckets;

    public FastJigsawSpace(AABB outerBounds) {
        super(createUnitShape());
        this.minX = (int) Math.floor(outerBounds.minX + 1.0E-5);
        this.minY = (int) Math.floor(outerBounds.minY + 1.0E-5);
        this.minZ = (int) Math.floor(outerBounds.minZ + 1.0E-5);
        this.maxX = (int) Math.ceil(outerBounds.maxX - 1.0E-5);
        this.maxY = (int) Math.ceil(outerBounds.maxY - 1.0E-5);
        this.maxZ = (int) Math.ceil(outerBounds.maxZ - 1.0E-5);
    }

    public FastJigsawSpace(BoundingBox sourceBox) {
        super(createUnitShape());
        this.minX = sourceBox.minX();
        this.minY = sourceBox.minY();
        this.minZ = sourceBox.minZ();
        this.maxX = sourceBox.maxX() + 1;
        this.maxY = sourceBox.maxY() + 1;
        this.maxZ = sourceBox.maxZ() + 1;
    }

    private static BitSetDiscreteVoxelShape createUnitShape() {
        BitSetDiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);
        return shape;
    }

    @Override
    public boolean isEmpty() {
        return this.minX >= this.maxX || this.minY >= this.maxY || this.minZ >= this.maxZ;
    }

    public static BoundingBox toPieceBoundingBox(AABB b) {
        return new BoundingBox(
            (int) Math.floor(b.minX + 1.0E-5),
            (int) Math.floor(b.minY + 1.0E-5),
            (int) Math.floor(b.minZ + 1.0E-5),
            (int) Math.ceil(b.maxX - 1.0E-5) - 1,
            (int) Math.ceil(b.maxY - 1.0E-5) - 1,
            (int) Math.ceil(b.maxZ - 1.0E-5) - 1
        );
    }

    public boolean isCollidingOrOutside(BoundingBox targetBB) {
        if (targetBB.minX() < this.minX || targetBB.maxX() + 1 > this.maxX
                || targetBB.minY() < this.minY || targetBB.maxY() + 1 > this.maxY
                || targetBB.minZ() < this.minZ || targetBB.maxZ() + 1 > this.maxZ) {
            return true;
        }

        int count = this.placedBoxes.size();
        if (count < 16 || this.chunkBuckets == null) {
            for (int i = 0; i < count; i++) {
                if (this.placedBoxes.get(i).intersects(targetBB)) {
                    return true;
                }
            }
            return false;
        }

        int minChunkX = targetBB.minX() >> 4;
        int maxChunkX = targetBB.maxX() >> 4;
        int minChunkZ = targetBB.minZ() >> 4;
        int maxChunkZ = targetBB.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkPos.pack(cx, cz);
                ObjectArrayList<BoundingBox> bucket = this.chunkBuckets.get(key);
                if (bucket != null) {
                    int bSize = bucket.size();
                    for (int i = 0; i < bSize; i++) {
                        if (bucket.get(i).intersects(targetBB)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public void addPiece(BoundingBox targetBB) {
        this.placedBoxes.add(targetBB);

        if (this.placedBoxes.size() == 16 && this.chunkBuckets == null) {
            this.chunkBuckets = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < this.placedBoxes.size(); i++) {
                indexBox(this.placedBoxes.get(i));
            }
        } else if (this.chunkBuckets != null) {
            indexBox(targetBB);
        }
    }

    private void indexBox(BoundingBox box) {
        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkPos.pack(cx, cz);
                this.chunkBuckets.computeIfAbsent(key, k -> new ObjectArrayList<>()).add(box);
            }
        }
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return COORDS;
    }

    @Override
    protected int findIndex(Direction.Axis axis, double coord) {
        return Mth.clamp((int) Math.floor(coord), -1, 0);
    }
}
