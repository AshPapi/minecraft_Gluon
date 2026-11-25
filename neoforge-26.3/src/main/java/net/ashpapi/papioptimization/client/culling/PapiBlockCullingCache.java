package net.ashpapi.papioptimization.client.culling;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ashpapi.papioptimization.access.PapiCullableBlockState;
import net.ashpapi.papioptimization.config.LeavesCullingMode;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.BlockStateModelExtension;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT)
public final class PapiBlockCullingCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/BlockCulling");

    public static final int SHIFT_SUPPLY_NON_EMPTY = 6;
    public static final int SHIFT_SUPPLY_FULL = 12;
    public static final int FLAG_CANT_CULL = 1 << 18;
    public static final int FLAG_TRANSLUCENT = 1 << 19;
    public static final int FLAG_IS_LEAVES = 1 << 20;
    public static final int FLAG_FULL_SURFACE_GEOM = 1 << 21;
    public static final int FLAG_WATERLOGGED = 1 << 22;
    public static final int SHIFT_LEAF_DISTANCE = 23;
    public static final int MASK_LEAF_DISTANCE = 7;
    public static final int FLAG_FLAT_CARPET = 1 << 26;
    public static final int SHIFT_SNOW_LAYERS = 27;
    public static final int MASK_SNOW_LAYERS = 0xF;
    public static final int FLAG_READY = 1 << 31;

    private static final TagKey<Block> GLUON_DONT_CULL =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("gluon", "dont_cull"));
    private static final TagKey<Block> MORECULLING_DONT_CULL =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("moreculling", "dont_cull"));

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final long[] FULL_FACE_MASK = new long[]{-1L, -1L, -1L, -1L};

    private static final ThreadLocal<BlockPos.MutableBlockPos> WORKER_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private static final @Nullable Field DELEGATE_FIELD;
    private static final @Nullable Field WEIGHTED_LIST_FIELD;
    private static final @Nullable Field MULTIPART_SHARED_FIELD;
    private static final @Nullable Field MULTIPART_STATE_FIELD;
    private static final @Nullable Field MULTIPART_MODELS_FIELD;
    private static final @Nullable Method MULTIPART_SELECT_METHOD;

    static {
        Field del = null;
        Field wl = null;
        Field mShared = null;
        Field mState = null;
        Field mModels = null;
        Method mSelect = null;
        try {
            del = DelegateBlockStateModel.class.getDeclaredField("delegate");
            del.setAccessible(true);
        } catch (Throwable ignored) {
        }
        try {
            wl = WeightedVariants.class.getDeclaredField("list");
            wl.setAccessible(true);
        } catch (Throwable ignored) {
        }
        try {
            mShared = MultiPartModel.class.getDeclaredField("shared");
            mShared.setAccessible(true);
            mState = MultiPartModel.class.getDeclaredField("blockState");
            mState.setAccessible(true);
            mModels = MultiPartModel.class.getDeclaredField("models");
            mModels.setAccessible(true);
            mSelect = mShared.getType().getDeclaredMethod("selectModels", BlockState.class);
            mSelect.setAccessible(true);
        } catch (Throwable ignored) {
        }
        DELEGATE_FIELD = del;
        WEIGHTED_LIST_FIELD = wl;
        MULTIPART_SHARED_FIELD = mShared;
        MULTIPART_STATE_FIELD = mState;
        MULTIPART_MODELS_FIELD = mModels;
        MULTIPART_SELECT_METHOD = mSelect;
    }

    private static final ClassValue<Boolean> DYNAMIC_MODEL_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> cls) {
            if (cls == SingleVariant.class || cls == WeightedVariants.class || cls == MultiPartModel.class) {
                return Boolean.FALSE;
            }
            try {
                Class<?> declaring = cls.getMethod(
                        "collectParts",
                        BlockAndTintGetter.class,
                        BlockPos.class,
                        BlockState.class,
                        RandomSource.class,
                        List.class
                ).getDeclaringClass();
                if (declaring == BlockStateModelExtension.class
                        || declaring == DynamicBlockStateModel.class
                        || declaring == DelegateBlockStateModel.class) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            } catch (Throwable ignored) {
                return Boolean.TRUE;
            }
        }
    };

    private static volatile boolean cutoutLeaves = true;
    private static volatile boolean builtOnce = false;
    private static volatile int lastSyncedTagEpoch = -1;

    private PapiBlockCullingCache() {}

    public static void setCutoutLeaves(boolean cutout) {
        cutoutLeaves = cutout;
    }

    public static boolean isCutoutLeaves() {
        return cutoutLeaves;
    }

    public static boolean isFullLeaves(BlockState state) {
        return state instanceof PapiCullableBlockState ext && (ext.papi$getCullFlags() & FLAG_IS_LEAVES) != 0;
    }

    public static void rebuild(ModelManager modelManager) {
        PapiConfig.ensureLoaded();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            cutoutLeaves = mc.options.cutoutLeaves().get();
        }
        BlockState probe = Block.BLOCK_STATE_REGISTRY.byId(0);
        if (!(probe instanceof PapiCullableBlockState)) {
            builtOnce = false;
            return;
        }
        BlockStateModelSet modelSet = modelManager.getBlockStateModelSet();
        if (modelSet == null) {
            return;
        }
        BlockStateModel missingModel = modelManager.getMissingModels() != null
                ? modelManager.getMissingModels().block()
                : null;

        Set<String> blacklist = PapiConfig.blockCullBlacklistSet();
        boolean cullModded = PapiConfig.cullModdedBlocksByDefault();

        Map<MaskKey, long[]> maskInterner = new HashMap<>(256);
        maskInterner.put(new MaskKey(FULL_FACE_MASK), FULL_FACE_MASK);
        Map<TableKey, long[][]> tableInterner = new HashMap<>(512);

        RandomSource scratchRandom = new SingleThreadedRandomSource(42L);
        List<BlockStateModelPart> scratchParts = new ArrayList<>(8);
        EvalResult eval = new EvalResult();

        int currentTagEpoch = PapiTagEpoch.current();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            PapiCullableBlockState ext = (PapiCullableBlockState) state;
            Block block = state.getBlock();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            String idStr = blockId != null ? blockId.toString() : "minecraft:air";

            boolean configBlocked = blacklist.contains(idStr)
                    || (!cullModded && blockId != null && !"minecraft".equals(blockId.getNamespace()));
            boolean tagBlocked = isTagDontCull(state);

            int propFlags = computePropertyFlags(state, block);

            if (state.isAir()) {
                ext.papi$setCullData(propFlags, null);
                continue;
            }

            BlockStateModel rootModel = modelSet.get(state);
            if (rootModel == null || rootModel == missingModel) {
                ext.papi$setCullData(FLAG_CANT_CULL | propFlags, null);
                continue;
            }

            eval.reset();
            evaluateModel(rootModel, state, scratchRandom, scratchParts, eval, 0);

            if (eval.dynamic) {
                ext.papi$setCullData(FLAG_CANT_CULL | propFlags, null);
                continue;
            }

            boolean fullGeom6 = eval.fullSurfaceGeomMask == 0x3F;
            if (fullGeom6) {
                propFlags |= FLAG_FULL_SURFACE_GEOM;
            }
            if (isEligibleLeafBlock(state, block, fullGeom6) && !configBlocked && !tagBlocked) {
                propFlags |= FLAG_IS_LEAVES;
            }

            if (eval.translucent) {
                propFlags |= FLAG_TRANSLUCENT;
            }
            if (configBlocked || tagBlocked) {
                propFlags |= FLAG_CANT_CULL;
            }

            boolean isWallTorch = block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock;
            if (isWallTorch) {
                eval.demandValidMask = 0;
            }

            if (!eval.translucent) {
                Class<?> cls = block.getClass();
                if (cls == CarpetBlock.class
                        || cls == WoolCarpetBlock.class
                        || (block instanceof MossyCarpetBlock
                                && state.hasProperty(MossyCarpetBlock.BASE)
                                && state.getValue(MossyCarpetBlock.BASE))) {
                    propFlags |= FLAG_FLAT_CARPET;
                }
                if (block instanceof SnowLayerBlock && state.hasProperty(SnowLayerBlock.LAYERS)) {
                    int layers = state.getValue(SnowLayerBlock.LAYERS);
                    propFlags |= (layers & MASK_SNOW_LAYERS) << SHIFT_SNOW_LAYERS;
                }
            }

            boolean suppressSupply = eval.translucent || block instanceof PowderSnowBlock;

            int supplyFullBits = 0;
            int supplyNonEmptyBits = 0;
            int demandValidBits = 0;
            long[][] rawTable = null;

            for (int d = 0; d < 6; d++) {
                if ((eval.demandValidMask & (1 << d)) != 0 && !isMaskZero(eval.demand[d])) {
                    demandValidBits |= (1 << d);
                    if (rawTable == null) {
                        rawTable = new long[12][];
                    }
                    rawTable[d] = internMask(maskInterner, eval.demand[d]);
                }
                if (!suppressSupply && !isMaskZero(eval.supply[d])) {
                    boolean isFull = isMaskFull(eval.supply[d]);
                    if (isFull && !state.canOcclude() && eval.fullSupplyCount == 6) {
                        continue;
                    }
                    supplyNonEmptyBits |= (1 << d);
                    if (isFull) {
                        supplyFullBits |= (1 << d);
                    }
                    if (rawTable == null) {
                        rawTable = new long[12][];
                    }
                    rawTable[6 + d] = isFull ? FULL_FACE_MASK : internMask(maskInterner, eval.supply[d]);
                }
            }

            long[][] internedTable = null;
            if (rawTable != null) {
                TableKey tk = new TableKey(rawTable);
                internedTable = tableInterner.get(tk);
                if (internedTable == null) {
                    tableInterner.put(tk, rawTable);
                    internedTable = rawTable;
                }
            }

            int finalFlags = FLAG_READY
                    | propFlags
                    | demandValidBits
                    | (supplyNonEmptyBits << SHIFT_SUPPLY_NON_EMPTY)
                    | (supplyFullBits << SHIFT_SUPPLY_FULL);
            ext.papi$setCullData(finalFlags, internedTable);
        }

        lastSyncedTagEpoch = currentTagEpoch;
        builtOnce = true;
    }

    public static void syncTagsIfNeeded() {
        if (!builtOnce) {
            return;
        }
        int curEpoch = PapiTagEpoch.current();
        if (curEpoch == lastSyncedTagEpoch) {
            return;
        }
        lastSyncedTagEpoch = curEpoch;
        Set<String> blacklist = PapiConfig.blockCullBlacklistSet();
        boolean cullModded = PapiConfig.cullModdedBlocksByDefault();

        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            if (state.isAir() || !(state instanceof PapiCullableBlockState ext)) {
                continue;
            }
            int flags = ext.papi$getCullFlags();
            if ((flags & FLAG_READY) == 0) {
                continue;
            }
            Block block = state.getBlock();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            String idStr = blockId != null ? blockId.toString() : "minecraft:air";
            boolean blocked = blacklist.contains(idStr)
                    || (!cullModded && blockId != null && !"minecraft".equals(blockId.getNamespace()))
                    || isTagDontCull(state);

            if (blocked) {
                flags |= FLAG_CANT_CULL;
            } else {
                flags &= ~FLAG_CANT_CULL;
            }
            boolean fullGeom6 = (flags & FLAG_FULL_SURFACE_GEOM) != 0;
            if (isEligibleLeafBlock(state, block, fullGeom6) && !blocked) {
                flags |= FLAG_IS_LEAVES;
            } else {
                flags &= ~FLAG_IS_LEAVES;
            }
            ext.papi$setCullFlags(flags);
        }
    }

    public static boolean shouldSkipFaceLocal(
            PapiCullableBlockState selfExt,
            BlockState selfState,
            BlockState adjState,
            Direction dir
    ) {
        int sFlags = selfExt.papi$getCullFlags();
        if ((sFlags & FLAG_READY) == 0 || (sFlags & FLAG_CANT_CULL) != 0) {
            return false;
        }
        if (!(adjState instanceof PapiCullableBlockState adjExt)) {
            return false;
        }
        int aFlags = adjExt.papi$getCullFlags();
        if ((aFlags & FLAG_CANT_CULL) != 0) {
            return false;
        }

        if (dir.getAxis().isHorizontal()) {
            int sSnow = (sFlags >>> SHIFT_SNOW_LAYERS) & MASK_SNOW_LAYERS;
            if (sSnow != 0) {
                if (selfState == adjState) {
                    return true;
                }
                int aSnow = (aFlags >>> SHIFT_SNOW_LAYERS) & MASK_SNOW_LAYERS;
                if (aSnow >= sSnow) {
                    return true;
                }
            }
            if (((sFlags & aFlags) & FLAG_FLAT_CARPET) != 0) {
                return true;
            }
        }

        if (((sFlags & aFlags) & FLAG_IS_LEAVES) != 0 && ((sFlags ^ aFlags) & FLAG_WATERLOGGED) == 0) {
            if (!cutoutLeaves || PapiConfig.rawSolidLeaves()) {
                return true;
            }
            LeavesCullingMode mode = PapiConfig.rawLeavesCullingMode();
            if (mode == LeavesCullingMode.FAST) {
                return true;
            }
            if (mode == LeavesCullingMode.VERTICAL) {
                return dir.getAxis() == Direction.Axis.Y;
            }
            if (mode == LeavesCullingMode.STATE) {
                int sDist = (sFlags >>> SHIFT_LEAF_DISTANCE) & MASK_LEAF_DISTANCE;
                int aDist = (aFlags >>> SHIFT_LEAF_DISTANCE) & MASK_LEAF_DISTANCE;
                return aDist > 0 && aDist < sDist;
            }
        }

        if (!PapiConfig.rawBlockStateCulling()) {
            return false;
        }

        int dIdx = dir.get3DDataValue();
        if ((sFlags & (1 << dIdx)) == 0) {
            return false;
        }
        int oIdx = dIdx ^ 1;
        if ((aFlags & (1 << (SHIFT_SUPPLY_NON_EMPTY + oIdx))) == 0) {
            return false;
        }
        if ((aFlags & (1 << (SHIFT_SUPPLY_FULL + oIdx))) != 0) {
            return true;
        }

        long[] sMask = selfExt.papi$getFaceMask(dIdx);
        long[] aMask = adjExt.papi$getFaceMask(6 + oIdx);
        if (sMask == null || aMask == null) {
            return false;
        }
        return ((sMask[0] & ~aMask[0])
                | (sMask[1] & ~aMask[1])
                | (sMask[2] & ~aMask[2])
                | (sMask[3] & ~aMask[3])) == 0L;
    }

    public static boolean needsPositionalLeafCheck(BlockState selfState, BlockState adjState) {
        if (!cutoutLeaves || PapiConfig.rawSolidLeaves()) {
            return false;
        }
        LeavesCullingMode mode = PapiConfig.rawLeavesCullingMode();
        if (mode != LeavesCullingMode.DEPTH && mode != LeavesCullingMode.CHECK) {
            return false;
        }
        if (!(selfState instanceof PapiCullableBlockState selfExt) || !(adjState instanceof PapiCullableBlockState adjExt)) {
            return false;
        }
        int sFlags = selfExt.papi$getCullFlags();
        int aFlags = adjExt.papi$getCullFlags();
        if ((sFlags & FLAG_IS_LEAVES) != 0) {
            return (aFlags & FLAG_IS_LEAVES) != 0 && ((sFlags ^ aFlags) & FLAG_WATERLOGGED) == 0;
        }
        if (PapiConfig.rawIncludeMangroveRoots() && selfState.getBlock() instanceof MangroveRootsBlock) {
            return ((aFlags & FLAG_IS_LEAVES) != 0 || adjState.getBlock() instanceof MangroveRootsBlock)
                    && ((sFlags ^ aFlags) & FLAG_WATERLOGGED) == 0;
        }
        return false;
    }

    public static byte checkPositionalLeaves(
            BlockGetter level,
            int x,
            int y,
            int z,
            BlockState selfState,
            BlockState adjState,
            Direction dir
    ) {
        if (!(selfState instanceof PapiCullableBlockState selfExt)) {
            return -1;
        }
        LeavesCullingMode mode = PapiConfig.rawLeavesCullingMode();
        int selfWater = selfExt.papi$getCullFlags() & FLAG_WATERLOGGED;
        boolean allowRoots = PapiConfig.rawIncludeMangroveRoots() && selfState.getBlock() instanceof MangroveRootsBlock;
        BlockPos.MutableBlockPos mpos = WORKER_POS.get();

        if (mode == LeavesCullingMode.DEPTH) {
            int depth = PapiConfig.rawLeavesCullingDepth();
            int dx = dir.getStepX();
            int dy = dir.getStepY();
            int dz = dir.getStepZ();
            for (int k = 2; k <= depth; k++) {
                mpos.set(x + dx * k, y + dy * k, z + dz * k);
                BlockState stepState = level.getBlockState(mpos);
                if (!isValidCanopyCover(stepState, selfWater, allowRoots)) {
                    return -1;
                }
            }
            return 0;
        }

        if (mode == LeavesCullingMode.CHECK) {
            int ax = x + dir.getStepX();
            int ay = y + dir.getStepY();
            int az = z + dir.getStepZ();
            Direction opp = dir.getOpposite();
            for (int i = 0; i < 6; i++) {
                Direction d = DIRECTIONS[i];
                if (d == opp) {
                    continue;
                }
                mpos.set(ax + d.getStepX(), ay + d.getStepY(), az + d.getStepZ());
                BlockState neighbor = level.getBlockState(mpos);
                if (!isValidCanopyCover(neighbor, selfWater, allowRoots)) {
                    return -1;
                }
            }
            return 0;
        }

        return -1;
    }

    private static boolean isValidCanopyCover(BlockState state, int requiredWaterBit, boolean allowRoots) {
        if (state.isAir()) {
            return false;
        }
        if (state instanceof PapiCullableBlockState ext) {
            int flags = ext.papi$getCullFlags();
            if ((flags & FLAG_IS_LEAVES) != 0) {
                return (flags & FLAG_WATERLOGGED) == requiredWaterBit;
            }
            if (allowRoots && state.getBlock() instanceof MangroveRootsBlock) {
                return (flags & FLAG_WATERLOGGED) == requiredWaterBit;
            }
        }
        return state.canOcclude() && state.isSolidRender();
    }

    private static int computePropertyFlags(BlockState state, Block block) {
        int flags = 0;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            flags |= FLAG_WATERLOGGED;
        }
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            int dist = Math.min(MASK_LEAF_DISTANCE, Math.max(0, state.getValue(LeavesBlock.DISTANCE)));
            flags |= dist << SHIFT_LEAF_DISTANCE;
        }
        return flags;
    }

    private static boolean isTagDontCull(BlockState state) {
        try {
            return state.is(GLUON_DONT_CULL) || state.is(MORECULLING_DONT_CULL);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isEligibleLeafBlock(BlockState state, Block block, boolean fullGeom6) {
        if (!fullGeom6 || block instanceof MangroveRootsBlock) {
            return false;
        }
        if (block instanceof LeavesBlock) {
            return true;
        }
        try {
            return state.is(BlockTags.LEAVES);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void evaluateModel(
            BlockStateModel model,
            BlockState state,
            RandomSource random,
            List<BlockStateModelPart> scratchParts,
            EvalResult out,
            int depth
    ) {
        if (model == null || depth > 16) {
            out.dynamic = true;
            return;
        }
        Class<?> cls = model.getClass();
        if (DYNAMIC_MODEL_CLASS.get(cls)) {
            out.dynamic = true;
            return;
        }

        if (model instanceof DelegateBlockStateModel) {
            if (DELEGATE_FIELD == null) {
                out.dynamic = true;
                return;
            }
            try {
                BlockStateModel inner = (BlockStateModel) DELEGATE_FIELD.get(model);
                evaluateModel(inner, state, random, scratchParts, out, depth + 1);
            } catch (Throwable ignored) {
                out.dynamic = true;
            }
            return;
        }

        if (cls == SingleVariant.class) {
            scratchParts.clear();
            random.setSeed(42L);
            model.collectParts(random, scratchParts);
            for (int i = 0, n = scratchParts.size(); i < n; i++) {
                evaluatePartInto(scratchParts.get(i), out);
            }
            out.updateFullSupplyCount();
            return;
        }

        if (cls == WeightedVariants.class) {
            if (WEIGHTED_LIST_FIELD == null) {
                out.dynamic = true;
                return;
            }
            try {
                WeightedList<BlockStateModel> wlist = (WeightedList<BlockStateModel>) WEIGHTED_LIST_FIELD.get(model);
                List<Weighted<BlockStateModel>> entries = wlist.unwrap();
                if (entries.isEmpty()) {
                    return;
                }
                EvalResult first = new EvalResult();
                evaluateModel(entries.get(0).value(), state, random, scratchParts, first, depth + 1);
                if (first.dynamic) {
                    out.dynamic = true;
                    return;
                }
                if (entries.size() == 1) {
                    out.copyFrom(first);
                    return;
                }
                EvalResult sub = new EvalResult();
                for (int i = 1, n = entries.size(); i < n; i++) {
                    sub.reset();
                    evaluateModel(entries.get(i).value(), state, random, scratchParts, sub, depth + 1);
                    if (sub.dynamic) {
                        out.dynamic = true;
                        return;
                    }
                    first.intersectSupplyUnionDemand(sub);
                }
                first.updateFullSupplyCount();
                out.copyFrom(first);
            } catch (Throwable ignored) {
                out.dynamic = true;
            }
            return;
        }

        if (cls == MultiPartModel.class) {
            if (MULTIPART_SHARED_FIELD == null || MULTIPART_MODELS_FIELD == null || MULTIPART_SELECT_METHOD == null || MULTIPART_STATE_FIELD == null) {
                out.dynamic = true;
                return;
            }
            try {
                List<BlockStateModel> subModels = (List<BlockStateModel>) MULTIPART_MODELS_FIELD.get(model);
                if (subModels == null) {
                    Object shared = MULTIPART_SHARED_FIELD.get(model);
                    BlockState mpState = (BlockState) MULTIPART_STATE_FIELD.get(model);
                    subModels = (List<BlockStateModel>) MULTIPART_SELECT_METHOD.invoke(shared, mpState != null ? mpState : state);
                    MULTIPART_MODELS_FIELD.set(model, subModels);
                }
                if (subModels.isEmpty()) {
                    return;
                }
                EvalResult sub = new EvalResult();
                boolean[] faceHasAnyDemand = new boolean[6];
                int demandInvalidBits = 0;

                for (int i = 0, n = subModels.size(); i < n; i++) {
                    sub.reset();
                    evaluateModel(subModels.get(i), state, random, scratchParts, sub, depth + 1);
                    if (sub.dynamic) {
                        out.dynamic = true;
                        return;
                    }
                    out.translucent |= sub.translucent;
                    out.fullSurfaceGeomMask |= sub.fullSurfaceGeomMask;
                    for (int d = 0; d < 6; d++) {
                        orMask(out.supply[d], sub.supply[d]);
                        if (sub.faceHadQuads[d]) {
                            faceHasAnyDemand[d] = true;
                            if ((sub.demandValidMask & (1 << d)) == 0) {
                                demandInvalidBits |= (1 << d);
                            } else {
                                orMask(out.demand[d], sub.demand[d]);
                            }
                        }
                    }
                }
                int validBits = 0;
                for (int d = 0; d < 6; d++) {
                    if (faceHasAnyDemand[d] && (demandInvalidBits & (1 << d)) == 0) {
                        validBits |= (1 << d);
                    }
                }
                out.demandValidMask = validBits;
                for (int d = 0; d < 6; d++) {
                    out.faceHadQuads[d] = faceHasAnyDemand[d];
                }
                out.updateFullSupplyCount();
            } catch (Throwable ignored) {
                out.dynamic = true;
            }
            return;
        }

        out.dynamic = true;
    }

    private static void evaluatePartInto(BlockStateModelPart part, EvalResult out) {
        List<BakedQuad> unculled = part.getQuads(null);
        for (int i = 0, n = unculled.size(); i < n; i++) {
            if (isQuadTranslucent(unculled.get(i))) {
                out.translucent = true;
                break;
            }
        }

        for (int d = 0; d < 6; d++) {
            Direction dir = DIRECTIONS[d];
            List<BakedQuad> quads = part.getQuads(dir);
            int qSize = quads.size();
            if (qSize == 0) {
                continue;
            }
            boolean previouslyInvalid = out.faceHadQuads[d] && (out.demandValidMask & (1 << d)) == 0;
            out.faceHadQuads[d] = true;
            boolean allFlushAndBounded = !previouslyInvalid;
            long[] geomUnion = new long[4];

            for (int qIdx = 0; qIdx < qSize; qIdx++) {
                BakedQuad quad = quads.get(qIdx);
                boolean qTrans = isQuadTranslucent(quad);
                if (qTrans) {
                    out.translucent = true;
                }
                if (!analyzeFaceQuad(quad, dir, out.demand[d], qTrans ? null : out.supply[d], geomUnion)) {
                    allFlushAndBounded = false;
                }
            }

            if (allFlushAndBounded) {
                out.demandValidMask |= (1 << d);
            } else {
                out.demandValidMask &= ~(1 << d);
            }
            if (isMaskFull(geomUnion)) {
                out.fullSurfaceGeomMask |= (1 << d);
            }
        }
    }

    private static boolean isQuadTranslucent(BakedQuad quad) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        if ((info.flags() & BakedQuad.FLAG_TRANSLUCENT) != 0) {
            return true;
        }
        return PapiSpriteOpacityTracker.hasTranslucentPixels(info.sprite());
    }

    private static boolean analyzeFaceQuad(
            BakedQuad quad,
            Direction face,
            long[] demandAcc,
            long @Nullable [] supplyAcc,
            long[] geomUnionAcc
    ) {
        Vector3fc p0 = quad.position0();
        Vector3fc p1 = quad.position1();
        Vector3fc p2 = quad.position2();
        Vector3fc p3 = quad.position3();

        float targetPlane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F : 0.0F;
        float n0 = axisCoord(p0, face);
        float n1 = axisCoord(p1, face);
        float n2 = axisCoord(p2, face);
        float n3 = axisCoord(p3, face);

        if (Math.abs(n0 - targetPlane) > 1.5E-3F
                || Math.abs(n1 - targetPlane) > 1.5E-3F
                || Math.abs(n2 - targetPlane) > 1.5E-3F
                || Math.abs(n3 - targetPlane) > 1.5E-3F) {
            return false;
        }

        float tx0 = tangentU(p0, face);
        float tx1 = tangentU(p1, face);
        float tx2 = tangentU(p2, face);
        float tx3 = tangentU(p3, face);

        float ty0 = tangentV(p0, face);
        float ty1 = tangentV(p1, face);
        float ty2 = tangentV(p2, face);
        float ty3 = tangentV(p3, face);

        float minX = Math.min(Math.min(tx0, tx1), Math.min(tx2, tx3));
        float maxX = Math.max(Math.max(tx0, tx1), Math.max(tx2, tx3));
        float minY = Math.min(Math.min(ty0, ty1), Math.min(ty2, ty3));
        float maxY = Math.max(Math.max(ty0, ty1), Math.max(ty2, ty3));

        if (minX < -1.5E-3F || maxX > 1.0015F || minY < -1.5E-3F || maxY > 1.0015F) {
            return false;
        }

        float spanX = maxX - minX;
        float spanY = maxY - minY;
        if (spanX <= 1.0E-4F || spanY <= 1.0E-4F) {
            return true;
        }

        int dx0 = Mth.clamp(Mth.floor((minX + 1.0E-4F) * 16.0F), 0, 15);
        int dx1 = Mth.clamp(Mth.ceil((maxX - 1.0E-4F) * 16.0F) - 1, dx0, 15);
        int dy0 = Mth.clamp(Mth.floor((minY + 1.0E-4F) * 16.0F), 0, 15);
        int dy1 = Mth.clamp(Mth.ceil((maxY - 1.0E-4F) * 16.0F) - 1, dy0, 15);
        fillRectBits(demandAcc, dx0, dy0, dx1, dy1);

        boolean isAxisRect = isCornerVertex(tx0, ty0, minX, maxX, minY, maxY)
                && isCornerVertex(tx1, ty1, minX, maxX, minY, maxY)
                && isCornerVertex(tx2, ty2, minX, maxX, minY, maxY)
                && isCornerVertex(tx3, ty3, minX, maxX, minY, maxY);
        if (!isAxisRect) {
            return true;
        }

        int sx0 = Mth.clamp(Mth.ceil((minX - 1.0E-4F) * 16.0F), 0, 16);
        int sx1 = Mth.clamp(Mth.floor((maxX + 1.0E-4F) * 16.0F) - 1, -1, 15);
        int sy0 = Mth.clamp(Mth.ceil((minY - 1.0E-4F) * 16.0F), 0, 16);
        int sy1 = Mth.clamp(Mth.floor((maxY + 1.0E-4F) * 16.0F) - 1, -1, 15);
        if (sx0 > sx1 || sy0 > sy1) {
            return true;
        }

        fillRectBits(geomUnionAcc, sx0, sy0, sx1, sy1);

        if (supplyAcc == null) {
            return true;
        }

        TextureAtlasSprite sprite = quad.materialInfo().sprite();
        long puv0 = quad.packedUV0();
        long puv1 = quad.packedUV1();
        long puv2 = quad.packedUV2();
        long puv3 = quad.packedUV3();

        float qu0 = UVPair.unpackU(puv0);
        float qv0 = UVPair.unpackV(puv0);
        float qu1 = UVPair.unpackU(puv1);
        float qv1 = UVPair.unpackV(puv1);
        float qu2 = UVPair.unpackU(puv2);
        float qv2 = UVPair.unpackV(puv2);
        float qu3 = UVPair.unpackU(puv3);
        float qv3 = UVPair.unpackV(puv3);

        float minU = Math.min(Math.min(qu0, qu1), Math.min(qu2, qu3));
        float maxU = Math.max(Math.max(qu0, qu1), Math.max(qu2, qu3));
        float minV = Math.min(Math.min(qv0, qv1), Math.min(qv2, qv3));
        float maxV = Math.max(Math.max(qv0, qv1), Math.max(qv2, qv3));

        if (PapiSpriteOpacityTracker.isUvBoxOpaque(sprite, minU, minV, maxU, maxV)) {
            fillRectBits(supplyAcc, sx0, sy0, sx1, sy1);
            return true;
        }

        float[] txArr = new float[]{tx0, tx1, tx2, tx3};
        float[] tyArr = new float[]{ty0, ty1, ty2, ty3};
        float[] uArr = new float[]{qu0, qu1, qu2, qu3};
        float[] vArr = new float[]{qv0, qv1, qv2, qv3};

        int i00 = closestCornerIndex(txArr, tyArr, minX, minY);
        int i10 = closestCornerIndex(txArr, tyArr, maxX, minY);
        int i01 = closestCornerIndex(txArr, tyArr, minX, maxY);
        int i11 = closestCornerIndex(txArr, tyArr, maxX, maxY);

        for (int gy = sy0; gy <= sy1; gy++) {
            float fyA = (((gy + 0.2F) / 16.0F) - minY) / spanY;
            float fyB = (((gy + 0.8F) / 16.0F) - minY) / spanY;
            for (int gx = sx0; gx <= sx1; gx++) {
                float fxA = (((gx + 0.2F) / 16.0F) - minX) / spanX;
                float fxB = (((gx + 0.8F) / 16.0F) - minX) / spanX;
                if (sampleCornerOpaque(sprite, fxA, fyA, uArr, vArr, i00, i10, i01, i11)
                        && sampleCornerOpaque(sprite, fxB, fyA, uArr, vArr, i00, i10, i01, i11)
                        && sampleCornerOpaque(sprite, fxA, fyB, uArr, vArr, i00, i10, i01, i11)
                        && sampleCornerOpaque(sprite, fxB, fyB, uArr, vArr, i00, i10, i01, i11)) {
                    int bitIdx = (gy << 4) | gx;
                    supplyAcc[bitIdx >>> 6] |= (1L << (bitIdx & 63));
                }
            }
        }
        return true;
    }

    private static boolean sampleCornerOpaque(
            TextureAtlasSprite sprite,
            float sx,
            float sy,
            float[] uArr,
            float[] vArr,
            int i00,
            int i10,
            int i01,
            int i11
    ) {
        float uTop = Mth.lerp(sx, uArr[i00], uArr[i10]);
        float uBot = Mth.lerp(sx, uArr[i01], uArr[i11]);
        float u = Mth.lerp(sy, uTop, uBot);

        float vTop = Mth.lerp(sx, vArr[i00], vArr[i10]);
        float vBot = Mth.lerp(sx, vArr[i01], vArr[i11]);
        float v = Mth.lerp(sy, vTop, vBot);

        return PapiSpriteOpacityTracker.isUvPointOpaque(sprite, u, v);
    }

    private static int closestCornerIndex(float[] tx, float[] ty, float cx, float cy) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float dx = tx[i] - cx;
            float dy = ty[i] - cy;
            float d = dx * dx + dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static boolean isCornerVertex(float x, float y, float minX, float maxX, float minY, float maxY) {
        boolean xOk = Math.abs(x - minX) <= 1.5E-3F || Math.abs(x - maxX) <= 1.5E-3F;
        boolean yOk = Math.abs(y - minY) <= 1.5E-3F || Math.abs(y - maxY) <= 1.5E-3F;
        return xOk && yOk;
    }

    private static float axisCoord(Vector3fc v, Direction face) {
        return switch (face.getAxis()) {
            case X -> v.x();
            case Y -> v.y();
            case Z -> v.z();
        };
    }

    private static float tangentU(Vector3fc v, Direction face) {
        return switch (face.getAxis()) {
            case X -> v.z();
            case Y, Z -> v.x();
        };
    }

    private static float tangentV(Vector3fc v, Direction face) {
        return switch (face.getAxis()) {
            case Y -> v.z();
            case X, Z -> v.y();
        };
    }

    private static void fillRectBits(long[] words, int x0, int y0, int x1, int y1) {
        long rowMask = ((1L << (x1 - x0 + 1)) - 1L) << x0;
        for (int y = y0; y <= y1; y++) {
            int wordIdx = y >>> 2;
            int shift = (y & 3) << 4;
            words[wordIdx] |= (rowMask << shift);
        }
    }

    private static boolean isMaskZero(long[] m) {
        return (m[0] | m[1] | m[2] | m[3]) == 0L;
    }

    private static boolean isMaskFull(long[] m) {
        return (m[0] & m[1] & m[2] & m[3]) == -1L;
    }

    private static void orMask(long[] dst, long[] src) {
        dst[0] |= src[0];
        dst[1] |= src[1];
        dst[2] |= src[2];
        dst[3] |= src[3];
    }

    private static void andMask(long[] dst, long[] src) {
        dst[0] &= src[0];
        dst[1] &= src[1];
        dst[2] &= src[2];
        dst[3] &= src[3];
    }

    private static long[] internMask(Map<MaskKey, long[]> interner, long[] src) {
        MaskKey key = new MaskKey(src);
        long[] existing = interner.get(key);
        if (existing != null) {
            return existing;
        }
        long[] copy = Arrays.copyOf(src, 4);
        interner.put(new MaskKey(copy), copy);
        return copy;
    }

    private static final class EvalResult {
        final long[][] demand = new long[6][4];
        final long[][] supply = new long[6][4];
        final boolean[] faceHadQuads = new boolean[6];
        int demandValidMask;
        int fullSurfaceGeomMask;
        int fullSupplyCount;
        boolean translucent;
        boolean dynamic;

        void reset() {
            for (int d = 0; d < 6; d++) {
                Arrays.fill(this.demand[d], 0L);
                Arrays.fill(this.supply[d], 0L);
                this.faceHadQuads[d] = false;
            }
            this.demandValidMask = 0;
            this.fullSurfaceGeomMask = 0;
            this.fullSupplyCount = 0;
            this.translucent = false;
            this.dynamic = false;
        }

        void copyFrom(EvalResult other) {
            for (int d = 0; d < 6; d++) {
                System.arraycopy(other.demand[d], 0, this.demand[d], 0, 4);
                System.arraycopy(other.supply[d], 0, this.supply[d], 0, 4);
                this.faceHadQuads[d] = other.faceHadQuads[d];
            }
            this.demandValidMask = other.demandValidMask;
            this.fullSurfaceGeomMask = other.fullSurfaceGeomMask;
            this.fullSupplyCount = other.fullSupplyCount;
            this.translucent = other.translucent;
            this.dynamic = other.dynamic;
        }

        void intersectSupplyUnionDemand(EvalResult other) {
            this.translucent |= other.translucent;
            this.fullSurfaceGeomMask &= other.fullSurfaceGeomMask;
            for (int d = 0; d < 6; d++) {
                andMask(this.supply[d], other.supply[d]);
                if (other.faceHadQuads[d]) {
                    if (!this.faceHadQuads[d]) {
                        this.faceHadQuads[d] = true;
                        if ((other.demandValidMask & (1 << d)) != 0) {
                            this.demandValidMask |= (1 << d);
                            System.arraycopy(other.demand[d], 0, this.demand[d], 0, 4);
                        } else {
                            this.demandValidMask &= ~(1 << d);
                        }
                    } else if ((other.demandValidMask & (1 << d)) != 0 && (this.demandValidMask & (1 << d)) != 0) {
                        orMask(this.demand[d], other.demand[d]);
                    } else {
                        this.demandValidMask &= ~(1 << d);
                    }
                }
            }
        }

        void updateFullSupplyCount() {
            int count = 0;
            for (int d = 0; d < 6; d++) {
                if (isMaskFull(this.supply[d])) {
                    count++;
                }
            }
            this.fullSupplyCount = count;
        }
    }

    private record MaskKey(long w0, long w1, long w2, long w3) {
        MaskKey(long[] arr) {
            this(arr[0], arr[1], arr[2], arr[3]);
        }
    }

    private static final class TableKey {
        final long[][] table;
        final int hash;

        TableKey(long[][] table) {
            this.table = table;
            this.hash = Arrays.hashCode(table);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TableKey other && Arrays.equals(this.table, other.table);
        }
    }
}
