package net.ashpapi.papioptimization.mixin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PapiMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/MixinPlugin");

    private static final Set<String> APPLIED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SKIPPED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final CopyOnWriteArrayList<String> DISABLE_REASONS = new CopyOnWriteArrayList<>();

    private String mixinPrefix = "";
    private final Set<String> disabledMixins = new HashSet<>();
    private boolean disableLightingPackage;

    public static int getActiveMixinCount() {
        return APPLIED_MIXINS.size();
    }

    public static int getDisabledMixinCount() {
        return SKIPPED_MIXINS.size();
    }

    public static List<String> getDisableReasons() {
        return Collections.unmodifiableList(DISABLE_REASONS);
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPrefix = (mixinPackage != null && !mixinPackage.isEmpty()) ? mixinPackage + "." : "";
        this.disabledMixins.clear();
        this.disableLightingPackage = false;
        APPLIED_MIXINS.clear();
        SKIPPED_MIXINS.clear();
        DISABLE_REASONS.clear();

        PapiConfig.ensureLoaded();

        Set<String> reasons = new LinkedHashSet<>();

        LoadingModList modList;
        try {
            FMLLoader loader = FMLLoader.getCurrentOrNull();
            modList = loader != null ? loader.getLoadingModList() : null;
        } catch (Throwable ignored) {
            modList = null;
        }

        boolean hasSodium = modList != null && hasAny(modList, "sodium", "embeddium", "rubidium");
        boolean hasImmediatelyFast = modList != null && hasAny(modList, "immediatelyfast");
        boolean hasShaders = modList != null && hasAny(modList, "iris", "oculus");
        boolean hasVitrail = modList != null && hasAny(modList, "vitrail", "vulkanmod");
        boolean hasVitrailMod = modList != null && hasAny(modList, "vitrail");
        boolean hasEbe = modList != null && hasAny(modList, "enhancedblockentities", "ebe_reloaded", "ebe");
        boolean hasMoonrise = modList != null && hasAny(modList, "moonrise");
        boolean hasLighting = modList != null && (hasMoonrise || hasAny(modList, "scalablelux", "starlight"));
        boolean hasC2me = modList != null && hasAny(modList, "c2me", "c2me_base", "c2me-base");
        boolean hasSoundPhysics = modList != null && hasAny(modList, "sound_physics_remastered");
        boolean hasSkinLayers = modList != null && hasAny(modList, "skinlayers3d");
        boolean hasPapirius = modList != null && hasAny(modList, "papirius", "voxy");

        boolean cfgEbe = PapiConfig.ebe();
        boolean cfgEbeSodium = PapiConfig.ebeSodiumAdapter();
        boolean cfgEbeVitrail = PapiConfig.ebeVitrailAdapter();

        boolean allowVanillaEbe = cfgEbe && !hasSodium && !hasVitrail && !hasEbe && !hasShaders;
        boolean allowSodiumEbe = cfgEbe && cfgEbeSodium && hasSodium && !hasVitrail && !hasEbe && !hasShaders;
        boolean allowVitrailEbe = cfgEbe && cfgEbeSodium && cfgEbeVitrail && hasSodium && hasVitrailMod && !hasEbe && !hasShaders;

        if (!allowVanillaEbe) {
            this.disabledMixins.add("render.SectionCompilerEbeMixin");
            this.disabledMixins.add("render.SectionCompilerResultsEbeMixin");
            this.disabledMixins.add("render.CompiledSectionMeshEbeMixin");
            this.disabledMixins.add("render.RenderSectionEbeCommitMixin");
        }
        if (!allowSodiumEbe) {
            this.disabledMixins.add("render.SodiumEbeMixin");
        }
        if (!allowVitrailEbe) {
            this.disabledMixins.add("render.VitrailEbeMixin");
        }
        if (!allowSodiumEbe && !allowVitrailEbe) {
            this.disabledMixins.add("render.SodiumEbeCommitMixin");
        }
        if (!allowVanillaEbe && !allowSodiumEbe && !allowVitrailEbe) {
            disableEbeRuntimeMixins();
        }
        if (!hasVitrail) {
            this.disabledMixins.add("render.VitrailShadowPassMixin");
        }
        if (!hasSodium && !hasVitrail) {
            this.disabledMixins.add("render.SodiumBlockOcclusionMixin");
        }
        if (!hasShaders && !hasVitrail) {
            this.disabledMixins.add("weather.WeatherPrecipitationCullingMixin");
        }

        if (hasSodium) {
            reasons.add("mod:sodium");
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("particle.QuadParticleRenderStateMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("biome.ClientLevelBiomeBlendMixin");
            this.disabledMixins.add("biome.FastBlockTintCacheMixin");
        }
        if (hasImmediatelyFast) {
            reasons.add("mod:immediatelyfast");
            disableGuiBatchingGroup();
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("client.ByteBufferBuilderMixin");
            this.disabledMixins.add("client.FontSplitterFastMixin");
            this.disabledMixins.add("client.FontManagerCacheMixin");
            this.disabledMixins.add("client.FontTextureAtlasMixin");
            this.disabledMixins.add("client.UnihexProviderFastMixin");
            this.disabledMixins.add("map.MapRendererMixin");
            this.disabledMixins.add("map.MapInstanceTextureMixin");
            this.disabledMixins.add("map.MapRenderStateMixin");
            this.disabledMixins.add("map.MapTextureManagerMixin");
            this.disabledMixins.add("map.MapGuiGraphicsMixin");
            this.disabledMixins.add("particle.QuadParticleRenderStateMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
            this.disabledMixins.add("render.RenderTypesTextSortMixin");
            this.disabledMixins.add("render.RenderSetupBuilderTextSortMixin");
        }
        if (hasShaders) {
            reasons.add("mod:shaders");
            this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.LivingEntityRendererLodMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
            this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
            this.disabledMixins.add("render.TextGlyphRendererFastMixin");
            this.disabledMixins.add("render.RenderTypesTextSortMixin");
            this.disabledMixins.add("render.RenderSetupBuilderTextSortMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
            this.disabledMixins.add("weather.WeatherEffectRendererMixin");
            this.disabledMixins.add("client.CameraMixin");
        }
        if (hasVitrail) {
            reasons.add("mod:vitrail");
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("client.ByteBufferBuilderMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
            this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
            this.disabledMixins.add("render.LivingEntityRendererLodMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
            this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
            this.disabledMixins.add("render.TextGlyphRendererFastMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
            this.disabledMixins.add("weather.ClientLevelWeatherMixin");
            this.disabledMixins.add("weather.WeatherEffectRendererMixin");
            this.disabledMixins.add("client.CameraMixin");
        }
        if (hasPapirius) {
            reasons.add("mod:papirius/voxy");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
        }
        if (hasSkinLayers) {
            reasons.add("mod:skinlayers3d");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
        }
        if (hasSoundPhysics) {
            reasons.add("mod:sound_physics_remastered");
            this.disabledMixins.add("sound.SoundEngineMixin");
        }

        boolean hasEntityCulling = modList != null && hasAny(modList, "entityculling");
        boolean hasParticleCore = modList != null && hasAny(modList, "particle_core");
        boolean hasAsyncParticles = modList != null && hasAny(modList, "asyncparticles");
        boolean hasMoreCulling = modList != null && hasAny(modList, "moreculling");
        boolean hasBadOptimizations = modList != null && hasAny(modList, "badoptimizations");

        List<String> redundant = new ArrayList<>();
        if (hasLighting) {
            redundant.add("lighting");
            reasons.add("mod:lighting");
            this.disableLightingPackage = true;
        }
        if (hasMoonrise) {
            redundant.add("moonrise");
            reasons.add("mod:moonrise");
            disableMoonriseGroup();
        }
        if (hasC2me) {
            redundant.add("c2me");
            reasons.add("mod:c2me");
            disableC2meGroup();
        }

        if (modList != null) {
            if (hasAny(modList, "lithium", "radium", "canary")) {
                redundant.add("lithium");
                reasons.add("mod:lithium");
                disableLithiumGroup();
            }
            if (hasAny(modList, "alternate-current", "alternate_current")) {
                redundant.add("alternate-current");
                reasons.add("mod:alternate-current");
                this.disabledMixins.add("redstone.DefaultRedstoneWireEvaluatorMixin");
                this.disabledMixins.add("redstone.ServerLevelRedstoneMixin");
            }
            if (hasAny(modList, "ferritecore")) {
                redundant.add("ferritecore");
                reasons.add("mod:ferritecore");
                disableFerriteCoreGroup();
            }
            if (hasAny(modList, "lazydfu")) {
                redundant.add("lazydfu");
                reasons.add("mod:lazydfu");
                this.disabledMixins.add("startup.DataFixersMixin");
                this.disabledMixins.add("startup.DataFixerUpperMixin");
                this.disabledMixins.add("startup.DataFixTypesMixin");
            }
            if (hasAny(modList, "fastmapcodec", "fast_map_codec")) {
                redundant.add("fastmapcodec");
                reasons.add("mod:fastmapcodec");
                this.disabledMixins.add("startup.BaseMapCodecMixin");
                this.disabledMixins.add("startup.StrictUnboundedMapCodecMixin");
            }
            if (hasAny(modList, "modernfix")) {
                redundant.add("modernfix");
                reasons.add("mod:modernfix");
                disableModernFixGroup();
            }
            if (hasEntityCulling) {
                redundant.add("entityculling");
                reasons.add("mod:entityculling");
                disableEntityCullingGroup();
            }
            if (hasParticleCore) {
                redundant.add("particle_core");
                reasons.add("mod:particle_core");
                this.disabledMixins.add("particle.ParticleCullingMixin");
                this.disabledMixins.add("particle.ParticleEngineMixin");
                this.disabledMixins.add("particle.QuadParticleGroupCullingMixin");
                this.disabledMixins.add("particle.SingleQuadParticleMixin");
                this.disabledMixins.add("particle.QuadParticleRenderStateMixin");
                this.disabledMixins.add("client.ClientLevelParticleSpawnMixin");
            }
            if (hasAsyncParticles) {
                redundant.add("asyncparticles");
                reasons.add("mod:asyncparticles");
                this.disabledMixins.add("particle.ParticleCullingMixin");
                this.disabledMixins.add("particle.ParticleEngineMixin");
                this.disabledMixins.add("particle.QuadParticleGroupCullingMixin");
                this.disabledMixins.add("particle.SingleQuadParticleMixin");
                this.disabledMixins.add("particle.QuadParticleRenderStateMixin");
                this.disabledMixins.add("client.ClientLevelParticleSpawnMixin");
            }
            if (hasMoreCulling) {
                redundant.add("moreculling");
                reasons.add("mod:moreculling");
                disableMoreCullingGroup();
            }
            if (hasEbe) {
                redundant.add("enhancedblockentities");
                reasons.add("mod:enhancedblockentities");
                disableEbeGroup();
                this.disabledMixins.add("blockentity.ShulkerBoxSleepMixin");
            }
            if (hasAny(modList, "krypton", "kreno", "krypton_fnp", "kryptonfoxified", "krypton_foxified", "pluto")) {
                redundant.add("krypton");
                reasons.add("mod:krypton");
                disableKryptonGroup();
            }
            if (hasAny(modList, "e4mc", "e4mc_minecraft")) {
                reasons.add("mod:e4mc");
                this.disabledMixins.add("network.CipherDecoderMixin");
                this.disabledMixins.add("network.CipherEncoderMixin");
            }
            if (hasAny(modList, "polymorph")) {
                reasons.add("mod:polymorph");
                this.disabledMixins.add("crafting.RecipeManagerFastMixin");
                this.disabledMixins.add("crafting.CraftingMenuCacheMixin");
            }
            if (hasAny(modList, "fastsuite")) {
                redundant.add("fastsuite");
                reasons.add("mod:fastsuite");
                this.disabledMixins.add("crafting.RecipeManagerFastMixin");
                this.disabledMixins.add("crafting.RecipeMapMixin");
            }
            if (hasAny(modList, "fastbench", "fastworkbench")) {
                redundant.add("fastworkbench");
                reasons.add("mod:fastworkbench");
                disableFastWorkbenchGroup();
            }
            if (hasAny(modList, "fastfurnace")) {
                redundant.add("fastfurnace");
                reasons.add("mod:fastfurnace");
                this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
            }
            if (hasAny(modList, "clumps")) {
                redundant.add("clumps");
                reasons.add("mod:clumps");
                this.disabledMixins.add("entity.ExperienceOrbFastMixin");
            }
            if (hasAny(modList, "servercore")) {
                redundant.add("servercore");
                reasons.add("mod:servercore");
                disableServerCoreGroup();
            }
            if (hasAny(modList, "noisium", "noisiumed")) {
                redundant.add("noisium");
                reasons.add("mod:noisium");
                disableNoisiumGroup();
            }
            if (hasBadOptimizations) {
                redundant.add("badoptimizations");
                reasons.add("mod:badoptimizations");
                disableBadOptimizationsModOverlap();
            }
            if (hasAny(modList, "fastmap")) {
                redundant.add("fastmap");
                reasons.add("mod:fastmap");
                disableFastMapGroup();
            }
            if (hasAny(modList, "smoothmaps")) {
                reasons.add("mod:smoothmaps");
                this.disabledMixins.add("render.PaintingRendererMixin");
                this.disabledMixins.add("render.PaintingRenderStateMixin");
            }
            applyBadOptimizationsModProperties(modList, reasons);
        }

        boolean cfgEntityCulling = PapiConfig.entityCulling();
        boolean cfgParticleCulling = PapiConfig.particleCulling();
        boolean cfgClientTickCulling = PapiConfig.clientTickCulling();
        boolean cfgMoreCulling = PapiConfig.moreCulling();
        boolean cfgBadOptimizations = PapiConfig.badOptimizations();

        boolean activeEntityCulling = cfgEntityCulling && !hasEntityCulling;
        boolean activeParticleCulling = cfgParticleCulling && !hasAsyncParticles && !hasParticleCore;
        boolean activeMoreCulling = cfgMoreCulling && !hasMoreCulling;
        boolean activeBadOptimizations = cfgBadOptimizations && !hasBadOptimizations;

        if (!cfgEntityCulling) {
            reasons.add("config:entityCulling");
            disableEntityCullingGroup();
        } else if (!cfgClientTickCulling) {
            reasons.add("config:clientTickCulling");
            this.disabledMixins.add("client.ClientLevelTickCullingMixin");
            this.disabledMixins.add("client.ClientLivingEntityTickMixin");
        }
        if (!cfgParticleCulling) {
            reasons.add("config:particleCulling");
            this.disabledMixins.add("particle.ParticleCullingMixin");
            this.disabledMixins.add("particle.QuadParticleGroupCullingMixin");
            this.disabledMixins.add("particle.SingleQuadParticleMixin");
            this.disabledMixins.add("particle.QuadParticleRenderStateMixin");
            this.disabledMixins.add("client.ClientLevelParticleSpawnMixin");
            this.disabledMixins.add("client.CampfireBlockEntityClientMixin");
        }
        if (!activeParticleCulling) {
            this.disabledMixins.add("particle.ParticleEngineMixin");
        }
        if (!activeEntityCulling && !activeBadOptimizations) {
            this.disabledMixins.add("render.EntityTypeMixin");
            this.disabledMixins.add("render.BlockEntityTypeMixin");
        }
        if (!activeEntityCulling && !activeParticleCulling) {
            this.disabledMixins.add("client.ClientChunkCacheOcclusionMixin");
            this.disabledMixins.add("client.ClientLevelOcclusionInvalidationMixin");
            this.disabledMixins.add("client.LevelChunkSectionOcclusionMixin");
        }
        if (!activeEntityCulling && !activeParticleCulling && !activeMoreCulling) {
            this.disabledMixins.add("client.LevelExtractorOcclusionMixin");
            this.disabledMixins.add("render.VitrailShadowPassMixin");
        }
        if (!cfgEbe) {
            reasons.add("config:ebe");
            disableEbeGroup();
        } else {
            if (!cfgEbeSodium) {
                reasons.add("config:ebeSodiumAdapter");
            }
            if (!cfgEbeVitrail) {
                reasons.add("config:ebeVitrailAdapter");
            }
        }
        if (!cfgMoreCulling) {
            reasons.add("config:moreCulling");
            disableMoreCullingGroup();
        }
        if (!activeMoreCulling && this.disabledMixins.contains("render.BlockEntityRenderDispatcherEbeMixin")) {
            this.disabledMixins.add("render.SignTextEmptyCacheMixin");
        }
        if (!PapiConfig.guiBatching()) {
            reasons.add("config:guiBatching");
            disableGuiBatchingGroup();
        }
        if (!PapiConfig.fontAtlasResizing()) {
            reasons.add("config:fontAtlasResizing");
            this.disabledMixins.add("client.FontTextureAtlasMixin");
        }
        if (!PapiConfig.mapAtlas()) {
            reasons.add("config:mapAtlas");
            this.disabledMixins.add("map.MapTextureManagerMixin");
            this.disabledMixins.add("map.MapGuiGraphicsMixin");
        }
        if (!PapiConfig.skipTextTranslucencySorting()) {
            reasons.add("config:skipTextTranslucencySorting");
            this.disabledMixins.add("render.RenderTypesTextSortMixin");
            this.disabledMixins.add("render.RenderSetupBuilderTextSortMixin");
        }
        if (!cfgBadOptimizations) {
            reasons.add("config:badOptimizations");
            disableBadOptimizationsGroup();
        }
        if (!PapiConfig.alternateCurrent()) {
            reasons.add("config:alternateCurrent");
            this.disabledMixins.add("redstone.DefaultRedstoneWireEvaluatorMixin");
            this.disabledMixins.add("redstone.ServerLevelRedstoneMixin");
        }
        if (!PapiConfig.lithiumDeep()) {
            reasons.add("config:lithiumDeep");
            disableLithiumGroup();
        }
        if (!PapiConfig.noisiumC2me()) {
            reasons.add("config:noisiumC2me");
            disableNoisiumGroup();
            disableC2meGroup();
        }
        if (!PapiConfig.fastFurnace()) {
            reasons.add("config:fastFurnace");
            this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
        }
        if (!PapiConfig.fastWorkbench()) {
            reasons.add("config:fastWorkbench");
            disableFastWorkbenchGroup();
        }
        if (!PapiConfig.clumps()) {
            reasons.add("config:clumps");
            this.disabledMixins.add("entity.ExperienceOrbFastMixin");
        }
        if (!PapiConfig.serverCoreMspt()) {
            reasons.add("config:serverCoreMspt");
            disableServerCoreGroup();
        }
        if (!PapiConfig.ferriteCore()) {
            reasons.add("config:ferriteCore");
            disableFerriteCoreGroup();
        }
        if (!PapiConfig.modernFix()) {
            reasons.add("config:modernFix");
            disableModernFixGroup();
        }
        if (!PapiConfig.krypton()) {
            reasons.add("config:krypton");
            disableKryptonGroup();
        }
        if (!PapiConfig.fastMap()) {
            reasons.add("config:fastMap");
            disableFastMapGroup();
        }

        DISABLE_REASONS.addAll(reasons);

        if (!redundant.isEmpty()) {
            LOGGER.info(
                    "Detected redundant optimization mod(s) {}; Gluon yielded overlapping mixins safely (these mods are unnecessary alongside Gluon).",
                    redundant
            );
        }
    }

    private void applyBadOptimizationsModProperties(LoadingModList modList, Set<String> reasons) {
        try {
            Method getMods = modList.getClass().getMethod("getMods");
            Object modsObj = getMods.invoke(modList);
            if (!(modsObj instanceof Iterable<?> mods)) {
                return;
            }
            Method getModProps = null;
            Method getConfigEl = null;
            for (Object mod : mods) {
                if (mod == null) {
                    continue;
                }
                if (getModProps == null) {
                    try {
                        getModProps = mod.getClass().getMethod("getModProperties");
                    } catch (NoSuchMethodException ignored) {
                    }
                    try {
                        getConfigEl = mod.getClass().getMethod("getConfigElement", String[].class);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (getModProps != null) {
                    Object propsObj = getModProps.invoke(mod);
                    if (propsObj instanceof Map<?, ?> props) {
                        Object bo = props.get("badoptimizations");
                        if (bo instanceof Map<?, ?> boMap) {
                            applyBoMapFlags(boMap, reasons);
                        }
                    }
                }
                if (getConfigEl != null) {
                    checkBoConfigKey(mod, getConfigEl, "lightmap", "render.LightmapRenderStateExtractorMixin", reasons);
                    checkBoConfigKey(mod, getConfigEl, "sky_color", "render.LightmapRenderStateExtractorMixin", reasons);
                    checkBoConfigKey(mod, getConfigEl, "entity_renderer", "render.EntityRenderDispatcherCacheMixin", reasons);
                    checkBoConfigKey(mod, getConfigEl, "block_entity_renderer", "render.BlockEntityRenderDispatcherCacheMixin", reasons);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyBoMapFlags(Map<?, ?> boMap, Set<String> reasons) {
        if (Boolean.FALSE.equals(boMap.get("lightmap")) || Boolean.FALSE.equals(boMap.get("sky_color"))) {
            reasons.add("modprop:badoptimizations.lightmap");
            this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
        }
        if (Boolean.FALSE.equals(boMap.get("entity_renderer"))) {
            reasons.add("modprop:badoptimizations.entity_renderer");
            this.disabledMixins.add("render.EntityRenderDispatcherCacheMixin");
        }
        if (Boolean.FALSE.equals(boMap.get("block_entity_renderer"))) {
            reasons.add("modprop:badoptimizations.block_entity_renderer");
            this.disabledMixins.add("render.BlockEntityRenderDispatcherCacheMixin");
        }
    }

    private void checkBoConfigKey(Object mod, Method getConfigEl, String key, String mixin, Set<String> reasons) {
        try {
            Object optObj = getConfigEl.invoke(mod, (Object) new String[]{"modproperties", "badoptimizations", key});
            if (optObj instanceof Optional<?> opt && opt.isPresent() && Boolean.FALSE.equals(opt.get())) {
                reasons.add("modprop:badoptimizations." + key);
                this.disabledMixins.add(mixin);
            }
        } catch (Throwable ignored) {
        }
    }

    private void disableEntityCullingGroup() {
        this.disabledMixins.add("render.EntityRenderDispatcherMixin");
        this.disabledMixins.add("render.EntityRendererCullingBoxMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherMixin");
        this.disabledMixins.add("render.LivingEntityRendererLodMixin");
        this.disabledMixins.add("client.ClientLevelTickCullingMixin");
        this.disabledMixins.add("client.ClientLivingEntityTickMixin");
    }

    private void disableEbeRuntimeMixins() {
        this.disabledMixins.add("blockentity.StaticLidBlockEntityMixin");
        this.disabledMixins.add("client.BannerEbeClientMixin");
        this.disabledMixins.add("client.BellEbeClientMixin");
        this.disabledMixins.add("client.BlockColorsEbeMixin");
        this.disabledMixins.add("client.ChestEbeClientMixin");
        this.disabledMixins.add("client.DecoratedPotEbeClientMixin");
        this.disabledMixins.add("client.EnderChestEbeClientMixin");
        this.disabledMixins.add("client.ShulkerBoxEbeClientMixin");
        this.disabledMixins.add("client.SpriteSourceListEbeMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherEbeMixin");
    }

    private void disableEbeGroup() {
        disableEbeRuntimeMixins();
        this.disabledMixins.add("render.StaticBlockEntityRenderMixin");
        this.disabledMixins.add("render.SectionCompilerEbeMixin");
        this.disabledMixins.add("render.SectionCompilerResultsEbeMixin");
        this.disabledMixins.add("render.CompiledSectionMeshEbeMixin");
        this.disabledMixins.add("render.RenderSectionEbeCommitMixin");
        this.disabledMixins.add("render.SodiumEbeMixin");
        this.disabledMixins.add("render.SodiumEbeCommitMixin");
        this.disabledMixins.add("render.VitrailEbeMixin");
    }

    private void disableMoreCullingGroup() {
        this.disabledMixins.add("client.SpriteContentsOpacityMixin");
        this.disabledMixins.add("map.MapRenderStateMixin");
        this.disabledMixins.add("render.AbstractSignRendererMixin");
        this.disabledMixins.add("render.BlockModelFeatureRendererMixin");
        this.disabledMixins.add("render.BlockModelRenderStateMixin");
        this.disabledMixins.add("render.BlockModelSubmitMixin");
        this.disabledMixins.add("render.BlockStateBaseCullingMixin");
        this.disabledMixins.add("render.EndGatewayBlockMixin");
        this.disabledMixins.add("render.EndPortalRendererMixin");
        this.disabledMixins.add("render.ItemFrameRendererMixin");
        this.disabledMixins.add("render.ItemQuadsCullingMixin");
        this.disabledMixins.add("render.ItemStackLayerCullingMixin");
        this.disabledMixins.add("render.LeavesBlockMixin");
        this.disabledMixins.add("render.MangroveRootsBlockMixin");
        this.disabledMixins.add("render.MoreBlockCullingMixin");
        this.disabledMixins.add("render.OrderedSubmitNodeCollectorMixin");
        this.disabledMixins.add("render.PaintingRendererMixin");
        this.disabledMixins.add("render.PaintingRenderStateMixin");
        this.disabledMixins.add("render.PowderSnowBlockMixin");
        this.disabledMixins.add("render.ShulkerBoxRendererMixin");
        this.disabledMixins.add("render.ShulkerBoxRenderStateMixin");
        this.disabledMixins.add("render.SodiumBlockOcclusionMixin");
        this.disabledMixins.add("render.SubmitNodeCollectionMixin");
        this.disabledMixins.add("render.SubmitNodeStorageMixin");
        this.disabledMixins.add("weather.WeatherEffectRendererMixin");
        this.disabledMixins.add("weather.WeatherPrecipitationCullingMixin");
    }

    private void disableGuiBatchingGroup() {
        this.disabledMixins.add("client.FontMixin");
        this.disabledMixins.add("client.GuiItemAtlasMixin");
        this.disabledMixins.add("client.GuiRenderStateMixin");
        this.disabledMixins.add("client.GuiRenderStateNodeMixin");
        this.disabledMixins.add("client.GuiRendererBatchMixin");
        this.disabledMixins.add("client.ScissorStateMixin");
        this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
        this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
        this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
        this.disabledMixins.add("render.TextGlyphRendererFastMixin");
    }

    private void disableBadOptimizationsModOverlap() {
        this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
        this.disabledMixins.add("client.ClientLevelSkyFlashAccessor");
        this.disabledMixins.add("render.EntityRenderDispatcherCacheMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherCacheMixin");
        this.disabledMixins.add("client.DebugRendererMixin");
        this.disabledMixins.add("client.ToastManagerMixin");
        this.disabledMixins.add("particle.ParticleEngineExtractFastMixin");
        this.disabledMixins.add("client.AbstractClientPlayerFovMixin");
        this.disabledMixins.add("client.TutorialMixin");
    }

    private void disableBadOptimizationsGroup() {
        disableBadOptimizationsModOverlap();
        this.disabledMixins.add("render.FogRendererMixin");
        this.disabledMixins.add("render.SkyRendererMixin");
        this.disabledMixins.add("render.EntityRendererShadowFastMixin");
        this.disabledMixins.add("client.ClientTelemetryStubMixin");
        this.disabledMixins.add("worldgen.EnvironmentAttributeProbeMixin");
    }

    private void disableFastWorkbenchGroup() {
        this.disabledMixins.add("crafting.AbstractCraftingMenuMixin");
        this.disabledMixins.add("crafting.CraftingMenuCacheMixin");
        this.disabledMixins.add("crafting.ResultContainerCacheMixin");
        this.disabledMixins.add("crafting.TransientCraftingContainerBatchMixin");
        this.disabledMixins.add("crafting.ResultSlotFastMixin");
        this.disabledMixins.add("redstone.CrafterBlockFastMixin");
        this.disabledMixins.add("client.RecipeBookTickMixin");
    }

    private void disableFastMapGroup() {
        this.disabledMixins.add("map.EntityMapCleanupMixin");
        this.disabledMixins.add("map.MapItemSavedDataAccessor");
        this.disabledMixins.add("map.MapItemSavedDataMixin");
        this.disabledMixins.add("map.MapItemUpdateMixin");
        this.disabledMixins.add("map.ServerEntityMapTrackingMixin");
        this.disabledMixins.add("map.ServerPlayerMapTrackingMixin");
        this.disabledMixins.add("map.HoldingPlayerMixin");
        this.disabledMixins.add("map.MapRendererMixin");
        this.disabledMixins.add("map.MapInstanceTextureMixin");
        this.disabledMixins.add("map.MapRenderStateMixin");
        this.disabledMixins.add("map.MapTextureManagerMixin");
        this.disabledMixins.add("map.MapGuiGraphicsMixin");
    }

    private void disableMoonriseGroup() {
        disableC2meGroup();
        this.disabledMixins.add("chunk.PalettedContainerCountMixin");
        this.disabledMixins.add("chunk.SimpleBitStorageMixin");
        this.disabledMixins.add("chunk.ZeroBitStorageMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackedEntityMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackingStateMixin");
        this.disabledMixins.add("worldgen.ServerLevelRandomTickMixin");
        this.disabledMixins.add("worldgen.NaturalSpawnerMixin");
        this.disabledMixins.add("worldgen.LocalMobCapFastMixin");
        this.disabledMixins.add("worldgen.FlowingFluidMixin");
        this.disabledMixins.add("worldgen.FluidStateCacheMixin");
        this.disabledMixins.add("worldgen.PerlinNoiseFastMixin");
        this.disabledMixins.add("poi.PoiManagerMixin");
        this.disabledMixins.add("poi.PoiSectionFastMixin");
        this.disabledMixins.add("poi.PoiRecordFastMixin");
        this.disabledMixins.add("poi.AcquirePoiFastMixin");
        this.disabledMixins.add("poi.PortalForcerFastMixin");
        this.disabledMixins.add("poi.SectionStorageColumnIndexMixin");
        this.disabledMixins.add("biome.BiomeManagerMixin");
        this.disabledMixins.add("biome.BiomeFastMixin");
    }

    private void disableLithiumGroup() {
        this.disabledMixins.add("biome.BiomeFastMixin");
        this.disabledMixins.add("biome.BiomeManagerMixin");
        this.disabledMixins.add("biome.MobSpawnSettingsFastMixin");
        this.disabledMixins.add("block.BlockPatternFastMixin");
        this.disabledMixins.add("block.ComposterEmptyZeroAllocMixin");
        this.disabledMixins.add("block.ComposterOutputZeroAllocMixin");
        this.disabledMixins.add("block.ComposterZeroAllocMixin");
        this.disabledMixins.add("block.LeavesDecayOptimizationMixin");
        this.disabledMixins.add("blockentity.BellBlockEntityMixin");
        this.disabledMixins.add("blockentity.CampfireBlockEntityMixin");
        this.disabledMixins.add("blockentity.FastBeaconMixin");
        this.disabledMixins.add("blockentity.FastBeehiveMixin");
        this.disabledMixins.add("blockentity.FastBrewingStandMixin");
        this.disabledMixins.add("blockentity.FastConduitMixin");
        this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
        this.disabledMixins.add("blockentity.FastHopperMixin");
        this.disabledMixins.add("blockentity.ShulkerBoxSleepMixin");
        this.disabledMixins.add("chunk.ChunkAccessHeightCacheMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackedEntityMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackingStateMixin");
        this.disabledMixins.add("chunk.CrudeIncrementalBiMapFastMixin");
        this.disabledMixins.add("chunk.HashMapPaletteFastMixin");
        this.disabledMixins.add("chunk.LevelChunkFastBlockEntityMixin");
        this.disabledMixins.add("chunk.LevelChunkTicksFastMixin");
        this.disabledMixins.add("chunk.PalettedContainerCountMixin");
        this.disabledMixins.add("chunk.ServerChunkCacheMixin");
        this.disabledMixins.add("chunk.SimpleBitStorageMixin");
        this.disabledMixins.add("chunk.TicketStorageExpiringMixin");
        this.disabledMixins.add("chunk.ZeroBitStorageMixin");
        this.disabledMixins.add("core.BlockPosMixin");
        this.disabledMixins.add("core.ClassInstanceMultiMapFastMixin");
        this.disabledMixins.add("core.LevelHeightCacheMixin");
        this.disabledMixins.add("core.SortedArraySetFastMixin");
        this.disabledMixins.add("entity.AbstractBoatFastMixin");
        this.disabledMixins.add("entity.AgeableWaterCreatureFastMixin");
        this.disabledMixins.add("entity.AllayClientFastMixin");
        this.disabledMixins.add("entity.AttributeInstanceFastMixin");
        this.disabledMixins.add("entity.AttributeMapFastMixin");
        this.disabledMixins.add("entity.BehaviorMemoryCacheMixin");
        this.disabledMixins.add("entity.BrainFastMixin");
        this.disabledMixins.add("entity.CopperGolemClientFastMixin");
        this.disabledMixins.add("entity.EntityEquipmentFastMixin");
        this.disabledMixins.add("entity.EntityFastFluidMixin");
        this.disabledMixins.add("entity.EntityFluidInteractionMixin");
        this.disabledMixins.add("entity.EntityPassengersFastMixin");
        this.disabledMixins.add("entity.EntitySectionIterationMixin");
        this.disabledMixins.add("entity.EntitySectionStorageMixin");
        this.disabledMixins.add("entity.EntitySprintParticlesMixin");
        this.disabledMixins.add("entity.FrogAttackablesSensorMixin");
        this.disabledMixins.add("entity.GateBehaviorFastMixin");
        this.disabledMixins.add("entity.GoalFlagMixin");
        this.disabledMixins.add("entity.GoalSelectorFastMixin");
        this.disabledMixins.add("entity.GolemRandomStrollInVillageGoalMixin");
        this.disabledMixins.add("entity.HoglinSpecificSensorMixin");
        this.disabledMixins.add("entity.InventoryFastMixin");
        this.disabledMixins.add("entity.ItemEntityMergeMixin");
        this.disabledMixins.add("entity.LevelEntityQueryMixin");
        this.disabledMixins.add("entity.LevelPushableMixin");
        this.disabledMixins.add("entity.LivingEntityEquipmentEpochMixin");
        this.disabledMixins.add("entity.LivingEntityFastTickMixin");
        this.disabledMixins.add("entity.LlamaFollowCaravanGoalMixin");
        this.disabledMixins.add("entity.LongJumpToRandomPosFastMixin");
        this.disabledMixins.add("entity.MemorySlotFastMixin");
        this.disabledMixins.add("entity.MobClientAmbientFastMixin");
        this.disabledMixins.add("entity.MoveToBlockGoalFastMixin");
        this.disabledMixins.add("entity.NearestItemSensorMixin");
        this.disabledMixins.add("entity.ObtainRaidLeaderBannerGoalMixin");
        this.disabledMixins.add("entity.OldMinecartBehaviorFastMixin");
        this.disabledMixins.add("entity.PiglinSpecificSensorMixin");
        this.disabledMixins.add("entity.RaidFastMixin");
        this.disabledMixins.add("entity.RaiderBannerCacheMixin");
        this.disabledMixins.add("entity.RemoveBlockGoalFastMixin");
        this.disabledMixins.add("entity.SensorFastMixin");
        this.disabledMixins.add("entity.ServerEntityFastMixin");
        this.disabledMixins.add("entity.TemptingSensorMixin");
        this.disabledMixins.add("entity.VillagerGoalPackagesFastMixin");
        this.disabledMixins.add("entity.WrappedGoalMixin");
        this.disabledMixins.add("entity.villager.SecondaryPoiSensorOptimizationMixin");
        this.disabledMixins.add("explosion.EntityBasedExplosionDamageCalculatorMixin");
        this.disabledMixins.add("explosion.ServerExplosionMixin");
        this.disabledMixins.add("map.EntityMapCleanupMixin");
        this.disabledMixins.add("map.HoldingPlayerMixin");
        this.disabledMixins.add("map.MapItemSavedDataAccessor");
        this.disabledMixins.add("map.MapItemSavedDataMixin");
        this.disabledMixins.add("map.ServerEntityMapTrackingMixin");
        this.disabledMixins.add("map.ServerPlayerMapTrackingMixin");
        this.disabledMixins.add("math.AABBMixin");
        this.disabledMixins.add("math.AxisCycleFastMixin");
        this.disabledMixins.add("math.DirectionFastMixin");
        this.disabledMixins.add("math.MthMixin");
        this.disabledMixins.add("math.Vec3Mixin");
        this.disabledMixins.add("memory.EntityTickListLeakMixin");
        this.disabledMixins.add("memory.PalettedContainerThreadingMixin");
        this.disabledMixins.add("pathfinding.BinaryHeapFastMixin");
        this.disabledMixins.add("pathfinding.BlockStateBasePathTypeMixin");
        this.disabledMixins.add("pathfinding.HappyGhastNavigationMixin");
        this.disabledMixins.add("pathfinding.LivingEntityNavigationMixin");
        this.disabledMixins.add("pathfinding.MobNavigationTrackMixin");
        this.disabledMixins.add("pathfinding.NodeEvaluatorAccessor");
        this.disabledMixins.add("pathfinding.PathFinderMixin");
        this.disabledMixins.add("pathfinding.PathNavigationMixin");
        this.disabledMixins.add("pathfinding.PathNavigationRegionFastMixin");
        this.disabledMixins.add("pathfinding.ServerEntityCallbacksNavigationMixin");
        this.disabledMixins.add("pathfinding.ServerLevelNavigationMixin");
        this.disabledMixins.add("pathfinding.WalkNodeEvaluatorFastMixin");
        this.disabledMixins.add("physics.BlockCollisionsMixin");
        this.disabledMixins.add("physics.BlockFullShapeCacheMixin");
        this.disabledMixins.add("physics.BlockGetterRaycastMixin");
        this.disabledMixins.add("physics.CollisionGetterFreePosMixin");
        this.disabledMixins.add("physics.CubePointRangeMixin");
        this.disabledMixins.add("physics.CubeVoxelShapeMixin");
        this.disabledMixins.add("physics.EntityCollideMixin");
        this.disabledMixins.add("physics.EntityCollisionContextMixin");
        this.disabledMixins.add("physics.LevelCollisionFastMixin");
        this.disabledMixins.add("physics.ShapesCollideMixin");
        this.disabledMixins.add("physics.StepBasedCollectorFastMixin");
        this.disabledMixins.add("physics.VoxelShapeCacheMixin");
        this.disabledMixins.add("physics.WorldBorderFastMixin");
        this.disabledMixins.add("piston.PistonBaseBlockMixin");
        this.disabledMixins.add("piston.PistonMovingBlockEntityMixin");
        this.disabledMixins.add("piston.PistonStructureResolverMixin");
        this.disabledMixins.add("piston.ServerLevelBlockEventsMixin");
        this.disabledMixins.add("poi.AcquirePoiFastMixin");
        this.disabledMixins.add("poi.PoiManagerMixin");
        this.disabledMixins.add("poi.PoiRecordFastMixin");
        this.disabledMixins.add("poi.PoiSectionFastMixin");
        this.disabledMixins.add("poi.PortalForcerFastMixin");
        this.disabledMixins.add("poi.SectionStorageAccessor");
        this.disabledMixins.add("poi.SectionStorageColumnIndexMixin");
        this.disabledMixins.add("projectile.ProjectileUtilFastMixin");
        this.disabledMixins.add("redstone.ComparatorBlockOptimizationMixin");
        this.disabledMixins.add("redstone.RedstoneWireBlockFastMixin");
        this.disabledMixins.add("redstone.RedstoneWireEvaluatorFastMixin");
        this.disabledMixins.add("sculk.EuclideanGameEventListenerRegistryMixin");
        this.disabledMixins.add("sculk.GameEventDispatcherMixin");
        this.disabledMixins.add("sculk.LevelChunkListenerMixin");
        this.disabledMixins.add("sculk.ServerLevelGameEventMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.BlockStatePairKeyHashMixin");
        this.disabledMixins.add("worldgen.FlowingFluidMixin");
        this.disabledMixins.add("worldgen.FluidStateCacheMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.NaturalSpawnerMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.ServerLevelRandomTickMixin");
    }

    private void disableC2meGroup() {
        this.disabledMixins.add("chunk.ChunkAccessHeightCacheMixin");
        this.disabledMixins.add("chunk.ChunkAccessReferencesMixin");
        this.disabledMixins.add("chunk.ChunkHolderAccessMixin");
        this.disabledMixins.add("chunk.ChunkHolderStatusGuardMixin");
        this.disabledMixins.add("chunk.ChunkMapAutosaveOptimizerMixin");
        this.disabledMixins.add("chunk.ChunkStatusTasksDeadlockMixin");
        this.disabledMixins.add("chunk.CrudeIncrementalBiMapFastMixin");
        this.disabledMixins.add("chunk.HashMapPaletteFastMixin");
        this.disabledMixins.add("chunk.LevelChunkTicksFastMixin");
        this.disabledMixins.add("chunk.ProtoChunkFastBlockMixin");
        this.disabledMixins.add("chunk.ServerChunkCacheMixin");
        this.disabledMixins.add("chunk.TicketStorageExpiringMixin");
        this.disabledMixins.add("core.CompoundTag1FastMixin");
        this.disabledMixins.add("core.CompoundTagFastMixin");
        this.disabledMixins.add("core.IdentifierCacheMixin");
        this.disabledMixins.add("core.ListTagFastMixin");
        this.disabledMixins.add("core.SortedArraySetFastMixin");
        this.disabledMixins.add("lighting.ThreadedLevelLightEngineMixin");
        this.disabledMixins.add("memory.PalettedContainerThreadingMixin");
        this.disabledMixins.add("threading.UtilWorkerPriorityMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.BeardifierMixin");
        this.disabledMixins.add("worldgen.ChunkGeneratorStructureStateMixin");
        this.disabledMixins.add("worldgen.ChunkGeneratorStructureSyncMixin");
        this.disabledMixins.add("worldgen.ClimateRTreeFastMixin");
        this.disabledMixins.add("worldgen.ClimateRTreeNodeMixin");
        this.disabledMixins.add("worldgen.ConcentricRingsPlacementMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
        this.disabledMixins.add("worldgen.InterpolatedSamplerFastMixin");
        this.disabledMixins.add("worldgen.JigsawPlacementMixin");
        this.disabledMixins.add("worldgen.JigsawPlacementPlacerMixin");
        this.disabledMixins.add("worldgen.JigsawReplacementProcessorMixin");
        this.disabledMixins.add("worldgen.LegacyRandomWorldgenMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.LocalMobCapCalculatorMixin");
        this.disabledMixins.add("worldgen.MaterialRuleContextMixin");
        this.disabledMixins.add("worldgen.MaterialSystemBufferMixin");
        this.disabledMixins.add("worldgen.MinecraftServerSpawnMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.SequenceRuleFastMixin");
        this.disabledMixins.add("worldgen.SinglePoolElementMixin");
        this.disabledMixins.add("worldgen.StaticCache2DMixin");
        this.disabledMixins.add("worldgen.StructurePiecesBuilderMixin");
        this.disabledMixins.add("worldgen.UnsyncedRandomInitMixin");
        this.disabledMixins.add("worldgen.WorldGenRegionMixin");
    }

    private void disableNoisiumGroup() {
        this.disabledMixins.add("biome.BiomeFastMixin");
        this.disabledMixins.add("biome.BiomeManagerMixin");
        this.disabledMixins.add("chunk.ProtoChunkFastBlockMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.BeardifierMixin");
        this.disabledMixins.add("worldgen.CombiningPredicateMixin");
        this.disabledMixins.add("worldgen.DebugLevelSourceFastMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
        this.disabledMixins.add("worldgen.InterpolatedSamplerFastMixin");
        this.disabledMixins.add("worldgen.LegacyRandomWorldgenMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.MaterialRuleContextMixin");
        this.disabledMixins.add("worldgen.MaterialSystemBufferMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.OreFeatureMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.PerlinNoiseFastMixin");
        this.disabledMixins.add("worldgen.SequenceRuleFastMixin");
        this.disabledMixins.add("worldgen.SimplexNoiseFastMixin");
        this.disabledMixins.add("worldgen.SmearedPerlinNoiseFastMixin");
        this.disabledMixins.add("worldgen.StateTestingPredicateMixin");
        this.disabledMixins.add("worldgen.UnsyncedRandomInitMixin");
    }

    private void disableServerCoreGroup() {
        this.disabledMixins.add("block.LiquidBlockRandomTickMixin");
        this.disabledMixins.add("blockentity.BaseCommandBlockFastMixin");
        this.disabledMixins.add("chunk.ChunkMapAutosaveOptimizerMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackedEntityMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackingStateMixin");
        this.disabledMixins.add("entity.ArmorStandOptimizationMixin");
        this.disabledMixins.add("entity.BeeSyncLoadMixin");
        this.disabledMixins.add("entity.LivingEntityArmorStandTickMixin");
        this.disabledMixins.add("entity.MobAiThrottlerMixin");
        this.disabledMixins.add("entity.MobDespawnFastPathMixin");
        this.disabledMixins.add("entity.RemoveBlockGoalSyncMixin");
        this.disabledMixins.add("entity.villager.SleepInBedSyncMixin");
        this.disabledMixins.add("entity.villager.VillagerLobotomyMixin");
        this.disabledMixins.add("redstone.LevelComparatorSyncLoadMixin");
        this.disabledMixins.add("sculk.DynamicGameEventListenerSyncMixin");
        this.disabledMixins.add("weather.LevelChunkLightningMixin");
        this.disabledMixins.add("worldgen.ChunkGeneratorStructureSyncMixin");
        this.disabledMixins.add("worldgen.LocalMobCapCalculatorMixin");
        this.disabledMixins.add("worldgen.LocalMobCapFastMixin");
        this.disabledMixins.add("worldgen.NaturalSpawnerMixin");
        this.disabledMixins.add("worldgen.PatrolSpawnerFastMixin");
    }

    private void disableFerriteCoreGroup() {
        this.disabledMixins.add("chunk.PalettedContainerCountMixin");
        this.disabledMixins.add("client.QuadCollectionBuilderDeduplicationMixin");
        this.disabledMixins.add("core.CompoundTag1FastMixin");
        this.disabledMixins.add("core.CompoundTagFastMixin");
        this.disabledMixins.add("core.IdentifierCacheMixin");
        this.disabledMixins.add("core.ListTagFastMixin");
        this.disabledMixins.add("core.PatchedDataComponentMapMixin");
        this.disabledMixins.add("memory.ArrayVoxelShapeAccessor");
        this.disabledMixins.add("memory.BitSetDiscreteVoxelShapeAccessor");
        this.disabledMixins.add("memory.BlockBehaviourCacheDeduplicationMixin");
        this.disabledMixins.add("memory.BlockCapabilityAccessor");
        this.disabledMixins.add("memory.BlockStateCacheDeduplicationMixin");
        this.disabledMixins.add("memory.CapabilityHooksCompactMixin");
        this.disabledMixins.add("memory.EntityCapabilityAccessor");
        this.disabledMixins.add("memory.ItemCapabilityAccessor");
        this.disabledMixins.add("memory.MappedRegistryCompactMixin");
        this.disabledMixins.add("memory.PalettedContainerThreadingMixin");
        this.disabledMixins.add("memory.PropertyInternMixin");
        this.disabledMixins.add("memory.RegisterCapabilitiesEventTrackMixin");
        this.disabledMixins.add("memory.SliceShapeAccessor");
        this.disabledMixins.add("memory.StateDefinitionRadixMixin");
        this.disabledMixins.add("memory.StateHolderKeysAccessor");
        this.disabledMixins.add("memory.StateHolderRadixMixin");
        this.disabledMixins.add("memory.SubShapeAccessor");
        this.disabledMixins.add("memory.VoxelShapeMemoryAccessor");
        this.disabledMixins.add("memory.WallBlockShapeDeduplicationMixin");
        this.disabledMixins.add("startup.TagEntryCacheMixin");
        this.disabledMixins.add("startup.TagOrElementLocationCacheMixin");
    }

    private void disableModernFixGroup() {
        this.disabledMixins.add("chunk.ChunkStatusTasksDeadlockMixin");
        this.disabledMixins.add("chunk.ImposterProtoChunkCompactMixin");
        this.disabledMixins.add("chunk.PalettedContainerCompactReadMixin");
        this.disabledMixins.add("client.BlockColorsSafetyMixin");
        this.disabledMixins.add("client.ClientChunkCacheLeakMixin");
        this.disabledMixins.add("client.ClientChunkStorageLeakMixin");
        this.disabledMixins.add("client.ClientConfigurationPacketListenerMixin");
        this.disabledMixins.add("client.ClientLevelLeakMixin");
        this.disabledMixins.add("client.IntegratedServerPriorityMixin");
        this.disabledMixins.add("client.LevelLeakAccessor");
        this.disabledMixins.add("client.LivingEntityRendererLayerSafetyMixin");
        this.disabledMixins.add("client.MemoryLeakFixMixin");
        this.disabledMixins.add("client.ModelManagerMemoryMixin");
        this.disabledMixins.add("client.PoseStackAccessor");
        this.disabledMixins.add("client.QuadCollectionBuilderDeduplicationMixin");
        this.disabledMixins.add("client.ReloadableResourceManagerSafetyMixin");
        this.disabledMixins.add("client.SessionSearchTreesMixin");
        this.disabledMixins.add("client.UnihexProviderFastMixin");
        this.disabledMixins.add("core.IdentifierCacheMixin");
        this.disabledMixins.add("core.ReloadableServerResourcesMixin");
        this.disabledMixins.add("crafting.RecipeManagerFastMixin");
        this.disabledMixins.add("crafting.RecipeMapMixin");
        this.disabledMixins.add("memory.EntityTickListLeakMixin");
        this.disabledMixins.add("memory.MappedRegistryCompactMixin");
        this.disabledMixins.add("memory.PropertyInternMixin");
        this.disabledMixins.add("memory.WallBlockShapeDeduplicationMixin");
        this.disabledMixins.add("render.PoseStackGuardMixin");
        this.disabledMixins.add("startup.BaseMapCodecMixin");
        this.disabledMixins.add("startup.CreativeModeTabMemoMixin");
        this.disabledMixins.add("startup.DataFixTypesMixin");
        this.disabledMixins.add("startup.DataFixerUpperMixin");
        this.disabledMixins.add("startup.DataFixersMixin");
        this.disabledMixins.add("startup.FilePackResourcesMixin");
        this.disabledMixins.add("startup.MinecraftServerReloadExecutorMixin");
        this.disabledMixins.add("startup.PathPackResourcesMixin");
        this.disabledMixins.add("startup.SharedZipFileAccessAccessor");
        this.disabledMixins.add("startup.StrictUnboundedMapCodecMixin");
        this.disabledMixins.add("startup.TagEntryCacheMixin");
        this.disabledMixins.add("startup.TagOrElementLocationCacheMixin");
        this.disabledMixins.add("startup.VanillaRegistriesMemoMixin");
        this.disabledMixins.add("threading.UtilWorkerPriorityMixin");
        this.disabledMixins.add("worldgen.ChunkGeneratorStructureStateMixin");
        this.disabledMixins.add("worldgen.ConcentricRingsPlacementMixin");
        this.disabledMixins.add("worldgen.MinecraftServerSpawnMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
    }

    private void disableKryptonGroup() {
        this.disabledMixins.add("entity.ServerEntityFastMixin");
        this.disabledMixins.add("network.ChunkMapFlushMixin");
        this.disabledMixins.add("network.CipherBaseAccessor");
        this.disabledMixins.add("network.CipherDecoderMixin");
        this.disabledMixins.add("network.CipherEncoderMixin");
        this.disabledMixins.add("network.CompressionDecoderMixin");
        this.disabledMixins.add("network.CompressionEncoderMixin");
        this.disabledMixins.add("network.ConnectionFlushConsolidationMixin");
        this.disabledMixins.add("network.LegacyQueryHandlerMixin");
        this.disabledMixins.add("network.PacketProcessorMixin");
        this.disabledMixins.add("network.PlayerChunkSenderFlushMixin");
        this.disabledMixins.add("network.ServerChunkCacheFlushMixin");
        this.disabledMixins.add("network.ServerStatusPacketListenerImplMixin");
        this.disabledMixins.add("network.SynchedEntityDataMixin");
        this.disabledMixins.add("network.Utf8StringMixin");
        this.disabledMixins.add("network.VarIntMixin");
        this.disabledMixins.add("network.VarLongMixin");
        this.disabledMixins.add("network.Varint21FrameDecoderMixin");
        this.disabledMixins.add("network.Varint21LengthFieldPrependerMixin");
    }

    private static boolean hasAny(LoadingModList modList, String... modIds) {
        for (String id : modIds) {
            if (modList.getModFileById(id) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!this.mixinPrefix.isEmpty() && !mixinClassName.startsWith(this.mixinPrefix)) {
            return false;
        }
        String rel = !this.mixinPrefix.isEmpty()
                ? mixinClassName.substring(this.mixinPrefix.length())
                : mixinClassName;
        boolean apply = !(this.disableLightingPackage && rel.startsWith("lighting."))
                && !this.disabledMixins.contains(rel);
        if (apply) {
            APPLIED_MIXINS.add(rel);
            SKIPPED_MIXINS.remove(rel);
        } else {
            SKIPPED_MIXINS.add(rel);
            APPLIED_MIXINS.remove(rel);
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
