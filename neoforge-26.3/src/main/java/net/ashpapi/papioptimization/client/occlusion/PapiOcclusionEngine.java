package net.ashpapi.papioptimization.client.occlusion;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.ashpapi.papioptimization.access.PapiOcclusionSection;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import net.ashpapi.papioptimization.mixin.client.FrustumInvoker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PapiOcclusionEngine {

    public static final byte TARGET_ENTITY = 1;
    public static final byte TARGET_BLOCK_ENTITY = 2;
    public static final byte TARGET_PARTICLE = 3;

    private static final int HYST_TABLE_SIZE = 32768;
    private static final int HYST_TABLE_MASK = HYST_TABLE_SIZE - 1;
    private static final int HYST_HOLD_FRAMES = 2;
    private static final long FALLBACK_FRAME_STEP_NANOS = 8_000_000L;
    private static final double PARTICLE_MIN_CULL_DIST_SQR = 12.25;
    private static final double MAX_CULL_BOX_SPAN = 32.0;
    private static final int MAX_RAY_STEPS = 384;

    private static final int SECTION_CACHE_SIZE = 4096;
    private static final int SECTION_CACHE_MASK = SECTION_CACHE_SIZE - 1;
    private static final long FALLBACK_SECTION_TTL_TICKS = 40L;
    private static final int MAX_BATCH_REQUESTS = 16384;
    private static final int VIS_TABLE_SIZE = 32768;
    private static final int VIS_TABLE_MASK = VIS_TABLE_SIZE - 1;
    private static final int VIS_OCCUPIED_WORDS = VIS_TABLE_SIZE >>> 6;
    private static final int MAX_CORNER_BLOCKERS = 12;
    private static final long FALLBACK_SUBMIT_INTERVAL_NANOS = 8_000_000L;
    private static final long SINGLE_VOXEL_MAX_AGE_TICKS = 6L;
    private static final long MULTI_VOXEL_MAX_AGE_TICKS = 4L;
    private static final long SINGLE_VOXEL_MAX_AGE_NANOS = 300_000_000L;
    private static final long MULTI_VOXEL_MAX_AGE_NANOS = 200_000_000L;
    private static final double MULTI_VOXEL_MAX_SHIFT_SQR = 0.4 * 0.4;

    private static final int PARTICLE_MEMO_SIZE = 4096;
    private static final int PARTICLE_MEMO_MASK = PARTICLE_MEMO_SIZE - 1;
    private static final int PARTICLE_LIGHT_SIZE = 4096;
    private static final int PARTICLE_LIGHT_MASK = PARTICLE_LIGHT_SIZE - 1;

    private static final int QBC_SIZE = 8192;
    private static final int QBC_MASK = QBC_SIZE - 1;
    private static final int QBC_EXTRA_POOL_SIZE = 8192;

    private static final byte KIND_EMPTY = 0;
    private static final byte KIND_FULL = 1;
    private static final byte KIND_SPARSE = 2;

    private static final byte STATE_VISIBLE = 0;
    private static final byte STATE_OCCLUDED_SINGLE_VOXEL = 1;
    private static final byte STATE_OCCLUDED_MULTI_VOXEL = 2;

    private static final SectionEntry TRANSIENT_EMPTY_ENTRY = new SectionEntry();

    private static final class SectionEntry {
        long secKey = Long.MIN_VALUE;
        long validatedSeq = -1L;
        int observedEpoch = 0;
        long expireTick = Long.MIN_VALUE;
        WeakReference<LevelChunkSection> sectionRef = new WeakReference<>(null);
        byte kind = KIND_EMPTY;
        short nonEmptyYLayers = 0;
        final long[] words = new long[64];
    }

    private static final class RequestBatch {
        int generation = -1;
        int frameId = -1;
        WeakReference<ClientLevel> levelRef = new WeakReference<>(null);
        long gameTime;
        double camX;
        double camY;
        double camZ;
        boolean camInOpaque;
        boolean solidLeavesMode;
        int count;
        final byte[] kinds = new byte[MAX_BATCH_REQUESTS];
        final long[] keys = new long[MAX_BATCH_REQUESTS];
        final double[] minX = new double[MAX_BATCH_REQUESTS];
        final double[] minY = new double[MAX_BATCH_REQUESTS];
        final double[] minZ = new double[MAX_BATCH_REQUESTS];
        final double[] maxX = new double[MAX_BATCH_REQUESTS];
        final double[] maxY = new double[MAX_BATCH_REQUESTS];
        final double[] maxZ = new double[MAX_BATCH_REQUESTS];
        final int[] seenStamps = new int[VIS_TABLE_SIZE];
        final byte[] seenKinds = new byte[VIS_TABLE_SIZE];
        final long[] seenKeys = new long[VIS_TABLE_SIZE];
        int seenStamp = 1;

        void reset(
                int gen,
                int fid,
                @Nullable ClientLevel level,
                long time,
                double cx,
                double cy,
                double cz,
                boolean inOpaque,
                boolean leavesSolid
        ) {
            this.generation = gen;
            this.frameId = fid;
            this.levelRef = new WeakReference<>(level);
            this.gameTime = time;
            this.camX = cx;
            this.camY = cy;
            this.camZ = cz;
            this.camInOpaque = inOpaque;
            this.solidLeavesMode = leavesSolid;
            this.count = 0;
            this.seenStamp++;
            if (this.seenStamp == 0) {
                Arrays.fill(this.seenStamps, 0);
                this.seenStamp = 1;
            }
        }

        void add(byte kind, long key, double bxMin, double byMin, double bzMin, double bxMax, double byMax, double bzMax) {
            int idx = this.count;
            if (idx >= MAX_BATCH_REQUESTS) {
                return;
            }
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            int stamp = this.seenStamp;
            for (int probe = 0; probe < 8; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.seenStamps[s] != stamp) {
                    this.seenStamps[s] = stamp;
                    this.seenKinds[s] = kind;
                    this.seenKeys[s] = key;
                    break;
                }
                if (this.seenKinds[s] == kind && this.seenKeys[s] == key) {
                    return;
                }
            }
            this.kinds[idx] = kind;
            this.keys[idx] = key;
            this.minX[idx] = bxMin;
            this.minY[idx] = byMin;
            this.minZ[idx] = bzMin;
            this.maxX[idx] = bxMax;
            this.maxY[idx] = byMax;
            this.maxZ[idx] = bzMax;
            this.count = idx + 1;
        }
    }

    private static final class VisibilitySnapshot {
        int generation = -1;
        final long[] occupiedWords = new long[VIS_OCCUPIED_WORDS];
        final int[] entryGens = new int[VIS_TABLE_SIZE];
        final long[] entrySeqs = new long[VIS_TABLE_SIZE];
        final long[] entryTicks = new long[VIS_TABLE_SIZE];
        final long[] entryNanos = new long[VIS_TABLE_SIZE];
        final byte[] kinds = new byte[VIS_TABLE_SIZE];
        final long[] keys = new long[VIS_TABLE_SIZE];
        final byte[] states = new byte[VIS_TABLE_SIZE];
        final long[] blockerPos = new long[VIS_TABLE_SIZE];
        final byte[] cornerBlockerCount = new byte[VIS_TABLE_SIZE];
        final long[] cornerBlockers = new long[VIS_TABLE_SIZE * MAX_CORNER_BLOCKERS];
        final float[] snapCamX = new float[VIS_TABLE_SIZE];
        final float[] snapCamY = new float[VIS_TABLE_SIZE];
        final float[] snapCamZ = new float[VIS_TABLE_SIZE];
        final float[] centerX = new float[VIS_TABLE_SIZE];
        final float[] centerY = new float[VIS_TABLE_SIZE];
        final float[] centerZ = new float[VIS_TABLE_SIZE];
        final float[] maxHalfSpan = new float[VIS_TABLE_SIZE];

        void prepareForGeneration(int gen) {
            if (this.generation != gen) {
                this.generation = gen;
                Arrays.fill(this.occupiedWords, 0L);
            }
        }

        void invalidate() {
            this.generation = -1;
            Arrays.fill(this.occupiedWords, 0L);
        }

        private boolean isSlotAlive(int s, int gen, long currentTick, long nowNanos) {
            if (this.entryGens[s] != gen) {
                return false;
            }
            long tickAge = currentTick - this.entryTicks[s];
            if (tickAge < 0L || tickAge > SINGLE_VOXEL_MAX_AGE_TICKS) {
                return false;
            }
            long nanoAge = nowNanos - this.entryNanos[s];
            return nanoAge <= SINGLE_VOXEL_MAX_AGE_NANOS;
        }

        void put(
                int gen,
                long seq,
                long gameTime,
                long nowNanos,
                byte kind,
                long key,
                byte state,
                long blocker,
                float scx,
                float scy,
                float scz,
                float cx,
                float cy,
                float cz,
                float halfSpan
        ) {
            put(gen, seq, gameTime, nowNanos, kind, key, state, blocker, null, 0, scx, scy, scz, cx, cy, cz, halfSpan);
        }

        void put(
                int gen,
                long seq,
                long gameTime,
                long nowNanos,
                byte kind,
                long key,
                byte state,
                long blocker,
                long @Nullable [] extraBlockers,
                int extraCount,
                float scx,
                float scy,
                float scz,
                float cx,
                float cy,
                float cz,
                float halfSpan
        ) {
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            int freeSlot = -1;
            int oldestSlot = slot;
            long oldestSeq = Long.MAX_VALUE;
            for (int probe = 0; probe < 12; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.entryGens[s] == gen && this.kinds[s] == kind && this.keys[s] == key) {
                    if (seq >= this.entrySeqs[s]) {
                        writeSlot(s, gen, seq, gameTime, nowNanos, kind, key, state, blocker, extraBlockers, extraCount, scx, scy, scz, cx, cy, cz, halfSpan);
                    }
                    return;
                }
                if (!isSlotAlive(s, gen, gameTime, nowNanos)) {
                    if (freeSlot < 0) {
                        freeSlot = s;
                    }
                } else if (this.entrySeqs[s] < oldestSeq) {
                    oldestSeq = this.entrySeqs[s];
                    oldestSlot = s;
                }
            }
            int targetSlot = freeSlot >= 0 ? freeSlot : oldestSlot;
            writeSlot(targetSlot, gen, seq, gameTime, nowNanos, kind, key, state, blocker, extraBlockers, extraCount, scx, scy, scz, cx, cy, cz, halfSpan);
        }

        private void writeSlot(
                int s,
                int gen,
                long seq,
                long gameTime,
                long nowNanos,
                byte kind,
                long key,
                byte state,
                long blocker,
                long @Nullable [] extraBlockers,
                int extraCount,
                float scx,
                float scy,
                float scz,
                float cx,
                float cy,
                float cz,
                float halfSpan
        ) {
            this.occupiedWords[s >>> 6] |= (1L << (s & 63));
            this.entryGens[s] = gen;
            this.entrySeqs[s] = seq;
            this.entryTicks[s] = gameTime;
            this.entryNanos[s] = nowNanos;
            this.kinds[s] = kind;
            this.keys[s] = key;
            this.states[s] = state;
            this.blockerPos[s] = blocker;
            int count = (extraBlockers != null && extraCount > 0) ? Math.min(MAX_CORNER_BLOCKERS, extraCount) : 0;
            this.cornerBlockerCount[s] = (byte) count;
            if (count > 0) {
                int base = s * MAX_CORNER_BLOCKERS;
                for (int k = 0; k < count; k++) {
                    this.cornerBlockers[base + k] = extraBlockers[k];
                }
            }
            this.snapCamX[s] = scx;
            this.snapCamY[s] = scy;
            this.snapCamZ[s] = scz;
            this.centerX[s] = cx;
            this.centerY[s] = cy;
            this.centerZ[s] = cz;
            this.maxHalfSpan[s] = halfSpan;
        }

        int findSlot(int gen, long currentTick, long nowNanos, byte kind, long key) {
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            for (int probe = 0; probe < 12; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.entryGens[s] == gen && this.kinds[s] == kind && this.keys[s] == key) {
                    return isSlotAlive(s, gen, currentTick, nowNanos) ? s : -1;
                }
            }
            return -1;
        }

        void mergeFrom(VisibilitySnapshot src, int gen, long currentTick, long nowNanos, long[] scratch) {
            if (src.generation != gen) {
                return;
            }
            for (int w = 0; w < VIS_OCCUPIED_WORDS; w++) {
                long word = src.occupiedWords[w];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    word &= word - 1L;
                    int i = (w << 6) | bit;
                    if (src.isSlotAlive(i, gen, currentTick, nowNanos)) {
                        byte kind = src.kinds[i];
                        long key = src.keys[i];
                        long srcSeq = src.entrySeqs[i];
                        int existing = findSlot(gen, currentTick, nowNanos, kind, key);
                        if (existing < 0 || this.entrySeqs[existing] < srcSeq) {
                            int extra = src.cornerBlockerCount[i] & 0xFF;
                            long[] buf = null;
                            if (extra > 0) {
                                buf = scratch;
                                int base = i * MAX_CORNER_BLOCKERS;
                                for (int k = 0; k < extra; k++) {
                                    buf[k] = src.cornerBlockers[base + k];
                                }
                            }
                            put(
                                    gen,
                                    srcSeq,
                                    src.entryTicks[i],
                                    src.entryNanos[i],
                                    kind,
                                    key,
                                    src.states[i],
                                    src.blockerPos[i],
                                    buf,
                                    extra,
                                    src.snapCamX[i],
                                    src.snapCamY[i],
                                    src.snapCamZ[i],
                                    src.centerX[i],
                                    src.centerY[i],
                                    src.centerZ[i],
                                    src.maxHalfSpan[i]
                            );
                        }
                    } else {
                        src.occupiedWords[w] &= ~(1L << bit);
                    }
                }
            }
        }
    }

    private static final long[] RENDER_MERGE_SCRATCH = new long[MAX_CORNER_BLOCKERS];

    private static final AtomicInteger GENERATION = new AtomicInteger(1);
    private static final AtomicInteger WORLD_BLOCK_EPOCH = new AtomicInteger(1);
    private static final AtomicInteger WORLD_LIGHT_EPOCH = new AtomicInteger(1);
    private static final AtomicInteger NEXT_SECTION_EPOCH = new AtomicInteger(1);
    private static final @Nullable VarHandle PALETTED_DATA_HANDLE = resolvePalettedDataHandle();
    private static final AtomicInteger EXTERNAL_SHADOW_PASS_DEPTH = new AtomicInteger(0);

    private static final AtomicReference<@Nullable RequestBatch> PENDING_REQ_SLOT = new AtomicReference<>(null);
    private static final AtomicReference<@Nullable RequestBatch> FREE_REQ_SLOT = new AtomicReference<>(new RequestBatch());
    private static final AtomicReference<@Nullable VisibilitySnapshot> READY_VIS_SLOT = new AtomicReference<>(null);
    private static final AtomicReference<@Nullable VisibilitySnapshot> FREE_VIS_SLOT = new AtomicReference<>(new VisibilitySnapshot());

    private static RequestBatch renderReq = new RequestBatch();
    private static VisibilitySnapshot renderVis = new VisibilitySnapshot();
    private static WeakReference<ClientLevel> renderLevelRef = new WeakReference<>(null);
    private static long lastSubmitNanos = 0L;
    private static final BlockPos.MutableBlockPos RENDER_WORK_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos LIGHT_WORK_POS = new BlockPos.MutableBlockPos();

    private static int capturedEntityId = Integer.MIN_VALUE;
    private static @Nullable AABB capturedEntityBox = null;

    private static int pendingNameTagEntityId = Integer.MIN_VALUE;
    private static double pendingNameTagDistSq = 0.0;
    private static double pendingNameTagMaxDist = 64.0;
    private static double pendingBelowNameMaxDist = 10.0;

    @SuppressWarnings("unchecked")
    private static final ArrayList<EntityRenderState>[] NAMETAG_STATE_POOLS = new ArrayList[]{
            new ArrayList<>(32),
            new ArrayList<>(32)
    };
    private static int nameTagPoolFlip = 0;
    private static int nameTagPoolCursor = 0;

    private static boolean inFrameExtraction = false;
    private static boolean mainFrameActive = false;
    private static int frameExtractionDepth = 0;
    private static long frameShadowMask = 0L;
    private static boolean frameHitboxesEnabled = false;
    private static boolean frameCamInOpaque = false;
    private static boolean leavesOpaque = false;
    private static int frameCamBlkX = 0;
    private static int frameCamBlkY = 0;
    private static int frameCamBlkZ = 0;
    private static long frameNowNanos = 0L;
    private static @Nullable Frustum activeFrustum = null;
    private static @Nullable Frustum lastMainFrustum = null;
    private static final Frustum[] PASS_FRUSTUM_STACK = new Frustum[64];
    private static final Vec3[] PASS_CAMERA_POS_STACK = new Vec3[64];

    private static volatile @Nullable Class<?> lastNormalFrustumClass = null;
    private static volatile @Nullable Class<?> lastShadowFrustumClass = null;

    private static final int[] PARTICLE_MEMO_STAMPS = new int[PARTICLE_MEMO_SIZE];
    private static final long[] PARTICLE_MEMO_KEYS = new long[PARTICLE_MEMO_SIZE];
    private static final byte[] PARTICLE_MEMO_VALS = new byte[PARTICLE_MEMO_SIZE];
    private static int particleMemoStamp = 1;

    private static final int[] PARTICLE_LIGHT_STAMPS = new int[PARTICLE_LIGHT_SIZE];
    private static final long[] PARTICLE_LIGHT_KEYS = new long[PARTICLE_LIGHT_SIZE];
    private static final int[] PARTICLE_LIGHT_VALS = new int[PARTICLE_LIGHT_SIZE];
    private static int particleLightStamp = 1;
    private static WeakReference<ClientLevel> lastLightLevelRef = new WeakReference<>(null);
    private static int lastLightBlockEpoch = -1;
    private static int lastLightWorldEpoch = -1;
    private static long lastLightGameTime = Long.MIN_VALUE;

    private static final int[] HYST_STAMPS = new int[HYST_TABLE_SIZE];
    private static final byte[] HYST_KINDS = new byte[HYST_TABLE_SIZE];
    private static final long[] HYST_KEYS = new long[HYST_TABLE_SIZE];
    private static final byte[] HYST_STREAKS = new byte[HYST_TABLE_SIZE];
    private static final int[] HYST_LAST_FRAME = new int[HYST_TABLE_SIZE];
    private static int hystTableStamp = 1;
    private static int hystFrame = 1;
    private static long lastHystFrameNanos = 0L;

    private static long frameCulledEntities = 0L;
    private static long frameCulledBlockEntities = 0L;
    private static long frameCulledParticles = 0L;
    private static volatile long lastFrameCulledEntities = 0L;
    private static volatile long lastFrameCulledBlockEntities = 0L;
    private static volatile long lastFrameCulledParticles = 0L;
    private static volatile long totalCulledEntities = 0L;
    private static volatile long totalCulledBlockEntities = 0L;
    private static volatile long totalCulledParticles = 0L;

    private static long tickSkippedEntitiesAcc = 0L;
    private static long tickProcessedEntitiesAcc = 0L;
    private static long lastClientEntityTickGameTime = Long.MIN_VALUE;
    private static volatile long lastTickSkippedEntities = 0L;
    private static volatile long lastTickProcessedEntities = 0L;
    private static volatile long lastWorkerPassMicros = 0L;

    private static volatile @Nullable Thread workerThread;
    private static final Object WORKER_INIT_LOCK = new Object();

    private static final @Nullable MethodHandle IRIS_API_SHADOW_CHECK = resolveIrisApiShadowCheck();
    private static final @Nullable MethodHandle IRIS_RENDERER_ACTIVE_GETTER = resolveIrisShadowActiveGetter();
    private static final @Nullable MethodHandle IRIS_SHADOW_STATE_CHECK = resolveIrisShadowStateCheck();

    private PapiOcclusionEngine() {}

    public static void resetCapturedEntityBox() {
        if (RenderSystem.isOnRenderThread()) {
            capturedEntityId = Integer.MIN_VALUE;
            capturedEntityBox = null;
        }
    }

    public static void captureEntityCullingBox(int entityId, @Nullable AABB box) {
        if (RenderSystem.isOnRenderThread()) {
            capturedEntityId = entityId;
            capturedEntityBox = box;
        }
    }

    public static @Nullable AABB consumeCapturedEntityBox(int entityId) {
        if (RenderSystem.isOnRenderThread()) {
            AABB box = (capturedEntityId == entityId) ? capturedEntityBox : null;
            capturedEntityId = Integer.MIN_VALUE;
            capturedEntityBox = null;
            return box;
        }
        return null;
    }

    public static void resetPendingNameTagEntity() {
        if (RenderSystem.isOnRenderThread()) {
            pendingNameTagEntityId = Integer.MIN_VALUE;
        }
    }

    public static void markPendingNameTagEntity(int entityId, double distSq, double maxNameDist, double maxBelowDist) {
        if (RenderSystem.isOnRenderThread()) {
            pendingNameTagEntityId = entityId;
            pendingNameTagDistSq = distSq;
            pendingNameTagMaxDist = maxNameDist;
            pendingBelowNameMaxDist = maxBelowDist;
        }
    }

    public static boolean consumePendingNameTagEntity(int entityId) {
        if (RenderSystem.isOnRenderThread() && pendingNameTagEntityId == entityId) {
            pendingNameTagEntityId = Integer.MIN_VALUE;
            return true;
        }
        return false;
    }

    public static double getPendingNameTagDistSq() {
        return pendingNameTagDistSq;
    }

    public static double getPendingNameTagMaxDist() {
        return pendingNameTagMaxDist;
    }

    public static double getPendingBelowNameMaxDist() {
        return pendingBelowNameMaxDist;
    }

    public static EntityRenderState acquirePooledNameTagState() {
        if (!RenderSystem.isOnRenderThread()) {
            EntityRenderState fallback = new EntityRenderState();
            fallback.entityType = EntityTypes.INTERACTION;
            fallback.isInvisible = true;
            return fallback;
        }
        ArrayList<EntityRenderState> pool = NAMETAG_STATE_POOLS[nameTagPoolFlip];
        int idx = nameTagPoolCursor;
        EntityRenderState state;
        if (idx < pool.size()) {
            state = pool.get(idx);
            state.resetRenderData();
            if (!state.shadowPieces.isEmpty()) {
                state.shadowPieces.clear();
            }
        } else {
            state = new EntityRenderState();
            if (idx < 2048) {
                pool.add(state);
            }
        }
        nameTagPoolCursor = idx + 1;
        state.entityType = EntityTypes.INTERACTION;
        state.x = 0.0;
        state.y = 0.0;
        state.z = 0.0;
        state.ageInTicks = 0.0F;
        state.boundingBoxWidth = 0.0F;
        state.boundingBoxHeight = 0.0F;
        state.eyeHeight = 0.0F;
        state.distanceToCameraSq = 0.0;
        state.isInvisible = true;
        state.isDiscrete = false;
        state.displayFireAnimation = false;
        state.lightCoords = 15728880;
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        state.passengerOffset = null;
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        state.leashStates = null;
        state.shadowRadius = 0.0F;
        state.partialTick = 0.0F;
        return state;
    }

    private static @Nullable VarHandle resolvePalettedDataHandle() {
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            return MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup())
                    .findVarHandle(PalettedContainer.class, "data", dataClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable MethodHandle resolveIrisApiShadowCheck() {
        try {
            ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, cl);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getInstance = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass));
            Object api = getInstance.invoke();
            if (api == null) {
                return null;
            }
            MethodHandle isShadow = lookup.findVirtual(apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            return isShadow.bindTo(api);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable MethodHandle resolveIrisShadowActiveGetter() {
        String[] candidates = {
                "net.irisshaders.iris.shadows.ShadowRenderer",
                "net.coderbot.iris.pipeline.ShadowRenderer"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
        for (String clsName : candidates) {
            try {
                Class<?> cls = Class.forName(clsName, false, cl);
                return lookup.findStaticGetter(cls, "ACTIVE", boolean.class);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static @Nullable MethodHandle resolveIrisShadowStateCheck() {
        String[] candidates = {
                "net.irisshaders.iris.shadows.ShadowRenderingState",
                "net.coderbot.iris.shadows.ShadowRenderingState"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
        for (String clsName : candidates) {
            try {
                Class<?> cls = Class.forName(clsName, false, cl);
                return lookup.findStatic(cls, "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static void enterShadowPass() {
        EXTERNAL_SHADOW_PASS_DEPTH.incrementAndGet();
    }

    public static void exitShadowPass() {
        EXTERNAL_SHADOW_PASS_DEPTH.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public static void onClientBlockOrChunkChange() {
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void onClientLightChange() {
        WORLD_LIGHT_EPOCH.incrementAndGet();
    }

    public static int nextSectionEpoch() {
        int ep = NEXT_SECTION_EPOCH.incrementAndGet();
        if (ep == 0) {
            ep = NEXT_SECTION_EPOCH.incrementAndGet();
        }
        return ep;
    }

    public static void invalidateBlockSectionAt(@Nullable ClientLevel level, @Nullable BlockPos pos) {
        if (level != null && pos != null) {
            bumpLoadedSectionEpoch(level, pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
        WORLD_BLOCK_EPOCH.incrementAndGet();
        WORLD_LIGHT_EPOCH.incrementAndGet();
    }

    public static void invalidateSectionCoords(@Nullable ClientLevel level, int sx, int sy, int sz) {
        if (level != null) {
            bumpLoadedSectionEpoch(level, sx, sy, sz);
        }
        WORLD_LIGHT_EPOCH.incrementAndGet();
    }

    private static void bumpLoadedSectionEpoch(ClientLevel level, int sx, int sy, int sz) {
        try {
            LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
            if (chunk != null) {
                int idx = chunk.getSectionIndexFromSectionY(sy);
                LevelChunkSection[] sections = chunk.getSections();
                if (sections != null && idx >= 0 && idx < sections.length) {
                    LevelChunkSection sec = sections[idx];
                    if (sec instanceof PapiOcclusionSection occSec) {
                        occSec.papi$bumpOcclusionEpoch();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void invalidateLoadedChunkSections(@Nullable LevelChunk chunk) {
        if (chunk != null) {
            LevelChunkSection[] sections = chunk.getSections();
            if (sections != null) {
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec instanceof PapiOcclusionSection occSec) {
                        occSec.papi$bumpOcclusionEpoch();
                    }
                }
            }
        }
        WORLD_BLOCK_EPOCH.incrementAndGet();
        WORLD_LIGHT_EPOCH.incrementAndGet();
    }

    private static boolean checkShadowPassLive() {
        if (EXTERNAL_SHADOW_PASS_DEPTH.get() > 0) {
            return true;
        }
        MethodHandle apiMh = IRIS_API_SHADOW_CHECK;
        if (apiMh != null) {
            try {
                if ((boolean) apiMh.invokeExact()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        MethodHandle activeMh = IRIS_RENDERER_ACTIVE_GETTER;
        if (activeMh != null) {
            try {
                if ((boolean) activeMh.invokeExact()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        MethodHandle stateMh = IRIS_SHADOW_STATE_CHECK;
        if (stateMh != null) {
            try {
                return (boolean) stateMh.invokeExact();
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean isShadowPass() {
        if (EXTERNAL_SHADOW_PASS_DEPTH.get() > 0) {
            return true;
        }
        if (RenderSystem.isOnRenderThread() && frameExtractionDepth > 0 && frameShadowMask != 0L) {
            return true;
        }
        return checkShadowPassLive();
    }

    public static boolean isShadowFrustum(@Nullable Frustum frustum) {
        if (frustum == null) {
            return false;
        }
        Class<?> cls = frustum.getClass();
        if (cls == Frustum.class || cls == lastNormalFrustumClass) {
            return false;
        }
        if (cls == lastShadowFrustumClass) {
            return true;
        }
        String name = cls.getName();
        boolean shadow = name.startsWith("dev.vitrail.")
                || name.contains("Shadow")
                || name.contains("shadow")
                || name.startsWith("net.irisshaders.")
                || name.startsWith("net.coderbot.");
        if (shadow) {
            lastShadowFrustumClass = cls;
        } else {
            lastNormalFrustumClass = cls;
        }
        return shadow;
    }

    public static boolean shouldSkipEntityCulling() {
        if (EXTERNAL_SHADOW_PASS_DEPTH.get() > 0) {
            return true;
        }
        if (RenderSystem.isOnRenderThread()) {
            if (frameExtractionDepth > 0 && frameShadowMask != 0L) {
                return true;
            }
            if (inFrameExtraction && frameHitboxesEnabled) {
                return true;
            }
        }
        return checkShadowPassLive();
    }

    public static boolean shouldSkipParticleCulling(@Nullable Frustum frustum) {
        if (EXTERNAL_SHADOW_PASS_DEPTH.get() > 0) {
            return true;
        }
        if (RenderSystem.isOnRenderThread() && frameExtractionDepth > 0 && frameShadowMask != 0L) {
            return true;
        }
        return checkShadowPassLive() || isShadowFrustum(frustum);
    }

    public static int classifyBoxInFrustum(
            @Nullable Frustum frustum,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        if (frustum == null) {
            return -2;
        }
        if (frustum.getClass() == Frustum.class) {
            int r = ((FrustumInvoker) frustum).papi$cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
            return (r == -2 || r == -1) ? r : 0;
        }
        return frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ)) ? -1 : 0;
    }

    public static boolean isBoxInFrustum(
            @Nullable Frustum frustum,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        if (frustum == null) {
            return true;
        }
        if (frustum.getClass() == Frustum.class) {
            int r = ((FrustumInvoker) frustum).papi$cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
            return r == -2 || r == -1;
        }
        return frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public static void setCurrentPassFrustum(@Nullable Camera camera, @Nullable Frustum frustum) {
        if (!RenderSystem.isOnRenderThread() || frameExtractionDepth <= 0) {
            return;
        }
        int idx = Math.min(63, frameExtractionDepth - 1);
        boolean invalid = frustum == null
                || camera == null
                || camera.isPanoramicMode()
                || checkShadowPassLive()
                || isShadowFrustum(frustum);
        if (invalid) {
            PASS_FRUSTUM_STACK[idx] = null;
            PASS_CAMERA_POS_STACK[idx] = null;
            return;
        }
        PASS_FRUSTUM_STACK[idx] = frustum;
        PASS_CAMERA_POS_STACK[idx] = camera.position();
        if (idx == 0 && mainFrameActive) {
            activeFrustum = frustum;
            lastMainFrustum = frustum;
        }
    }

    public static @Nullable Frustum getCurrentWeatherCullFrustum(@Nullable Vec3 cameraPos) {
        if (!RenderSystem.isOnRenderThread() || frameExtractionDepth <= 0 || isShadowPass()) {
            return null;
        }
        int idx = Math.min(63, frameExtractionDepth - 1);
        Frustum f = PASS_FRUSTUM_STACK[idx];
        if (f == null || isShadowFrustum(f)) {
            return null;
        }
        Vec3 passCam = PASS_CAMERA_POS_STACK[idx];
        if (cameraPos != null && passCam != null && passCam.distanceToSqr(cameraPos) > 1.0E-3) {
            return null;
        }
        return f;
    }

    public static boolean isBoxInActiveFrustum(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        if (!RenderSystem.isOnRenderThread() || !inFrameExtraction) {
            return true;
        }
        return isBoxInFrustum(activeFrustum, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean isPointOutsideLastFrustum(double x, double y, double z, double margin) {
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }
        Frustum f = lastMainFrustum;
        if (f == null) {
            return false;
        }
        return !isBoxInFrustum(f, x - margin, y - margin, z - margin, x + margin, y + margin, z + margin);
    }

    public static void beginFrame(@Nullable ClientLevel level, @Nullable Camera camera) {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        capturedEntityId = Integer.MIN_VALUE;
        capturedEntityBox = null;
        pendingNameTagEntityId = Integer.MIN_VALUE;

        int newDepth = frameExtractionDepth + 1;
        frameExtractionDepth = newDepth;
        int bitIdx = Math.min(62, newDepth - 1);
        int stackIdx = Math.min(63, newDepth - 1);

        boolean liveShadow = checkShadowPassLive();
        boolean panoramic = camera != null && camera.isPanoramicMode();
        Frustum cullFrustum = camera != null ? camera.getCullFrustum() : null;
        boolean shadowFrust = isShadowFrustum(cullFrustum);

        if (!liveShadow && !shadowFrust && !panoramic && camera != null && cullFrustum != null) {
            PASS_FRUSTUM_STACK[stackIdx] = cullFrustum;
            PASS_CAMERA_POS_STACK[stackIdx] = camera.position();
        } else {
            PASS_FRUSTUM_STACK[stackIdx] = null;
            PASS_CAMERA_POS_STACK[stackIdx] = null;
        }

        if (newDepth > 1) {
            inFrameExtraction = false;
            if (liveShadow || shadowFrust) {
                frameShadowMask |= (1L << bitIdx);
            } else {
                frameShadowMask &= ~(1L << bitIdx);
            }
            return;
        }

        PapiBlockCullingCache.syncTagsIfNeeded();

        if (liveShadow || shadowFrust || level == null || camera == null) {
            frameShadowMask = (liveShadow || shadowFrust) ? 1L : 0L;
            mainFrameActive = false;
            inFrameExtraction = false;
            activeFrustum = null;
            return;
        }

        frameShadowMask = 0L;
        ensureWorkerStarted();

        nameTagPoolFlip ^= 1;
        nameTagPoolCursor = 0;

        long now = System.nanoTime();
        long gameTime = level.getGameTime();
        Vec3 camPos = camera.position();
        double cx = camPos.x;
        double cy = camPos.y;
        double cz = camPos.z;

        Minecraft mc = Minecraft.getInstance();
        boolean wantLeavesOpaque = PapiConfig.solidLeaves()
                || (mc != null && mc.options != null && !mc.options.cutoutLeaves().get());

        int gen = GENERATION.get();
        if (renderLevelRef.get() != level || wantLeavesOpaque != leavesOpaque) {
            leavesOpaque = wantLeavesOpaque;
            gen = GENERATION.incrementAndGet();
            renderLevelRef = new WeakReference<>(level);
            lastLightLevelRef = new WeakReference<>(level);
            renderVis.invalidate();
            resetHysteresis();
            bumpParticleLightStamp();
            lastLightGameTime = gameTime;
            lastLightBlockEpoch = WORLD_BLOCK_EPOCH.get();
            lastLightWorldEpoch = WORLD_LIGHT_EPOCH.get();
            Thread wt = workerThread;
            if (wt != null) {
                LockSupport.unpark(wt);
            }
        }

        drainReadyVis(gen, gameTime, now);

        lastFrameCulledEntities = frameCulledEntities;
        lastFrameCulledBlockEntities = frameCulledBlockEntities;
        lastFrameCulledParticles = frameCulledParticles;
        frameCulledEntities = 0L;
        frameCulledBlockEntities = 0L;
        frameCulledParticles = 0L;
        hystFrame++;
        if (hystFrame == Integer.MAX_VALUE) {
            resetHysteresis();
        }
        lastHystFrameNanos = now;

        particleMemoStamp++;
        if (particleMemoStamp == 0) {
            Arrays.fill(PARTICLE_MEMO_STAMPS, 0);
            particleMemoStamp = 1;
        }

        int curBlockEpoch = WORLD_BLOCK_EPOCH.get();
        int curLightEpoch = WORLD_LIGHT_EPOCH.get();
        if (lastLightLevelRef.get() != level
                || curBlockEpoch != lastLightBlockEpoch
                || curLightEpoch != lastLightWorldEpoch
                || gameTime != lastLightGameTime) {
            lastLightLevelRef = new WeakReference<>(level);
            lastLightBlockEpoch = curBlockEpoch;
            lastLightWorldEpoch = curLightEpoch;
            lastLightGameTime = gameTime;
            bumpParticleLightStamp();
        }

        frameHitboxesEnabled = mc != null
                && mc.debugEntries != null
                && mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);

        frameCamBlkX = Mth.floor(cx);
        frameCamBlkY = Mth.floor(cy);
        frameCamBlkZ = Mth.floor(cz);
        frameCamInOpaque = isLiveOpaque(level, RENDER_WORK_POS.set(frameCamBlkX, frameCamBlkY, frameCamBlkZ));
        frameNowNanos = now;
        activeFrustum = cullFrustum;
        lastMainFrustum = cullFrustum;
        mainFrameActive = true;
        inFrameExtraction = true;

        renderReq.reset(gen, hystFrame, level, gameTime, cx, cy, cz, frameCamInOpaque, leavesOpaque);
    }

    public static void endFrame() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        capturedEntityId = Integer.MIN_VALUE;
        capturedEntityBox = null;
        pendingNameTagEntityId = Integer.MIN_VALUE;
        if (frameExtractionDepth > 1) {
            int stackIdx = Math.min(63, frameExtractionDepth - 1);
            PASS_FRUSTUM_STACK[stackIdx] = null;
            PASS_CAMERA_POS_STACK[stackIdx] = null;
            int bitIdx = Math.min(62, frameExtractionDepth - 1);
            frameShadowMask &= ~(1L << bitIdx);
            frameExtractionDepth--;
            if (frameExtractionDepth == 1) {
                inFrameExtraction = mainFrameActive;
            }
            return;
        }
        PASS_FRUSTUM_STACK[0] = null;
        PASS_CAMERA_POS_STACK[0] = null;
        frameExtractionDepth = 0;
        frameShadowMask = 0L;
        activeFrustum = null;
        boolean wasActive = mainFrameActive;
        mainFrameActive = false;
        inFrameExtraction = false;
        if (wasActive && renderReq.count > 0) {
            submitCurrentRenderBatch(System.nanoTime());
        }
    }

    private static void drainReadyVis(int gen, long gameTime, long nowNanos) {
        VisibilitySnapshot nextVis = READY_VIS_SLOT.getAndSet(null);
        if (nextVis != null) {
            if (renderVis.generation == gen && nextVis.generation == gen) {
                nextVis.mergeFrom(renderVis, gen, gameTime, nowNanos, RENDER_MERGE_SCRATCH);
            }
            FREE_VIS_SLOT.set(renderVis);
            renderVis = nextVis;
        }
    }

    private static void submitCurrentRenderBatch(long now) {
        ClientLevel lvl = renderReq.levelRef.get();
        int gen = renderReq.generation;
        int curFrameId = renderReq.frameId;
        long gt = renderReq.gameTime;
        double cx = renderReq.camX;
        double cy = renderReq.camY;
        double cz = renderReq.camZ;
        boolean camOpaque = renderReq.camInOpaque;
        boolean solidLeaves = renderReq.solidLeavesMode;
        RequestBatch existing = PENDING_REQ_SLOT.get();
        if (existing != null && existing.generation == gen) {
            boolean keepExisting = (curFrameId > 0 && existing.frameId == curFrameId)
                    || (curFrameId < 0 && existing.count >= renderReq.count);
            if (keepExisting) {
                Thread wt = workerThread;
                if (wt != null) {
                    LockSupport.unpark(wt);
                }
                renderReq.reset(gen, curFrameId, lvl, gt, cx, cy, cz, camOpaque, solidLeaves);
                lastSubmitNanos = now;
                return;
            }
        }
        RequestBatch displacedReq = PENDING_REQ_SLOT.getAndSet(renderReq);
        RequestBatch nextReq = displacedReq != null ? displacedReq : FREE_REQ_SLOT.getAndSet(null);
        renderReq = nextReq != null ? nextReq : new RequestBatch();
        Thread wt = workerThread;
        if (wt != null) {
            LockSupport.unpark(wt);
        }
        renderReq.reset(gen, curFrameId, lvl, gt, cx, cy, cz, camOpaque, solidLeaves);
        lastSubmitNanos = now;
    }

    private static void bumpParticleLightStamp() {
        particleLightStamp++;
        if (particleLightStamp == 0) {
            Arrays.fill(PARTICLE_LIGHT_STAMPS, 0);
            particleLightStamp = 1;
        }
    }

    public static boolean isWithinParticleLod(ClientLevel level, double px, double py, double pz) {
        if (RenderSystem.isOnRenderThread()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null || (mc.level != null && mc.level != level)) {
                return true;
            }
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            double dx = px - cam.x;
            double dy = py - cam.y;
            double dz = pz - cam.z;
            return dx * dx + dy * dy + dz * dz <= PapiConfig.particleLodDistanceSqr();
        }
        return true;
    }

    public static int getCachedParticleLight(ClientLevel level, double px, double py, double pz) {
        int bx = Mth.floor(px);
        int by = Mth.floor(py);
        int bz = Mth.floor(pz);
        if (!RenderSystem.isOnRenderThread()) {
            if (!level.hasChunk(bx >> 4, bz >> 4)) {
                return 15728640;
            }
            return LightCoordsUtil.getLightCoords(level, new BlockPos(bx, by, bz));
        }
        if (((by + 2048) >>> 12) != 0 || (((bx + 33554432) | (bz + 33554432)) >>> 26) != 0) {
            return sampleParticleLightDirect(level, bx, by, bz);
        }
        if (inFrameExtraction) {
            if (lastLightLevelRef.get() != level) {
                return sampleParticleLightDirect(level, bx, by, bz);
            }
        } else {
            int curBlock = WORLD_BLOCK_EPOCH.get();
            int curLight = WORLD_LIGHT_EPOCH.get();
            long gt = level.getGameTime();
            if (curBlock != lastLightBlockEpoch
                    || curLight != lastLightWorldEpoch
                    || gt != lastLightGameTime
                    || lastLightLevelRef.get() != level) {
                lastLightLevelRef = new WeakReference<>(level);
                lastLightBlockEpoch = curBlock;
                lastLightWorldEpoch = curLight;
                lastLightGameTime = gt;
                bumpParticleLightStamp();
            }
        }
        long key = BlockPos.asLong(bx, by, bz);
        int slot = mix64(key) & PARTICLE_LIGHT_MASK;
        int stamp = particleLightStamp;
        for (int probe = 0; probe < 4; probe++) {
            int s = (slot + probe) & PARTICLE_LIGHT_MASK;
            if (PARTICLE_LIGHT_STAMPS[s] == stamp && PARTICLE_LIGHT_KEYS[s] == key) {
                return PARTICLE_LIGHT_VALS[s];
            }
            if (PARTICLE_LIGHT_STAMPS[s] != stamp) {
                int light = sampleParticleLightDirect(level, bx, by, bz);
                PARTICLE_LIGHT_STAMPS[s] = stamp;
                PARTICLE_LIGHT_KEYS[s] = key;
                PARTICLE_LIGHT_VALS[s] = light;
                return light;
            }
        }
        int light = sampleParticleLightDirect(level, bx, by, bz);
        PARTICLE_LIGHT_STAMPS[slot] = stamp;
        PARTICLE_LIGHT_KEYS[slot] = key;
        PARTICLE_LIGHT_VALS[slot] = light;
        return light;
    }

    private static int sampleParticleLightDirect(ClientLevel level, int bx, int by, int bz) {
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return 15728640;
        }
        return LightCoordsUtil.getLightCoords(level, LIGHT_WORK_POS.set(bx, by, bz));
    }

    public static boolean isEntityOccludedForClientTick(ClientLevel level, Entity entity, double minDistSqr) {
        if (!RenderSystem.isOnRenderThread() || renderLevelRef.get() != level) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || frameHitboxesEnabled) {
            return false;
        }
        AABB box = entity.getBoundingBox();
        boolean validBox = box != null && !box.isInfinite() && !box.hasNaN();
        if (mc.player != null) {
            double px = mc.player.getX();
            double py = mc.player.getEyeY();
            double pz = mc.player.getZ();
            double dpx = validBox ? (px - Mth.clamp(px, box.minX, box.maxX)) : (px - entity.getX());
            double dpy = validBox ? (py - Mth.clamp(py, box.minY, box.maxY)) : (py - entity.getY());
            double dpz = validBox ? (pz - Mth.clamp(pz, box.minZ, box.maxZ)) : (pz - entity.getZ());
            if (dpx * dpx + dpy * dpy + dpz * dpz <= minDistSqr) {
                return false;
            }
        }
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 camPos = cam.position();
        double dcx = validBox ? (camPos.x - Mth.clamp(camPos.x, box.minX, box.maxX)) : (camPos.x - entity.getX());
        double dcy = validBox ? (camPos.y - Mth.clamp(camPos.y, box.minY, box.maxY)) : (camPos.y - entity.getY());
        double dcz = validBox ? (camPos.z - Mth.clamp(camPos.z, box.minZ, box.maxZ)) : (camPos.z - entity.getZ());
        if (dcx * dcx + dcy * dcy + dcz * dcz <= minDistSqr) {
            return false;
        }

        Frustum frustum = lastMainFrustum;
        if (frustum != null && validBox) {
            if (!isBoxInFrustum(
                    frustum,
                    box.minX - 1.0,
                    box.minY - 1.0,
                    box.minZ - 1.0,
                    box.maxX + 1.0,
                    box.maxY + 1.0,
                    box.maxZ + 1.0
            )) {
                return true;
            }
        }

        int gen = GENERATION.get();
        VisibilitySnapshot vis = renderVis;
        if (vis.generation != gen) {
            return false;
        }
        int slot = vis.findSlot(gen, level.getGameTime(), System.nanoTime(), TARGET_ENTITY, entity.getId());
        return slot >= 0 && vis.states[slot] != STATE_VISIBLE;
    }

    public static void recordClientEntityTick(long gameTime, boolean skipped) {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        if (gameTime != lastClientEntityTickGameTime) {
            if (lastClientEntityTickGameTime != Long.MIN_VALUE) {
                lastTickSkippedEntities = tickSkippedEntitiesAcc;
                lastTickProcessedEntities = tickProcessedEntitiesAcc;
            }
            tickSkippedEntitiesAcc = 0L;
            tickProcessedEntitiesAcc = 0L;
            lastClientEntityTickGameTime = gameTime;
        }
        if (skipped) {
            tickSkippedEntitiesAcc++;
        } else {
            tickProcessedEntitiesAcc++;
        }
    }

    private static void ensureWorkerStarted() {
        if (workerThread != null) {
            return;
        }
        synchronized (WORKER_INIT_LOCK) {
            if (workerThread != null) {
                return;
            }
            Thread t = new Thread(new PapiOcclusionWorker(), "PapiOcclusionWorker");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            workerThread = t;
            t.start();
        }
    }

    private static void resetHysteresis() {
        hystTableStamp++;
        if (hystTableStamp == 0) {
            Arrays.fill(HYST_STAMPS, 0);
            hystTableStamp = 1;
        }
        hystFrame = 1;
        lastHystFrameNanos = 0L;
    }

    private static void advanceFallbackFrameIfNeeded(long now) {
        if (!inFrameExtraction && now - lastHystFrameNanos >= FALLBACK_FRAME_STEP_NANOS) {
            lastFrameCulledEntities = frameCulledEntities;
            lastFrameCulledBlockEntities = frameCulledBlockEntities;
            lastFrameCulledParticles = frameCulledParticles;
            frameCulledEntities = 0L;
            frameCulledBlockEntities = 0L;
            frameCulledParticles = 0L;
            hystFrame++;
            if (hystFrame == Integer.MAX_VALUE) {
                resetHysteresis();
            }
            lastHystFrameNanos = now;
        }
    }

    private static void recordCulled(byte targetKind) {
        if (targetKind == TARGET_ENTITY) {
            frameCulledEntities++;
            totalCulledEntities++;
        } else if (targetKind == TARGET_BLOCK_ENTITY) {
            frameCulledBlockEntities++;
            totalCulledBlockEntities++;
        } else if (targetKind == TARGET_PARTICLE) {
            frameCulledParticles++;
            totalCulledParticles++;
        }
    }

    public static void recordFrustumCulledParticle() {
        frameCulledParticles++;
        totalCulledParticles++;
    }

    public static long getLastFrameCulledEntities() {
        return lastFrameCulledEntities;
    }

    public static long getLastFrameCulledBlockEntities() {
        return lastFrameCulledBlockEntities;
    }

    public static long getLastFrameCulledParticles() {
        return lastFrameCulledParticles;
    }

    public static long getTotalCulledEntities() {
        return totalCulledEntities;
    }

    public static long getTotalCulledBlockEntities() {
        return totalCulledBlockEntities;
    }

    public static long getTotalCulledParticles() {
        return totalCulledParticles;
    }

    public static long getLastTickSkippedEntities() {
        return lastTickSkippedEntities;
    }

    public static long getLastTickProcessedEntities() {
        return lastTickProcessedEntities;
    }

    public static long getLastWorkerPassMicros() {
        return lastWorkerPassMicros;
    }

    private static boolean applyHysteresis(byte targetKind, long targetKey, boolean rawOccluded) {
        int slot = mix64(targetKey ^ ((long) targetKind << 56)) & HYST_TABLE_MASK;
        int stamp = hystTableStamp;
        if (!rawOccluded) {
            for (int probe = 0; probe < 8; probe++) {
                int s = (slot + probe) & HYST_TABLE_MASK;
                if (HYST_STAMPS[s] == stamp && HYST_KINDS[s] == targetKind && HYST_KEYS[s] == targetKey) {
                    HYST_STREAKS[s] = 0;
                    HYST_LAST_FRAME[s] = 0;
                    break;
                }
            }
            return false;
        }
        int frame = hystFrame;
        int found = -1;
        int freeSlot = -1;
        for (int probe = 0; probe < 8; probe++) {
            int s = (slot + probe) & HYST_TABLE_MASK;
            if (HYST_STAMPS[s] == stamp && HYST_KINDS[s] == targetKind && HYST_KEYS[s] == targetKey) {
                found = s;
                break;
            }
            if (freeSlot < 0) {
                if (HYST_STAMPS[s] != stamp
                        || HYST_STREAKS[s] == 0
                        || (HYST_LAST_FRAME[s] != frame && HYST_LAST_FRAME[s] != frame - 1)) {
                    freeSlot = s;
                }
            }
        }
        if (found < 0) {
            if (freeSlot < 0) {
                return false;
            }
            HYST_STAMPS[freeSlot] = stamp;
            HYST_KINDS[freeSlot] = targetKind;
            HYST_KEYS[freeSlot] = targetKey;
            HYST_STREAKS[freeSlot] = 1;
            HYST_LAST_FRAME[freeSlot] = frame;
            return false;
        }
        int last = HYST_LAST_FRAME[found];
        byte streak = HYST_STREAKS[found];
        if (last != frame) {
            if (last == frame - 1) {
                streak = (byte) Math.min(4, streak + 1);
            } else {
                streak = 1;
            }
            HYST_STREAKS[found] = streak;
            HYST_LAST_FRAME[found] = frame;
        }
        if (streak >= HYST_HOLD_FRAMES) {
            recordCulled(targetKind);
            return true;
        }
        return false;
    }

    public static void clear() {
        int newGen = GENERATION.incrementAndGet();
        WORLD_BLOCK_EPOCH.incrementAndGet();
        WORLD_LIGHT_EPOCH.incrementAndGet();
        EXTERNAL_SHADOW_PASS_DEPTH.set(0);
        if (RenderSystem.isOnRenderThread()) {
            capturedEntityId = Integer.MIN_VALUE;
            capturedEntityBox = null;
            pendingNameTagEntityId = Integer.MIN_VALUE;
            nameTagPoolCursor = 0;
            NAMETAG_STATE_POOLS[0].clear();
            NAMETAG_STATE_POOLS[1].clear();
            inFrameExtraction = false;
            mainFrameActive = false;
            frameExtractionDepth = 0;
            frameShadowMask = 0L;
            activeFrustum = null;
            lastMainFrustum = null;
            renderLevelRef = new WeakReference<>(null);
            lastLightLevelRef = new WeakReference<>(null);
            renderVis.invalidate();
            renderReq.reset(newGen, -1, null, 0L, 0.0, 0.0, 0.0, false, leavesOpaque);
            lastSubmitNanos = 0L;
            resetHysteresis();
            bumpParticleLightStamp();
            particleMemoStamp++;
            if (particleMemoStamp == 0) {
                Arrays.fill(PARTICLE_MEMO_STAMPS, 0);
                particleMemoStamp = 1;
            }
            frameCulledEntities = 0L;
            frameCulledBlockEntities = 0L;
            frameCulledParticles = 0L;
            tickSkippedEntitiesAcc = 0L;
            tickProcessedEntitiesAcc = 0L;
            lastClientEntityTickGameTime = Long.MIN_VALUE;
        }
        RequestBatch pending = PENDING_REQ_SLOT.getAndSet(null);
        if (pending != null) {
            pending.levelRef = new WeakReference<>(null);
            FREE_REQ_SLOT.set(pending);
        }
        VisibilitySnapshot ready = READY_VIS_SLOT.getAndSet(null);
        if (ready != null) {
            ready.invalidate();
            FREE_VIS_SLOT.set(ready);
        }
        lastFrameCulledEntities = 0L;
        lastFrameCulledBlockEntities = 0L;
        lastFrameCulledParticles = 0L;
        totalCulledEntities = 0L;
        totalCulledBlockEntities = 0L;
        totalCulledParticles = 0L;
        lastTickSkippedEntities = 0L;
        lastTickProcessedEntities = 0L;
        lastWorkerPassMicros = 0L;
        Thread wt = workerThread;
        if (wt != null) {
            LockSupport.unpark(wt);
        }
    }

    public static boolean isParticleOccluded(ClientLevel level, Vec3 cameraPos, double px, double py, double pz) {
        if (!RenderSystem.isOnRenderThread() || frameExtractionDepth > 1 || isShadowPass()) {
            return false;
        }
        int bx = Mth.floor(px);
        int by = Mth.floor(py);
        int bz = Mth.floor(pz);
        long posKey = BlockPos.asLong(bx, by, bz);

        if (inFrameExtraction) {
            if (renderLevelRef.get() != level) {
                return false;
            }
            int mSlot = mix64(posKey) & PARTICLE_MEMO_MASK;
            int mStamp = particleMemoStamp;
            int writeSlot = mSlot;
            for (int probe = 0; probe < 4; probe++) {
                int s = (mSlot + probe) & PARTICLE_MEMO_MASK;
                if (PARTICLE_MEMO_STAMPS[s] == mStamp && PARTICLE_MEMO_KEYS[s] == posKey) {
                    if (PARTICLE_MEMO_VALS[s] == 2) {
                        recordCulled(TARGET_PARTICLE);
                        return true;
                    }
                    return false;
                }
                if (PARTICLE_MEMO_STAMPS[s] != mStamp) {
                    writeSlot = s;
                    break;
                }
            }
            boolean occluded = evalBoxOccluded(
                    level,
                    cameraPos,
                    bx + 0.05,
                    by + 0.05,
                    bz + 0.05,
                    bx + 0.95,
                    by + 0.95,
                    bz + 0.95,
                    TARGET_PARTICLE,
                    posKey,
                    PARTICLE_MIN_CULL_DIST_SQR,
                    true
            );
            PARTICLE_MEMO_STAMPS[writeSlot] = mStamp;
            PARTICLE_MEMO_KEYS[writeSlot] = posKey;
            PARTICLE_MEMO_VALS[writeSlot] = (byte) (occluded ? 2 : 1);
            return occluded;
        }

        return evalBoxOccluded(
                level,
                cameraPos,
                bx + 0.05,
                by + 0.05,
                bz + 0.05,
                bx + 0.95,
                by + 0.95,
                bz + 0.95,
                TARGET_PARTICLE,
                posKey,
                PARTICLE_MIN_CULL_DIST_SQR,
                true
        );
    }

    public static boolean isLargeParticleOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            double px,
            double py,
            double pz,
            double radius
    ) {
        if (!RenderSystem.isOnRenderThread() || frameExtractionDepth > 1 || isShadowPass()) {
            return false;
        }
        int bx = Mth.floor(px);
        int by = Mth.floor(py);
        int bz = Mth.floor(pz);
        int rBucket = Mth.clamp((int) Math.ceil(radius * 2.0), 1, 16);
        long rSalt = ((long) rBucket * 0x9E3779B97F4A7C15L) ^ 0x5555555555555555L;
        long key = BlockPos.asLong(bx, by, bz) ^ rSalt;

        if (inFrameExtraction) {
            if (renderLevelRef.get() != level) {
                return false;
            }
            int mSlot = mix64(key) & PARTICLE_MEMO_MASK;
            int mStamp = particleMemoStamp;
            int writeSlot = mSlot;
            for (int probe = 0; probe < 4; probe++) {
                int s = (mSlot + probe) & PARTICLE_MEMO_MASK;
                if (PARTICLE_MEMO_STAMPS[s] == mStamp && PARTICLE_MEMO_KEYS[s] == key) {
                    if (PARTICLE_MEMO_VALS[s] == 2) {
                        recordCulled(TARGET_PARTICLE);
                        return true;
                    }
                    return false;
                }
                if (PARTICLE_MEMO_STAMPS[s] != mStamp) {
                    writeSlot = s;
                    break;
                }
            }
            boolean occluded = evalBoxOccluded(
                    level,
                    cameraPos,
                    px - radius,
                    py - radius,
                    pz - radius,
                    px + radius,
                    py + radius,
                    pz + radius,
                    TARGET_PARTICLE,
                    key,
                    PARTICLE_MIN_CULL_DIST_SQR,
                    true
            );
            PARTICLE_MEMO_STAMPS[writeSlot] = mStamp;
            PARTICLE_MEMO_KEYS[writeSlot] = key;
            PARTICLE_MEMO_VALS[writeSlot] = (byte) (occluded ? 2 : 1);
            return occluded;
        }

        return evalBoxOccluded(
                level,
                cameraPos,
                px - radius,
                py - radius,
                pz - radius,
                px + radius,
                py + radius,
                pz + radius,
                TARGET_PARTICLE,
                key,
                PARTICLE_MIN_CULL_DIST_SQR,
                true
        );
    }

    public static boolean isBoxOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            AABB box,
            byte targetKind,
            long targetKey,
            double minCullDistSqr
    ) {
        return isBoxOccluded(
                level,
                cameraPos,
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                targetKind,
                targetKey,
                minCullDistSqr
        );
    }

    public static boolean isBoxOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            double rawMinX,
            double rawMinY,
            double rawMinZ,
            double rawMaxX,
            double rawMaxY,
            double rawMaxZ,
            byte targetKind,
            long targetKey,
            double minCullDistSqr
    ) {
        if (!RenderSystem.isOnRenderThread() || frameExtractionDepth > 1 || isShadowPass()) {
            return false;
        }
        return evalBoxOccluded(
                level,
                cameraPos,
                rawMinX,
                rawMinY,
                rawMinZ,
                rawMaxX,
                rawMaxY,
                rawMaxZ,
                targetKind,
                targetKey,
                minCullDistSqr,
                false
        );
    }

    private static boolean evalBoxOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            double rawMinX,
            double rawMinY,
            double rawMinZ,
            double rawMaxX,
            double rawMaxY,
            double rawMaxZ,
            byte targetKind,
            long targetKey,
            double minCullDistSqr,
            boolean preAdjustedBounds
    ) {
        if (Double.isNaN(rawMinX) || Double.isNaN(rawMinY) || Double.isNaN(rawMinZ)
                || Double.isNaN(rawMaxX) || Double.isNaN(rawMaxY) || Double.isNaN(rawMaxZ)) {
            return false;
        }

        double cx = cameraPos.x;
        double cy = cameraPos.y;
        double cz = cameraPos.z;

        double bxMin;
        double byMin;
        double bzMin;
        double bxMax;
        double byMax;
        double bzMax;

        if (preAdjustedBounds) {
            bxMin = rawMinX;
            byMin = rawMinY;
            bzMin = rawMinZ;
            bxMax = rawMaxX;
            byMax = rawMaxY;
            bzMax = rawMaxZ;
        } else if (targetKind == TARGET_BLOCK_ENTITY) {
            bxMin = adjustGridBoundMin(rawMinX);
            byMin = adjustGridBoundMin(rawMinY);
            bzMin = adjustGridBoundMin(rawMinZ);
            bxMax = adjustGridBoundMax(rawMaxX);
            byMax = adjustGridBoundMax(rawMaxY);
            bzMax = adjustGridBoundMax(rawMaxZ);
        } else {
            double rawSpanX = rawMaxX - rawMinX;
            double rawSpanY = rawMaxY - rawMinY;
            double rawSpanZ = rawMaxZ - rawMinZ;
            bxMin = expandWithoutCrossingFloor(rawMinX, -0.03, rawSpanX);
            byMin = expandWithoutCrossingFloor(rawMinY, -0.03, rawSpanY);
            bzMin = expandWithoutCrossingFloor(rawMinZ, -0.03, rawSpanZ);
            bxMax = expandWithoutCrossingCeil(rawMaxX, 0.03, rawSpanX);
            byMax = expandWithoutCrossingCeil(rawMaxY, 0.03, rawSpanY);
            bzMax = expandWithoutCrossingCeil(rawMaxZ, 0.03, rawSpanZ);
        }

        if (bxMax < bxMin) { double mid = 0.5 * (bxMin + bxMax); bxMin = mid - 0.02; bxMax = mid + 0.02; }
        if (byMax < byMin) { double mid = 0.5 * (byMin + byMax); byMin = mid - 0.02; byMax = mid + 0.02; }
        if (bzMax < bzMin) { double mid = 0.5 * (bzMin + bzMax); bzMin = mid - 0.02; bzMax = mid + 0.02; }

        double spanX = bxMax - bxMin;
        double spanY = byMax - byMin;
        double spanZ = bzMax - bzMin;
        if (spanX > MAX_CULL_BOX_SPAN || spanY > MAX_CULL_BOX_SPAN || spanZ > MAX_CULL_BOX_SPAN) {
            return false;
        }

        if (cx >= bxMin && cx <= bxMax && cy >= byMin && cy <= byMax && cz >= bzMin && cz <= bzMax) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        double centerX = 0.5 * (bxMin + bxMax);
        double centerY = 0.5 * (byMin + byMax);
        double centerZ = 0.5 * (bzMin + bzMax);
        double dxC = centerX - cx;
        double dyC = centerY - cy;
        double dzC = centerZ - cz;
        if (dxC * dxC + dyC * dyC + dzC * dzC <= minCullDistSqr) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        int camBlkX;
        int camBlkY;
        int camBlkZ;
        boolean camInOpaque;
        long evalNowNanos;
        if (inFrameExtraction) {
            if (renderLevelRef.get() != level) {
                return false;
            }
            camBlkX = frameCamBlkX;
            camBlkY = frameCamBlkY;
            camBlkZ = frameCamBlkZ;
            camInOpaque = frameCamInOpaque;
            evalNowNanos = frameNowNanos;
        } else {
            camBlkX = Mth.floor(cx);
            camBlkY = Mth.floor(cy);
            camBlkZ = Mth.floor(cz);
            camInOpaque = isLiveOpaque(level, RENDER_WORK_POS.set(camBlkX, camBlkY, camBlkZ));
            ensureWorkerStarted();
            evalNowNanos = System.nanoTime();
        }

        long gameTime = level.getGameTime();
        int gen = GENERATION.get();
        if (renderLevelRef.get() != level) {
            gen = GENERATION.incrementAndGet();
            renderLevelRef = new WeakReference<>(level);
            renderVis.invalidate();
            resetHysteresis();
            renderReq.reset(gen, -1, level, gameTime, cx, cy, cz, camInOpaque, leavesOpaque);
            lastSubmitNanos = evalNowNanos;
            Thread wt = workerThread;
            if (wt != null) {
                LockSupport.unpark(wt);
            }
        }

        if (!inFrameExtraction) {
            drainReadyVis(gen, gameTime, evalNowNanos);
            advanceFallbackFrameIfNeeded(evalNowNanos);
            if (renderReq.generation != gen) {
                renderReq.reset(gen, -1, level, gameTime, cx, cy, cz, camInOpaque, leavesOpaque);
                lastSubmitNanos = evalNowNanos;
            } else if (renderReq.count > 0 && (evalNowNanos - lastSubmitNanos >= FALLBACK_SUBMIT_INTERVAL_NANOS || renderReq.count >= MAX_BATCH_REQUESTS)) {
                renderReq.gameTime = gameTime;
                renderReq.camX = cx;
                renderReq.camY = cy;
                renderReq.camZ = cz;
                renderReq.camInOpaque = camInOpaque;
                renderReq.solidLeavesMode = leavesOpaque;
                submitCurrentRenderBatch(evalNowNanos);
            }
        } else if (renderReq.count >= MAX_BATCH_REQUESTS) {
            renderReq.gameTime = gameTime;
            renderReq.camX = cx;
            renderReq.camY = cy;
            renderReq.camZ = cz;
            renderReq.camInOpaque = camInOpaque;
            renderReq.solidLeavesMode = leavesOpaque;
            submitCurrentRenderBatch(evalNowNanos);
        }

        renderReq.add(targetKind, targetKey, bxMin, byMin, bzMin, bxMax, byMax, bzMax);

        VisibilitySnapshot vis = renderVis;
        if (vis.generation != gen) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        int slot = vis.findSlot(gen, gameTime, evalNowNanos, targetKind, targetKey);
        if (slot < 0) {
            return applyHysteresis(targetKind, targetKey, false);
        }
        byte state = vis.states[slot];
        if (state == STATE_VISIBLE) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        long packed = vis.blockerPos[slot];
        int ox = BlockPos.getX(packed);
        int oy = BlockPos.getY(packed);
        int oz = BlockPos.getZ(packed);
        if (ox == camBlkX && oy == camBlkY && oz == camBlkZ) {
            return applyHysteresis(targetKind, targetKey, false);
        }
        if (!isLiveOpaque(level, RENDER_WORK_POS.set(ox, oy, oz))) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        boolean rawOccluded;
        if (state == STATE_OCCLUDED_SINGLE_VOXEL) {
            rawOccluded = allCornersBlockedByVoxel(cx, cy, cz, bxMin, byMin, bzMin, bxMax, byMax, bzMax, ox, oy, oz);
        } else {
            long tickAge = gameTime - vis.entryTicks[slot];
            long nanoAge = evalNowNanos - vis.entryNanos[slot];
            float curHalfSpan = (float) (0.5 * Math.max(spanX, Math.max(spanY, spanZ)));
            if (tickAge > MULTI_VOXEL_MAX_AGE_TICKS
                    || nanoAge > MULTI_VOXEL_MAX_AGE_NANOS
                    || curHalfSpan > vis.maxHalfSpan[slot] + 0.15F) {
                rawOccluded = false;
            } else {
                double dCamX = cx - vis.snapCamX[slot];
                double dCamY = cy - vis.snapCamY[slot];
                double dCamZ = cz - vis.snapCamZ[slot];
                if (dCamX * dCamX + dCamY * dCamY + dCamZ * dCamZ > MULTI_VOXEL_MAX_SHIFT_SQR) {
                    rawOccluded = false;
                } else {
                    double dBoxX = centerX - vis.centerX[slot];
                    double dBoxY = centerY - vis.centerY[slot];
                    double dBoxZ = centerZ - vis.centerZ[slot];
                    double camShiftSq = dCamX * dCamX + dCamY * dCamY + dCamZ * dCamZ;
                    double boxShiftSq = dBoxX * dBoxX + dBoxY * dBoxY + dBoxZ * dBoxZ;
                    if (boxShiftSq > MULTI_VOXEL_MAX_SHIFT_SQR) {
                        rawOccluded = false;
                    } else {
                        rawOccluded = true;
                        int extraCount = vis.cornerBlockerCount[slot] & 0xFF;
                        int base = slot * MAX_CORNER_BLOCKERS;
                        for (int k = 0; k < extraCount; k++) {
                            long cb = vis.cornerBlockers[base + k];
                            int cbx = BlockPos.getX(cb);
                            int cby = BlockPos.getY(cb);
                            int cbz = BlockPos.getZ(cb);
                            if ((cbx == camBlkX && cby == camBlkY && cbz == camBlkZ)
                                    || !isLiveOpaque(level, RENDER_WORK_POS.set(cbx, cby, cbz))) {
                                rawOccluded = false;
                                break;
                            }
                        }
                        if (rawOccluded && (camShiftSq > 1.0E-4 || boxShiftSq > 1.0E-4)) {
                            rawOccluded = allCornersAndCenterBlockedBySet(
                                    cx, cy, cz,
                                    bxMin, byMin, bzMin,
                                    bxMax, byMax, bzMax,
                                    ox, oy, oz,
                                    vis.cornerBlockers, base, extraCount
                            );
                        }
                    }
                }
            }
        }
        return applyHysteresis(targetKind, targetKey, rawOccluded);
    }

    private static double adjustGridBoundMin(double v) {
        double fl = Math.floor(v);
        if (v - fl < 0.03) {
            return fl + 0.03;
        }
        return v;
    }

    private static double adjustGridBoundMax(double v) {
        double cl = Math.ceil(v);
        if (cl - v < 0.03) {
            return cl - 0.03;
        }
        if (v - Math.floor(v) < 1.0E-6) {
            return v - 0.03;
        }
        return v;
    }

    private static double expandWithoutCrossingFloor(double v, double delta, double rawSpan) {
        double fl = Math.floor(v);
        if (v - fl < 0.02) {
            return rawSpan > 0.05 ? fl + 0.02 : v;
        }
        return Math.max(fl + 0.02, v + delta);
    }

    private static double expandWithoutCrossingCeil(double v, double delta, double rawSpan) {
        double cl = Math.ceil(v);
        if (cl - v < 0.02) {
            return rawSpan > 0.05 ? cl - 0.02 : v;
        }
        if (v - Math.floor(v) < 1.0E-6) {
            return rawSpan > 0.05 ? v - 0.02 : v;
        }
        return Math.min(cl - 0.02, v + delta);
    }

    private static final class PapiOcclusionWorker implements Runnable {

        private final SectionEntry[] sectionCache = new SectionEntry[SECTION_CACHE_SIZE];
        private final boolean[] opaqueTable = new boolean[256];
        private final long[] scratchWords = new long[64];
        private final long[] cornerHitBuf = new long[MAX_CORNER_BLOCKERS];
        private final long[] workerMergeScratch = new long[MAX_CORNER_BLOCKERS];

        private final int[] qbcStamps = new int[QBC_SIZE];
        private final long[] qbcKeys = new long[QBC_SIZE];
        private final byte[] qbcStates = new byte[QBC_SIZE];
        private final long[] qbcBlockers = new long[QBC_SIZE];
        private final byte[] qbcExtraCounts = new byte[QBC_SIZE];
        private final int[] qbcExtraOffsets = new int[QBC_SIZE];
        private final long[] qbcExtraPool = new long[QBC_EXTRA_POOL_SIZE];
        private final float[] qbcVisX = new float[QBC_SIZE];
        private final float[] qbcVisY = new float[QBC_SIZE];
        private final float[] qbcVisZ = new float[QBC_SIZE];
        private int qbcStamp = 1;
        private int qbcExtraPoolTop = 0;

        private long batchSeq = 1L;
        private VisibilitySnapshot workerVis = new VisibilitySnapshot();
        private int workerGeneration = -1;

        PapiOcclusionWorker() {
            for (int i = 0; i < SECTION_CACHE_SIZE; i++) {
                this.sectionCache[i] = new SectionEntry();
            }
        }

        private void clearSections() {
            for (int i = 0; i < SECTION_CACHE_SIZE; i++) {
                SectionEntry e = this.sectionCache[i];
                e.secKey = Long.MIN_VALUE;
                e.validatedSeq = -1L;
                e.observedEpoch = 0;
                e.expireTick = Long.MIN_VALUE;
                e.sectionRef = new WeakReference<>(null);
                e.kind = KIND_EMPTY;
                e.nonEmptyYLayers = 0;
            }
        }

        private void resetQuantizedBoxCache() {
            this.qbcStamp++;
            if (this.qbcStamp == 0) {
                Arrays.fill(this.qbcStamps, 0);
                this.qbcStamp = 1;
            }
            this.qbcExtraPoolTop = 0;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    int currentGen = GENERATION.get();
                    if (this.workerGeneration != currentGen) {
                        clearSections();
                        this.workerGeneration = currentGen;
                        this.workerVis.invalidate();
                    }

                    RequestBatch nextReq = PENDING_REQ_SLOT.getAndSet(null);
                    if (nextReq == null) {
                        LockSupport.park();
                        continue;
                    }

                    processBatch(nextReq);
                    nextReq.levelRef = new WeakReference<>(null);
                    FREE_REQ_SLOT.set(nextReq);
                } catch (Throwable ignored) {
                }
            }
        }

        private void processBatch(RequestBatch req) {
            int reqGen = req.generation;
            if (reqGen != GENERATION.get()) {
                return;
            }
            ClientLevel level = req.levelRef.get();
            int count = req.count;
            if (level == null || count <= 0) {
                return;
            }

            long seq = ++this.batchSeq;
            long gameTime = req.gameTime;
            long batchNanos = System.nanoTime();
            double cx = req.camX;
            double cy = req.camY;
            double cz = req.camZ;
            boolean camInOpaque = req.camInOpaque;
            boolean solidLeavesMode = req.solidLeavesMode;
            float scx = (float) cx;
            float scy = (float) cy;
            float scz = (float) cz;
            int camBlkX = Mth.floor(cx);
            int camBlkY = Mth.floor(cy);
            int camBlkZ = Mth.floor(cz);

            this.workerVis.prepareForGeneration(reqGen);
            resetQuantizedBoxCache();

            for (int i = 0; i < count; i++) {
                if ((i & 31) == 0 && GENERATION.get() != reqGen) {
                    return;
                }
                double bxMin = req.minX[i];
                double byMin = req.minY[i];
                double bzMin = req.minZ[i];
                double bxMax = req.maxX[i];
                double byMax = req.maxY[i];
                double bzMax = req.maxZ[i];

                double spanX = bxMax - bxMin;
                double spanY = byMax - byMin;
                double spanZ = bzMax - bzMin;
                double centerX = 0.5 * (bxMin + bxMax);
                double centerY = 0.5 * (byMin + byMax);
                double centerZ = 0.5 * (bzMin + bzMax);
                float fcx = (float) centerX;
                float fcy = (float) centerY;
                float fcz = (float) centerZ;
                float halfSpan = (float) (0.5 * Math.max(spanX, Math.max(spanY, spanZ)));

                int qxMin = Mth.floor((bxMin - camBlkX) * 4.0);
                int qyMin = Mth.floor((byMin - camBlkY) * 4.0);
                int qzMin = Mth.floor((bzMin - camBlkZ) * 4.0);
                int qxMax = (int) Math.ceil((bxMax - camBlkX) * 4.0);
                int qyMax = (int) Math.ceil((byMax - camBlkY) * 4.0);
                int qzMax = (int) Math.ceil((bzMax - camBlkZ) * 4.0);
                int qdx = qxMax - qxMin;
                int qdy = qyMax - qyMin;
                int qdz = qzMax - qzMin;

                boolean canUseQbc = qxMin >= -1024 && qxMin <= 1023
                        && qyMin >= -1024 && qyMin <= 1023
                        && qzMin >= -1024 && qzMin <= 1023
                        && qdx >= 1 && qdx <= 24
                        && qdy >= 1 && qdy <= 24
                        && qdz >= 1 && qdz <= 24;
                long qKey = 0L;
                int qSlot = -2;

                boolean checkEntProtrusions = req.kinds[i] == TARGET_ENTITY && spanX <= 4.0 && spanY <= 4.0 && spanZ <= 4.0;

                if (canUseQbc) {
                    qKey = ((long) (req.kinds[i] & 0xFF) << 56)
                            | ((long) (qxMin & 0x7FF) << 45)
                            | ((long) (qyMin & 0x7FF) << 34)
                            | ((long) (qzMin & 0x7FF) << 23)
                            | ((long) (qdx & 0x1F) << 15)
                            | ((long) (qdy & 0x1F) << 8)
                            | (long) (qdz & 0x1F);
                    qSlot = lookupQbcSlot(qKey);
                    if (qSlot >= 0) {
                        byte cachedState = this.qbcStates[qSlot];
                        if (cachedState == STATE_VISIBLE) {
                            float vx = this.qbcVisX[qSlot];
                            float vy = this.qbcVisY[qSlot];
                            float vz = this.qbcVisZ[qSlot];
                            if (vx >= bxMin && vx <= bxMax && vy >= byMin && vy <= byMax && vz >= bzMin && vz <= bzMax) {
                                this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_VISIBLE, 0L, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                                continue;
                            }
                        } else if (cachedState == STATE_OCCLUDED_SINGLE_VOXEL) {
                            long cachedBlocker = this.qbcBlockers[qSlot];
                            int cbx = BlockPos.getX(cachedBlocker);
                            int cby = BlockPos.getY(cachedBlocker);
                            int cbz = BlockPos.getZ(cachedBlocker);
                            if (allCornersBlockedByVoxel(cx, cy, cz, bxMin, byMin, bzMin, bxMax, byMax, bzMax, cbx, cby, cbz)
                                    && (!checkEntProtrusions || areEntityProtrusionsBlockedBySet(
                                    level, gameTime, seq, solidLeavesMode,
                                    cx, cy, cz, bxMin, bxMax, centerX, centerY, centerZ, bzMin, bzMax,
                                    cbx, cby, cbz, this.qbcExtraPool, 0, 0
                            ))) {
                                this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_OCCLUDED_SINGLE_VOXEL, cachedBlocker, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                                continue;
                            }
                        } else if (cachedState == STATE_OCCLUDED_MULTI_VOXEL
                                && qdx <= 8 && qdy <= 12 && qdz <= 8
                                && spanX <= 1.2 && spanY <= 2.2 && spanZ <= 1.2) {
                            long cachedBlocker = this.qbcBlockers[qSlot];
                            int cbx = BlockPos.getX(cachedBlocker);
                            int cby = BlockPos.getY(cachedBlocker);
                            int cbz = BlockPos.getZ(cachedBlocker);
                            int extraCnt = this.qbcExtraCounts[qSlot] & 0xFF;
                            int extraOff = this.qbcExtraOffsets[qSlot];
                            if (allCornersAndCenterBlockedBySet(
                                    cx, cy, cz,
                                    bxMin, byMin, bzMin,
                                    bxMax, byMax, bzMax,
                                    cbx, cby, cbz,
                                    this.qbcExtraPool, extraOff, extraCnt
                            ) && (!checkEntProtrusions || areEntityProtrusionsBlockedBySet(
                                    level, gameTime, seq, solidLeavesMode,
                                    cx, cy, cz, bxMin, bxMax, centerX, centerY, centerZ, bzMin, bzMax,
                                    cbx, cby, cbz, this.qbcExtraPool, extraOff, extraCnt
                            ))) {
                                for (int k = 0; k < extraCnt; k++) {
                                    this.cornerHitBuf[k] = this.qbcExtraPool[extraOff + k];
                                }
                                this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_OCCLUDED_MULTI_VOXEL, cachedBlocker, this.cornerHitBuf, extraCnt, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                                continue;
                            }
                        }
                    }
                }

                long firstHit = traceRay(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, centerX, centerY, centerZ);
                if (firstHit == Long.MIN_VALUE) {
                    this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_VISIBLE, 0L, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                    if (canUseQbc) {
                        storeQbcVisible(qKey, fcx, fcy, fcz);
                    }
                    continue;
                }

                int fx = BlockPos.getX(firstHit);
                int fy = BlockPos.getY(firstHit);
                int fz = BlockPos.getZ(firstHit);
                int blockedCornersMask = cornersBlockedByVoxelMask(cx, cy, cz, bxMin, byMin, bzMin, bxMax, byMax, bzMax, fx, fy, fz);
                if (blockedCornersMask == 0xFF && (!checkEntProtrusions || areEntityProtrusionsBlockedBySet(
                        level, gameTime, seq, solidLeavesMode,
                        cx, cy, cz, bxMin, bxMax, centerX, centerY, centerZ, bzMin, bzMax,
                        fx, fy, fz, this.cornerHitBuf, 0, 0
                ))) {
                    this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_OCCLUDED_SINGLE_VOXEL, firstHit, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                    if (canUseQbc) {
                        storeQbcOccluded(qKey, STATE_OCCLUDED_SINGLE_VOXEL, firstHit, null, 0);
                    }
                    continue;
                }

                boolean allBlocked = true;
                int extraCount = 0;
                double witnessX = centerX;
                double witnessY = centerY;
                double witnessZ = centerZ;

                for (int mask = 0; mask < 8; mask++) {
                    if (((blockedCornersMask >>> mask) & 1) != 0) {
                        continue;
                    }
                    double tx = (mask & 1) == 0 ? bxMin : bxMax;
                    double ty = (mask & 2) == 0 ? byMin : byMax;
                    double tz = (mask & 4) == 0 ? bzMin : bzMax;
                    if (isTargetBlockedByKnownVoxels(cx, cy, cz, tx, ty, tz, extraCount)) {
                        continue;
                    }
                    long hit = traceRay(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, tx, ty, tz);
                    if (hit == Long.MIN_VALUE) {
                        allBlocked = false;
                        witnessX = tx;
                        witnessY = ty;
                        witnessZ = tz;
                        break;
                    }
                    extraCount = appendBlocker(firstHit, hit, extraCount);
                }

                if (allBlocked && (spanX > 1.2 || spanY > 1.2 || spanZ > 1.2)) {
                    if (cx < bxMin) {
                        extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 0, bxMin, byMin, byMax, bzMin, bzMax);
                        if (extraCount < 0) {
                            allBlocked = false;
                            witnessX = bxMin;
                            witnessY = centerY;
                            witnessZ = centerZ;
                        }
                    } else if (cx > bxMax) {
                        extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 0, bxMax, byMin, byMax, bzMin, bzMax);
                        if (extraCount < 0) {
                            allBlocked = false;
                            witnessX = bxMax;
                            witnessY = centerY;
                            witnessZ = centerZ;
                        }
                    }
                    if (allBlocked) {
                        if (cy < byMin) {
                            extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 1, byMin, bxMin, bxMax, bzMin, bzMax);
                            if (extraCount < 0) {
                                allBlocked = false;
                                witnessX = centerX;
                                witnessY = byMin;
                                witnessZ = centerZ;
                            }
                        } else if (cy > byMax) {
                            extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 1, byMax, bxMin, bxMax, bzMin, bzMax);
                            if (extraCount < 0) {
                                allBlocked = false;
                                witnessX = centerX;
                                witnessY = byMax;
                                witnessZ = centerZ;
                            }
                        }
                    }
                    if (allBlocked) {
                        if (cz < bzMin) {
                            extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 2, bzMin, bxMin, bxMax, byMin, byMax);
                            if (extraCount < 0) {
                                allBlocked = false;
                                witnessX = centerX;
                                witnessY = centerY;
                                witnessZ = bzMin;
                            }
                        } else if (cz > bzMax) {
                            extraCount = testFaceGrid(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, firstHit, extraCount, 2, bzMax, bxMin, bxMax, byMin, byMax);
                            if (extraCount < 0) {
                                allBlocked = false;
                                witnessX = centerX;
                                witnessY = centerY;
                                witnessZ = bzMax;
                            }
                        }
                    }
                }

                if (allBlocked && req.kinds[i] == TARGET_ENTITY && spanX <= 4.0 && spanY <= 4.0 && spanZ <= 4.0) {
                    extraCount = testEntityAirProtrusions(
                            level, gameTime, seq, camInOpaque, solidLeavesMode,
                            cx, cy, cz, firstHit, extraCount,
                            bxMin, bxMax, centerX, centerY, centerZ, bzMin, bzMax
                    );
                    if (extraCount < 0) {
                        allBlocked = false;
                    }
                }

                if (allBlocked) {
                    this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_OCCLUDED_MULTI_VOXEL, firstHit, this.cornerHitBuf, extraCount, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                    if (canUseQbc && qdx <= 8 && qdy <= 12 && qdz <= 8 && spanX <= 1.2 && spanY <= 2.2 && spanZ <= 1.2) {
                        storeQbcOccluded(qKey, STATE_OCCLUDED_MULTI_VOXEL, firstHit, this.cornerHitBuf, extraCount);
                    }
                } else {
                    this.workerVis.put(reqGen, seq, gameTime, batchNanos, req.kinds[i], req.keys[i], STATE_VISIBLE, firstHit, scx, scy, scz, fcx, fcy, fcz, halfSpan);
                    if (canUseQbc && extraCount >= 0) {
                        storeQbcVisible(qKey, (float) witnessX, (float) witnessY, (float) witnessZ);
                    }
                }
            }

            lastWorkerPassMicros = (System.nanoTime() - batchNanos) / 1_000L;

            if (GENERATION.get() == reqGen) {
                VisibilitySnapshot prevReady = READY_VIS_SLOT.getAndSet(null);
                if (prevReady != null && prevReady.generation == reqGen) {
                    this.workerVis.mergeFrom(prevReady, reqGen, gameTime, batchNanos, this.workerMergeScratch);
                }
                VisibilitySnapshot displacedVis = READY_VIS_SLOT.getAndSet(this.workerVis);
                if (displacedVis != null && prevReady != null) {
                    FREE_VIS_SLOT.set(prevReady);
                }
                VisibilitySnapshot recycled = displacedVis != null
                        ? displacedVis
                        : (prevReady != null ? prevReady : FREE_VIS_SLOT.getAndSet(null));
                this.workerVis = recycled != null ? recycled : new VisibilitySnapshot();
            }
        }

        private int testFaceGrid(
                ClientLevel level,
                long gameTime,
                long seq,
                boolean camInOpaque,
                boolean solidLeavesMode,
                double cx, double cy, double cz,
                long firstHit,
                int extraCount,
                int fixedAxis,
                double fixedCoord,
                double uMin, double uMax,
                double vMin, double vMax
        ) {
            int fx = BlockPos.getX(firstHit);
            int fy = BlockPos.getY(firstHit);
            int fz = BlockPos.getZ(firstHit);
            double spanU = uMax - uMin;
            double spanV = vMax - vMin;
            int stepsU = spanU > 1.2 ? Math.min(32, Math.max(2, (int) Math.ceil(spanU))) : 2;
            int stepsV = spanV > 1.2 ? Math.min(32, Math.max(2, (int) Math.ceil(spanV))) : 2;
            for (int iu = 0; iu <= stepsU; iu++) {
                boolean edgeU = (iu == 0 || iu == stepsU);
                double u = uMin + (spanU * iu) / stepsU;
                for (int iv = 0; iv <= stepsV; iv++) {
                    if (edgeU && (iv == 0 || iv == stepsV)) {
                        continue;
                    }
                    double v = vMin + (spanV * iv) / stepsV;
                    double tx = fixedAxis == 0 ? fixedCoord : u;
                    double ty = fixedAxis == 1 ? fixedCoord : (fixedAxis == 0 ? u : v);
                    double tz = fixedAxis == 2 ? fixedCoord : v;
                    if (voxelBlocksTargetPoint(cx, cy, cz, tx, ty, tz, fx, fy, fz)
                            || isTargetBlockedByKnownVoxels(cx, cy, cz, tx, ty, tz, extraCount)) {
                        continue;
                    }
                    long hit = traceRay(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, tx, ty, tz);
                    if (hit == Long.MIN_VALUE) {
                        return -1;
                    }
                    extraCount = appendBlocker(firstHit, hit, extraCount);
                }
            }
            return extraCount;
        }

        private boolean areEntityProtrusionsBlockedBySet(
                ClientLevel level,
                long gameTime,
                long seq,
                boolean solidLeavesMode,
                double cx, double cy, double cz,
                double bxMin, double bxMax,
                double centerX, double centerY, double centerZ,
                double bzMin, double bzMax,
                int ox, int oy, int oz,
                long[] extras, int extraOff, int extraCnt
        ) {
            for (int dir = 0; dir < 4; dir++) {
                double tx = dir == 0 ? (bxMin - 0.35) : (dir == 1 ? (bxMax + 0.35) : centerX);
                double ty = centerY;
                double tz = dir == 2 ? (bzMin - 0.35) : (dir == 3 ? (bzMax + 0.35) : centerZ);
                int vx = Mth.floor(tx);
                int vy = Mth.floor(ty);
                int vz = Mth.floor(tz);
                if (isVoxelOpaqueInCache(level, gameTime, seq, solidLeavesMode, vx, vy, vz)) {
                    continue;
                }
                if (!isPointBlockedBySet(cx, cy, cz, tx, ty, tz, ox, oy, oz, extras, extraOff, extraCnt)) {
                    return false;
                }
            }
            return true;
        }

        private int testEntityAirProtrusions(
                ClientLevel level,
                long gameTime,
                long seq,
                boolean camInOpaque,
                boolean solidLeavesMode,
                double cx, double cy, double cz,
                long firstHit,
                int extraCount,
                double bxMin, double bxMax,
                double centerX, double centerY, double centerZ,
                double bzMin, double bzMax
        ) {
            int fx = BlockPos.getX(firstHit);
            int fy = BlockPos.getY(firstHit);
            int fz = BlockPos.getZ(firstHit);
            for (int dir = 0; dir < 4; dir++) {
                double tx = dir == 0 ? (bxMin - 0.35) : (dir == 1 ? (bxMax + 0.35) : centerX);
                double ty = centerY;
                double tz = dir == 2 ? (bzMin - 0.35) : (dir == 3 ? (bzMax + 0.35) : centerZ);
                int vx = Mth.floor(tx);
                int vy = Mth.floor(ty);
                int vz = Mth.floor(tz);
                if (isVoxelOpaqueInCache(level, gameTime, seq, solidLeavesMode, vx, vy, vz)) {
                    continue;
                }
                if (voxelBlocksTargetPoint(cx, cy, cz, tx, ty, tz, fx, fy, fz)
                        || isTargetBlockedByKnownVoxels(cx, cy, cz, tx, ty, tz, extraCount)) {
                    continue;
                }
                long hit = traceRay(level, gameTime, seq, camInOpaque, solidLeavesMode, cx, cy, cz, tx, ty, tz);
                if (hit == Long.MIN_VALUE) {
                    return -1;
                }
                extraCount = appendBlocker(firstHit, hit, extraCount);
            }
            return extraCount;
        }

        private boolean isVoxelOpaqueInCache(
                ClientLevel level,
                long gameTime,
                long seq,
                boolean solidLeavesMode,
                int x, int y, int z
        ) {
            SectionEntry sec = getOrBuildSection(level, gameTime, seq, solidLeavesMode, x >> 4, y >> 4, z >> 4);
            byte k = sec.kind;
            if (k == KIND_EMPTY) {
                return false;
            }
            if (k == KIND_FULL) {
                return true;
            }
            int ly = y & 15;
            if (((sec.nonEmptyYLayers >>> ly) & 1) == 0) {
                return false;
            }
            int wordIdx = (ly << 2) | ((z & 15) >> 2);
            int bit = ((z & 3) << 4) | (x & 15);
            return ((sec.words[wordIdx] >>> bit) & 1L) != 0L;
        }

        private boolean isTargetBlockedByKnownVoxels(
                double cx, double cy, double cz,
                double tx, double ty, double tz,
                int extraCount
        ) {
            for (int k = 0; k < extraCount; k++) {
                long packed = this.cornerHitBuf[k];
                if (voxelBlocksTargetPoint(
                        cx, cy, cz, tx, ty, tz,
                        BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed)
                )) {
                    return true;
                }
            }
            return false;
        }

        private int appendBlocker(long firstHit, long hit, int extraCount) {
            if (hit != firstHit && extraCount < MAX_CORNER_BLOCKERS) {
                for (int k = 0; k < extraCount; k++) {
                    if (this.cornerHitBuf[k] == hit) {
                        return extraCount;
                    }
                }
                this.cornerHitBuf[extraCount] = hit;
                return extraCount + 1;
            }
            return extraCount;
        }

        private int lookupQbcSlot(long qKey) {
            int base = mix64(qKey) & QBC_MASK;
            int st = this.qbcStamp;
            for (int p = 0; p < 4; p++) {
                int s = (base + p) & QBC_MASK;
                if (this.qbcStamps[s] != st) {
                    return -2;
                }
                if (this.qbcKeys[s] == qKey) {
                    return s;
                }
            }
            return -2;
        }

        private void storeQbcVisible(long qKey, float vx, float vy, float vz) {
            int base = mix64(qKey) & QBC_MASK;
            int st = this.qbcStamp;
            for (int p = 0; p < 4; p++) {
                int s = (base + p) & QBC_MASK;
                if (this.qbcStamps[s] != st || this.qbcKeys[s] == qKey) {
                    this.qbcStamps[s] = st;
                    this.qbcKeys[s] = qKey;
                    this.qbcStates[s] = STATE_VISIBLE;
                    this.qbcVisX[s] = vx;
                    this.qbcVisY[s] = vy;
                    this.qbcVisZ[s] = vz;
                    return;
                }
            }
        }

        private void storeQbcOccluded(long qKey, byte state, long blocker, long @Nullable [] extras, int extraCount) {
            int cnt = (extras != null && extraCount > 0) ? Math.min(MAX_CORNER_BLOCKERS, extraCount) : 0;
            if (state == STATE_OCCLUDED_MULTI_VOXEL && this.qbcExtraPoolTop + cnt > QBC_EXTRA_POOL_SIZE) {
                return;
            }
            int base = mix64(qKey) & QBC_MASK;
            int st = this.qbcStamp;
            for (int p = 0; p < 4; p++) {
                int s = (base + p) & QBC_MASK;
                if (this.qbcStamps[s] != st || this.qbcKeys[s] == qKey) {
                    this.qbcStamps[s] = st;
                    this.qbcKeys[s] = qKey;
                    this.qbcStates[s] = state;
                    this.qbcBlockers[s] = blocker;
                    this.qbcExtraCounts[s] = (byte) cnt;
                    if (cnt > 0) {
                        int off = this.qbcExtraPoolTop;
                        this.qbcExtraOffsets[s] = off;
                        for (int k = 0; k < cnt; k++) {
                            this.qbcExtraPool[off + k] = extras[k];
                        }
                        this.qbcExtraPoolTop = off + cnt;
                    }
                    return;
                }
            }
        }

        private long traceRay(
                ClientLevel level,
                long gameTime,
                long batchSeq,
                boolean camInOpaque,
                boolean solidLeavesMode,
                double ox, double oy, double oz,
                double tx, double ty, double tz
        ) {
            double dx = tx - ox;
            double dy = ty - oy;
            double dz = tz - oz;

            int x = Mth.floor(ox);
            int y = Mth.floor(oy);
            int z = Mth.floor(oz);
            int endX = Mth.floor(tx);
            int endY = Mth.floor(ty);
            int endZ = Mth.floor(tz);

            if (x == endX && y == endY && z == endZ) {
                return Long.MIN_VALUE;
            }

            int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
            int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
            int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

            double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
            double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
            double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;

            double tMaxX = stepX != 0 ? ((stepX > 0 ? (x + 1.0 - ox) : (ox - x)) * tDeltaX) : Double.MAX_VALUE;
            double tMaxY = stepY != 0 ? ((stepY > 0 ? (y + 1.0 - oy) : (oy - y)) * tDeltaY) : Double.MAX_VALUE;
            double tMaxZ = stepZ != 0 ? ((stepZ > 0 ? (z + 1.0 - oz) : (oz - z)) * tDeltaZ) : Double.MAX_VALUE;

            int cachedSecX = Integer.MIN_VALUE;
            int cachedSecY = Integer.MIN_VALUE;
            int cachedSecZ = Integer.MIN_VALUE;
            SectionEntry secEntry = TRANSIENT_EMPTY_ENTRY;
            boolean clipping = camInOpaque;

            for (int iter = 0; iter < MAX_RAY_STEPS; iter++) {
                if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                    if (tMaxX >= 1.0) return Long.MIN_VALUE;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else if (tMaxY <= tMaxZ) {
                    if (tMaxY >= 1.0) return Long.MIN_VALUE;
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ >= 1.0) return Long.MIN_VALUE;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }

                if (x == endX && y == endY && z == endZ) {
                    return Long.MIN_VALUE;
                }

                int sx = x >> 4;
                int sy = y >> 4;
                int sz = z >> 4;
                if (sx != cachedSecX || sy != cachedSecY || sz != cachedSecZ) {
                    cachedSecX = sx;
                    cachedSecY = sy;
                    cachedSecZ = sz;
                    secEntry = getOrBuildSection(level, gameTime, batchSeq, solidLeavesMode, sx, sy, sz);
                }

                byte kind = secEntry.kind;
                if (kind == KIND_EMPTY) {
                    clipping = false;
                    if ((endX >> 4) == sx && (endY >> 4) == sy && (endZ >> 4) == sz) {
                        return Long.MIN_VALUE;
                    }
                    int remX = stepX > 0 ? (15 - (x & 15)) : (stepX < 0 ? (x & 15) : 0);
                    int remY = stepY > 0 ? (15 - (y & 15)) : (stepY < 0 ? (y & 15) : 0);
                    int remZ = stepZ > 0 ? (15 - (z & 15)) : (stepZ < 0 ? (z & 15) : 0);
                    double tSecX = stepX != 0 ? (tMaxX + remX * tDeltaX) : Double.MAX_VALUE;
                    double tSecY = stepY != 0 ? (tMaxY + remY * tDeltaY) : Double.MAX_VALUE;
                    double tSecZ = stepZ != 0 ? (tMaxZ + remZ * tDeltaZ) : Double.MAX_VALUE;
                    double tExit = Math.min(tSecX, Math.min(tSecY, tSecZ));
                    if (tExit >= 1.0) {
                        return Long.MIN_VALUE;
                    }
                    if (stepX != 0 && remX > 0 && tMaxX < tExit) {
                        int kx = Math.min(remX, 1 + (int) ((tExit - tMaxX) / tDeltaX));
                        while (kx > 0 && tMaxX + (kx - 1) * tDeltaX >= tExit) {
                            kx--;
                        }
                        while (kx < remX && tMaxX + kx * tDeltaX < tExit) {
                            kx++;
                        }
                        if (kx > 0) {
                            x += kx * stepX;
                            tMaxX += kx * tDeltaX;
                        }
                    }
                    if (stepY != 0 && remY > 0 && tMaxY < tExit) {
                        int ky = Math.min(remY, 1 + (int) ((tExit - tMaxY) / tDeltaY));
                        while (ky > 0 && tMaxY + (ky - 1) * tDeltaY >= tExit) {
                            ky--;
                        }
                        while (ky < remY && tMaxY + ky * tDeltaY < tExit) {
                            ky++;
                        }
                        if (ky > 0) {
                            y += ky * stepY;
                            tMaxY += ky * tDeltaY;
                        }
                    }
                    if (stepZ != 0 && remZ > 0 && tMaxZ < tExit) {
                        int kz = Math.min(remZ, 1 + (int) ((tExit - tMaxZ) / tDeltaZ));
                        while (kz > 0 && tMaxZ + (kz - 1) * tDeltaZ >= tExit) {
                            kz--;
                        }
                        while (kz < remZ && tMaxZ + kz * tDeltaZ < tExit) {
                            kz++;
                        }
                        if (kz > 0) {
                            z += kz * stepZ;
                            tMaxZ += kz * tDeltaZ;
                        }
                    }
                    continue;
                }

                if (kind == KIND_FULL) {
                    if (!clipping) {
                        return BlockPos.asLong(x, y, z);
                    }
                    continue;
                }

                int ly = y & 15;
                if (((secEntry.nonEmptyYLayers >>> ly) & 1) == 0) {
                    clipping = false;
                    continue;
                }

                int lx = x & 15;
                int lz = z & 15;
                int wordIdx = (ly << 2) | (lz >> 2);
                int bit = ((lz & 3) << 4) | lx;
                if (((secEntry.words[wordIdx] >>> bit) & 1L) != 0L) {
                    if (!clipping) {
                        return BlockPos.asLong(x, y, z);
                    }
                } else {
                    clipping = false;
                }
            }
            return Long.MIN_VALUE;
        }

        @SuppressWarnings("unchecked")
        private SectionEntry getOrBuildSection(
                ClientLevel level,
                long gameTime,
                long batchSeq,
                boolean solidLeavesMode,
                int sx,
                int sy,
                int sz
        ) {
            long secKey = SectionPos.asLong(sx, sy, sz);
            int slot = mix64(secKey) & SECTION_CACHE_MASK;
            SectionEntry entry = this.sectionCache[slot];
            if (entry.secKey == secKey && entry.validatedSeq == batchSeq) {
                return entry;
            }

            LevelChunk chunk;
            try {
                chunk = level.getChunkSource().getChunk(sx, sz, false);
            } catch (Throwable ignored) {
                return TRANSIENT_EMPTY_ENTRY;
            }
            if (chunk == null) {
                return TRANSIENT_EMPTY_ENTRY;
            }

            int secIndex = chunk.getSectionIndexFromSectionY(sy);
            LevelChunkSection[] sections = chunk.getSections();
            if (secIndex < 0 || secIndex >= sections.length) {
                entry.secKey = secKey;
                entry.validatedSeq = batchSeq;
                entry.observedEpoch = 0;
                entry.expireTick = Long.MAX_VALUE;
                entry.kind = KIND_EMPTY;
                entry.nonEmptyYLayers = 0;
                return entry;
            }

            LevelChunkSection section = sections[secIndex];
            if (section == null) {
                entry.secKey = secKey;
                entry.validatedSeq = batchSeq;
                entry.observedEpoch = 0;
                entry.expireTick = Long.MAX_VALUE;
                entry.kind = KIND_EMPTY;
                entry.nonEmptyYLayers = 0;
                return entry;
            }

            boolean hasDirectEpoch = section instanceof PapiOcclusionSection;
            int secEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
            if (entry.secKey == secKey
                    && entry.observedEpoch == secEpoch
                    && entry.sectionRef.get() == section
                    && (hasDirectEpoch || gameTime <= entry.expireTick)) {
                entry.validatedSeq = batchSeq;
                return entry;
            }

            if (section.hasOnlyAir()) {
                int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                if (postEpoch != secEpoch) {
                    return TRANSIENT_EMPTY_ENTRY;
                }
                entry.secKey = secKey;
                entry.validatedSeq = batchSeq;
                entry.observedEpoch = secEpoch;
                entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                entry.sectionRef = new WeakReference<>(section);
                entry.kind = KIND_EMPTY;
                entry.nonEmptyYLayers = 0;
                return entry;
            }

            PalettedContainer<BlockState> states = section.getStates();
            VarHandle dataHandle = PALETTED_DATA_HANDLE;
            if (states == null || dataHandle == null) {
                return TRANSIENT_EMPTY_ENTRY;
            }

            Object rawData = dataHandle.getVolatile(states);
            if (!(rawData instanceof PalettedContainerDataAccessor<?> rawAccessor)) {
                return TRANSIENT_EMPTY_ENTRY;
            }

            PalettedContainerDataAccessor<BlockState> snap = (PalettedContainerDataAccessor<BlockState>) rawAccessor;
            Palette<BlockState> palette = snap.papi$getPalette();
            BitStorage storage = snap.papi$getStorage();
            if (palette == null || storage == null) {
                return TRANSIENT_EMPTY_ENTRY;
            }

            try {
                int paletteSize = palette.getSize();
                if (paletteSize <= 0) {
                    return TRANSIENT_EMPTY_ENTRY;
                }
                if (paletteSize == 1) {
                    BlockState only = palette.valueFor(0);
                    boolean solid = isStateOpaque(only, solidLeavesMode);
                    int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                    if (dataHandle.getVolatile(states) != rawData || postEpoch != secEpoch) {
                        return TRANSIENT_EMPTY_ENTRY;
                    }
                    entry.secKey = secKey;
                    entry.validatedSeq = batchSeq;
                    entry.observedEpoch = secEpoch;
                    entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                    entry.sectionRef = new WeakReference<>(section);
                    entry.kind = solid ? KIND_FULL : KIND_EMPTY;
                    entry.nonEmptyYLayers = solid ? (short) 0xFFFF : 0;
                    return entry;
                }

                int bits = storage.getBits();
                long[] words = this.scratchWords;
                int solidCount = 0;
                int layerMask = 0;
                boolean raced = false;

                if (bits == 4 && storage instanceof SimpleBitStorage sbs) {
                    int limit = Math.min(paletteSize, 16);
                    int opaqueMask = 0;
                    for (int id = 0; id < limit; id++) {
                        if (isStateOpaque(palette.valueFor(id), solidLeavesMode)) {
                            opaqueMask |= (1 << id);
                        }
                    }
                    if (opaqueMask == 0) {
                        int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                        if (dataHandle.getVolatile(states) != rawData || postEpoch != secEpoch) {
                            return TRANSIENT_EMPTY_ENTRY;
                        }
                        entry.secKey = secKey;
                        entry.validatedSeq = batchSeq;
                        entry.observedEpoch = secEpoch;
                        entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                        entry.sectionRef = new WeakReference<>(section);
                        entry.kind = KIND_EMPTY;
                        entry.nonEmptyYLayers = 0;
                        return entry;
                    }
                    long[] raw = sbs.getRaw();
                    if (raw.length >= 256) {
                        for (int w = 0; w < 64; w++) {
                            int rb = w << 2;
                            long word = unpackNibbleLong(raw[rb], opaqueMask)
                                    | (unpackNibbleLong(raw[rb + 1], opaqueMask) << 16)
                                    | (unpackNibbleLong(raw[rb + 2], opaqueMask) << 32)
                                    | (unpackNibbleLong(raw[rb + 3], opaqueMask) << 48);
                            words[w] = word;
                            if (word != 0L) {
                                solidCount += Long.bitCount(word);
                                layerMask |= (1 << (w >>> 2));
                            }
                        }
                    } else {
                        raced = true;
                    }
                } else if (bits >= 5 && bits <= 8 && storage instanceof SimpleBitStorage sbs) {
                    int cap = 1 << bits;
                    int limit = Math.min(paletteSize, cap);
                    boolean[] table = this.opaqueTable;
                    boolean anyOpaque = false;
                    for (int id = 0; id < limit; id++) {
                        boolean op = isStateOpaque(palette.valueFor(id), solidLeavesMode);
                        table[id] = op;
                        anyOpaque |= op;
                    }
                    for (int id = limit; id < cap; id++) {
                        table[id] = false;
                    }
                    if (!anyOpaque) {
                        int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                        if (dataHandle.getVolatile(states) != rawData || postEpoch != secEpoch) {
                            return TRANSIENT_EMPTY_ENTRY;
                        }
                        entry.secKey = secKey;
                        entry.validatedSeq = batchSeq;
                        entry.observedEpoch = secEpoch;
                        entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                        entry.sectionRef = new WeakReference<>(section);
                        entry.kind = KIND_EMPTY;
                        entry.nonEmptyYLayers = 0;
                        return entry;
                    }
                    Arrays.fill(words, 0L);
                    long[] raw = sbs.getRaw();
                    int valuesPerLong = 64 / bits;
                    long valMask = (1L << bits) - 1L;
                    int idx = 0;
                    for (int r = 0; r < raw.length && idx < 4096; r++) {
                        long v = raw[r];
                        int rem = Math.min(valuesPerLong, 4096 - idx);
                        for (int j = 0; j < rem; j++) {
                            int id = (int) (v & valMask);
                            v >>>= bits;
                            if (table[id]) {
                                int w = idx >>> 6;
                                words[w] |= (1L << (idx & 63));
                                solidCount++;
                                layerMask |= (1 << (idx >>> 8));
                            }
                            idx++;
                        }
                    }
                    if (idx < 4096) {
                        raced = true;
                    }
                } else {
                    boolean useTable = false;
                    int tableLimit = 0;
                    boolean[] table = this.opaqueTable;
                    boolean anyOpaque = true;
                    if (bits > 0 && bits <= 8) {
                        useTable = true;
                        anyOpaque = false;
                        int cap = 1 << bits;
                        tableLimit = Math.min(paletteSize, cap);
                        for (int id = 0; id < tableLimit; id++) {
                            boolean op = isStateOpaque(palette.valueFor(id), solidLeavesMode);
                            table[id] = op;
                            anyOpaque |= op;
                        }
                    }
                    if (!anyOpaque) {
                        int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                        if (dataHandle.getVolatile(states) != rawData || postEpoch != secEpoch) {
                            return TRANSIENT_EMPTY_ENTRY;
                        }
                        entry.secKey = secKey;
                        entry.validatedSeq = batchSeq;
                        entry.observedEpoch = secEpoch;
                        entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                        entry.sectionRef = new WeakReference<>(section);
                        entry.kind = KIND_EMPTY;
                        entry.nonEmptyYLayers = 0;
                        return entry;
                    }
                    for (int y = 0; y < 16; y++) {
                        int yBase = y << 8;
                        for (int z = 0; z < 16; z += 4) {
                            long word = 0L;
                            for (int dz = 0; dz < 4; dz++) {
                                int gz = z + dz;
                                int yzBase = yBase | (gz << 4);
                                int shiftBase = dz << 4;
                                for (int x = 0; x < 16; x++) {
                                    int id = storage.get(yzBase | x);
                                    boolean isOpaque;
                                    if (useTable) {
                                        if (id >= 0 && id < tableLimit) {
                                            isOpaque = table[id];
                                        } else {
                                            raced = true;
                                            isOpaque = false;
                                        }
                                    } else {
                                        isOpaque = isStateOpaque(palette.valueFor(id), solidLeavesMode);
                                    }
                                    if (isOpaque) {
                                        word |= (1L << (shiftBase | x));
                                        solidCount++;
                                    }
                                }
                            }
                            words[(y << 2) | (z >> 2)] = word;
                            if (word != 0L) {
                                layerMask |= (1 << y);
                            }
                        }
                    }
                }

                int postEpoch = hasDirectEpoch ? ((PapiOcclusionSection) section).papi$getOcclusionEpoch() : WORLD_BLOCK_EPOCH.get();
                if (raced || dataHandle.getVolatile(states) != rawData || postEpoch != secEpoch) {
                    return TRANSIENT_EMPTY_ENTRY;
                }

                entry.secKey = secKey;
                entry.validatedSeq = batchSeq;
                entry.observedEpoch = secEpoch;
                entry.expireTick = hasDirectEpoch ? Long.MAX_VALUE : gameTime + FALLBACK_SECTION_TTL_TICKS;
                entry.sectionRef = new WeakReference<>(section);
                if (solidCount == 0) {
                    entry.kind = KIND_EMPTY;
                    entry.nonEmptyYLayers = 0;
                } else if (solidCount == 4096) {
                    entry.kind = KIND_FULL;
                    entry.nonEmptyYLayers = (short) 0xFFFF;
                } else {
                    System.arraycopy(words, 0, entry.words, 0, 64);
                    entry.kind = KIND_SPARSE;
                    entry.nonEmptyYLayers = (short) layerMask;
                }
                return entry;
            } catch (Throwable ignored) {
                return TRANSIENT_EMPTY_ENTRY;
            }
        }

        private static long unpackNibbleLong(long r, int opaqueMask) {
            int b = ((opaqueMask >>> ((int) r & 15)) & 1)
                    | (((opaqueMask >>> ((int) (r >>> 4) & 15)) & 1) << 1)
                    | (((opaqueMask >>> ((int) (r >>> 8) & 15)) & 1) << 2)
                    | (((opaqueMask >>> ((int) (r >>> 12) & 15)) & 1) << 3)
                    | (((opaqueMask >>> ((int) (r >>> 16) & 15)) & 1) << 4)
                    | (((opaqueMask >>> ((int) (r >>> 20) & 15)) & 1) << 5)
                    | (((opaqueMask >>> ((int) (r >>> 24) & 15)) & 1) << 6)
                    | (((opaqueMask >>> ((int) (r >>> 28) & 15)) & 1) << 7)
                    | (((opaqueMask >>> ((int) (r >>> 32) & 15)) & 1) << 8)
                    | (((opaqueMask >>> ((int) (r >>> 36) & 15)) & 1) << 9)
                    | (((opaqueMask >>> ((int) (r >>> 40) & 15)) & 1) << 10)
                    | (((opaqueMask >>> ((int) (r >>> 44) & 15)) & 1) << 11)
                    | (((opaqueMask >>> ((int) (r >>> 48) & 15)) & 1) << 12)
                    | (((opaqueMask >>> ((int) (r >>> 52) & 15)) & 1) << 13)
                    | (((opaqueMask >>> ((int) (r >>> 56) & 15)) & 1) << 14)
                    | (((opaqueMask >>> ((int) (r >>> 60) & 15)) & 1) << 15);
            return b & 0xFFFFL;
        }
    }

    private static int cornersBlockedByVoxelMask(
            double cx, double cy, double cz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int ox, int oy, int oz
    ) {
        int maskOut = 0;
        for (int mask = 0; mask < 8; mask++) {
            double tx = (mask & 1) == 0 ? minX : maxX;
            double ty = (mask & 2) == 0 ? minY : maxY;
            double tz = (mask & 4) == 0 ? minZ : maxZ;
            if (voxelBlocksTargetPoint(cx, cy, cz, tx, ty, tz, ox, oy, oz)) {
                maskOut |= (1 << mask);
            }
        }
        return maskOut;
    }

    private static boolean allCornersAndCenterBlockedBySet(
            double cx, double cy, double cz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int ox, int oy, int oz,
            long[] extras, int extraOffset, int extraCount
    ) {
        double midX = 0.5 * (minX + maxX);
        double midY = 0.5 * (minY + maxY);
        double midZ = 0.5 * (minZ + maxZ);
        if (!isPointBlockedBySet(cx, cy, cz, midX, midY, midZ, ox, oy, oz, extras, extraOffset, extraCount)) {
            return false;
        }
        for (int mask = 0; mask < 8; mask++) {
            double tx = (mask & 1) == 0 ? minX : maxX;
            double ty = (mask & 2) == 0 ? minY : maxY;
            double tz = (mask & 4) == 0 ? minZ : maxZ;
            if (!isPointBlockedBySet(cx, cy, cz, tx, ty, tz, ox, oy, oz, extras, extraOffset, extraCount)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPointBlockedBySet(
            double cx, double cy, double cz,
            double tx, double ty, double tz,
            int ox, int oy, int oz,
            long[] extras, int extraOffset, int extraCount
    ) {
        if (voxelBlocksTargetPointEps(cx, cy, cz, tx, ty, tz, ox, oy, oz)) {
            return true;
        }
        for (int k = 0; k < extraCount; k++) {
            long p = extras[extraOffset + k];
            if (voxelBlocksTargetPointEps(cx, cy, cz, tx, ty, tz, BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) {
                return true;
            }
        }
        return false;
    }

    private static boolean voxelBlocksTargetPointEps(
            double cx, double cy, double cz,
            double tx, double ty, double tz,
            int ox, int oy, int oz
    ) {
        if (Mth.floor(tx) == ox && Mth.floor(ty) == oy && Mth.floor(tz) == oz) {
            return false;
        }
        return segmentHitsUnitBox(
                cx, cy, cz, tx, ty, tz,
                ox - 1.0E-5, oy - 1.0E-5, oz - 1.0E-5,
                ox + 1.00001, oy + 1.00001, oz + 1.00001
        );
    }

    private static boolean allCornersBlockedByVoxel(
            double cx, double cy, double cz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int ox, int oy, int oz
    ) {
        for (int mask = 0; mask < 8; mask++) {
            double tx = (mask & 1) == 0 ? minX : maxX;
            double ty = (mask & 2) == 0 ? minY : maxY;
            double tz = (mask & 4) == 0 ? minZ : maxZ;
            if (!voxelBlocksTargetPoint(cx, cy, cz, tx, ty, tz, ox, oy, oz)) {
                return false;
            }
        }
        return true;
    }

    private static boolean voxelBlocksTargetPoint(
            double cx, double cy, double cz,
            double tx, double ty, double tz,
            int ox, int oy, int oz
    ) {
        if (Mth.floor(tx) == ox && Mth.floor(ty) == oy && Mth.floor(tz) == oz) {
            return false;
        }
        return segmentHitsUnitBox(cx, cy, cz, tx, ty, tz, ox, oy, oz, ox + 1.0, oy + 1.0, oz + 1.0);
    }

    private static boolean segmentHitsUnitBox(
            double cx, double cy, double cz,
            double tx, double ty, double tz,
            double bMinX, double bMinY, double bMinZ,
            double bMaxX, double bMaxY, double bMaxZ
    ) {
        double dx = tx - cx;
        double dy = ty - cy;
        double dz = tz - cz;
        double tMin = 0.0;
        double tMax = 1.0;

        if (Math.abs(dx) < 1.0E-9) {
            if (cx < bMinX || cx > bMaxX) return false;
        } else {
            double inv = 1.0 / dx;
            double t1 = (bMinX - cx) * inv;
            double t2 = (bMaxX - cx) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dy) < 1.0E-9) {
            if (cy < bMinY || cy > bMaxY) return false;
        } else {
            double inv = 1.0 / dy;
            double t1 = (bMinY - cy) * inv;
            double t2 = (bMaxY - cy) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dz) < 1.0E-9) {
            if (cz < bMinZ || cz > bMaxZ) return false;
        } else {
            double inv = 1.0 / dz;
            double t1 = (bMinZ - cz) * inv;
            double t2 = (bMaxZ - cz) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        return tMax > 0.0 && tMin < 1.0;
    }

    private static boolean isStateOpaque(@Nullable BlockState st, boolean solidLeavesMode) {
        if (st == null) {
            return false;
        }
        if (st.isSolidRender() && st.canOcclude()) {
            return true;
        }
        return solidLeavesMode && st.getBlock() instanceof LeavesBlock;
    }

    private static boolean isLiveOpaque(ClientLevel level, BlockPos pos) {
        return isStateOpaque(level.getBlockState(pos), leavesOpaque);
    }

    private static int mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return (int) (z ^ (z >>> 31));
    }
}
