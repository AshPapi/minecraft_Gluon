package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import java.lang.ref.WeakReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BaseCommandBlock.class, priority = 1500)
public abstract class BaseCommandBlockFastMixin {

    @Unique
    private @Nullable String papi$cachedRawCommand;

    @Unique
    private @Nullable WeakReference<CommandDispatcher<CommandSourceStack>> papi$cachedDispatcherRef;

    @Unique
    private @Nullable ParseResults<CommandSourceStack> papi$cachedParseResults;

    @Unique
    private void papi$invalidateCommandCache() {
        this.papi$cachedRawCommand = null;
        this.papi$cachedDispatcherRef = null;
        this.papi$cachedParseResults = null;
    }

    @Inject(method = "setCommand", at = @At("RETURN"))
    private void papi$onSetCommand(String command, CallbackInfo ci) {
        this.papi$invalidateCommandCache();
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void papi$onLoadCommand(ValueInput input, CallbackInfo ci) {
        this.papi$invalidateCommandCache();
    }

    @WrapOperation(
            method = "performCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/Commands;performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V"
            )
    )
    private void papi$executeWithCachedParseTree(
            Commands commands,
            CommandSourceStack source,
            String rawCommand,
            Operation<Void> original
    ) {
        CommandDispatcher<CommandSourceStack> dispatcher = commands.getDispatcher();
        String trimmed = Commands.trimOptionalPrefix(rawCommand);
        WeakReference<CommandDispatcher<CommandSourceStack>> ref = this.papi$cachedDispatcherRef;
        ParseResults<CommandSourceStack> cached = this.papi$cachedParseResults;

        ParseResults<CommandSourceStack> toRun;
        if (cached == null
                || ref == null
                || ref.get() != dispatcher
                || !rawCommand.equals(this.papi$cachedRawCommand)) {
            cached = dispatcher.parse(trimmed, source);
            this.papi$cachedRawCommand = rawCommand;
            this.papi$cachedDispatcherRef = new WeakReference<>(dispatcher);
            this.papi$cachedParseResults = cached;
            toRun = cached;
        } else {
            for (CommandContextBuilder<CommandSourceStack> ctx = cached.getContext(); ctx != null; ctx = ctx.getChild()) {
                ctx.withSource(source);
            }
            toRun = Commands.mapSource(cached, ignored -> source);
        }

        try {
            commands.performCommand(toRun, trimmed);
        } finally {
            ParseResults<CommandSourceStack> stored = this.papi$cachedParseResults;
            if (stored != null) {
                for (CommandContextBuilder<CommandSourceStack> ctx = stored.getContext(); ctx != null; ctx = ctx.getChild()) {
                    ctx.withSource(null);
                }
            }
        }
    }
}
