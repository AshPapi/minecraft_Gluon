package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EntityType.class, priority = 1500)
public abstract class EntityTypeMixin implements EntityTypeRendererCache {

    @Unique
    private volatile @Nullable Object papi$rendererSlot;

    @Unique
    private volatile byte papi$cullWhitelistState;

    @Unique
    private volatile byte papi$tickCullState;

    @Unique
    private volatile @Nullable Class<?> papi$tickCullSampleClass;

    @Override
    public @Nullable Object papi$getRendererSlot() {
        return this.papi$rendererSlot;
    }

    @Override
    public @Nullable EntityRenderer<?, ?> papi$getRenderer() {
        Object slot = this.papi$rendererSlot;
        return (slot == null || slot == PAPI$NO_RENDERER) ? null : (EntityRenderer<?, ?>) slot;
    }

    @Override
    public void papi$setRenderer(@Nullable EntityRenderer<?, ?> renderer) {
        this.papi$rendererSlot = renderer != null ? renderer : PAPI$NO_RENDERER;
        this.papi$cullWhitelistState = 0;
        this.papi$tickCullState = 0;
        this.papi$tickCullSampleClass = null;
    }

    @Override
    public boolean papi$isCullWhitelisted() {
        byte st = this.papi$cullWhitelistState;
        if (st != 0) {
            return st == 2;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        boolean wl = id != null && PapiConfig.entityCullWhitelistSet().contains(id.toString());
        this.papi$cullWhitelistState = (byte) (wl ? 2 : 1);
        return wl;
    }

    @Override
    public boolean papi$isTickCullAllowed(Entity sample) {
        Class<?> sampleCls = sample.getClass();
        byte st = this.papi$tickCullState;
        if (st == 1) {
            return false;
        }
        if (st == 2 && this.papi$tickCullSampleClass == sampleCls) {
            return true;
        }
        if (this.papi$isCullWhitelisted()) {
            this.papi$tickCullState = 1;
            return false;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        if (id == null || PapiConfig.tickCullWhitelistSet().contains(id.toString())) {
            this.papi$tickCullState = 1;
            return false;
        }
        String clsName = sampleCls.getName();
        boolean allowed = clsName.startsWith("net.minecraft.world.entity.")
                && !clsName.startsWith("net.minecraft.world.entity.projectile.")
                && !(sample instanceof Avatar)
                && !(sample instanceof VehicleEntity)
                && !(sample instanceof Display)
                && !(sample instanceof Projectile)
                && !(sample instanceof EyeOfEnder)
                && !(sample instanceof EvokerFangs)
                && !(sample instanceof LightningBolt)
                && !(sample instanceof AreaEffectCloud)
                && !(sample instanceof FallingBlockEntity)
                && !(sample instanceof PrimedTnt)
                && !(sample instanceof EndCrystal)
                && !(sample instanceof EnderDragon)
                && !(sample instanceof WitherBoss)
                && !(sample instanceof Warden)
                && !(sample instanceof Shulker)
                && !(sample instanceof Guardian);
        if (this.papi$tickCullSampleClass == null) {
            this.papi$tickCullSampleClass = sampleCls;
            this.papi$tickCullState = (byte) (allowed ? 2 : 1);
        }
        return allowed;
    }
}
