package net.ashpapi.papioptimization.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.ashpapi.papioptimization.PapiOptimizationMod;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.mixin.PapiMixinPlugin;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class PapiStatusCommand {

    private static volatile @Nullable Supplier<String> clientEbeProvider;
    private static volatile @Nullable Supplier<String> clientOcclusionProvider;

    private PapiStatusCommand() {}

    public static void registerClientStatusProvider(
            Supplier<String> ebeProvider,
            Supplier<String> occlusionProvider
    ) {
        clientEbeProvider = ebeProvider;
        clientOcclusionProvider = occlusionProvider;
    }

    public static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("gluon")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(PapiStatusCommand::buildServerStatusComponent, false);
                            return 1;
                        }))
                        .then(Commands.literal("redstone")
                                .executes(ctx -> setRedstoneRuntime(ctx.getSource(), !PapiConfig.rawAlternateCurrentRuntime()))
                                .then(Commands.literal("on").executes(ctx -> setRedstoneRuntime(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setRedstoneRuntime(ctx.getSource(), false)))
                        )
        );
    }

    private static int setRedstoneRuntime(CommandSourceStack source, boolean enabled) {
        PapiConfig.setAlternateCurrentRuntime(enabled);
        String mode = enabled ? "Wavefront (Alternate Current)" : "Vanilla DefaultRedstoneWireEvaluator";
        source.sendSuccess(
                () -> Component.literal("[" + PapiOptimizationMod.MOD_NAME + "] Redstone solver: " + mode),
                true
        );
        return 1;
    }

    public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("gluon")
                        .then(Commands.literal("client").executes(ctx -> {
                            ctx.getSource().sendSuccess(PapiStatusCommand::buildClientStatusComponent, false);
                            return 1;
                        }))
        );
    }

    public static Component buildServerStatusComponent() {
        int active = PapiMixinPlugin.getActiveMixinCount();
        int disabled = PapiMixinPlugin.getDisabledMixinCount();
        List<String> reasons = PapiMixinPlugin.getDisableReasons();
        String reasonStr = reasons.isEmpty() ? "none" : String.join(", ", reasons);

        double mspt = PapiMsptController.getAverageMspt();
        double target = PapiConfig.msptTarget();
        int aiInterval = PapiMsptController.getAiThrottleInterval();
        String acState = PapiConfig.rawAlternateCurrentRuntime() ? "on" : "off";

        String msg = String.format(
                Locale.ROOT,
                "[%s v%s Server] Mixins: active=%d, disabled=%d | Yield/Off: %s | MSPT: %.2f ms (target %.1f, AI=%dt) | AC: %s",
                PapiOptimizationMod.MOD_NAME,
                PapiOptimizationMod.MOD_VERSION,
                active,
                disabled,
                reasonStr,
                mspt,
                target,
                aiInterval,
                acState
        );
        return Component.literal(msg);
    }

    public static Component buildClientStatusComponent() {
        int active = PapiMixinPlugin.getActiveMixinCount();
        int disabled = PapiMixinPlugin.getDisabledMixinCount();
        List<String> reasons = PapiMixinPlugin.getDisableReasons();
        String reasonStr = reasons.isEmpty() ? "none" : String.join(", ", reasons);

        Supplier<String> ebeSup = clientEbeProvider;
        String ebeStatus = ebeSup != null ? ebeSup.get() : "server-only";

        Supplier<String> occSup = clientOcclusionProvider;
        String occStatus = occSup != null ? occSup.get() : "server-only";

        String msg = String.format(
                Locale.ROOT,
                "[%s v%s Client] Mixins: active=%d, disabled=%d | Yield/Off: %s | EBE: %s | Occlusion: %s | ParticleLOD: %.0fb",
                PapiOptimizationMod.MOD_NAME,
                PapiOptimizationMod.MOD_VERSION,
                active,
                disabled,
                reasonStr,
                ebeStatus,
                occStatus,
                PapiConfig.particleLodDistance()
        );
        return Component.literal(msg);
    }
}
