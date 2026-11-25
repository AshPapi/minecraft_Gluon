package net.ashpapi.papioptimization.redstone;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.EnumSet;
import net.ashpapi.papioptimization.access.PapiServerLevelRedstone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

public final class PapiRedstoneWavefront {

    private static final int BASE_CAPACITY = 256;
    private static final int TRIM_THRESHOLD = 2048;
    private static final int MAX_CLUSTER_NODES = 8192;
    private static final int UPDATE_KNOWN_AND_CLIENTS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Direction[] ALL_DIRS = PapiRedstoneDirections.ALL;
    private static final Direction[] HORIZONTAL = PapiRedstoneDirections.HORIZONTAL;
    private static final Direction[] SHAPE_UPDATE_ORDER = {
        Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP
    };
    private static final EnumSet<Direction> ALL_DIRECTION_SET = EnumSet.allOf(Direction.class);

    private static final byte[] FLOW_DIR_FROM_MASK = {
        2, 0, 1, 0,
        2, 2, 1, 1,
        3, 3, 1, 0,
        2, 3, 2, 2
    };

    private static final Direction[][] FLOW_NOTIFY_ORDER = new Direction[4][6];

    static {
        for (int fwd = 0; fwd < 4; fwd++) {
            FLOW_NOTIFY_ORDER[fwd][0] = HORIZONTAL[(fwd + 1) & 3];
            FLOW_NOTIFY_ORDER[fwd][1] = HORIZONTAL[(fwd + 3) & 3];
            FLOW_NOTIFY_ORDER[fwd][2] = Direction.DOWN;
            FLOW_NOTIFY_ORDER[fwd][3] = Direction.UP;
            FLOW_NOTIFY_ORDER[fwd][4] = HORIZONTAL[(fwd + 2) & 3];
            FLOW_NOTIFY_ORDER[fwd][5] = HORIZONTAL[fwd];
        }
    }

    public static final class Solver {
        final Long2IntOpenHashMap nodeIndex = new Long2IntOpenHashMap(BASE_CAPACITY);
        final LongArrayList positions = new LongArrayList(BASE_CAPACITY);
        final Long2ByteOpenHashMap conductorDirectCache = new Long2ByteOpenHashMap(BASE_CAPACITY);

        int[] oldPower = new int[BASE_CAPACITY];
        int[] newPower = new int[BASE_CAPACITY];
        byte[] externalPower = new byte[BASE_CAPACITY];
        byte[] flowIn = new byte[BASE_CAPACITY];
        boolean[] inCluster = new boolean[BASE_CAPACITY];
        boolean[] queued = new boolean[BASE_CAPACITY];
        boolean[] expanded = new boolean[BASE_CAPACITY];
        boolean[] changed = new boolean[BASE_CAPACITY];
        boolean[] removed = new boolean[BASE_CAPACITY];
        byte[] edgeCount = new byte[BASE_CAPACITY];
        int[] edgeData = new int[BASE_CAPACITY << 3];
        int[] sortedChanged = new int[BASE_CAPACITY];

        final int[][] buckets = new int[16][64];
        final int[] bucketSizes = new int[16];
        final int[] bucketCursors = new int[16];
        final int[] peakCounts = new int[16];
        final int[] peakOffsets = new int[16];
        int highestBucket = 0;

        int[] bfsQueue = new int[512];

        final LongOpenHashSet notifyCenters = new LongOpenHashSet(BASE_CAPACITY);
        final LongOpenHashSet notifySeen = new LongOpenHashSet(BASE_CAPACITY);
        long[] notifyQueue = new long[512];
        byte[] notifyQueueDir = new byte[512];
        RedstoneWireBlock[] notifyQueueWire = new RedstoneWireBlock[512];
        Orientation[] notifyQueueBaseOrient = new Orientation[512];
        int notifyHead = 0;
        int notifyTail = 0;

        RedstoneWireBlock[] pendingWires = new RedstoneWireBlock[32];
        long[] pendingPositions = new long[32];
        BlockState[] pendingStates = new BlockState[32];
        Orientation[] pendingOrientations = new Orientation[32];
        boolean[] pendingSkipShapes = new boolean[32];
        int pendingHead = 0;
        int pendingTail = 0;

        final long[] chunkKeys = new long[4];
        final LevelChunk[] chunkRefs = new LevelChunk[4];

        final EnumSet<Direction> scratchSides = EnumSet.allOf(Direction.class);
        final BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutNbr = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutStep = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutDirect = new BlockPos.MutableBlockPos();

        boolean active = false;
        boolean inDrainLoop = false;
        int peakNodes = 0;

        public Solver() {
            this.nodeIndex.defaultReturnValue(-1);
            this.conductorDirectCache.defaultReturnValue((byte) -1);
            Arrays.fill(this.chunkKeys, Long.MIN_VALUE);
        }

        boolean enqueueOrRun(
                RedstoneWireBlock wireBlock,
                Level level,
                BlockPos pos,
                BlockState state,
                @Nullable Orientation orientation,
                boolean skipShapeUpdates
        ) {
            if (this.active) {
                this.enqueuePending(wireBlock, pos.asLong(), state, orientation, skipShapeUpdates);
                return true;
            }
            this.active = true;
            this.inDrainLoop = false;
            this.peakNodes = 0;
            try {
                if (!this.solveSingleWave(wireBlock, level, pos.asLong(), state, orientation, skipShapeUpdates)) {
                    return false;
                }
                this.inDrainLoop = true;
                this.drainPendingAndNotifications(level);
                return true;
            } finally {
                this.finishOuter();
            }
        }

