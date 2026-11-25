package net.ashpapi.papioptimization.client;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MapDecorationCache {

    private static final int MAX_CACHE_SIZE = 2048;
    private static final int EVICT_BATCH_SIZE = MAX_CACHE_SIZE >>> 2;
    private static final Object STATE_LOCK = new Object();
    private static final Map<MapDecoration, MapRenderState.MapDecorationRenderState> CACHE = new ConcurrentHashMap<>(256);
    private static volatile WeakReference<TextureAtlas> LAST_ATLAS = new WeakReference<>(null);
    private static volatile int LAST_EPOCH = -1;

    private MapDecorationCache() {}

    public static MapRenderState.MapDecorationRenderState getOrCreate(MapDecoration decoration, TextureAtlas sprites) {
        int epoch = PapiClientCaches.epoch();
        if (LAST_EPOCH != epoch || LAST_ATLAS.get() != sprites) {
            synchronized (STATE_LOCK) {
                if (LAST_EPOCH != epoch || LAST_ATLAS.get() != sprites) {
                    CACHE.clear();
                    LAST_ATLAS = new WeakReference<>(sprites);
                    LAST_EPOCH = epoch;
                }
            }
        }
        if (!decoration.renderOnFrame()) {
            return buildState(decoration, sprites);
        }
        MapRenderState.MapDecorationRenderState cached = CACHE.get(decoration);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            evictBatch();
        }
        MapRenderState.MapDecorationRenderState built = buildState(decoration, sprites);
        MapRenderState.MapDecorationRenderState existing = CACHE.putIfAbsent(decoration, built);
        return existing != null ? existing : built;
    }

    private static void evictBatch() {
        synchronized (STATE_LOCK) {
            if (CACHE.size() < MAX_CACHE_SIZE) {
                return;
            }
            Iterator<MapDecoration> it = CACHE.keySet().iterator();
            int removed = 0;
            while (it.hasNext() && removed < EVICT_BATCH_SIZE) {
                it.next();
                it.remove();
                removed++;
            }
        }
    }

    private static MapRenderState.MapDecorationRenderState buildState(MapDecoration dec, TextureAtlas sprites) {
        MapRenderState.MapDecorationRenderState state = new MapRenderState.MapDecorationRenderState();
        state.type = dec.type();
        state.atlasSprite = sprites.getSprite(dec.getSpriteLocation());
        state.x = dec.x();
        state.y = dec.y();
        state.rot = dec.rot();
        state.name = dec.name().orElse(null);
        state.renderOnFrame = dec.renderOnFrame();
        return state;
    }

    public static void clear() {
        synchronized (STATE_LOCK) {
            CACHE.clear();
            LAST_ATLAS = new WeakReference<>(null);
            LAST_EPOCH = -1;
        }
    }
}
