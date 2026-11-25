package net.ashpapi.papioptimization.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PapiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/Config");
    private static final String FILE_NAME = "gluon.properties";

    private static final String DEFAULT_ENTITY_CULL_WHITELIST =
            "botania:mana_burst,drg_flares:drg_flares,quark:soul_bead";
    private static final String DEFAULT_BLOCK_ENTITY_CULL_WHITELIST =
            "minecraft:beacon,create:rope_pulley,create:hose_pulley,botania:magic_missile,botania:flame_ring,botania:falling_star,betterend:eternal_pedestal";

    private static volatile boolean loaded = false;

    private static volatile boolean entityCulling = true;
    private static volatile boolean particleCulling = true;
    private static volatile boolean renderNametagsThroughWalls = true;
    private static volatile boolean clientTickCulling = true;
    private static volatile boolean solidLeaves = false;
    private static volatile boolean f3DebugInfo = true;
    private static volatile boolean ebe = true;
    private static volatile boolean ebeSodiumAdapter = true;
    private static volatile boolean ebeVitrailAdapter = true;
    private static volatile boolean moreCulling = true;
    private static volatile boolean blockStateCulling = true;
    private static volatile LeavesCullingMode leavesCullingMode = LeavesCullingMode.DEPTH;
    private static volatile int leavesCullingDepth = 2;
    private static volatile boolean includeMangroveRoots = false;
    private static volatile boolean endGatewayCulling = false;
    private static volatile boolean cullModdedBlocksByDefault = true;
    private static volatile boolean guiBatching = true;
    private static volatile boolean fontAtlasResizing = true;
    private static volatile boolean mapAtlas = true;
    private static volatile boolean skipTextTranslucencySorting = true;
    private static volatile boolean badOptimizations = true;
    private static volatile boolean alternateCurrent = true;
    private static volatile boolean alternateCurrentRuntime = true;
    private static volatile boolean lithiumDeep = true;
    private static volatile boolean noisiumC2me = true;
    private static volatile boolean fastFurnace = true;
    private static volatile boolean fastWorkbench = true;
    private static volatile boolean clumps = true;
    private static volatile boolean serverCoreMspt = true;
    private static volatile boolean ferriteCore = true;
    private static volatile boolean modernFix = true;
    private static volatile boolean krypton = true;
    private static volatile boolean fastMap = true;
    private static volatile int networkCompressionLevel = 4;

    private static volatile double msptTarget = 45.0;
    private static volatile double particleLodDistance = 30.0;
    private static volatile double particleLodDistanceSqr = 900.0;
    private static volatile double tickCullingMinDistance = 16.0;
    private static volatile double itemFrameLodRange = 16.0;
    private static volatile double itemFrameLodRangeSqr = 256.0;
    private static volatile double itemFrame3FaceRange = 4.0;
    private static volatile double itemFrame3FaceRangeSqr = 16.0;

    private static volatile String entityCullWhitelistRaw = DEFAULT_ENTITY_CULL_WHITELIST;
    private static volatile String blockEntityCullWhitelistRaw = DEFAULT_BLOCK_ENTITY_CULL_WHITELIST;
    private static volatile String tickCullWhitelistRaw = "";
    private static volatile String blockCullBlacklistRaw = "";

    private static volatile Set<String> entityCullWhitelistSet = parseIdSet(DEFAULT_ENTITY_CULL_WHITELIST);
    private static volatile Set<String> blockEntityCullWhitelistSet = parseIdSet(DEFAULT_BLOCK_ENTITY_CULL_WHITELIST);
    private static volatile Set<String> tickCullWhitelistSet = Collections.emptySet();
    private static volatile Set<String> blockCullBlacklistSet = Collections.emptySet();

    private PapiConfig() {}

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (PapiConfig.class) {
            if (loaded) {
                return;
            }
            loadInternal();
            loaded = true;
        }
    }

    private static Path resolveConfigPath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            if (dir != null) {
                return dir.resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", FILE_NAME);
    }

    private static void loadInternal() {
        Path path = resolveConfigPath();
        Properties props = new Properties();
        boolean fileExisted = Files.isRegularFile(path);
        boolean needsWrite = !fileExisted;

        if (fileExisted) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (Throwable t) {
                LOGGER.warn("Failed to read {}; using defaults.", path, t);
                props.clear();
                needsWrite = true;
            }
        }

        boolean[] modified = new boolean[]{needsWrite};
        entityCulling = readBool(props, "entityCulling", true, modified);
        particleCulling = readBool(props, "particleCulling", true, modified);
        renderNametagsThroughWalls = readBool(props, "renderNametagsThroughWalls", true, modified);
        clientTickCulling = readBool(props, "clientTickCulling", true, modified);
        solidLeaves = readBool(props, "solidLeaves", false, modified);
        f3DebugInfo = readBool(props, "f3DebugInfo", true, modified);
        ebe = readBool(props, "ebe", true, modified);
        ebeSodiumAdapter = readBool(props, "ebeSodiumAdapter", true, modified);
        ebeVitrailAdapter = readBool(props, "ebeVitrailAdapter", true, modified);
        moreCulling = readBool(props, "moreCulling", true, modified);
        blockStateCulling = readBool(props, "blockStateCulling", true, modified);
        leavesCullingMode = readLeavesMode(props, "leavesCullingMode", LeavesCullingMode.DEPTH, modified);
        leavesCullingDepth = readInt(props, "leavesCullingDepth", 2, 1, 4, modified);
        includeMangroveRoots = readBool(props, "includeMangroveRoots", false, modified);
        endGatewayCulling = readBool(props, "endGatewayCulling", false, modified);
        cullModdedBlocksByDefault = readBool(props, "cullModdedBlocksByDefault", true, modified);
        guiBatching = readBool(props, "guiBatching", true, modified);
        fontAtlasResizing = readBool(props, "fontAtlasResizing", true, modified);
        mapAtlas = readBool(props, "mapAtlas", true, modified);
        skipTextTranslucencySorting = readBool(props, "skipTextTranslucencySorting", true, modified);
        badOptimizations = readBool(props, "badOptimizations", true, modified);
        alternateCurrent = readBool(props, "alternateCurrent", true, modified);
        alternateCurrentRuntime = alternateCurrent;
        lithiumDeep = readBool(props, "lithiumDeep", true, modified);
        noisiumC2me = readBool(props, "noisiumC2me", true, modified);
        fastFurnace = readBool(props, "fastFurnace", true, modified);
        fastWorkbench = readBool(props, "fastWorkbench", true, modified);
        clumps = readBool(props, "clumps", true, modified);
        serverCoreMspt = readBool(props, "serverCoreMspt", true, modified);
        ferriteCore = readBool(props, "ferriteCore", true, modified);
        modernFix = readBool(props, "modernFix", true, modified);
        krypton = readBool(props, "krypton", true, modified);
        fastMap = readBool(props, "fastMap", true, modified);
        networkCompressionLevel = readInt(props, "networkCompressionLevel", 4, -1, 9, modified);

        msptTarget = readDouble(props, "msptTarget", 45.0, 10.0, 100.0, modified);
        particleLodDistance = readDouble(props, "particleLodDistance", 30.0, 12.0, 128.0, modified);
        particleLodDistanceSqr = particleLodDistance * particleLodDistance;
        tickCullingMinDistance = readDouble(props, "tickCullingMinDistance", 16.0, 4.0, 128.0, modified);
        itemFrameLodRange = readDouble(props, "itemFrameLodRange", 16.0, 4.0, 128.0, modified);
        itemFrameLodRangeSqr = itemFrameLodRange * itemFrameLodRange;
        itemFrame3FaceRange = readDouble(props, "itemFrame3FaceRange", 4.0, 1.0, 64.0, modified);
        itemFrame3FaceRangeSqr = itemFrame3FaceRange * itemFrame3FaceRange;

        entityCullWhitelistRaw = readString(props, "entityCullWhitelist", DEFAULT_ENTITY_CULL_WHITELIST, modified);
        blockEntityCullWhitelistRaw = readString(props, "blockEntityCullWhitelist", DEFAULT_BLOCK_ENTITY_CULL_WHITELIST, modified);
        tickCullWhitelistRaw = readString(props, "tickCullWhitelist", "", modified);
        blockCullBlacklistRaw = readString(props, "blockCullBlacklist", "", modified);

        entityCullWhitelistSet = parseIdSet(entityCullWhitelistRaw);
        blockEntityCullWhitelistSet = parseIdSet(blockEntityCullWhitelistRaw);
        tickCullWhitelistSet = parseIdSet(tickCullWhitelistRaw);
        blockCullBlacklistSet = parseValidatedIdSet(blockCullBlacklistRaw);

        if (modified[0]) {
            writeConfig(path);
        }
    }

    private static boolean readBool(Properties props, String key, boolean def, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        LOGGER.warn("Invalid boolean for '{}' ('{}'); falling back to default {}.", key, raw, def);
        modified[0] = true;
        return def;
    }

    private static double readDouble(Properties props, String key, double def, double min, double max, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        try {
            double val = Double.parseDouble(raw.trim());
            if (Double.isNaN(val) || Double.isInfinite(val) || val < min || val > max) {
                LOGGER.warn("Out-of-range value for '{}' ('{}', expected {}..{}); falling back to {}.", key, raw, min, max, def);
                modified[0] = true;
                return def;
            }
            return val;
        } catch (NumberFormatException ignored) {
            LOGGER.warn("Invalid number for '{}' ('{}'); falling back to {}.", key, raw, def);
            modified[0] = true;
            return def;
        }
    }

    private static int readInt(Properties props, String key, int def, int min, int max, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < min || val > max) {
                LOGGER.warn("Out-of-range value for '{}' ('{}', expected {}..{}); falling back to {}.", key, raw, min, max, def);
                modified[0] = true;
                return def;
            }
            return val;
        } catch (NumberFormatException ignored) {
            LOGGER.warn("Invalid integer for '{}' ('{}'); falling back to {}.", key, raw, def);
            modified[0] = true;
            return def;
        }
    }

    private static LeavesCullingMode readLeavesMode(Properties props, String key, LeavesCullingMode def, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        LeavesCullingMode parsed = LeavesCullingMode.fromString(raw, def);
        if (parsed == null) {
            LOGGER.warn("Invalid leavesCullingMode '{}' ('{}'); falling back to {}.", key, raw, def);
            modified[0] = true;
            return def;
        }
        return parsed;
    }

    private static String readString(Properties props, String key, String def, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        return raw.trim();
    }

    private static Set<String> parseIdSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        String[] parts = csv.split(",");
        Set<String> set = new HashSet<>(parts.length * 2);
        for (String part : parts) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                if (token.indexOf(':') < 0) {
                    token = "minecraft:" + token;
                }
                set.add(token);
            }
        }
        return set.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    private static Set<String> parseValidatedIdSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        String[] parts = csv.split(",");
        Set<String> set = new HashSet<>(parts.length * 2);
        for (String part : parts) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (token.indexOf(':') < 0) {
                token = "minecraft:" + token;
            }
            if (isValidResourceId(token)) {
                set.add(token);
            } else {
                LOGGER.warn("Ignoring malformed block identifier '{}' in blockCullBlacklist.", part.trim());
            }
        }
        return set.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    private static boolean isValidResourceId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon >= id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        for (int i = colon + 1, n = id.length(); i < n; i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    private static void writeConfig(Path path) {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("entityCulling", Boolean.toString(entityCulling));
        ordered.put("particleCulling", Boolean.toString(particleCulling));
        ordered.put("renderNametagsThroughWalls", Boolean.toString(renderNametagsThroughWalls));
        ordered.put("clientTickCulling", Boolean.toString(clientTickCulling));
        ordered.put("solidLeaves", Boolean.toString(solidLeaves));
        ordered.put("f3DebugInfo", Boolean.toString(f3DebugInfo));
        ordered.put("ebe", Boolean.toString(ebe));
        ordered.put("ebeSodiumAdapter", Boolean.toString(ebeSodiumAdapter));
        ordered.put("ebeVitrailAdapter", Boolean.toString(ebeVitrailAdapter));
        ordered.put("moreCulling", Boolean.toString(moreCulling));
        ordered.put("blockStateCulling", Boolean.toString(blockStateCulling));
        ordered.put("leavesCullingMode", leavesCullingMode.name());
        ordered.put("leavesCullingDepth", Integer.toString(leavesCullingDepth));
        ordered.put("includeMangroveRoots", Boolean.toString(includeMangroveRoots));
        ordered.put("endGatewayCulling", Boolean.toString(endGatewayCulling));
        ordered.put("cullModdedBlocksByDefault", Boolean.toString(cullModdedBlocksByDefault));
        ordered.put("guiBatching", Boolean.toString(guiBatching));
        ordered.put("fontAtlasResizing", Boolean.toString(fontAtlasResizing));
        ordered.put("mapAtlas", Boolean.toString(mapAtlas));
        ordered.put("skipTextTranslucencySorting", Boolean.toString(skipTextTranslucencySorting));
        ordered.put("badOptimizations", Boolean.toString(badOptimizations));
        ordered.put("alternateCurrent", Boolean.toString(alternateCurrent));
        ordered.put("lithiumDeep", Boolean.toString(lithiumDeep));
        ordered.put("noisiumC2me", Boolean.toString(noisiumC2me));
        ordered.put("fastFurnace", Boolean.toString(fastFurnace));
        ordered.put("fastWorkbench", Boolean.toString(fastWorkbench));
        ordered.put("clumps", Boolean.toString(clumps));
        ordered.put("serverCoreMspt", Boolean.toString(serverCoreMspt));
        ordered.put("ferriteCore", Boolean.toString(ferriteCore));
        ordered.put("modernFix", Boolean.toString(modernFix));
        ordered.put("krypton", Boolean.toString(krypton));
        ordered.put("fastMap", Boolean.toString(fastMap));
        ordered.put("networkCompressionLevel", Integer.toString(networkCompressionLevel));
        ordered.put("msptTarget", Double.toString(msptTarget));
        ordered.put("particleLodDistance", Double.toString(particleLodDistance));
        ordered.put("tickCullingMinDistance", Double.toString(tickCullingMinDistance));
        ordered.put("itemFrameLodRange", Double.toString(itemFrameLodRange));
        ordered.put("itemFrame3FaceRange", Double.toString(itemFrame3FaceRange));
        ordered.put("entityCullWhitelist", entityCullWhitelistRaw);
        ordered.put("blockEntityCullWhitelist", blockEntityCullWhitelistRaw);
        ordered.put("tickCullWhitelist", tickCullWhitelistRaw);
        ordered.put("blockCullBlacklist", blockCullBlacklistRaw);

        StringBuilder sb = new StringBuilder(768);
        sb.append("# Gluon module configuration\n");
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException t) {
            LOGGER.warn("Unable to write config file {}.", path, t);
        }
    }

    public static boolean entityCulling() {
        ensureLoaded();
        return entityCulling;
    }

    public static boolean particleCulling() {
        ensureLoaded();
        return particleCulling;
    }

    public static boolean renderNametagsThroughWalls() {
        ensureLoaded();
        return renderNametagsThroughWalls;
    }

    public static boolean clientTickCulling() {
        ensureLoaded();
        return clientTickCulling;
    }

    public static boolean solidLeaves() {
        ensureLoaded();
        return solidLeaves;
    }

    public static boolean f3DebugInfo() {
        ensureLoaded();
        return f3DebugInfo;
    }

    public static boolean ebe() {
        ensureLoaded();
        return ebe;
    }

    public static boolean ebeSodiumAdapter() {
        ensureLoaded();
        return ebeSodiumAdapter;
    }

    public static boolean ebeVitrailAdapter() {
        ensureLoaded();
        return ebeVitrailAdapter;
    }

    public static boolean moreCulling() {
        ensureLoaded();
        return moreCulling;
    }

    public static boolean guiBatching() {
        ensureLoaded();
        return guiBatching;
    }

    public static boolean badOptimizations() {
        ensureLoaded();
        return badOptimizations;
    }

    public static boolean alternateCurrent() {
        ensureLoaded();
        return alternateCurrent;
    }

    public static boolean lithiumDeep() {
        ensureLoaded();
        return lithiumDeep;
    }

    public static boolean noisiumC2me() {
        ensureLoaded();
        return noisiumC2me;
    }

    public static boolean fastFurnace() {
        ensureLoaded();
        return fastFurnace;
    }

    public static boolean clumps() {
        ensureLoaded();
        return clumps;
    }

    public static boolean serverCoreMspt() {
        ensureLoaded();
        return serverCoreMspt;
    }

    public static boolean ferriteCore() {
        ensureLoaded();
        return ferriteCore;
    }

    public static boolean modernFix() {
        ensureLoaded();
        return modernFix;
    }

    public static boolean krypton() {
        ensureLoaded();
        return krypton;
    }

    public static double msptTarget() {
        ensureLoaded();
        return msptTarget;
    }

    public static double particleLodDistance() {
        ensureLoaded();
        return particleLodDistance;
    }

    public static double particleLodDistanceSqr() {
        ensureLoaded();
        return particleLodDistanceSqr;
    }

    public static double tickCullingMinDistance() {
        ensureLoaded();
        return tickCullingMinDistance;
    }

    public static Set<String> entityCullWhitelistSet() {
        ensureLoaded();
        return entityCullWhitelistSet;
    }

    public static Set<String> blockEntityCullWhitelistSet() {
        ensureLoaded();
        return blockEntityCullWhitelistSet;
    }

    public static Set<String> tickCullWhitelistSet() {
        ensureLoaded();
        return tickCullWhitelistSet;
    }

    public static Set<String> blockCullBlacklistSet() {
        ensureLoaded();
        return blockCullBlacklistSet;
    }

    public static boolean blockStateCulling() {
        ensureLoaded();
        return blockStateCulling;
    }

    public static LeavesCullingMode leavesCullingMode() {
        ensureLoaded();
        return leavesCullingMode;
    }

    public static int leavesCullingDepth() {
        ensureLoaded();
        return leavesCullingDepth;
    }

    public static boolean includeMangroveRoots() {
        ensureLoaded();
        return includeMangroveRoots;
    }

    public static boolean endGatewayCulling() {
        ensureLoaded();
        return endGatewayCulling;
    }

    public static boolean cullModdedBlocksByDefault() {
        ensureLoaded();
        return cullModdedBlocksByDefault;
    }

    public static double itemFrameLodRangeSqr() {
        ensureLoaded();
        return itemFrameLodRangeSqr;
    }

    public static double itemFrame3FaceRangeSqr() {
        ensureLoaded();
        return itemFrame3FaceRangeSqr;
    }

    public static boolean rawSolidLeaves() {
        return solidLeaves;
    }

    public static boolean rawBlockStateCulling() {
        return blockStateCulling;
    }

    public static LeavesCullingMode rawLeavesCullingMode() {
        return leavesCullingMode;
    }

    public static int rawLeavesCullingDepth() {
        return leavesCullingDepth;
    }

    public static boolean rawIncludeMangroveRoots() {
        return includeMangroveRoots;
    }

    public static boolean rawEndGatewayCulling() {
        return endGatewayCulling;
    }

    public static double rawItemFrameLodRangeSqr() {
        return itemFrameLodRangeSqr;
    }

    public static double rawItemFrame3FaceRangeSqr() {
        return itemFrame3FaceRangeSqr;
    }

    public static boolean fontAtlasResizing() {
        ensureLoaded();
        return fontAtlasResizing;
    }

    public static boolean rawFontAtlasResizing() {
        return fontAtlasResizing;
    }

    public static boolean mapAtlas() {
        ensureLoaded();
        return mapAtlas;
    }

    public static boolean rawMapAtlas() {
        return mapAtlas;
    }

    public static boolean skipTextTranslucencySorting() {
        ensureLoaded();
        return skipTextTranslucencySorting;
    }

    public static boolean rawAlternateCurrentRuntime() {
        return alternateCurrentRuntime;
    }

    public static void setAlternateCurrentRuntime(boolean enabled) {
        alternateCurrentRuntime = enabled;
    }

    public static boolean fastWorkbench() {
        ensureLoaded();
        return fastWorkbench;
    }

    public static boolean fastMap() {
        ensureLoaded();
        return fastMap;
    }

    public static int networkCompressionLevel() {
        ensureLoaded();
        return networkCompressionLevel;
    }

    public static int rawNetworkCompressionLevel() {
        return networkCompressionLevel;
    }

    public static double rawMsptTarget() {
        return msptTarget;
    }

    public static List<String> getDisabledGroups() {
        ensureLoaded();
        List<String> list = new ArrayList<>();
        if (!entityCulling) list.add("entityCulling");
        if (!particleCulling) list.add("particleCulling");
        if (!clientTickCulling) list.add("clientTickCulling");
        if (!ebe) list.add("ebe");
        if (!ebeSodiumAdapter) list.add("ebeSodiumAdapter");
        if (!ebeVitrailAdapter) list.add("ebeVitrailAdapter");
        if (!moreCulling) list.add("moreCulling");
        if (!guiBatching) list.add("guiBatching");
        if (!fontAtlasResizing) list.add("fontAtlasResizing");
        if (!mapAtlas) list.add("mapAtlas");
        if (!skipTextTranslucencySorting) list.add("skipTextTranslucencySorting");
        if (!badOptimizations) list.add("badOptimizations");
        if (!alternateCurrent) list.add("alternateCurrent");
        if (!lithiumDeep) list.add("lithiumDeep");
        if (!noisiumC2me) list.add("noisiumC2me");
        if (!fastFurnace) list.add("fastFurnace");
        if (!fastWorkbench) list.add("fastWorkbench");
        if (!clumps) list.add("clumps");
        if (!serverCoreMspt) list.add("serverCoreMspt");
        if (!ferriteCore) list.add("ferriteCore");
        if (!modernFix) list.add("modernFix");
        if (!krypton) list.add("krypton");
        if (!fastMap) list.add("fastMap");
        return Collections.unmodifiableList(list);
    }
}