        private void enqueuePending(
                RedstoneWireBlock wireBlock,
                long packedPos,
                BlockState state,
                @Nullable Orientation orientation,
                boolean skipShapeUpdates
        ) {
            if (this.pendingTail == this.pendingPositions.length) {
                if (this.pendingHead > 0) {
                    int rem = this.pendingTail - this.pendingHead;
                    System.arraycopy(this.pendingWires, this.pendingHead, this.pendingWires, 0, rem);
                    System.arraycopy(this.pendingPositions, this.pendingHead, this.pendingPositions, 0, rem);
                    System.arraycopy(this.pendingStates, this.pendingHead, this.pendingStates, 0, rem);
                    System.arraycopy(this.pendingOrientations, this.pendingHead, this.pendingOrientations, 0, rem);
                    System.arraycopy(this.pendingSkipShapes, this.pendingHead, this.pendingSkipShapes, 0, rem);
                    Arrays.fill(this.pendingWires, rem, this.pendingTail, null);
                    Arrays.fill(this.pendingStates, rem, this.pendingTail, null);
                    Arrays.fill(this.pendingOrientations, rem, this.pendingTail, null);
                    this.pendingHead = 0;
                    this.pendingTail = rem;
                } else {
                    int next = this.pendingPositions.length << 1;
                    this.pendingWires = Arrays.copyOf(this.pendingWires, next);
                    this.pendingPositions = Arrays.copyOf(this.pendingPositions, next);
                    this.pendingStates = Arrays.copyOf(this.pendingStates, next);
                    this.pendingOrientations = Arrays.copyOf(this.pendingOrientations, next);
                    this.pendingSkipShapes = Arrays.copyOf(this.pendingSkipShapes, next);
                }
            }
            int idx = this.pendingTail++;
            this.pendingWires[idx] = wireBlock;
            this.pendingPositions[idx] = packedPos;
            this.pendingStates[idx] = state;
            this.pendingOrientations[idx] = orientation;
            this.pendingSkipShapes[idx] = skipShapeUpdates;
        }

        private void drainPendingAndNotifications(Level level) {
            while (this.pendingHead < this.pendingTail || this.notifyHead < this.notifyTail) {
                while (this.pendingHead < this.pendingTail) {
                    int idx = this.pendingHead++;
                    RedstoneWireBlock wb = this.pendingWires[idx];
                    long pPos = this.pendingPositions[idx];
                    BlockState pState = this.pendingStates[idx];
                    Orientation pOrient = this.pendingOrientations[idx];
                    boolean pSkip = this.pendingSkipShapes[idx];
                    this.pendingWires[idx] = null;
                    this.pendingStates[idx] = null;
                    this.pendingOrientations[idx] = null;
                    this.solveSingleWave(wb, level, pPos, pState, pOrient, pSkip);
                }
                if (this.notifyHead < this.notifyTail) {
                    int nIdx = this.notifyHead++;
                    long np = this.notifyQueue[nIdx];
                    byte dirOrd = this.notifyQueueDir[nIdx];
                    RedstoneWireBlock wb = this.notifyQueueWire[nIdx];
                    Orientation baseOrient = this.notifyQueueBaseOrient[nIdx];
                    this.notifyQueueWire[nIdx] = null;
                    this.notifyQueueBaseOrient[nIdx] = null;
                    int nx = BlockPos.getX(np);
                    int ny = BlockPos.getY(np);
                    int nz = BlockPos.getZ(np);
                    this.mutPos.set(nx, ny, nz);
                    BlockState targetState = level.getBlockState(this.mutPos);
                    if (!targetState.isAir()) {
                        Direction front = (dirOrd >= 0 && dirOrd < ALL_DIRS.length) ? ALL_DIRS[dirOrd] : null;
                        Orientation orient = baseOrient != null && front != null
                                ? ExperimentalRedstoneUtils.withFront(baseOrient, front)
                                : ExperimentalRedstoneUtils.initialOrientation(level, front, null);
                        level.neighborChanged(BlockPos.of(np), wb, orient);
                    }
                }
            }
        }

        private void resetWave() {
            int sz = this.positions.size();
            if (sz > this.peakNodes) {
                this.peakNodes = sz;
            }
            this.nodeIndex.clear();
            this.positions.clear();
            this.conductorDirectCache.clear();
            this.notifyCenters.clear();
            this.notifySeen.clear();
            this.highestBucket = 0;
            for (int p = 0; p < 16; p++) {
                this.bucketSizes[p] = 0;
                this.bucketCursors[p] = 0;
            }
            Arrays.fill(this.chunkKeys, Long.MIN_VALUE);
            Arrays.fill(this.chunkRefs, null);
        }

        private void finishOuter() {
            int sz = this.positions.size();
            if (sz > this.peakNodes) {
                this.peakNodes = sz;
            }
            int peakNotify = this.notifyTail;
            int peakCond = this.conductorDirectCache.size();

            this.nodeIndex.clear();
            this.positions.clear();
            this.conductorDirectCache.clear();
            this.notifyCenters.clear();
            this.notifySeen.clear();
            this.highestBucket = 0;
            for (int p = 0; p < 16; p++) {
                this.bucketSizes[p] = 0;
                this.bucketCursors[p] = 0;
            }
            Arrays.fill(this.chunkKeys, Long.MIN_VALUE);
            Arrays.fill(this.chunkRefs, null);

            if (this.notifyTail > 0) {
                Arrays.fill(this.notifyQueueWire, 0, this.notifyTail, null);
                Arrays.fill(this.notifyQueueBaseOrient, 0, this.notifyTail, null);
            }
            if (this.pendingTail > 0) {
                Arrays.fill(this.pendingWires, 0, this.pendingTail, null);
                Arrays.fill(this.pendingStates, 0, this.pendingTail, null);
                Arrays.fill(this.pendingOrientations, 0, this.pendingTail, null);
            }
            this.notifyHead = 0;
            this.notifyTail = 0;
            this.pendingHead = 0;
            this.pendingTail = 0;
            this.inDrainLoop = false;
            this.active = false;

            if (this.peakNodes > TRIM_THRESHOLD || peakNotify > TRIM_THRESHOLD || peakCond > TRIM_THRESHOLD) {
                this.nodeIndex.trim(BASE_CAPACITY);
                this.positions.trim(BASE_CAPACITY);
                this.conductorDirectCache.trim(BASE_CAPACITY);
                this.notifyCenters.trim(BASE_CAPACITY);
                this.notifySeen.trim(BASE_CAPACITY);
                if (this.oldPower.length > TRIM_THRESHOLD) {
                    this.oldPower = new int[BASE_CAPACITY];
                    this.newPower = new int[BASE_CAPACITY];
                    this.externalPower = new byte[BASE_CAPACITY];
                    this.flowIn = new byte[BASE_CAPACITY];
                    this.inCluster = new boolean[BASE_CAPACITY];
                    this.queued = new boolean[BASE_CAPACITY];
                    this.expanded = new boolean[BASE_CAPACITY];
                    this.changed = new boolean[BASE_CAPACITY];
                    this.removed = new boolean[BASE_CAPACITY];
                    this.edgeCount = new byte[BASE_CAPACITY];
                    this.edgeData = new int[BASE_CAPACITY << 3];
                    this.sortedChanged = new int[BASE_CAPACITY];
                }
                if (this.bfsQueue.length > TRIM_THRESHOLD) {
                    this.bfsQueue = new int[512];
                }
                if (this.notifyQueue.length > (TRIM_THRESHOLD << 1)) {
                    this.notifyQueue = new long[512];
                    this.notifyQueueDir = new byte[512];
                    this.notifyQueueWire = new RedstoneWireBlock[512];
                    this.notifyQueueBaseOrient = new Orientation[512];
                }
                for (int p = 2; p < 16; p++) {
                    if (this.buckets[p].length > TRIM_THRESHOLD) {
                        this.buckets[p] = new int[64];
                    }
                }
            }
        }

