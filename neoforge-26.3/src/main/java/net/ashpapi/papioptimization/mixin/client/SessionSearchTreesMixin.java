package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SessionSearchTrees.class, priority = 1500)
public abstract class SessionSearchTreesMixin {

    @Shadow
    @Final
    private static SessionSearchTrees.Key RECIPE_COLLECTIONS;

    @Shadow
    @Final
    private Map<SessionSearchTrees.Key, Runnable> reloaders;

    @Unique
    private final Set<SessionSearchTrees.Key> papi$dirtyKeys = ConcurrentHashMap.newKeySet();

    @Unique
    private void papi$ensureBuilt(SessionSearchTrees.Key key) {
        if (key == null || !this.papi$dirtyKeys.contains(key)) {
            return;
        }
        synchronized (this) {
            if (!this.papi$dirtyKeys.contains(key)) {
                return;
            }
            Runnable reloader = this.reloaders.get(key);
            try {
                if (reloader != null) {
                    reloader.run();
                }
            } finally {
                this.papi$dirtyKeys.remove(key);
            }
        }
    }

    @WrapMethod(method = "register")
    private void papi$deferSearchTreeBuild(
            SessionSearchTrees.Key key,
            Runnable reloader,
            Operation<Void> original
    ) {
        if (!PapiConfig.modernFix() || key == null || reloader == null) {
            original.call(key, reloader);
            return;
        }
        synchronized (this) {
            this.reloaders.put(key, reloader);
            this.papi$dirtyKeys.add(key);
        }
    }

    @WrapMethod(method = "rebuildAfterLanguageChange")
    private void papi$deferLanguageRebuild(Operation<Void> original) {
        if (!PapiConfig.modernFix()) {
            original.call();
            return;
        }
        synchronized (this) {
            this.papi$dirtyKeys.addAll(this.reloaders.keySet());
        }
    }

    @Inject(method = "recipes", at = @At("HEAD"))
    private void papi$ensureRecipesBuilt(CallbackInfoReturnable<SearchTree<RecipeCollection>> cir) {
        this.papi$ensureBuilt(RECIPE_COLLECTIONS);
    }

    @Inject(
        method = "creativeTagSearch(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;)Lnet/minecraft/client/searchtree/SearchTree;",
        at = @At("HEAD")
    )
    private void papi$ensureCreativeTagBuilt(SessionSearchTrees.Key key, CallbackInfoReturnable<SearchTree<ItemStack>> cir) {
        this.papi$ensureBuilt(key);
    }

    @Inject(
        method = "creativeNameSearch(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;)Lnet/minecraft/client/searchtree/SearchTree;",
        at = @At("HEAD")
    )
    private void papi$ensureCreativeNameBuilt(SessionSearchTrees.Key key, CallbackInfoReturnable<SearchTree<ItemStack>> cir) {
        this.papi$ensureBuilt(key);
    }
}
