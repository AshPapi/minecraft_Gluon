package net.ashpapi.papioptimization.client.ebe;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT)
public final class PapiSodiumEbeBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/SodiumEBE");

    private static final class MeshBatch {
        final int epoch;
        final List<PapiEbeDynamicTracker> baked = new ArrayList<>(4);
        final List<PapiEbeDynamicTracker> unbaked = new ArrayList<>(4);

        MeshBatch(int epoch) {
            this.epoch = epoch;
        }

        boolean isEmpty() {
            return this.baked.isEmpty() && this.unbaked.isEmpty();
        }
    }

    private static final ThreadLocal<@Nullable MeshBatch> CURRENT_BATCH = new ThreadLocal<>();
    private static final Map<Object, MeshBatch> PENDING_OUTPUTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, List<PapiEbeDynamicTracker>> COMMITTED_BY_SECTION = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Long2ObjectOpenHashMap<List<PapiEbeDynamicTracker>> COMMITTED_BY_POS = new Long2ObjectOpenHashMap<>();

    private static volatile boolean initialized = false;
    private static volatile boolean ready = false;
    private static volatile MethodHandle cacheGetter;
    private static volatile MethodHandle getWorldSlice;
    private static volatile MethodHandle getBlockRenderer;
    private static volatile MethodHandle renderModel;
    private static volatile @Nullable MethodHandle getChunkX;
    private static volatile @Nullable MethodHandle getChunkY;
    private static volatile @Nullable MethodHandle getChunkZ;

    private PapiSodiumEbeBridge() {}

    public static void beginMeshTask(String backendName) {
        CURRENT_BATCH.remove();
        if (!initialized) {
            init(backendName);
        } else if (ready && !"disabled".equals(backendName)) {
            PapiEbeRuntime.markSectionCompilerHooked(backendName);
        }
    }

    public static void finishMeshTask(@Nullable Object buildOutput) {
        MeshBatch batch = CURRENT_BATCH.get();
        CURRENT_BATCH.remove();
        if (batch != null && !batch.isEmpty() && buildOutput != null && batch.epoch == PapiEbeRuntime.getReloadEpoch()) {
            PENDING_OUTPUTS.put(buildOutput, batch);
        }
    }

    private static long extractSectionKey(@Nullable Object section) {
        if (section == null) {
            return Long.MIN_VALUE;
        }
        MethodHandle hx = getChunkX;
        MethodHandle hy = getChunkY;
        MethodHandle hz = getChunkZ;
        if (hx == null || hy == null || hz == null) {
            return Long.MIN_VALUE;
        }
        try {
            int cx = (int) hx.invokeExact(section);
            int cy = (int) hy.invokeExact(section);
            int cz = (int) hz.invokeExact(section);
            return SectionPos.asLong(cx, cy, cz);
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    public static void commitBuildOutput(@Nullable Object section, @Nullable Object buildOutput) {
        long secKey = extractSectionKey(section);
        if (secKey != Long.MIN_VALUE) {
            List<PapiEbeDynamicTracker> prev;
            synchronized (COMMITTED_BY_POS) {
                prev = COMMITTED_BY_POS.remove(secKey);
            }
            if (prev != null) {
                for (int i = 0, n = prev.size(); i < n; i++) {
                    prev.get(i).papi$setEbeBaked(false);
                }
            }
        }
        if (section != null) {
            List<PapiEbeDynamicTracker> prev = COMMITTED_BY_SECTION.remove(section);
            if (prev != null) {
                for (int i = 0, n = prev.size(); i < n; i++) {
                    prev.get(i).papi$setEbeBaked(false);
                }
            }
        }
        if (buildOutput == null) {
            return;
        }
        MeshBatch batch = PENDING_OUTPUTS.remove(buildOutput);
        if (batch == null || batch.epoch != PapiEbeRuntime.getReloadEpoch()) {
            return;
        }
        List<PapiEbeDynamicTracker> liveBaked = null;
        for (int i = 0, n = batch.baked.size(); i < n; i++) {
            PapiEbeDynamicTracker tracker = batch.baked.get(i);
            PapiEbeRuntime.onCommittedBakedTracker(tracker);
            if (tracker.papi$isEbeBaked()) {
                if (liveBaked == null) {
                    liveBaked = new ArrayList<>(n);
                }
                liveBaked.add(tracker);
            }
        }
        for (int i = 0, n = batch.unbaked.size(); i < n; i++) {
            batch.unbaked.get(i).papi$setEbeBaked(false);
        }
        if (liveBaked != null && !liveBaked.isEmpty()) {
            if (secKey != Long.MIN_VALUE) {
                synchronized (COMMITTED_BY_POS) {
                    COMMITTED_BY_POS.put(secKey, liveBaked);
                }
            } else if (section != null) {
                COMMITTED_BY_SECTION.put(section, liveBaked);
            }
        }
    }

    public static void clearSection(int x, int y, int z) {
        long secKey = SectionPos.asLong(x, y, z);
        List<PapiEbeDynamicTracker> prev;
        synchronized (COMMITTED_BY_POS) {
            prev = COMMITTED_BY_POS.remove(secKey);
        }
        if (prev != null) {
            for (int i = 0, n = prev.size(); i < n; i++) {
                prev.get(i).papi$setEbeBaked(false);
            }
        }
        if (!COMMITTED_BY_SECTION.isEmpty()) {
            synchronized (COMMITTED_BY_SECTION) {
                var it = COMMITTED_BY_SECTION.entrySet().iterator();
                while (it.hasNext()) {
                    var entry = it.next();
                    if (extractSectionKey(entry.getKey()) == secKey) {
                        List<PapiEbeDynamicTracker> list = entry.getValue();
                        if (list != null) {
                            for (int i = 0, n = list.size(); i < n; i++) {
                                list.get(i).papi$setEbeBaked(false);
                            }
                        }
                        it.remove();
                    }
                }
            }
        }
    }

    public static void clearPending() {
        PENDING_OUTPUTS.clear();
        synchronized (COMMITTED_BY_POS) {
            for (List<PapiEbeDynamicTracker> list : COMMITTED_BY_POS.values()) {
                if (list != null) {
                    for (int i = 0, n = list.size(); i < n; i++) {
                        list.get(i).papi$setEbeBaked(false);
                    }
                }
            }
            COMMITTED_BY_POS.clear();
        }
        synchronized (COMMITTED_BY_SECTION) {
            for (List<PapiEbeDynamicTracker> list : COMMITTED_BY_SECTION.values()) {
                if (list != null) {
                    for (int i = 0, n = list.size(); i < n; i++) {
                        list.get(i).papi$setEbeBaked(false);
                    }
                }
            }
            COMMITTED_BY_SECTION.clear();
        }
    }

    private static MeshBatch getOrCreateBatch() {
        MeshBatch batch = CURRENT_BATCH.get();
        if (batch == null) {
            batch = new MeshBatch(PapiEbeRuntime.getReloadEpoch());
            CURRENT_BATCH.set(batch);
        }
        return batch;
    }

    public static synchronized boolean init(String backendName) {
        if (initialized) {
            if (ready) {
                PapiEbeRuntime.markSectionCompilerHooked(backendName);
            }
            return ready;
        }
        initialized = true;
        try {
            ClassLoader cl = PapiSodiumEbeBridge.class.getClassLoader();
            Class<?> buildCtxClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext", false, cl);
            Class<?> cacheClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache", false, cl);
            Class<?> levelSliceClass = Class.forName("net.caffeinemc.mods.sodium.client.world.LevelSlice", false, cl);
            Class<?> blockRendererClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", false, cl);

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle hCache = lookup.findGetter(buildCtxClass, "cache", cacheClass)
                    .asType(MethodType.methodType(Object.class, Object.class));
            MethodHandle hSlice = lookup.findVirtual(cacheClass, "getWorldSlice", MethodType.methodType(levelSliceClass))
                    .asType(MethodType.methodType(BlockGetter.class, Object.class));
            MethodHandle hRenderer = lookup.findVirtual(cacheClass, "getBlockRenderer", MethodType.methodType(blockRendererClass))
                    .asType(MethodType.methodType(Object.class, Object.class));
            MethodHandle hRender = lookup.findVirtual(
                            blockRendererClass,
                            "renderModel",
                            MethodType.methodType(void.class, BlockStateModel.class, BlockState.class, BlockPos.class, BlockPos.class)
                    )
                    .asType(MethodType.methodType(void.class, Object.class, BlockStateModel.class, BlockState.class, BlockPos.class, BlockPos.class));

            try {
                Class<?> renderSectionClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSection", false, cl);
                getChunkX = lookup.findVirtual(renderSectionClass, "getChunkX", MethodType.methodType(int.class))
                        .asType(MethodType.methodType(int.class, Object.class));
                getChunkY = lookup.findVirtual(renderSectionClass, "getChunkY", MethodType.methodType(int.class))
                        .asType(MethodType.methodType(int.class, Object.class));
                getChunkZ = lookup.findVirtual(renderSectionClass, "getChunkZ", MethodType.methodType(int.class))
                        .asType(MethodType.methodType(int.class, Object.class));
            } catch (Throwable ignored) {
                getChunkX = null;
                getChunkY = null;
                getChunkZ = null;
            }

            cacheGetter = hCache;
            getWorldSlice = hSlice;
            getBlockRenderer = hRenderer;
            renderModel = hRender;
            ready = true;
            PapiEbeRuntime.markSectionCompilerHooked(backendName);
            LOGGER.info("Gluon EBE adapter hooked into Sodium meshing pipeline (backend={}).", backendName);
            return true;
        } catch (Throwable t) {
            ready = false;
            LOGGER.warn("Gluon EBE Sodium/Vitrail adapter could not bind; keeping safe dynamic BER fallback.", t);
            return false;
        }
    }

    public static void bakeIntoSodium(
            Object buildContext,
            BlockPos blockPos,
            BlockPos modelOffset,
            BlockState blockState
    ) {
        if (!ready || !PapiEbeRuntime.isActive() || !PapiEbeRuntime.isBakeableBlock(blockState)) {
            return;
        }
        try {
            Object cache = cacheGetter.invokeExact(buildContext);
            BlockGetter slice = (BlockGetter) getWorldSlice.invokeExact(cache);
            if (slice == null) {
                return;
            }
            BlockEntity be = slice.getBlockEntity(blockPos);
            if (be == null || !PapiEbeRuntime.isBakeableBlockEntity(be, blockState)) {
                return;
            }
            PapiEbeDynamicTracker tracker = (be instanceof PapiEbeDynamicTracker t) ? t : null;
            if (PapiEbeRuntime.isDynamicNow(be, blockState, slice)
                    || (tracker != null && tracker.papi$wasEbeDynamic())) {
                if (tracker != null) {
                    getOrCreateBatch().unbaked.add(tracker);
                }
                return;
            }
            BlockStateModel staticModel = PapiEbeRuntime.getStaticModel(blockState, be);
            if (staticModel == null) {
                if (tracker != null) {
                    getOrCreateBatch().unbaked.add(tracker);
                }
                return;
            }
            Object renderer = getBlockRenderer.invokeExact(cache);
            renderModel.invokeExact(renderer, staticModel, blockState, blockPos, modelOffset);
            if (tracker != null) {
                getOrCreateBatch().baked.add(tracker);
            }
        } catch (Throwable t) {
            CURRENT_BATCH.remove();
            ready = false;
            PapiEbeRuntime.disable();
            LOGGER.warn("Gluon EBE Sodium/Vitrail bake failed; disabling EBE adapter safely.", t);
        }
    }
}