        private void ensureCapacity(int cap) {
            if (cap <= this.oldPower.length) {
                return;
            }
            int nextCap = Math.max(cap, this.oldPower.length << 1);
            this.oldPower = Arrays.copyOf(this.oldPower, nextCap);
            this.newPower = Arrays.copyOf(this.newPower, nextCap);
            this.externalPower = Arrays.copyOf(this.externalPower, nextCap);
            this.flowIn = Arrays.copyOf(this.flowIn, nextCap);
            this.inCluster = Arrays.copyOf(this.inCluster, nextCap);
            this.queued = Arrays.copyOf(this.queued, nextCap);
            this.expanded = Arrays.copyOf(this.expanded, nextCap);
            this.changed = Arrays.copyOf(this.changed, nextCap);
            this.removed = Arrays.copyOf(this.removed, nextCap);
            this.edgeCount = Arrays.copyOf(this.edgeCount, nextCap);
            this.edgeData = Arrays.copyOf(this.edgeData, nextCap << 3);
            this.sortedChanged = new int[nextCap];
        }

        private void enqueueBfs(int tail, int vIdx) {
            if (tail == this.bfsQueue.length) {
                this.bfsQueue = Arrays.copyOf(this.bfsQueue, this.bfsQueue.length << 1);
            }
            this.bfsQueue[tail] = vIdx;
        }

        private void pushBucket(int power, int idx) {
            if (power <= 1) {
                return;
            }
            int clamped = power > 15 ? 15 : power;
            int size = this.bucketSizes[clamped];
            int[] arr = this.buckets[clamped];
            if (size == arr.length) {
                arr = Arrays.copyOf(arr, size << 1);
                this.buckets[clamped] = arr;
            }
            arr[size] = idx;
            this.bucketSizes[clamped] = size + 1;
            if (clamped > this.highestBucket) {
                this.highestBucket = clamped;
            }
        }

        private BlockState getBlockStateCached(Level level, int x, int y, int z, BlockPos.MutableBlockPos pos) {
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(y) || x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) {
                return Blocks.VOID_AIR.defaultBlockState();
            }
            int cx = x >> 4;
            int cz = z >> 4;
            long cKey = ChunkPos.pack(cx, cz);
            int slot = ((cx * 31) ^ cz) & 3;
            LevelChunk chunk;
            if (this.chunkKeys[slot] == cKey) {
                chunk = this.chunkRefs[slot];
            } else {
                chunk = level.getChunk(cx, cz);
                this.chunkKeys[slot] = cKey;
                this.chunkRefs[slot] = chunk;
            }
            if (level.isDebug()) {
                return chunk.getBlockState(pos);
            }
            int secIdx = chunk.getSectionIndex(y);
            LevelChunkSection[] sections = chunk.getSections();
            if (secIdx >= 0 && secIdx < sections.length) {
                LevelChunkSection section = sections[secIdx];
                if (section.hasOnlyAir()) {
                    return Blocks.AIR.defaultBlockState();
                }
                return section.getBlockState(x & 15, y & 15, z & 15);
            }
            return Blocks.AIR.defaultBlockState();
        }

        private int getOrAddNode(long packed, BlockState state, boolean member) {
            int idx = this.nodeIndex.get(packed);
            if (idx >= 0) {
                if (member && !this.inCluster[idx]) {
                    this.inCluster[idx] = true;
                    this.newPower[idx] = 0;
                }
                return idx;
            }
            idx = this.positions.size();
            this.ensureCapacity(idx + 1);
            this.nodeIndex.put(packed, idx);
            this.positions.add(packed);
            int p = state.getValue(RedstoneWireBlock.POWER);
            this.oldPower[idx] = p;
            this.newPower[idx] = member ? 0 : p;
            this.externalPower[idx] = -1;
            this.flowIn[idx] = 0;
            this.inCluster[idx] = member;
            this.queued[idx] = false;
            this.expanded[idx] = false;
            this.changed[idx] = false;
            this.removed[idx] = false;
            this.edgeCount[idx] = 0;
            return idx;
        }

        private int getExternalPower(RedstoneWireBlock wireBlock, Level level, int idx) {
            byte cached = this.externalPower[idx];
            if (cached >= 0) {
                return cached;
            }
            long packed = this.positions.getLong(idx);
            int ext = this.computeExternalSignal(
                    wireBlock,
                    level,
                    BlockPos.getX(packed),
                    BlockPos.getY(packed),
                    BlockPos.getZ(packed)
            );
            this.externalPower[idx] = (byte) ext;
            return ext;
        }

