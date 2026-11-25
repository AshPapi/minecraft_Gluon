package net.ashpapi.papioptimization.mixin.poi;

import java.util.Optional;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.PapiFastPoiManager;
import net.ashpapi.papioptimization.ai.PapiSinglePoiTypeFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalForcer.class, priority = 1500)
public abstract class PortalForcerFastMixin {

    @Shadow
    @Final
    protected ServerLevel level;

    @Unique
    private static final Predicate<Holder<PoiType>> PAPI$FALLBACK_PORTAL_PRED =
            holder -> holder.is(PoiTypes.NETHER_PORTAL);

    @Unique
    private Predicate<Holder<PoiType>> papi$cachedPortalPredicate;

    @Inject(method = "findClosestPortalPosition", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindClosestPortalPosition(
            BlockPos exitPos,
            boolean isNether,
            WorldBorder worldBorder,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        PoiManager poiManager = this.level.getPoiManager();
        if (!(poiManager instanceof PapiFastPoiManager fast)) {
            return;
        }
        int radius = isNether ? 16 : 128;
        poiManager.ensureLoadedAndValid(this.level, exitPos, radius);

        Predicate<Holder<PoiType>> pred = this.papi$cachedPortalPredicate;
        if (pred == null) {
            Optional<Holder.Reference<PoiType>> holderOpt = this.level.registryAccess()
                    .lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .get(PoiTypes.NETHER_PORTAL);
            pred = holderOpt.<Predicate<Holder<PoiType>>>map(PapiSinglePoiTypeFilter::new)
                    .orElse(PAPI$FALLBACK_PORTAL_PRED);
            this.papi$cachedPortalPredicate = pred;
        }

        cir.setReturnValue(fast.papi$findClosestPortal(this.level, pred, exitPos, radius, worldBorder));
    }
}
