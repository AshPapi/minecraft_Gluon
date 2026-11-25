package net.ashpapi.papioptimization.ai;

import java.lang.ref.WeakReference;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jspecify.annotations.Nullable;

public final class PapiRaidBannerCache {

    private static volatile @Nullable CachedEntry cached;

    private PapiRaidBannerCache() {}

    public static ItemStack getReadOnly(HolderGetter<BannerPattern> getter) {
        CachedEntry snap = cached;
        if (snap != null && snap.getterRef.get() == getter) {
            return snap.banner;
        }
        ItemStack fresh = Raid.getOminousBannerInstance(getter);
        cached = new CachedEntry(new WeakReference<>(getter), fresh);
        return fresh;
    }

    private record CachedEntry(WeakReference<HolderGetter<BannerPattern>> getterRef, ItemStack banner) {}
}