        private int computeExternalSignal(RedstoneWireBlock wireBlock, Level level, int x, int y, int z) {
            int best = 0;
            for (int i = 0; i < 6; i++) {
                Direction dir = ALL_DIRS[i];
                int nx = x + dir.getStepX();
                int ny = y + dir.getStepY();
                int nz = z + dir.getStepZ();
                BlockState nState = this.getBlockStateCached(level, nx, ny, nz, this.mutNbr);
                if (nState.isAir() || nState.is(wireBlock)) {
                    continue;
                }
                int sig = nState.getSignal(level, this.mutNbr, dir);
                if (sig >= 15) {
                    return 15;
                }
                if (nState.shouldCheckWeakPower(level, this.mutNbr, dir)) {
                    int direct = this.getConductorDirectSignal(wireBlock, level, nx, ny, nz);
                    if (direct >= 15) {
                        return 15;
                    }
                    if (direct > sig) {
                        sig = direct;
                    }
                }
                if (sig > best) {
                    best = sig;
                }
            }
            return best;
        }

        private int getConductorDirectSignal(RedstoneWireBlock wireBlock, Level level, int cx, int cy, int cz) {
            long cPacked = BlockPos.asLong(cx, cy, cz);
            byte cached = this.conductorDirectCache.get(cPacked);
            if (cached >= 0) {
                return cached;
            }
            int best = 0;
            for (int i = 0; i < 6; i++) {
                Direction dir = ALL_DIRS[i];
                int sx = cx + dir.getStepX();
                int sy = cy + dir.getStepY();
                int sz = cz + dir.getStepZ();
                BlockState sState = this.getBlockStateCached(level, sx, sy, sz, this.mutDirect);
                if (sState.isAir() || sState.is(wireBlock)) {
                    continue;
                }
                int sig = sState.getDirectSignal(level, this.mutDirect, dir);
                if (sig >= 15) {
                    this.conductorDirectCache.put(cPacked, (byte) 15);
                    return 15;
                }
                if (sig > best) {
                    best = sig;
                }
            }
            this.conductorDirectCache.put(cPacked, (byte) best);
            return best;
        }

        private void discoverNode(RedstoneWireBlock wireBlock, Level level, int uIdx) {
            if (this.expanded[uIdx]) {
                return;
            }
            this.expanded[uIdx] = true;
            long uPacked = this.positions.getLong(uIdx);
            int ux = BlockPos.getX(uPacked);
            int uy = BlockPos.getY(uPacked);
            int uz = BlockPos.getZ(uPacked);

            if (this.externalPower[uIdx] < 0) {
                this.externalPower[uIdx] = (byte) this.computeExternalSignal(wireBlock, level, ux, uy, uz);
            }

            BlockState belowState = this.getBlockStateCached(level, ux, uy - 1, uz, this.mutPos);
            boolean belowUConductor = belowState.isRedstoneConductor(level, this.mutPos);

            BlockState aboveState = this.getBlockStateCached(level, ux, uy + 1, uz, this.mutPos);
            boolean aboveUConductor = aboveState.isRedstoneConductor(level, this.mutPos);

            int startDir = FLOW_DIR_FROM_MASK[this.flowIn[uIdx] & 15];
            int deg = 0;

            for (int step = 0; step < 4; step++) {
                int d = (startDir + step) & 3;
                Direction dir = HORIZONTAL[d];
                int vx = ux + dir.getStepX();
                int vz = uz + dir.getStepZ();

                BlockState sideState = this.getBlockStateCached(level, vx, uy, vz, this.mutNbr);
                if (sideState.is(wireBlock)) {
                    int vIdx = this.getOrAddNode(BlockPos.asLong(vx, uy, vz), sideState, false);
                    this.edgeData[(uIdx << 3) + deg++] = (vIdx << 4) | (d << 2) | 3;
                    continue;
                }

                boolean sideConductor = sideState.isRedstoneConductor(level, this.mutNbr);
                if (!aboveUConductor) {
                    BlockState upState = this.getBlockStateCached(level, vx, uy + 1, vz, this.mutStep);
                    if (upState.is(wireBlock)) {
                        int vIdx = this.getOrAddNode(BlockPos.asLong(vx, uy + 1, vz), upState, false);
                        this.edgeData[(uIdx << 3) + deg++] = (vIdx << 4) | (d << 2) | 2 | (sideConductor ? 1 : 0);
                    }
                }
                if (!sideConductor) {
                    BlockState downState = this.getBlockStateCached(level, vx, uy - 1, vz, this.mutStep);
                    if (downState.is(wireBlock)) {
                        int vIdx = this.getOrAddNode(BlockPos.asLong(vx, uy - 1, vz), downState, false);
                        this.edgeData[(uIdx << 3) + deg++] = (vIdx << 4) | (d << 2) | (belowUConductor ? 2 : 0) | 1;
                    }
                }
            }
            this.edgeCount[uIdx] = (byte) deg;
        }

        private int computeInitialDirect(RedstoneWireBlock wireBlock, Level level, int uIdx) {
            this.discoverNode(wireBlock, level, uIdx);
            int ext = this.externalPower[uIdx];
            if (ext >= 15) {
                return 15;
            }
            int maxWire = 0;
            int bestMask = 0;
            int deg = this.edgeCount[uIdx];
            int base = uIdx << 3;
            for (int k = 0; k < deg; k++) {
                int edge = this.edgeData[base + k];
                if ((edge & 1) == 0) {
                    continue;
                }
                int vIdx = edge >>> 4;
                int d = (edge >>> 2) & 3;
                int np = this.oldPower[vIdx];
                if (np > maxWire) {
                    maxWire = np;
                    bestMask = 1 << ((d + 2) & 3);
                } else if (np == maxWire && np > 0) {
                    bestMask |= 1 << ((d + 2) & 3);
                }
            }
            int fromWire = maxWire > 0 ? maxWire - 1 : 0;
            if (fromWire > ext) {
                this.flowIn[uIdx] |= (byte) bestMask;
                return fromWire;
            }
            return ext;
        }

