package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.ashpapi.papioptimization.access.PapiShulkerRenderState;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ShulkerBoxRenderer.class, priority = 1500)
public abstract class ShulkerBoxRendererMixin {

    @Unique
    private static final @Nullable Field PAPI$PART_CUBES_FIELD;

    @Unique
    private static final @Nullable Field PAPI$PART_CHILDREN_FIELD;

    @Unique
    private static final @Nullable Field PAPI$RENDERER_MODEL_FIELD;

    static {
        Field cubes = null;
        Field children = null;
        Field modelField = null;
        try {
            cubes = ModelPart.class.getDeclaredField("cubes");
            cubes.setAccessible(true);
            children = ModelPart.class.getDeclaredField("children");
            children.setAccessible(true);
            modelField = ShulkerBoxRenderer.class.getDeclaredField("model");
            modelField.setAccessible(true);
        } catch (Throwable ignored) {
        }
        PAPI$PART_CUBES_FIELD = cubes;
        PAPI$PART_CHILDREN_FIELD = children;
        PAPI$RENDERER_MODEL_FIELD = modelField;
    }

    @Shadow
    @Final
    private SpriteGetter sprites;

    @Shadow
    public static Transformation modelTransform(Direction direction) {
        throw new AssertionError();
    }

    @Unique
    private @Nullable Model<Float> papi$culledBaseModel;

    @SuppressWarnings("unchecked")
    @Inject(
            method = "<init>(Lnet/minecraft/client/model/geom/EntityModelSet;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;)V",
            at = @At("RETURN")
    )
    private void papi$bakeCulledBaseShulkerModel(
            EntityModelSet entityModelSet,
            SpriteGetter spriteGetter,
            CallbackInfo ci
    ) {
        if (PAPI$PART_CUBES_FIELD == null || PAPI$PART_CHILDREN_FIELD == null || PAPI$RENDERER_MODEL_FIELD == null) {
            return;
        }
        try {
            Object origModelObj = PAPI$RENDERER_MODEL_FIELD.get(this);
            if (origModelObj == null) {
                return;
            }
            ModelPart freshRoot = entityModelSet.bakeLayer(ModelLayers.SHULKER_BOX);
            Map<String, ModelPart> rootChildren = (Map<String, ModelPart>) PAPI$PART_CHILDREN_FIELD.get(freshRoot);
            List<ModelPart.Cube> rootCubes = (List<ModelPart.Cube>) PAPI$PART_CUBES_FIELD.get(freshRoot);
            ModelPart origBase = rootChildren.get("base");
            if (origBase == null) {
                return;
            }
            List<ModelPart.Cube> baseCubes = (List<ModelPart.Cube>) PAPI$PART_CUBES_FIELD.get(origBase);
            Map<String, ModelPart> baseChildren = (Map<String, ModelPart>) PAPI$PART_CHILDREN_FIELD.get(origBase);
            if (baseCubes.size() != 1 || !baseChildren.isEmpty()) {
                return;
            }
            ModelPart.Cube c = baseCubes.get(0);
            if (Math.abs(c.minX + 8.0F) > 1.0E-3F
                    || Math.abs(c.maxX - 8.0F) > 1.0E-3F
                    || Math.abs(c.minY + 8.0F) > 1.0E-3F
                    || Math.abs(c.maxY) > 1.0E-3F) {
                return;
            }

            ModelPart.Cube culledBaseCube = new ModelPart.Cube(
                    0,
                    28,
                    -8.0F,
                    -8.0F,
                    -8.0F,
                    16.0F,
                    8.0F,
                    16.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    false,
                    64.0F,
                    64.0F,
                    EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
            );
            ModelPart culledBasePart = new ModelPart(List.of(culledBaseCube), Map.of());
            culledBasePart.setInitialPose(origBase.getInitialPose());
            culledBasePart.loadPose(origBase.storePose());

            Map<String, ModelPart> newChildren = new LinkedHashMap<>(rootChildren);
            newChildren.put("base", culledBasePart);
            ModelPart newRoot = new ModelPart(rootCubes, Map.copyOf(newChildren));
            newRoot.setInitialPose(freshRoot.getInitialPose());
            newRoot.loadPose(freshRoot.storePose());

            Constructor<?> ctor = origModelObj.getClass().getDeclaredConstructor(ModelPart.class);
            ctor.setAccessible(true);
            this.papi$culledBaseModel = (Model<Float>) ctor.newInstance(newRoot);
        } catch (Throwable ignored) {
            this.papi$culledBaseModel = null;
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("RETURN")
    )
    private void papi$extractBottomOcclusion(
            ShulkerBoxBlockEntity blockEntity,
            ShulkerBoxRenderState state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            CallbackInfo ci
    ) {
        Level level = blockEntity.getLevel();
        Direction facing = state.direction;
        if (level == null || facing == null) {
            ((PapiShulkerRenderState) state).papi$setBottomOccluded(false);
            return;
        }
        int mark = BlockPosPool.mark();
        try {
            Direction opp = facing.getOpposite();
            BlockPos pos = blockEntity.getBlockPos();
            BlockPos.MutableBlockPos supportPos = BlockPosPool.get();
            supportPos.set(
                    pos.getX() + opp.getStepX(),
                    pos.getY() + opp.getStepY(),
                    pos.getZ() + opp.getStepZ()
            );
            BlockState support = level.getBlockState(supportPos);
            boolean occluded = (!support.isAir()
                    && support.canOcclude()
                    && Block.isFaceFull(support.getFaceOcclusionShape(facing), facing))
                    || (PapiConfig.rawSolidLeaves() && PapiBlockCullingCache.isFullLeaves(support));
            ((PapiShulkerRenderState) state).papi$setBottomOccluded(occluded);
        } finally {
            BlockPosPool.reset(mark);
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void papi$submitCulledBaseShulker(
            ShulkerBoxRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        Model<Float> culledModel = this.papi$culledBaseModel;
        if (culledModel == null || !((PapiShulkerRenderState) state).papi$isBottomOccluded()) {
            return;
        }
        DyeColor color = state.color;
        SpriteId spriteId = color == null ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION : Sheets.getShulkerBoxSprite(color);
        Float animState = Float.valueOf(state.progress);

        poseStack.pushPose();
        poseStack.mulPose(modelTransform(state.direction));
        culledModel.setupAnim(animState);
        collector.submitModel(
                culledModel,
                animState,
                poseStack,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                -1,
                spriteId,
                this.sprites,
                0
        );
        if (state.breakProgress != null) {
            collector.order(1).submitCrumblingOverlay(
                    culledModel,
                    animState,
                    poseStack,
                    spriteId.renderType(culledModel.renderType()),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    state.breakProgress
            );
        }
        poseStack.popPose();
        ci.cancel();
    }
}
