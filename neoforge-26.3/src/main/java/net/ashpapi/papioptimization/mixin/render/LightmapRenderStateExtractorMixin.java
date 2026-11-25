package net.ashpapi.papioptimization.mixin.render;

import java.lang.ref.WeakReference;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.mixin.client.ClientLevelSkyFlashAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LightmapRenderStateExtractor.class, priority = 1500)
public abstract class LightmapRenderStateExtractorMixin {

    @Shadow
    private boolean needsUpdate;

    @Shadow
    private float blockLightFlicker;

    @Shadow
    @Final
    private RandomSource randomSource;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GameRenderer renderer;

    @Unique
    private float papi$prevFlicker = 0.0F;

    @Unique
    private int papi$prevEpoch = -1;

    @Unique
    private double papi$prevGamma = Double.NaN;

    @Unique
    private double papi$prevDarknessScale = Double.NaN;

    @Unique
    private float papi$prevWaterVision = Float.NaN;

    @Unique
    private boolean papi$prevNightVision;

    @Unique
    private boolean papi$prevDarkness;

    @Unique
    private boolean papi$prevConduitPower;

    @Unique
    private float papi$prevBossDarkening = Float.NaN;

    @Unique
    private WeakReference<ClientLevel> papi$prevLevel = new WeakReference<>(null);

    @Unique
    private int papi$prevSkyDarken = Integer.MIN_VALUE;

    @Unique
    private float papi$prevAmbientLight = Float.NaN;

    @Unique
    private float papi$prevEndFlash = 0.0F;

    @Unique
    private boolean papi$prevSkyFlash;

    @Unique
    private float papi$prevSkyLightFactor = Float.NaN;

    @Unique
    private float papi$prevSkyColorR = Float.NaN;

    @Unique
    private float papi$prevSkyColorG = Float.NaN;

    @Unique
    private float papi$prevSkyColorB = Float.NaN;

    @Unique
    private float papi$prevAmbientR = Float.NaN;

    @Unique
    private float papi$prevAmbientG = Float.NaN;

    @Unique
    private float papi$prevAmbientB = Float.NaN;

    @Unique
    private float papi$prevBlockTintR = Float.NaN;

    @Unique
    private float papi$prevBlockTintG = Float.NaN;

    @Unique
    private float papi$prevBlockTintB = Float.NaN;

    @Unique
    private float papi$prevNvColorR = Float.NaN;

    @Unique
    private float papi$prevNvColorG = Float.NaN;

    @Unique
    private float papi$prevNvColorB = Float.NaN;