        private static int horizontalIndex(Direction dir) {
            return switch (dir) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> -1;
            };
        }

        private boolean solveSingleWave(
                RedstoneWireBlock wireBlock,
                Level level,
                long rootPacked,
                BlockState rootState,
                @Nullable Orientation orientation,
                boolean skipShapeUpdates
        ) {
            this.resetWave();

            int rx = BlockPos.getX(rootPacked);
            int ry = BlockPos.getY(rootPacked);
            int rz = BlockPos.getZ(rootPacked);
            BlockState curState = this.getBlockStateCached(level, rx, ry, rz, this.mutPos);

            if (!curState.is(wireBlock)) {
                if (!rootState.is(wireBlock)) {
                    return false;
                }
                int rootOld = rootState.getValue(RedstoneWireBlock.POWER);
                if (rootOld <= 0) {
                    return true;
                }
                return this.solveRemovedWireRoot(wireBlock, level, rootPacked, rx, ry, rz, rootOld, orientation);
            }

            int rootIdx = this.getOrAddNode(rootPacked, curState, true);
            if (orientation != null) {
                int hIdx = horizontalIndex(orientation.getFront());
                if (hIdx >= 0) {
                    this.flowIn[rootIdx] = (byte) (1 << hIdx);
                }
            }

            int rootOld = this.oldPower[rootIdx];
            int rootDirect = this.computeInitialDirect(wireBlock, level, rootIdx);

            if (rootDirect == rootOld) {
                return true;
            }

            if (rootDirect > rootOld) {
                this.newPower[rootIdx] = rootDirect;
                this.pushBucket(rootDirect, rootIdx);
                if (orientation != null) {
                    this.seedSiblingSourceWiresPowerUp(wireBlock, level, rootPacked, rx, ry, rz, orientation.getFront().getOpposite());
                }
                if (!this.drainWavefront(wireBlock, level)) {
                    return false;
                }
            } else {
                int tail = 0;
                this.queued[rootIdx] = true;
                this.enqueueBfs(tail++, rootIdx);
                if (orientation != null) {
                    tail = this.seedSiblingSourceWiresDepower(wireBlock, level, rootPacked, rx, ry, rz, orientation.getFront().getOpposite(), tail);
                }
                if (!this.collectDepowerCluster(wireBlock, level, tail)) {
                    return false;
                }
                this.seedClusterBoundaryAndDirect(wireBlock, level);
                if (!this.drainWavefront(wireBlock, level)) {
                    return false;
                }
            }

            this.commitAndNotify(wireBlock, level, orientation, skipShapeUpdates, rootPacked, false, 0L);
            return true;
        }

        private boolean solveRemovedWireRoot(
                RedstoneWireBlock wireBlock,
                Level level,
                long rootPacked,
                int rx,
                int ry,
                int rz,
                int rootOld,
                @Nullable Orientation orientation
        ) {
            BlockState belowState = this.getBlockStateCached(level, rx, ry - 1, rz, this.mutPos);
            boolean belowConductor = belowState.isRedstoneConductor(level, this.mutPos);

            BlockState aboveState = this.getBlockStateCached(level, rx, ry + 1, rz, this.mutPos);
            boolean aboveConductor = aboveState.isRedstoneConductor(level, this.mutPos);

            int tail = 0;
            for (int d = 0; d < 4; d++) {
                Direction dir = HORIZONTAL[d];
                int vx = rx + dir.getStepX();
                int vz = rz + dir.getStepZ();

                BlockState sideState = this.getBlockStateCached(level, vx, ry, vz, this.mutNbr);
                if (sideState.is(wireBlock)) {
                    tail = this.trySeedRemovedNeighbor(wireBlock, level, BlockPos.asLong(vx, ry, vz), sideState, rootOld, d, tail);
                    continue;
                }

                boolean sideConductor = sideState.isRedstoneConductor(level, this.mutNbr);
                if (!aboveConductor) {
                    BlockState upState = this.getBlockStateCached(level, vx, ry + 1, vz, this.mutStep);
                    if (upState.is(wireBlock)) {
                        tail = this.trySeedRemovedNeighbor(wireBlock, level, BlockPos.asLong(vx, ry + 1, vz), upState, rootOld, d, tail);
                    }
                }
                if (belowConductor && !sideConductor) {
                    BlockState downState = this.getBlockStateCached(level, vx, ry - 1, vz, this.mutStep);
                    if (downState.is(wireBlock)) {
                        tail = this.trySeedRemovedNeighbor(wireBlock, level, BlockPos.asLong(vx, ry - 1, vz), downState, rootOld, d, tail);
                    }
                }
            }

            if (tail > 0) {
                if (!this.collectDepowerCluster(wireBlock, level, tail)) {
                    return false;
                }
                this.seedClusterBoundaryAndDirect(wireBlock, level);
                if (!this.drainWavefront(wireBlock, level)) {
                    return false;
                }
            }

            this.commitAndNotify(wireBlock, level, orientation, true, rootPacked, true, rootPacked);
            return true;
        }

        private int trySeedRemovedNeighbor(
                RedstoneWireBlock wireBlock,
                Level level,
                long vPacked,
                BlockState vState,
                int rootOld,
                int d,
                int tail
        ) {
            int vOld = vState.getValue(RedstoneWireBlock.POWER);
            if (vOld <= 0) {
                return tail;
            }
            int vIdx = this.getOrAddNode(vPacked, vState, false);
            boolean shouldInclude = vOld < rootOld
                    || (rootOld == 15 && vOld == 15 && this.getExternalPower(wireBlock, level, vIdx) < 15);
            if (shouldInclude) {
                if (!this.inCluster[vIdx]) {
                    this.inCluster[vIdx] = true;
                    this.newPower[vIdx] = 0;
                }
                this.flowIn[vIdx] |= (byte) (1 << d);
                if (!this.queued[vIdx]) {
                    this.queued[vIdx] = true;
                    this.enqueueBfs(tail++, vIdx);
                }
            }
            return tail;
        }

        private void seedSiblingSourceWiresPowerUp(
                RedstoneWireBlock wireBlock,
                Level level,
                long rootPacked,
                int rx,
                int ry,
                int rz,
                Direction toSource
        ) {
            int sx = rx + toSource.getStepX();
            int sy = ry + toSource.getStepY();
            int sz = rz + toSource.getStepZ();
            for (int i = 0; i < 6; i++) {
                Direction d = ALL_DIRS[i];
                int wx = sx + d.getStepX();
                int wy = sy + d.getStepY();
                int wz = sz + d.getStepZ();
                long wPacked = BlockPos.asLong(wx, wy, wz);
                if (wPacked == rootPacked) {
                    continue;
                }
                BlockState wState = this.getBlockStateCached(level, wx, wy, wz, this.mutNbr);
                if (!wState.is(wireBlock)) {
                    continue;
                }
                int wIdx = this.getOrAddNode(wPacked, wState, false);
                int wExt = this.getExternalPower(wireBlock, level, wIdx);
                int cur = this.inCluster[wIdx] ? this.newPower[wIdx] : this.oldPower[wIdx];
                if (wExt > cur) {
                    this.inCluster[wIdx] = true;
                    this.newPower[wIdx] = wExt;
                    int hIdx = horizontalIndex(d);
                    if (hIdx >= 0) {
                        this.flowIn[wIdx] |= (byte) (1 << hIdx);
                    }
                    this.pushBucket(wExt, wIdx);
                }
            }
        }

        private int seedSiblingSourceWiresDepower(
                RedstoneWireBlock wireBlock,
                Level level,
                long rootPacked,
                int rx,
                int ry,
                int rz,
                Direction toSource,
                int tail
        ) {
            int sx = rx + toSource.getStepX();
            int sy = ry + toSource.getStepY();
            int sz = rz + toSource.getStepZ();
            for (int i = 0; i < 6; i++) {
                Direction d = ALL_DIRS[i];
                int wx = sx + d.getStepX();
                int wy = sy + d.getStepY();
                int wz = sz + d.getStepZ();
                long wPacked = BlockPos.asLong(wx, wy, wz);
                if (wPacked == rootPacked) {
                    continue;
                }
                BlockState wState = this.getBlockStateCached(level, wx, wy, wz, this.mutNbr);
                if (!wState.is(wireBlock)) {
                    continue;
                }
                int wIdx = this.getOrAddNode(wPacked, wState, false);
                int wOld = this.oldPower[wIdx];
                if (wOld > 0 && this.computeInitialDirect(wireBlock, level, wIdx) < wOld) {
                    if (!this.inCluster[wIdx]) {
                        this.inCluster[wIdx] = true;
                        this.newPower[wIdx] = 0;
                    }
                    int hIdx = horizontalIndex(d);
                    if (hIdx >= 0) {
                        this.flowIn[wIdx] |= (byte) (1 << hIdx);
                    }
                    if (!this.queued[wIdx]) {
                        this.queued[wIdx] = true;
                        this.enqueueBfs(tail++, wIdx);
                    }
                }
            }
            return tail;
        }

        private boolean collectDepowerCluster(RedstoneWireBlock wireBlock, Level level, int tail) {
            int head = 0;
            while (head < tail) {
                if (!this.inDrainLoop && this.positions.size() >= MAX_CLUSTER_NODES) {
                    return false;
                }
                int uIdx = this.bfsQueue[head++];
                int uOld = this.oldPower[uIdx];
                this.discoverNode(wireBlock, level, uIdx);
                if (uOld <= 1) {
                    continue;
                }
                int deg = this.edgeCount[uIdx];
                int base = uIdx << 3;
                for (int k = 0; k < deg; k++) {
                    int edge = this.edgeData[base + k];
                    if ((edge & 2) == 0) {
                        continue;
                    }
                    int vIdx = edge >>> 4;
                    int d = (edge >>> 2) & 3;
                    int vOld = this.oldPower[vIdx];
                    if (vOld <= 0) {
                        continue;
                    }
                    boolean depower = vOld < uOld
                            || (uOld == 15 && vOld == 15 && this.getExternalPower(wireBlock, level, vIdx) < 15);
                    if (depower) {
                        if (!this.inCluster[vIdx]) {
                            this.inCluster[vIdx] = true;
                            this.newPower[vIdx] = 0;
                        }
                        this.flowIn[vIdx] |= (byte) (1 << d);
                        if (!this.queued[vIdx]) {
                            this.queued[vIdx] = true;
                            this.enqueueBfs(tail++, vIdx);
                        }
                    }
                }
            }
            return true;
        }

        private void seedClusterBoundaryAndDirect(RedstoneWireBlock wireBlock, Level level) {
            int count = this.positions.size();
            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i]) {
                    continue;
                }
                this.discoverNode(wireBlock, level, i);
                int best = this.externalPower[i];
                int bestMask = 0;
                if (best < 15) {
                    int deg = this.edgeCount[i];
                    int base = i << 3;
                    for (int k = 0; k < deg; k++) {
                        int edge = this.edgeData[base + k];
                        if ((edge & 1) == 0) {
                            continue;
                        }
                        int vIdx = edge >>> 4;
                        if (this.inCluster[vIdx]) {
                            continue;
                        }
                        int offer = this.oldPower[vIdx] - 1;
                        if (offer > best) {
                            best = offer;
                            int d = (edge >>> 2) & 3;
                            bestMask = 1 << ((d + 2) & 3);
                        } else if (offer == best && offer > 0) {
                            int d = (edge >>> 2) & 3;
                            bestMask |= 1 << ((d + 2) & 3);
                        }
                    }
                }
                if (bestMask != 0) {
                    this.flowIn[i] = (byte) bestMask;
                }
                this.newPower[i] = best;
                if (best > 1) {
                    this.pushBucket(best, i);
                }
            }
        }

        private boolean drainWavefront(RedstoneWireBlock wireBlock, Level level) {
            while (this.highestBucket >= 2) {
                int p = this.highestBucket;
                int cursor = this.bucketCursors[p];
                if (cursor >= this.bucketSizes[p]) {
                    this.bucketCursors[p] = 0;
                    this.bucketSizes[p] = 0;
                    this.highestBucket--;
                    continue;
                }
                if (!this.inDrainLoop && this.positions.size() >= MAX_CLUSTER_NODES) {
                    return false;
                }
                int uIdx = this.buckets[p][cursor];
                this.bucketCursors[p] = cursor + 1;
                if (this.newPower[uIdx] != p) {
                    continue;
                }
                this.discoverNode(wireBlock, level, uIdx);
                int nextPower = p - 1;
                int deg = this.edgeCount[uIdx];
                for (int k = 0; k < deg; k++) {
                    int edge = this.edgeData[(uIdx << 3) + k];
                    if ((edge & 2) == 0) {
                        continue;
                    }
                    int vIdx = edge >>> 4;
                    int d = (edge >>> 2) & 3;
                    this.tryRelaxTarget(wireBlock, level, vIdx, nextPower, 1 << d);
                }
            }
            return true;
        }

        private void tryRelaxTarget(RedstoneWireBlock wireBlock, Level level, int vIdx, int nextPower, int flowBit) {
            int cur = this.inCluster[vIdx] ? this.newPower[vIdx] : this.oldPower[vIdx];
            int vExt = this.getExternalPower(wireBlock, level, vIdx);
            int candidate = Math.max(nextPower, vExt);
            if (candidate > cur) {
                this.inCluster[vIdx] = true;
                this.newPower[vIdx] = candidate;
                if (nextPower >= vExt) {
                    this.flowIn[vIdx] = (byte) flowBit;
                }
                this.pushBucket(candidate, vIdx);
            } else if (candidate == cur && candidate > 0 && nextPower == candidate) {
                this.flowIn[vIdx] |= (byte) flowBit;
            }
        }

        private void queueNotify(
                long packedPos,
                byte dirOrdinal,
                RedstoneWireBlock wireBlock,
                @Nullable Orientation baseOrientation
        ) {
            int idx = this.nodeIndex.get(packedPos);
            if (idx >= 0 && this.inCluster[idx]) {
                return;
            }
            if (!this.notifySeen.add(packedPos)) {
                return;
            }
            if (this.notifyTail == this.notifyQueue.length) {
                if (this.notifyHead > 0) {
                    int rem = this.notifyTail - this.notifyHead;
                    System.arraycopy(this.notifyQueue, this.notifyHead, this.notifyQueue, 0, rem);
                    System.arraycopy(this.notifyQueueDir, this.notifyHead, this.notifyQueueDir, 0, rem);
                    System.arraycopy(this.notifyQueueWire, this.notifyHead, this.notifyQueueWire, 0, rem);
                    System.arraycopy(this.notifyQueueBaseOrient, this.notifyHead, this.notifyQueueBaseOrient, 0, rem);
                    Arrays.fill(this.notifyQueueWire, rem, this.notifyTail, null);
                    Arrays.fill(this.notifyQueueBaseOrient, rem, this.notifyTail, null);
                    this.notifyHead = 0;
                    this.notifyTail = rem;
                } else {
                    int next = this.notifyQueue.length << 1;
                    this.notifyQueue = Arrays.copyOf(this.notifyQueue, next);
                    this.notifyQueueDir = Arrays.copyOf(this.notifyQueueDir, next);
                    this.notifyQueueWire = Arrays.copyOf(this.notifyQueueWire, next);
                    this.notifyQueueBaseOrient = Arrays.copyOf(this.notifyQueueBaseOrient, next);
                }
            }
            int slot = this.notifyTail++;
            this.notifyQueue[slot] = packedPos;
            this.notifyQueueDir[slot] = dirOrdinal;
            this.notifyQueueWire[slot] = wireBlock;
            this.notifyQueueBaseOrient[slot] = baseOrientation;
        }

        private void emitNode1And2HopNotifications(
                RedstoneWireBlock wireBlock,
                Level level,
                long uPacked,
                int ux,
                int uy,
                int uz,
                int fwdDir,
                @Nullable Orientation baseOrientation
        ) {
            Direction[] order = FLOW_NOTIFY_ORDER[fwdDir & 3];
            this.emitCenterNotifications(wireBlock, level, uPacked, ux, uy, uz, order, false, 0L, baseOrientation);
            for (int d = 0; d < 6; d++) {
                Direction dir = order[d];
                int nx = ux + dir.getStepX();
                int ny = uy + dir.getStepY();
                int nz = uz + dir.getStepZ();
                long nPacked = BlockPos.asLong(nx, ny, nz);
                this.emitCenterNotifications(wireBlock, level, nPacked, nx, ny, nz, order, true, uPacked, baseOrientation);
            }
        }

        private void emitCenterNotifications(
                RedstoneWireBlock wireBlock,
                Level level,
                long centerPacked,
                int cx,
                int cy,
                int cz,
                Direction[] order,
                boolean hasSkipTarget,
                long skipTargetPacked,
                @Nullable Orientation baseOrientation
        ) {
            if (!this.notifyCenters.add(centerPacked)) {
                return;
            }
            if (this.scratchSides.size() != 6) {
                this.scratchSides.addAll(ALL_DIRECTION_SET);
            }
            BlockPos centerPos = BlockPos.of(centerPacked);
            BlockState centerState = level.getBlockState(centerPos);
            BlockEvent.NeighborNotifyEvent event = EventHooks.onNeighborNotify(level, centerPos, centerState, this.scratchSides, false);
            if (event.isCanceled()) {
                return;
            }
            EnumSet<Direction> allowed = event.getNotifiedSides();
            boolean allSix = allowed.size() == 6;
            for (int d = 0; d < 6; d++) {
                Direction dir = order[d];
                if (!allSix && !allowed.contains(dir)) {
                    continue;
                }
                long nPacked = BlockPos.asLong(cx + dir.getStepX(), cy + dir.getStepY(), cz + dir.getStepZ());
                if (hasSkipTarget && nPacked == skipTargetPacked) {
                    continue;
                }
                this.queueNotify(nPacked, (byte) dir.ordinal(), wireBlock, baseOrientation);
            }
        }

        private void commitAndNotify(
                RedstoneWireBlock wireBlock,
                Level level,
                @Nullable Orientation baseOrientation,
                boolean skipShapeUpdates,
                long skipShapeRootPos,
                boolean hasRemovedRoot,
                long removedRootPacked
        ) {
            int count = this.positions.size();
            Arrays.fill(this.peakCounts, 0);
            int changedTotal = 0;

            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i]) {
                    continue;
                }
                int prev = this.oldPower[i];
                int next = this.newPower[i];
                if (prev == next) {
                    continue;
                }
                int peak = Math.max(prev, next);
                this.peakCounts[peak]++;
                changedTotal++;
            }

            if (changedTotal == 0 && !hasRemovedRoot) {
                return;
            }

            int offset = 0;
            for (int p = 15; p >= 0; p--) {
                this.peakOffsets[p] = offset;
                offset += this.peakCounts[p];
            }

            if (this.sortedChanged.length < changedTotal) {
                this.sortedChanged = new int[Math.max(changedTotal, this.oldPower.length)];
            }

            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i]) {
                    continue;
                }
                int prev = this.oldPower[i];
                int next = this.newPower[i];
                if (prev == next) {
                    continue;
                }
                int peak = Math.max(prev, next);
                this.sortedChanged[this.peakOffsets[peak]++] = i;
            }

            boolean anyCommitted = false;
            for (int k = 0; k < changedTotal; k++) {
                int i = this.sortedChanged[k];
                int next = this.newPower[i];
                long packed = this.positions.getLong(i);
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                this.mutPos.set(x, y, z);
                BlockState cur = level.getBlockState(this.mutPos);
                if (!cur.is(wireBlock)) {
                    continue;
                }
                this.mutNbr.set(x, y - 1, z);
                boolean belowConductor = level.getBlockState(this.mutNbr).isRedstoneConductor(level, this.mutNbr);
                if (!belowConductor && !cur.canSurvive(level, this.mutPos)) {
                    BlockPos immPos = BlockPos.of(packed);
                    Block.dropResources(cur, level, immPos);
                    level.removeBlock(immPos, false);
                    this.changed[i] = true;
                    this.removed[i] = true;
                    anyCommitted = true;
                } else {
                    BlockPos immPos = BlockPos.of(packed);
                    if (level.setBlock(immPos, cur.setValue(RedstoneWireBlock.POWER, next), UPDATE_KNOWN_AND_CLIENTS)) {
                        this.changed[i] = true;
                        anyCommitted = true;
                    }
                }
            }

            if (!anyCommitted && !hasRemovedRoot) {
                return;
            }

            for (int k = 0; k < changedTotal; k++) {
                int i = this.sortedChanged[k];
                if (!this.changed[i] || this.removed[i]) {
                    continue;
                }
                long packed = this.positions.getLong(i);
                if (skipShapeUpdates && packed == skipShapeRootPos) {
                    continue;
                }
                BlockPos wirePos = BlockPos.of(packed);
                BlockState newWireState = level.getBlockState(wirePos);
                if (!newWireState.is(wireBlock)) {
                    continue;
                }
                int wx = wirePos.getX();
                int wy = wirePos.getY();
                int wz = wirePos.getZ();
                for (int d = 0; d < 6; d++) {
                    Direction dir = SHAPE_UPDATE_ORDER[d];
                    this.mutNbr.set(wx + dir.getStepX(), wy + dir.getStepY(), wz + dir.getStepZ());
                    BlockState nbrState = level.getBlockState(this.mutNbr);
                    if (!nbrState.isAir() && !nbrState.is(wireBlock)) {
                        level.neighborShapeChanged(dir.getOpposite(), this.mutNbr, wirePos, newWireState, Block.UPDATE_CLIENTS, 511);
                    }
                }
            }

            if (hasRemovedRoot) {
                int rx = BlockPos.getX(removedRootPacked);
                int ry = BlockPos.getY(removedRootPacked);
                int rz = BlockPos.getZ(removedRootPacked);
                this.emitNode1And2HopNotifications(wireBlock, level, removedRootPacked, rx, ry, rz, 2, baseOrientation);
            }

            for (int k = 0; k < changedTotal; k++) {
                int i = this.sortedChanged[k];
                if (!this.changed[i]) {
                    continue;
                }
                long uPacked = this.positions.getLong(i);
                int ux = BlockPos.getX(uPacked);
                int uy = BlockPos.getY(uPacked);
                int uz = BlockPos.getZ(uPacked);
                int fwd = FLOW_DIR_FROM_MASK[this.flowIn[i] & 15];
                this.emitNode1And2HopNotifications(wireBlock, level, uPacked, ux, uy, uz, fwd, baseOrientation);
            }
        }
    }

    private PapiRedstoneWavefront() {}

    public static boolean updatePowerStrength(
            RedstoneWireBlock wireBlock,
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable Orientation orientation,
            boolean skipShapeUpdates
    ) {
        if (level.isClientSide() || !(level instanceof PapiServerLevelRedstone redstoneLevel)) {
            return false;
        }
        return redstoneLevel.papi$getRedstoneSolver()
                .enqueueOrRun(wireBlock, level, pos, state, orientation, skipShapeUpdates);
    }
}
