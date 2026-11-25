package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.math.FastMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = CampfireBlockEntity.class, priority = 1500)
public abstract class CampfireBlockEntityClientMixin {

    @Inject(method = "particleTick", at = @At("HEAD"), cancellable = true)
    private static void papi$optimizeParticleTick(
            Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        double refX;
        double refY;
        double refZ;
        if (mc.gameRenderer != null) {
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            refX = cam.x;
            refY = cam.y;
            refZ = cam.z;
        } else {
            LocalPlayer player = mc.player;
            if (player == null) {
                return;
            }
            refX = player.getX();
            refY = player.getY();
            refZ = player.getZ();
        }

        double dx = (double) pos.getX() + 0.5 - refX;
        double dy = (double) pos.getY() + 0.5 - refY;
        double dz = (double) pos.getZ() + 0.5 - refZ;

        boolean isSignalFire = state.hasProperty(CampfireBlock.SIGNAL_FIRE) && state.getValue(CampfireBlock.SIGNAL_FIRE);

        if (!isSignalFire) {
            if (!FastMath.isWithinDistance(dx, dy, dz, 48.0)) {
                ci.cancel();
                return;
            }
            if (!FastMath.isWithinDistance(dx, dy, dz, 24.0) && ((pos.getX() ^ pos.getZ() ^ (int) level.getGameTime()) & 1) == 0) {
                ci.cancel();
                return;
            }
        }

        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL && (!isSignalFire || (level.getGameTime() % 5L != 0L))) {
            ci.cancel();
        }
    }
}
