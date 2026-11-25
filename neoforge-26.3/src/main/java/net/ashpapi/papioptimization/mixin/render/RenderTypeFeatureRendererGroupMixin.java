package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Objects;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group", priority = 1500)
public abstract class RenderTypeFeatureRendererGroupMixin {

    @Shadow
    @Final
    private boolean canReorder;

    @Unique
    private @Nullable Object2IntOpenHashMap<PreparedRenderType> papi$renderTypeIndices;

    @Unique
    private static int papi$bannerViewEpoch = -1;

    @Unique
    private static @Nullable GpuTextureView papi$cachedBannerView;

    @Unique
    private static boolean papi$hasBannerSampler0(PreparedRenderType prt) {
        List<PreparedRenderType.Texture> texList = prt.textures();
        if (texList.isEmpty()) {
            return false;
        }
        int epoch = PapiClientCaches.epoch();
        GpuTextureView bannerView = papi$cachedBannerView;
        if (bannerView == null || papi$bannerViewEpoch != epoch) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return false;
            }
            bannerView = mc.getTextureManager().getTexture(Sheets.BANNER_SHEET).getTextureView();
            papi$cachedBannerView = bannerView;
            papi$bannerViewEpoch = epoch;
        }
        for (int i = 0, n = texList.size(); i < n; i++) {
            PreparedRenderType.Texture tex = texList.get(i);
            if ("Sampler0".equals(tex.name())) {
                return tex.textureView() == bannerView;
            }
        }
        return false;
    }

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I")
    )
    private int papi$fastLookupPreparedRenderType(List<?> list, Object prepared, Operation<Integer> original) {
        if (!this.canReorder) {
            return original.call(list, prepared);
        }
        Object2IntOpenHashMap<PreparedRenderType> map = this.papi$renderTypeIndices;
        if (list.isEmpty()) {
            if (map != null && !map.isEmpty()) {
                map.clear();
            }
            return -1;
        }
        if (prepared instanceof PreparedRenderType prt && papi$hasBannerSampler0(prt)) {
            return -1;
        }
        if (map == null) {
            return original.call(list, prepared);
        }
        int idx = map.getInt(prepared);
        if (idx >= 0 && idx < list.size() && Objects.equals(list.get(idx), prepared)) {
            return idx;
        }
        int found = original.call(list, prepared);
        if (found >= 0 && prepared instanceof PreparedRenderType prt) {
            map.put(prt, found);
        }
        return found;
    }

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1)
    )
    private boolean papi$recordPreparedRenderTypeIndex(List<Object> list, Object prepared, Operation<Boolean> original) {
        boolean added = original.call(list, prepared);
        if (added && this.canReorder && prepared instanceof PreparedRenderType prt && !papi$hasBannerSampler0(prt)) {
            Object2IntOpenHashMap<PreparedRenderType> map = this.papi$renderTypeIndices;
            if (map == null) {
                map = new Object2IntOpenHashMap<>(16);
                map.defaultReturnValue(-1);
                this.papi$renderTypeIndices = map;
            }
            int newIndex = list.size() - 1;
            int prev = map.getInt(prt);
            if (prev < 0 || prev >= newIndex || !Objects.equals(list.get(prev), prt)) {
                map.put(prt, newIndex);
            }
        }
        return added;
    }
}