    @Unique
    private static boolean papi$vecChanged(Vector3fc v, float pr, float pg, float pb) {
        return Float.isNaN(pr)
                || Math.abs(v.x() - pr) > 1.0E-4F
                || Math.abs(v.y() - pg) > 1.0E-4F
                || Math.abs(v.z() - pb) > 1.0E-4F;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$differentialLightmapTick(CallbackInfo ci) {
        ci.cancel();

        float delta = (this.randomSource.nextFloat() - this.randomSource.nextFloat())
                * this.randomSource.nextFloat()
                * this.randomSource.nextFloat()
                * 0.1F;
        this.blockLightFlicker += delta;
        this.blockLightFlicker *= 0.9F;

        boolean dirty = Math.abs(this.blockLightFlicker - this.papi$prevFlicker) > 0.015F;

        int epoch = PapiClientCaches.epoch();
        if (epoch != this.papi$prevEpoch) {
            this.papi$prevEpoch = epoch;
            dirty = true;
        }

        LocalPlayer player = this.minecraft.player;
        boolean hasNv = false;
        boolean hasConduit = false;
        if (player != null) {
            MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
            hasNv = nv != null;
            if (hasNv != this.papi$prevNightVision || (hasNv && nv.endsWithin(200))) {
                this.papi$prevNightVision = hasNv;
                dirty = true;
            }
            boolean hasDarkness = player.hasEffect(MobEffects.DARKNESS);
            if (hasDarkness || this.papi$prevDarkness) {
                this.papi$prevDarkness = hasDarkness;
                dirty = true;
            }
            hasConduit = player.hasEffect(MobEffects.CONDUIT_POWER);
            if (hasConduit != this.papi$prevConduitPower) {
                this.papi$prevConduitPower = hasConduit;
                dirty = true;
            }
            float waterVision = player.getWaterVision();
            if (waterVision != this.papi$prevWaterVision) {
                this.papi$prevWaterVision = waterVision;
                dirty = true;
            }
        } else {
            if (this.papi$prevNightVision || this.papi$prevDarkness || this.papi$prevConduitPower || !Float.isNaN(this.papi$prevWaterVision)) {
                this.papi$prevNightVision = false;
                this.papi$prevDarkness = false;
                this.papi$prevConduitPower = false;
                this.papi$prevWaterVision = Float.NaN;
                dirty = true;
            }
        }

        ClientLevel level = this.minecraft.level;
        if (level != this.papi$prevLevel.get()) {
            this.papi$prevLevel = new WeakReference<>(level);
            this.papi$prevSkyDarken = Integer.MIN_VALUE;
            this.papi$prevAmbientLight = Float.NaN;
            this.papi$prevEndFlash = 0.0F;
            this.papi$prevSkyFlash = false;
            this.papi$prevSkyLightFactor = Float.NaN;
            this.papi$prevSkyColorR = Float.NaN;
            this.papi$prevSkyColorG = Float.NaN;
            this.papi$prevSkyColorB = Float.NaN;
            this.papi$prevAmbientR = Float.NaN;
            this.papi$prevAmbientG = Float.NaN;
            this.papi$prevAmbientB = Float.NaN;
            this.papi$prevBlockTintR = Float.NaN;
            this.papi$prevBlockTintG = Float.NaN;
            this.papi$prevBlockTintB = Float.NaN;
            this.papi$prevNvColorR = Float.NaN;
            this.papi$prevNvColorG = Float.NaN;
            this.papi$prevNvColorB = Float.NaN;
            dirty = true;
        }
        if (level != null) {
            int skyDarken = level.getSkyDarken();
            if (skyDarken != this.papi$prevSkyDarken) {
                this.papi$prevSkyDarken = skyDarken;
                dirty = true;
            }
            float ambientLight = level.dimensionType().ambientLight();
            if (Float.isNaN(this.papi$prevAmbientLight) || ambientLight != this.papi$prevAmbientLight) {
                this.papi$prevAmbientLight = ambientLight;
                dirty = true;
            }
            boolean hideFlash = this.minecraft.options.hideLightningFlash().get();
            boolean skyFlash = !hideFlash
                    && level instanceof ClientLevelSkyFlashAccessor flashAccessor
                    && flashAccessor.papi$getSkyFlashTime() > 0;
            if (skyFlash != this.papi$prevSkyFlash) {
                this.papi$prevSkyFlash = skyFlash;
                dirty = true;
            }
            EndFlashState endFlash = level.endFlashState();
            float flashIntensity = (!hideFlash && endFlash != null) ? endFlash.getIntensity(1.0F) : 0.0F;
            if (flashIntensity > 0.0F) {
                BossHealthOverlay bossOverlay = this.minecraft.gui.hud.getBossOverlay();
                if (bossOverlay != null && bossOverlay.shouldCreateWorldFog()) {
                    flashIntensity /= 3.0F;
                }
            }
            if (Math.abs(flashIntensity - this.papi$prevEndFlash) > 1.0E-5F) {
                this.papi$prevEndFlash = flashIntensity;
                dirty = true;
            }
        }

        float bossDarkening = this.renderer.bossOverlayWorldDarkening(1.0F);
        if (Float.isNaN(this.papi$prevBossDarkening) || Math.abs(bossDarkening - this.papi$prevBossDarkening) > 1.0E-4F) {
            this.papi$prevBossDarkening = bossDarkening;
            dirty = true;
        }

        double gamma = this.minecraft.options.gamma().get();
        if (gamma != this.papi$prevGamma) {
            this.papi$prevGamma = gamma;
            dirty = true;
        }

        double darknessScale = this.minecraft.options.darknessEffectScale().get();
        if (darknessScale != this.papi$prevDarknessScale) {
            this.papi$prevDarknessScale = darknessScale;
            dirty = true;
        }

        Camera camera = this.renderer.mainCamera();
        if (camera != null) {
            EnvironmentAttributeProbe probe = camera.attributeProbe();
            if (probe != null) {
                float skyFactor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0F);
                if (Float.isNaN(this.papi$prevSkyLightFactor) || Math.abs(skyFactor - this.papi$prevSkyLightFactor) > 1.0E-4F) {
                    this.papi$prevSkyLightFactor = skyFactor;
                    dirty = true;
                }
                Vector3fc skyColor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, 1.0F);
                if (skyColor != null && papi$vecChanged(skyColor, this.papi$prevSkyColorR, this.papi$prevSkyColorG, this.papi$prevSkyColorB)) {
                    this.papi$prevSkyColorR = skyColor.x();
                    this.papi$prevSkyColorG = skyColor.y();
                    this.papi$prevSkyColorB = skyColor.z();
                    dirty = true;
                }
                Vector3fc ambientColor = probe.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, 1.0F);
                if (ambientColor != null && papi$vecChanged(ambientColor, this.papi$prevAmbientR, this.papi$prevAmbientG, this.papi$prevAmbientB)) {
                    this.papi$prevAmbientR = ambientColor.x();
                    this.papi$prevAmbientG = ambientColor.y();
                    this.papi$prevAmbientB = ambientColor.z();
                    dirty = true;
                }
                Vector3fc blockTint = probe.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, 1.0F);
                if (blockTint != null && papi$vecChanged(blockTint, this.papi$prevBlockTintR, this.papi$prevBlockTintG, this.papi$prevBlockTintB)) {
                    this.papi$prevBlockTintR = blockTint.x();
                    this.papi$prevBlockTintG = blockTint.y();
                    this.papi$prevBlockTintB = blockTint.z();
                    dirty = true;
                }
                if (hasNv || hasConduit) {
                    Vector3fc nvColor = probe.getValue(EnvironmentAttributes.NIGHT_VISION_COLOR, 1.0F);
                    if (nvColor != null && papi$vecChanged(nvColor, this.papi$prevNvColorR, this.papi$prevNvColorG, this.papi$prevNvColorB)) {
                        this.papi$prevNvColorR = nvColor.x();
                        this.papi$prevNvColorG = nvColor.y();
                        this.papi$prevNvColorB = nvColor.z();
                        dirty = true;
                    }
                }
            }
        }

        if (dirty) {
            this.needsUpdate = true;
            this.papi$prevFlicker = this.blockLightFlicker;
        }
    }
}
