package net.ashpapi.papioptimization.client.ebe;

import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.monster.shulker.ShulkerModel;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.model.object.skull.DragonHeadModel;
import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiEbeRuntime {

    private static final double BANNER_DYNAMIC_ENTER_DIST_SQ = 20.0 * 20.0;
    private static final double BANNER_DYNAMIC_EXIT_DIST_SQ = 26.0 * 26.0;
    private static final int MAX_POT_CACHE_SIZE = 512;
    private static final int REBUILD_RETRY_TICKS = 40;
    private static final Direction[] DIRECTIONS = Direction.values();

    private static volatile boolean sectionCompilerHooked = false;
    private static volatile boolean atlasPatched = false;
    private static volatile String activeBackend = "disabled";
    private static volatile boolean latchedXmasTextures = ChestRenderer.xmasTextures();

    private static volatile double camX;
    private static volatile double camY;
    private static volatile double camZ;
    private static volatile boolean camValid = false;
    private static volatile long lastClientGameTime = Long.MIN_VALUE;
    private static volatile int monoClientTick = 0;

    private static volatile @Nullable ReferenceOpenHashSet<Block> vanillaBakeableBlocks;

    private static final ConcurrentHashMap<StateModelKey, EpochModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PotModelKey, EpochModel> POT_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Set<DecoratedPotBlockEntity> ACTIVE_WOBBLING_POTS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Object CACHE_LOCK = new Object();
    private static final AtomicInteger RELOAD_EPOCH = new AtomicInteger(1);
    private static final BlockStateModel MISSING_SENTINEL =
            new SingleVariant(new SimpleBakedPart(Collections.emptyList(), createEmptyDirectionalLists(), null, 0, false));

    private record StateModelKey(BlockState state, boolean xmas) {}

    private record PotModelKey(
            Direction facing,
            @Nullable Identifier front,
            @Nullable Identifier back,
            @Nullable Identifier left,
            @Nullable Identifier right
    ) {}

    private record EpochModel(int epoch, BlockStateModel model) {}

    private PapiEbeRuntime() {}

    public static void markSectionCompilerHooked() {
        if (!sectionCompilerHooked) {
            markSectionCompilerHooked("vanilla");
        }
    }

    public static void markSectionCompilerHooked(String backend) {
        activeBackend = backend;
        sectionCompilerHooked = true;
    }

    public static void markAtlasPatched() {
        atlasPatched = true;
    }

    public static int getReloadEpoch() {
        return RELOAD_EPOCH.get();
    }

    public static void disable() {
        boolean wasHooked = sectionCompilerHooked;
        sectionCompilerHooked = false;
        activeBackend = "disabled";
        PapiSodiumEbeBridge.clearPending();
        if (wasHooked) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    PapiSodiumEbeBridge.clearPending();
                    if (mc.levelExtractor != null) {
                        mc.levelExtractor.allChanged();
                    }
                });
            }
        }
    }

    public static boolean isActive() {
        return sectionCompilerHooked && atlasPatched;
    }

    public static String getActiveBackend() {
        return isActive() ? activeBackend : "disabled";
    }

    public static void updateFrameContext(double x, double y, double z, @Nullable ClientLevel level) {
        camX = x;
        camY = y;
        camZ = z;
        camValid = true;
        if (level != null) {
            long gt = level.getGameTime();
            if (gt != lastClientGameTime) {
                lastClientGameTime = gt;
                monoClientTick++;
                tickWobblingPots(level);
            }
        }
    }

    public static void resetFrameContext() {
        camValid = false;
        lastClientGameTime = Long.MIN_VALUE;
        ACTIVE_WOBBLING_POTS.clear();
    }

    public static void onResourceReload() {
        latchedXmasTextures = ChestRenderer.xmasTextures();
        synchronized (CACHE_LOCK) {
            RELOAD_EPOCH.incrementAndGet();
            MODEL_CACHE.clear();
            POT_MODEL_CACHE.clear();
        }
        PapiSodiumEbeBridge.clearPending();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.mainCamera();
            if (cam != null) {
                var pos = cam.position();
                camX = pos.x;
                camY = pos.y;
                camZ = pos.z;
                camValid = true;
            }
        }
    }

    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            RELOAD_EPOCH.incrementAndGet();
            MODEL_CACHE.clear();
            POT_MODEL_CACHE.clear();
        }
        PapiSodiumEbeBridge.clearPending();
        resetFrameContext();
    }

    private static ReferenceOpenHashSet<Block> getVanillaBakeableBlocks() {
        ReferenceOpenHashSet<Block> set = vanillaBakeableBlocks;
        if (set != null) {
            return set;
        }
        ReferenceOpenHashSet<Block> built = new ReferenceOpenHashSet<>(64);
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null || !"minecraft".equals(key.getNamespace())) {
                continue;
            }
            if (block == Blocks.CHEST
                    || block == Blocks.TRAPPED_CHEST
                    || block == Blocks.ENDER_CHEST
                    || block == Blocks.BELL
                    || block == Blocks.DECORATED_POT
                    || block instanceof CopperChestBlock
                    || block instanceof ShulkerBoxBlock
                    || block instanceof AbstractBannerBlock) {
                built.add(block);
            } else if (block instanceof AbstractSkullBlock skull && skull.getType() != SkullBlock.Types.PLAYER) {
                built.add(block);
            }
        }
        vanillaBakeableBlocks = built;
        return built;
    }

    public static boolean isBakeableBlock(BlockState state) {
        return state.hasBlockEntity() && getVanillaBakeableBlocks().contains(state.getBlock());
    }

    public static boolean isBakeableBlockEntity(BlockEntity be, BlockState state) {
        if (!isBakeableBlock(state)) {
            return false;
        }
        BlockEntityType<?> type = be.getType();
        Block block = state.getBlock();
        if (block instanceof TrappedChestBlock) {
            return type == BlockEntityTypes.TRAPPED_CHEST;
        }
        if (block instanceof ChestBlock) {
            return type == BlockEntityTypes.CHEST;
        }
        if (block instanceof EnderChestBlock) {
            return type == BlockEntityTypes.ENDER_CHEST;
        }
        if (block instanceof ShulkerBoxBlock) {
            return type == BlockEntityTypes.SHULKER_BOX;
        }
        if (block instanceof BellBlock) {
            return type == BlockEntityTypes.BELL;
        }
        if (block instanceof DecoratedPotBlock) {
            return type == BlockEntityTypes.DECORATED_POT;
        }
        if (block instanceof AbstractSkullBlock skull) {
            return type == BlockEntityTypes.SKULL && skull.getType() != SkullBlock.Types.PLAYER;
        }
        if (block instanceof AbstractBannerBlock) {
            return type == BlockEntityTypes.BANNER;
        }
        return false;
    }

    private static ChestType oppositeChestType(ChestType type) {
        return type == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
    }

    private static @Nullable ChestBlockEntity findPairedChest(ChestBlockEntity chest, BlockState state, @Nullable BlockGetter getter) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return null;
        }
        BlockGetter bg = getter != null ? getter : chest.getLevel();
        if (bg == null) {
            return null;
        }
        BlockPos otherPos = chest.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
        BlockState otherState = bg.getBlockState(otherPos);
        if (!otherState.is(state.getBlock())
                || !otherState.hasProperty(ChestBlock.TYPE)
                || otherState.getValue(ChestBlock.TYPE) != oppositeChestType(type)
                || otherState.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) {
            return null;
        }
        BlockEntity otherBe = bg.getBlockEntity(otherPos);
        if (otherBe instanceof ChestBlockEntity otherChest && otherChest.getType() == chest.getType()) {
            return otherChest;
        }
        return null;
    }

    public static boolean isDynamicNow(@Nullable BlockEntity be, BlockState state, @Nullable BlockGetter blockGetter) {
        if (be == null) {
            return false;
        }
        if (be instanceof ChestBlockEntity chest) {
            if (chest.getOpenNess(0.0F) > 0.0F || chest.getOpenNess(1.0F) > 0.0F) {
                return true;
            }
            ChestBlockEntity paired = findPairedChest(chest, state, blockGetter);
            return paired != null && (paired.getOpenNess(0.0F) > 0.0F || paired.getOpenNess(1.0F) > 0.0F);
        }
        if (be instanceof EnderChestBlockEntity enderChest) {
            return enderChest.getOpenNess(0.0F) > 0.0F || enderChest.getOpenNess(1.0F) > 0.0F;
        }
        if (be instanceof ShulkerBoxBlockEntity shulker) {
            return shulker.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED
                    || shulker.getProgress(0.0F) > 0.0F
                    || shulker.getProgress(1.0F) > 0.0F;
        }
        if (be instanceof BellBlockEntity bell) {
            return bell.shaking || bell.ticks > 0;
        }
        if (be instanceof DecoratedPotBlockEntity pot) {
            DecoratedPotBlockEntity.WobbleStyle wobble = pot.lastWobbleStyle;
            if (wobble == null) {
                return false;
            }
            Level potLvl = pot.getLevel();
            long now = potLvl != null
                    ? potLvl.getGameTime()
                    : ((blockGetter instanceof Level lvl) ? lvl.getGameTime() : lastClientGameTime);
            if (now == Long.MIN_VALUE) {
                return false;
            }
            long elapsed = now - pot.wobbleStartedAtTick;
            return elapsed >= 0L && elapsed < wobble.duration;
        }
        if (be instanceof SkullBlockEntity skull) {
            if (state.hasProperty(AbstractSkullBlock.POWERED) && state.getValue(AbstractSkullBlock.POWERED)) {
                return true;
            }
            return skull.getAnimation(0.0F) > 0.0F || skull.getAnimation(1.0F) > 0.0F;
        }
        if (be instanceof BannerBlockEntity banner) {
            if (!banner.getPatterns().layers().isEmpty()) {
                return true;
            }
            if (!camValid) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.gameRenderer != null) {
                    Camera cam = mc.gameRenderer.mainCamera();
                    if (cam != null) {
                        var cpos = cam.position();
                        camX = cpos.x;
                        camY = cpos.y;
                        camZ = cpos.z;
                        camValid = true;
                    }
                }
                if (!camValid) {
                    return true;
                }
            }
            BlockPos pos = be.getBlockPos();
            double dx = (pos.getX() + 0.5) - camX;
            double dy = (pos.getY() + 0.5) - camY;
            double dz = (pos.getZ() + 0.5) - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            boolean wasDynamic = be instanceof PapiEbeDynamicTracker tracker && tracker.papi$wasEbeDynamic();
            return wasDynamic ? distSq < BANNER_DYNAMIC_EXIT_DIST_SQ : distSq < BANNER_DYNAMIC_ENTER_DIST_SQ;
        }
        return true;
    }

    public static void syncAnimatedBlockEntity(BlockEntity be) {
        if (!isActive() || be.isRemoved() || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!isBakeableBlockEntity(be, state) || !(be instanceof PapiEbeDynamicTracker tracker)) {
            return;
        }
        boolean dynamic = isDynamicNow(be, state, level);
        if (dynamic != tracker.papi$wasEbeDynamic()) {
            applyDynamicTransition(be, state, level, tracker, dynamic);
        }
    }

    public static void onDecoratedPotWobbleStart(DecoratedPotBlockEntity pot) {
        if (!isActive() || pot.isRemoved() || !(pot.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockState state = pot.getBlockState();
        if (!isBakeableBlockEntity(pot, state) || !(pot instanceof PapiEbeDynamicTracker tracker)) {
            return;
        }
        ACTIVE_WOBBLING_POTS.add(pot);
        if (!tracker.papi$wasEbeDynamic()) {
            applyDynamicTransition(pot, state, level, tracker, true);
        }
    }

    public static void onClientBlockEntityDataLoaded(BlockEntity be) {
        if (!isActive() || be.isRemoved() || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!isBakeableBlockEntity(be, state)) {
            return;
        }
        if (be instanceof PapiEbeDynamicTracker tracker) {
            boolean dynamic = isDynamicNow(be, state, level);
            tracker.papi$setEbeDynamic(dynamic);
            tracker.papi$setEbeRebuildTick(monoClientTick);
        }
        requestRebuild(level, be.getBlockPos(), state);
    }

    private static void tickWobblingPots(ClientLevel level) {
        if (ACTIVE_WOBBLING_POTS.isEmpty()) {
            return;
        }
        synchronized (ACTIVE_WOBBLING_POTS) {
            Iterator<DecoratedPotBlockEntity> it = ACTIVE_WOBBLING_POTS.iterator();
            while (it.hasNext()) {
                DecoratedPotBlockEntity pot = it.next();
                if (pot == null || pot.isRemoved() || pot.getLevel() != level) {
                    it.remove();
                    continue;
                }
                BlockState state = pot.getBlockState();
                if (!isDynamicNow(pot, state, level)) {
                    it.remove();
                    if (pot instanceof PapiEbeDynamicTracker tracker && tracker.papi$wasEbeDynamic()) {
                        applyDynamicTransition(pot, state, level, tracker, false);
                    }
                }
            }
        }
    }

    private static void applyDynamicTransition(
            BlockEntity be,
            BlockState state,
            ClientLevel level,
            PapiEbeDynamicTracker tracker,
            boolean dynamic
    ) {
        int nowTick = monoClientTick;
        tracker.papi$setEbeDynamic(dynamic);
        tracker.papi$setEbeRebuildTick(nowTick);
        if (be instanceof ChestBlockEntity chest) {
            ChestBlockEntity paired = findPairedChest(chest, state, level);
            if (paired instanceof PapiEbeDynamicTracker pairedTracker) {
                pairedTracker.papi$setEbeDynamic(dynamic);
                pairedTracker.papi$setEbeRebuildTick(nowTick);
            }
        }
        requestRebuild(level, be.getBlockPos(), state);
    }

    public static void onCommittedBakedTracker(PapiEbeDynamicTracker tracker) {
        if (!(tracker instanceof BlockEntity be) || be.isRemoved()) {
            tracker.papi$setEbeBaked(false);
            return;
        }
        tracker.papi$setEbeBaked(true);
        if (tracker.papi$wasEbeDynamic() && be.getLevel() instanceof ClientLevel level) {
            tracker.papi$setEbeRebuildTick(monoClientTick);
            requestRebuild(level, be.getBlockPos(), be.getBlockState());
        }
    }

    public static boolean shouldSkipDynamicRender(BlockEntity be, ClientLevel level, boolean hasCrumblingOverlay) {
        if (!isActive() || !(be instanceof PapiEbeDynamicTracker tracker)) {
            return false;
        }
        BlockState state = be.getBlockState();
        if (!isBakeableBlockEntity(be, state)) {
            return false;
        }
        boolean dynamic = isDynamicNow(be, state, level);
        if (dynamic != tracker.papi$wasEbeDynamic()) {
            applyDynamicTransition(be, state, level, tracker, dynamic);
        }
        if (dynamic || hasCrumblingOverlay) {
            return false;
        }
        if (tracker.papi$isEbeBaked()) {
            return true;
        }
        if (getStaticModel(state, be) == null) {
            return false;
        }
        int nowTick = monoClientTick;
        if (nowTick - tracker.papi$getEbeRebuildTick() >= REBUILD_RETRY_TICKS) {
            tracker.papi$setEbeRebuildTick(nowTick);
            requestRebuild(level, be.getBlockPos(), state);
        }
        return false;
    }

    public static void requestRebuild(ClientLevel level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(state.getBlock())
                        && otherState.hasProperty(ChestBlock.TYPE)
                        && otherState.getValue(ChestBlock.TYPE) == oppositeChestType(type)
                        && otherState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                    level.sendBlockUpdated(otherPos, otherState, otherState, Block.UPDATE_IMMEDIATE);
                }
            }
        }
    }

    public static @Nullable BlockStateModel getStaticModel(BlockState state) {
        return getStaticModel(state, null);
    }

    public static @Nullable BlockStateModel getStaticModel(BlockState state, @Nullable BlockEntity be) {
        int epoch = RELOAD_EPOCH.get();
        if (state.getBlock() instanceof DecoratedPotBlock) {
            PotDecorations decorations = (be instanceof DecoratedPotBlockEntity pot && pot.getDecorations() != null)
                    ? pot.getDecorations()
                    : PotDecorations.EMPTY;
            Direction facing = state.hasProperty(DecoratedPotBlock.HORIZONTAL_FACING)
                    ? state.getValue(DecoratedPotBlock.HORIZONTAL_FACING)
                    : Direction.NORTH;
            PotModelKey key = new PotModelKey(
                    facing,
                    extractPotPatternAsset(decorations.front()),
                    extractPotPatternAsset(decorations.back()),
                    extractPotPatternAsset(decorations.left()),
                    extractPotPatternAsset(decorations.right())
            );
            EpochModel cached = POT_MODEL_CACHE.get(key);
            if (cached != null && cached.epoch() == epoch) {
                return cached.model() == MISSING_SENTINEL ? null : cached.model();
            }
            TextureAtlas blocksAtlas = getBlocksAtlas();
            if (blocksAtlas == null) {
                return null;
            }
            BlockStateModel built = buildDecoratedPotModel(key, blocksAtlas);
            synchronized (CACHE_LOCK) {
                if (RELOAD_EPOCH.get() != epoch) {
                    return null;
                }
                EpochModel existing = POT_MODEL_CACHE.get(key);
                if (existing != null && existing.epoch() == epoch) {
                    return existing.model() == MISSING_SENTINEL ? null : existing.model();
                }
                if (POT_MODEL_CACHE.size() >= MAX_POT_CACHE_SIZE) {
                    Iterator<PotModelKey> it = POT_MODEL_CACHE.keySet().iterator();
                    if (it.hasNext()) {
                        POT_MODEL_CACHE.remove(it.next());
                    }
                }
                POT_MODEL_CACHE.put(key, new EpochModel(epoch, built != null ? built : MISSING_SENTINEL));
            }
            return built;
        }
        boolean xmas = latchedXmasTextures;
        StateModelKey stateKey = new StateModelKey(state, xmas);
        EpochModel cached = MODEL_CACHE.get(stateKey);
        if (cached != null && cached.epoch() == epoch) {
            return cached.model() == MISSING_SENTINEL ? null : cached.model();
        }
        TextureAtlas blocksAtlas = getBlocksAtlas();
        if (blocksAtlas == null) {
            return null;
        }
        BlockStateModel built = buildStaticModel(state, xmas, blocksAtlas);
        synchronized (CACHE_LOCK) {
            if (RELOAD_EPOCH.get() != epoch) {
                return null;
            }
            EpochModel existing = MODEL_CACHE.get(stateKey);
            if (existing != null && existing.epoch() == epoch) {
                return existing.model() == MISSING_SENTINEL ? null : existing.model();
            }
            MODEL_CACHE.put(stateKey, new EpochModel(epoch, built != null ? built : MISSING_SENTINEL));
        }
        return built;
    }

    private static @Nullable Identifier extractPotPatternAsset(Optional<ItemStackTemplate> optSherd) {
        if (optSherd.isEmpty()) {
            return null;
        }
        Holder<DecoratedPotPattern> holder = optSherd.get().get(DataComponents.PROVIDES_POTTERY_PATTERN);
        if (holder == null || !holder.isBound()) {
            return null;
        }
        return holder.value().assetId();
    }

    private static @Nullable TextureAtlas getBlocksAtlas() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getAtlasManager() == null) {
            return null;
        }
        try {
            return mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ModelPart bakeLayer(ModelLayerLocation layer, Supplier<LayerDefinition> fallback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            EntityModelSet models = mc.getEntityModels();
            if (models != null) {
                try {
                    return models.bakeLayer(layer);
                } catch (Throwable ignored) {
                }
            }
        }
        return fallback.get().bakeRoot();
    }

    private static @Nullable BlockStateModel buildStaticModel(BlockState state, boolean xmas, TextureAtlas blocksAtlas) {
        Block block = state.getBlock();
        if (block instanceof ChestBlock) {
            return buildChestModel(state, xmas, blocksAtlas);
        }
        if (block instanceof EnderChestBlock) {
            return buildEnderChestModel(state, blocksAtlas);
        }
        if (block instanceof ShulkerBoxBlock shulker) {
            return buildShulkerModel(state, shulker, blocksAtlas);
        }
        if (block instanceof BellBlock) {
            return buildBellModel(blocksAtlas);
        }
        if (block instanceof AbstractSkullBlock skullBlock) {
            return buildSkullModel(state, skullBlock, blocksAtlas);
        }
        if (block instanceof AbstractBannerBlock bannerBlock) {
            return buildBannerModel(state, bannerBlock, blocksAtlas);
        }
        return null;
    }

    private static @Nullable BlockStateModel buildChestModel(BlockState state, boolean xmas, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(ChestBlock.FACING);
        ChestType type = state.getValue(ChestBlock.TYPE);
        Block block = state.getBlock();
        SpriteId spriteId;
        if (block instanceof CopperChestBlock copper) {
            spriteId = Sheets.CHEST_COPPER.pick(copper.getState()).select(type);
        } else if (xmas) {
            spriteId = Sheets.CHEST_CHRISTMAS.select(type);
        } else if (block instanceof TrappedChestBlock) {
            spriteId = Sheets.CHEST_TRAPPED.select(type);
        } else {
            spriteId = Sheets.CHEST_REGULAR.select(type);
        }
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root;
        if (type == ChestType.LEFT) {
            root = bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT, ChestModel::createDoubleBodyLeftLayer);
        } else if (type == ChestType.RIGHT) {
            root = bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT, ChestModel::createDoubleBodyRightLayer);
        } else {
            root = bakeLayer(ModelLayers.CHEST, ChestModel::createSingleBodyLayer);
        }
        new ChestModel(root).setupAnim(0.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(ChestRenderer.modelTransformation(facing));
        return bakeChestParts(root, poseStack, sprite);
    }

    private static @Nullable BlockStateModel buildEnderChestModel(BlockState state, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(EnderChestBlock.FACING);
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, Sheets.ENDER_CHEST_LOCATION.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = bakeLayer(ModelLayers.CHEST, ChestModel::createSingleBodyLayer);
        new ChestModel(root).setupAnim(0.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(ChestRenderer.modelTransformation(facing));
        return bakeChestParts(root, poseStack, sprite);
    }

    private static BlockStateModel bakeChestParts(ModelPart root, PoseStack poseStack, TextureAtlasSprite sprite) {
        List<BakedQuad> unculled = new ArrayList<>();
        List<BakedQuad>[] culled = createEmptyDirectionalLists();
        if (root.hasChild("bottom") && root.hasChild("lid")) {
            appendQuads(root.getChild("bottom"), poseStack, sprite, -1, Direction.UP, unculled, culled);
            appendQuads(root.getChild("lid"), poseStack, sprite, -1, Direction.DOWN, unculled, culled);
            if (root.hasChild("lock")) {
                appendQuads(root.getChild("lock"), poseStack, sprite, -1, null, unculled, culled);
            }
        } else {
            appendQuads(root, poseStack, sprite, -1, null, unculled, culled);
        }
        return finalizeModel(unculled, culled, sprite, false);
    }

    private static @Nullable BlockStateModel buildShulkerModel(BlockState state, ShulkerBoxBlock block, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(ShulkerBoxBlock.FACING);
        DyeColor color = block.getColor();
        SpriteId spriteId = color == null
                ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
                : Sheets.SHULKER_TEXTURE_LOCATION.get(color.getId());
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = bakeLayer(ModelLayers.SHULKER_BOX, ShulkerModel::createBoxLayer);
        PoseStack poseStack = new PoseStack();
        Matrix4f mat = new Matrix4f()
                .translation(0.5F, 0.5F, 0.5F)
                .rotate(facing.getRotation())
                .scale(1.0F, -1.0F, -1.0F)
                .translate(0.0F, -1.0F, 0.0F);
        poseStack.mulPose(mat);
        List<BakedQuad> unculled = new ArrayList<>();
        List<BakedQuad>[] culled = createEmptyDirectionalLists();
        if (root.hasChild("base") && root.hasChild("lid")) {
            appendQuads(root.getChild("base"), poseStack, sprite, -1, facing, unculled, culled);
            appendQuads(root.getChild("lid"), poseStack, sprite, -1, facing.getOpposite(), unculled, culled);
        } else {
            appendQuads(root, poseStack, sprite, -1, null, unculled, culled);
        }
        return finalizeModel(unculled, culled, sprite, false);
    }

    private static @Nullable BlockStateModel buildBellModel(TextureAtlas blocksAtlas) {
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, BellRenderer.BELL_TEXTURE.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = bakeLayer(ModelLayers.BELL, BellModel::createBodyLayer);
        new BellModel(root).setupAnim(new BellModel.State(0.0F, null));
        PoseStack poseStack = new PoseStack();
        return bakeSinglePart(root, poseStack, sprite, -1, true);
    }

    private static @Nullable BlockStateModel buildDecoratedPotModel(PotModelKey key, TextureAtlas blocksAtlas) {
        TextureAtlasSprite baseSprite = lookupSprite(blocksAtlas, Sheets.DECORATED_POT_BASE.texture());
        TextureAtlasSprite blankSide = lookupSprite(blocksAtlas, Sheets.DECORATED_POT_SIDE.texture());
        if (baseSprite == null || blankSide == null) {
            return null;
        }
        TextureAtlasSprite frontSprite = resolvePotSideSprite(key.front(), blocksAtlas, blankSide);
        TextureAtlasSprite backSprite = resolvePotSideSprite(key.back(), blocksAtlas, blankSide);
        TextureAtlasSprite leftSprite = resolvePotSideSprite(key.left(), blocksAtlas, blankSide);
        TextureAtlasSprite rightSprite = resolvePotSideSprite(key.right(), blocksAtlas, blankSide);

        ModelPart baseRoot = bakeLayer(ModelLayers.DECORATED_POT_BASE, DecoratedPotRenderer::createBaseLayer);
        ModelPart sidesRoot = bakeLayer(ModelLayers.DECORATED_POT_SIDES, DecoratedPotRenderer::createSidesLayer);

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(DecoratedPotRenderer.modelTransformation(key.facing()));

        List<BakedQuad> unculled = new ArrayList<>();
        List<BakedQuad>[] culled = createEmptyDirectionalLists();
        appendQuads(baseRoot.getChild("neck"), poseStack, baseSprite, -1, null, unculled, culled);
        appendQuads(baseRoot.getChild("top"), poseStack, baseSprite, -1, Direction.DOWN, unculled, culled);
        appendQuads(baseRoot.getChild("bottom"), poseStack, baseSprite, -1, Direction.UP, unculled, culled);
        appendQuads(sidesRoot.getChild("front"), poseStack, frontSprite, -1, null, unculled, culled);
        appendQuads(sidesRoot.getChild("back"), poseStack, backSprite, -1, null, unculled, culled);
        appendQuads(sidesRoot.getChild("left"), poseStack, leftSprite, -1, null, unculled, culled);
        appendQuads(sidesRoot.getChild("right"), poseStack, rightSprite, -1, null, unculled, culled);

        return finalizeModel(unculled, culled, baseSprite, false);
    }

    private static TextureAtlasSprite resolvePotSideSprite(
            @Nullable Identifier patternAssetId,
            TextureAtlas blocksAtlas,
            TextureAtlasSprite fallback
    ) {
        if (patternAssetId == null) {
            return fallback;
        }
        SpriteId spriteId = Sheets.DECORATED_POT_MAPPER.apply(patternAssetId);
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        return sprite != null ? sprite : fallback;
    }

    private static @Nullable BlockStateModel buildSkullModel(
            BlockState state,
            AbstractSkullBlock block,
            TextureAtlas blocksAtlas
    ) {
        if (!(block.getType() instanceof SkullBlock.Types type) || type == SkullBlock.Types.PLAYER) {
            return null;
        }
        Identifier skinPath = SkullBlockRenderer.SKIN_BY_TYPE.get(type);
        if (skinPath == null) {
            return null;
        }
        Identifier spriteLoc = stripTexturePath(skinPath);
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteLoc);
        if (sprite == null) {
            return null;
        }

        ModelPart root;
        SkullModelBase model;
        switch (type) {
            case SKELETON -> {
                root = bakeLayer(ModelLayers.SKELETON_SKULL, SkullModel::createMobHeadLayer);
                model = new SkullModel(root);
            }
            case WITHER_SKELETON -> {
                root = bakeLayer(ModelLayers.WITHER_SKELETON_SKULL, SkullModel::createMobHeadLayer);
                model = new SkullModel(root);
            }
            case CREEPER -> {
                root = bakeLayer(ModelLayers.CREEPER_HEAD, SkullModel::createMobHeadLayer);
                model = new SkullModel(root);
            }
            case ZOMBIE -> {
                root = bakeLayer(ModelLayers.ZOMBIE_HEAD, SkullModel::createHumanoidHeadLayer);
                model = new SkullModel(root);
            }
            case PIGLIN -> {
                root = bakeLayer(ModelLayers.PIGLIN_HEAD, () -> LayerDefinition.create(PiglinHeadModel.createHeadModel(), 64, 64));
                model = new PiglinHeadModel(root);
            }
            case DRAGON -> {
                root = bakeLayer(ModelLayers.DRAGON_SKULL, DragonHeadModel::createHeadLayer);
                model = new DragonHeadModel(root);
            }
            default -> {
                return null;
            }
        }

        SkullModelBase.State animState = new SkullModelBase.State();
        animState.animationPos = 0.0F;
        animState.yRot = 0.0F;
        model.setupAnim(animState);

        PoseStack poseStack = new PoseStack();
        if (block instanceof WallSkullBlock) {
            poseStack.mulPose(SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(state.getValue(WallSkullBlock.FACING)));
        } else if (state.hasProperty(SkullBlock.ROTATION)) {
            poseStack.mulPose(SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(state.getValue(SkullBlock.ROTATION)));
        } else {
            return null;
        }
        return bakeSinglePart(root, poseStack, sprite, -1, false);
    }

    private static @Nullable BlockStateModel buildBannerModel(
            BlockState state,
            AbstractBannerBlock block,
            TextureAtlas blocksAtlas
    ) {
        TextureAtlasSprite baseSprite = lookupSprite(blocksAtlas, Sheets.BANNER_BASE.texture());
        TextureAtlasSprite flagSprite = lookupSprite(blocksAtlas, Sheets.BANNER_PATTERN_BASE.texture());
        if (baseSprite == null || flagSprite == null) {
            return null;
        }
        boolean standing = block instanceof BannerBlock;
        PoseStack poseStack = new PoseStack();
        if (standing) {
            poseStack.mulPose(BannerRenderer.TRANSFORMATIONS.freeTransformations(state.getValue(BannerBlock.ROTATION)));
        } else if (block instanceof WallBannerBlock) {
            poseStack.mulPose(BannerRenderer.TRANSFORMATIONS.wallTransformation(state.getValue(WallBannerBlock.FACING)));
        } else {
            return null;
        }

        ModelPart bodyRoot = standing
                ? bakeLayer(ModelLayers.STANDING_BANNER, () -> BannerModel.createBodyLayer(true))
                : bakeLayer(ModelLayers.WALL_BANNER, () -> BannerModel.createBodyLayer(false));
        ModelPart flagRoot = standing
                ? bakeLayer(ModelLayers.STANDING_BANNER_FLAG, () -> BannerFlagModel.createFlagLayer(true))
                : bakeLayer(ModelLayers.WALL_BANNER_FLAG, () -> BannerFlagModel.createFlagLayer(false));
        new BannerFlagModel(flagRoot).setupAnim(0.0F);

        List<BakedQuad> unculled = new ArrayList<>();
        List<BakedQuad>[] culled = createEmptyDirectionalLists();
        appendQuads(bodyRoot, poseStack, baseSprite, -1, null, unculled, culled);
        appendQuads(flagRoot, poseStack, flagSprite, 0, null, unculled, culled);
        return finalizeModel(unculled, culled, baseSprite, false);
    }

    private static Identifier stripTexturePath(Identifier textureLoc) {
        String path = textureLoc.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return Identifier.fromNamespaceAndPath(textureLoc.getNamespace(), path);
    }

    private static @Nullable TextureAtlasSprite lookupSprite(TextureAtlas atlas, Identifier textureId) {
        TextureAtlasSprite gluonSprite = atlas.getSprite(PapiEbeSpriteSource.toGluonSpriteId(textureId));
        if (gluonSprite != null && gluonSprite != atlas.missingSprite()) {
            return gluonSprite;
        }
        TextureAtlasSprite sprite = atlas.getSprite(textureId);
        if (sprite == null || sprite == atlas.missingSprite()) {
            return null;
        }
        return sprite;
    }

    private static BlockStateModel bakeSinglePart(
            ModelPart root,
            PoseStack poseStack,
            TextureAtlasSprite sprite,
            int tintIndex,
            boolean ambientOcclusion
    ) {
        List<BakedQuad> unculled = new ArrayList<>();
        List<BakedQuad>[] culled = createEmptyDirectionalLists();
        appendQuads(root, poseStack, sprite, tintIndex, null, unculled, culled);
        return finalizeModel(unculled, culled, sprite, ambientOcclusion);
    }

    private static void appendQuads(
            ModelPart root,
            PoseStack poseStack,
            TextureAtlasSprite sprite,
            int tintIndex,
            @Nullable Direction skipWorldFace,
            List<BakedQuad> unculled,
            List<BakedQuad>[] culled
    ) {
        Material.Baked material = new Material.Baked(sprite, false);
        Vector3f tmpNormal = new Vector3f();
        Vector3f edge1 = new Vector3f();
        Vector3f edge2 = new Vector3f();
        Vector3f cross = new Vector3f();
        Vector3f minPos = new Vector3f();
        Vector3f maxPos = new Vector3f();

        root.visit(poseStack, (pose, path, idx, cube) -> {
            Matrix4f posMat = pose.pose();
            Matrix3f normMat = pose.normal();
            for (ModelPart.Polygon poly : cube.polygons) {
                ModelPart.Vertex[] verts = poly.vertices();
                if (verts.length < 4) {
                    continue;
                }
                Vector3f p0 = posMat.transformPosition(verts[0].worldX(), verts[0].worldY(), verts[0].worldZ(), new Vector3f());
                Vector3f p1 = posMat.transformPosition(verts[1].worldX(), verts[1].worldY(), verts[1].worldZ(), new Vector3f());
                Vector3f p2 = posMat.transformPosition(verts[2].worldX(), verts[2].worldY(), verts[2].worldZ(), new Vector3f());
                Vector3f p3 = posMat.transformPosition(verts[3].worldX(), verts[3].worldY(), verts[3].worldZ(), new Vector3f());

                edge1.set(p1).sub(p0);
                edge2.set(p2).sub(p0);
                edge1.cross(edge2, cross);
                if (cross.lengthSquared() < 1.0E-8F) {
                    continue;
                }

                tmpNormal.set(poly.normal()).mul(normMat);
                float normLenSq = tmpNormal.lengthSquared();
                if (normLenSq > 1.0E-8F) {
                    tmpNormal.mul((float) (1.0 / Math.sqrt(normLenSq)));
                } else {
                    tmpNormal.set(cross).normalize();
                }

                Direction dir = Direction.getApproximateNearest(tmpNormal.x, tmpNormal.y, tmpNormal.z);
                if (skipWorldFace != null && dir == skipWorldFace && isAxisAlignedToDirection(tmpNormal, dir)) {
                    continue;
                }

                float u0f = verts[0].u();
                float v0f = verts[0].v();
                float u1f = verts[1].u();
                float v1f = verts[1].v();
                float u2f = verts[2].u();
                float v2f = verts[2].v();
                float u3f = verts[3].u();
                float v3f = verts[3].v();

                float minU = Math.min(Math.min(u0f, u1f), Math.min(u2f, u3f));
                float maxU = Math.max(Math.max(u0f, u1f), Math.max(u2f, u3f));
                float minV = Math.min(Math.min(v0f, v1f), Math.min(v2f, v3f));
                float maxV = Math.max(Math.max(v0f, v1f), Math.max(v2f, v3f));
                Transparency transparency = sprite.contents().computeTransparency(minU, minV, maxU, maxV);

                long uv0 = UVPair.pack(sprite.getU(u0f), sprite.getV(v0f));
                long uv1 = UVPair.pack(sprite.getU(u1f), sprite.getV(v1f));
                long uv2 = UVPair.pack(sprite.getU(u2f), sprite.getV(v2f));
                long uv3 = UVPair.pack(sprite.getU(u3f), sprite.getV(v3f));

                minPos.set(
                        Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)),
                        Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)),
                        Math.min(Math.min(p0.z, p1.z), Math.min(p2.z, p3.z))
                );
                maxPos.set(
                        Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)),
                        Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)),
                        Math.max(Math.max(p0.z, p1.z), Math.max(p2.z, p3.z))
                );
                Vector3f target0 = FaceInfo.fromFacing(dir).getVertexInfo(0).select(minPos, maxPos);
                float d0 = p0.distanceSquared(target0);
                float d1 = p1.distanceSquared(target0);
                float d2 = p2.distanceSquared(target0);
                float d3 = p3.distanceSquared(target0);
                int shift = 0;
                float best = d0;
                if (d1 < best) {
                    best = d1;
                    shift = 1;
                }
                if (d2 < best) {
                    best = d2;
                    shift = 2;
                }
                if (d3 < best) {
                    shift = 3;
                }

                Vector3f q0;
                Vector3f q1;
                Vector3f q2;
                Vector3f q3;
                long quv0;
                long quv1;
                long quv2;
                long quv3;
                switch (shift) {
                    case 1 -> {
                        q0 = p1; q1 = p2; q2 = p3; q3 = p0;
                        quv0 = uv1; quv1 = uv2; quv2 = uv3; quv3 = uv0;
                    }
                    case 2 -> {
                        q0 = p2; q1 = p3; q2 = p0; q3 = p1;
                        quv0 = uv2; quv1 = uv3; quv2 = uv0; quv3 = uv1;
                    }
                    case 3 -> {
                        q0 = p3; q1 = p0; q2 = p1; q3 = p2;
                        quv0 = uv3; quv1 = uv0; quv2 = uv1; quv3 = uv2;
                    }
                    default -> {
                        q0 = p0; q1 = p1; q2 = p2; q3 = p3;
                        quv0 = uv0; quv1 = uv1; quv2 = uv2; quv3 = uv3;
                    }
                }

                BakedQuad.MaterialInfo matInfo = BakedQuad.MaterialInfo.of(material, transparency, tintIndex, null, 0, true);
                BakedNormals bakedNormals = BakedNormals.of(BakedNormals.pack(tmpNormal));
                BakedQuad quad = new BakedQuad(q0, q1, q2, q3, quv0, quv1, quv2, quv3, dir, matInfo, bakedNormals, BakedColors.DEFAULT);

                Direction cullFace = detectCullFace(dir, tmpNormal, minPos, maxPos);
                if (cullFace != null) {
                    int ord = cullFace.ordinal();
                    if (culled[ord].isEmpty()) {
                        culled[ord] = new ArrayList<>(2);
                    }
                    culled[ord].add(quad);
                } else {
                    unculled.add(quad);
                }
            }
        });
    }

    private static boolean isAxisAlignedToDirection(Vector3f normal, Direction dir) {
        float dot = normal.x * dir.getStepX() + normal.y * dir.getStepY() + normal.z * dir.getStepZ();
        return dot > 0.999F;
    }

    private static @Nullable Direction detectCullFace(Direction dir, Vector3f normal, Vector3f minPos, Vector3f maxPos) {
        if (!isAxisAlignedToDirection(normal, dir)) {
            return null;
        }
        if (minPos.x < -1.0E-3F || maxPos.x > 1.001F
                || minPos.y < -1.0E-3F || maxPos.y > 1.001F
                || minPos.z < -1.0E-3F || maxPos.z > 1.001F) {
            return null;
        }
        return switch (dir) {
            case DOWN -> maxPos.y <= 1.0E-3F ? Direction.DOWN : null;
            case UP -> minPos.y >= 0.999F ? Direction.UP : null;
            case NORTH -> maxPos.z <= 1.0E-3F ? Direction.NORTH : null;
            case SOUTH -> minPos.z >= 0.999F ? Direction.SOUTH : null;
            case WEST -> maxPos.x <= 1.0E-3F ? Direction.WEST : null;
            case EAST -> minPos.x >= 0.999F ? Direction.EAST : null;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] createEmptyDirectionalLists() {
        List<BakedQuad>[] arr = (List<BakedQuad>[]) new List<?>[DIRECTIONS.length];
        for (int i = 0; i < DIRECTIONS.length; i++) {
            arr[i] = Collections.emptyList();
        }
        return arr;
    }

    private static BlockStateModel finalizeModel(
            List<BakedQuad> unculled,
            List<BakedQuad>[] culled,
            TextureAtlasSprite particleSprite,
            boolean ambientOcclusion
    ) {
        int flags = 0;
        for (int i = 0; i < unculled.size(); i++) {
            flags |= unculled.get(i).materialInfo().flags();
        }
        @SuppressWarnings("unchecked")
        List<BakedQuad>[] immutableCulled = (List<BakedQuad>[]) new List<?>[DIRECTIONS.length];
        for (int d = 0; d < DIRECTIONS.length; d++) {
            List<BakedQuad> list = culled[d];
            if (list.isEmpty()) {
                immutableCulled[d] = Collections.emptyList();
            } else {
                for (int i = 0; i < list.size(); i++) {
                    flags |= list.get(i).materialInfo().flags();
                }
                immutableCulled[d] = List.copyOf(list);
            }
        }
        return new SingleVariant(new SimpleBakedPart(
                unculled.isEmpty() ? Collections.emptyList() : List.copyOf(unculled),
                immutableCulled,
                new Material.Baked(particleSprite, false),
                flags,
                ambientOcclusion
        ));
    }

    private static final class SimpleBakedPart implements BlockStateModelPart {

        private final List<BakedQuad> unculledQuads;
        private final List<BakedQuad>[] culledQuads;
        private final Material.@Nullable Baked particleMaterial;
        private final int materialFlags;
        private final boolean ambientOcclusion;

        SimpleBakedPart(
                List<BakedQuad> unculledQuads,
                List<BakedQuad>[] culledQuads,
                Material.@Nullable Baked particleMaterial,
                int materialFlags,
                boolean ambientOcclusion
        ) {
            this.unculledQuads = unculledQuads;
            this.culledQuads = culledQuads;
            this.particleMaterial = particleMaterial;
            this.materialFlags = materialFlags;
            this.ambientOcclusion = ambientOcclusion;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? this.unculledQuads : this.culledQuads[direction.ordinal()];
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.ambientOcclusion;
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.particleMaterial;
        }

        @Override
        public int materialFlags() {
            return this.materialFlags;
        }
    }
}
