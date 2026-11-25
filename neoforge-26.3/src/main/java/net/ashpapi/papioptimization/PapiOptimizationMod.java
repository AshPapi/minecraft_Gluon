package net.ashpapi.papioptimization;

import net.ashpapi.papioptimization.command.PapiStatusCommand;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.core.PapiEncoderCacheCleaner;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.ashpapi.papioptimization.startup.PapiLazyDfuBridge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PapiOptimizationMod.MODID)
public class PapiOptimizationMod {

    public static final String MODID = "gluon";
    public static final String MOD_NAME = "Gluon";
    public static final String MOD_VERSION = "2.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public PapiOptimizationMod() {
        PapiConfig.ensureLoaded();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onServerStopped);
        LOGGER.info("[Gluon] Initialized v{} for NeoForge 26.3.", MOD_VERSION);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PapiStatusCommand.registerServer(event.getDispatcher());
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        PapiTagEpoch.bump();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        PapiMsptController.reset();
        PapiEncoderCacheCleaner.clear();
        PapiLazyDfuBridge.evictCachesNow();
    }
}
