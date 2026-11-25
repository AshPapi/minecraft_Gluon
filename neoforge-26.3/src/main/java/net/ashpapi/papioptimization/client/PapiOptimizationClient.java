package net.ashpapi.papioptimization.client;

import java.util.Locale;
import net.ashpapi.papioptimization.PapiOptimizationMod;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.command.PapiStatusCommand;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
@Mod(value = PapiOptimizationMod.MODID, dist = Dist.CLIENT)
public final class PapiOptimizationClient {

    private static final DebugEntryCategory GLUON_CATEGORY =
            new DebugEntryCategory(Component.literal("Gluon"), 9.5F);
    private static final Identifier GLUON_GROUP =
            Identifier.fromNamespaceAndPath(PapiOptimizationMod.MODID, "culling_group");
    private static final Identifier GLUON_ENTRY_ID =
            Identifier.fromNamespaceAndPath(PapiOptimizationMod.MODID, "occlusion_culling");

    public PapiOptimizationClient(IEventBus modBus) {
        PapiStatusCommand.registerClientStatusProvider(
                PapiEbeRuntime::getActiveBackend,
                PapiOptimizationClient::formatOcclusionStats
        );
        modBus.addListener(RegisterDebugEntriesEvent.class, PapiOptimizationClient::onRegisterDebugEntries);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PapiStatusCommand.registerClient(event.getDispatcher());
    }

    private static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
        event.register(GLUON_ENTRY_ID, new DebugScreenEntry() {
            @Override
            public void display(
                    DebugScreenDisplayer displayer,
                    @Nullable Level level,
                    @Nullable LevelChunk clientChunk,
                    @Nullable LevelChunk serverChunk
            ) {
                if (!PapiConfig.f3DebugInfo()) {
                    return;
                }
                long skipped = PapiOcclusionEngine.getLastTickSkippedEntities();
                long totalTick = skipped + PapiOcclusionEngine.getLastTickProcessedEntities();
                displayer.addToGroup(GLUON_GROUP, String.format(
                        Locale.ROOT,
                        "[Gluon] Cull E: %d | BE: %d | P: %d | TickSkip: %d/%d | Worker: %dus | EBE: %s",
                        PapiOcclusionEngine.getLastFrameCulledEntities(),
                        PapiOcclusionEngine.getLastFrameCulledBlockEntities(),
                        PapiOcclusionEngine.getLastFrameCulledParticles(),
                        skipped,
                        totalTick,
                        PapiOcclusionEngine.getLastWorkerPassMicros(),
                        PapiEbeRuntime.getActiveBackend()
                ));
            }

            @Override
            public boolean isAllowed(boolean reducedDebugInfo) {
                return !reducedDebugInfo;
            }

            @Override
            public DebugEntryCategory category() {
                return GLUON_CATEGORY;
            }
        });
        event.includeInProfile(GLUON_ENTRY_ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        event.includeInProfile(GLUON_ENTRY_ID, DebugScreenProfile.PERFORMANCE, DebugScreenEntryStatus.IN_OVERLAY);
    }

    private static String formatOcclusionStats() {
        return String.format(
                Locale.ROOT,
                "ent=%d(%d), be=%d(%d), part=%d(%d), tickSkip=%d/%d, worker=%dus",
                PapiOcclusionEngine.getLastFrameCulledEntities(),
                PapiOcclusionEngine.getTotalCulledEntities(),
                PapiOcclusionEngine.getLastFrameCulledBlockEntities(),
                PapiOcclusionEngine.getTotalCulledBlockEntities(),
                PapiOcclusionEngine.getLastFrameCulledParticles(),
                PapiOcclusionEngine.getTotalCulledParticles(),
                PapiOcclusionEngine.getLastTickSkippedEntities(),
                PapiOcclusionEngine.getLastTickSkippedEntities() + PapiOcclusionEngine.getLastTickProcessedEntities(),
                PapiOcclusionEngine.getLastWorkerPassMicros()
        );
    }
}
