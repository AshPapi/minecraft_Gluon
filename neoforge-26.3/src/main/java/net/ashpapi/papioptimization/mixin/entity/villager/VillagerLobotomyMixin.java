package net.ashpapi.papioptimization.mixin.entity.villager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Villager.class, priority = 1500)
public abstract class VillagerLobotomyMixin extends AbstractVillager {

    @Shadow
    public abstract int getVillagerXp();

    @Shadow
    public abstract boolean shouldRestock(ServerLevel level);

    @Unique
    private static final int PAPI$BASE_RECHECK_INTERVAL = 100;

    @Unique
    private static final int PAPI$FREE_BACKOFF_INTERVAL = 300;

    @Unique
    private static final int[] PAPI$DX = {1, -1, 0, 0};

    @Unique
    private static final int[] PAPI$DZ = {0, 0, 1, -1};

    @Unique
    private boolean papi$trapped;

    @Unique
    private int papi$freeChecks;

    @Unique
    private long papi$nextTrappedCheckTick = Long.MIN_VALUE;

    @Unique
    private double papi$lastCheckX = Double.NaN;

    @Unique
    private double papi$lastCheckY = Double.NaN;

    @Unique
    private double papi$lastCheckZ = Double.NaN;

    protected VillagerLobotomyMixin(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    @Unique
    private static boolean papi$isVillagerPassable(BlockState state) {
        if (state.isPathfindable(PathComputationType.LAND) || state.getBlock() instanceof BedBlock) {
            return true;
        }
        return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
    }

    @Unique
    private boolean papi$checkIsBoxedIn(ServerLevel level) {
        int bx = this.getBlockX();
        int by = Mth.floor(this.getY() + 0.0625D);
        int bz = this.getBlockZ();
        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos pos = BlockPosPool.get(bx, by + 2, bz);
            boolean headClearAbove = papi$isVillagerPassable(level.getBlockState(pos));
            var chunkSource = level.getChunkSource();
            for (int i = 0; i < 4; i++) {
                int nx = bx + PAPI$DX[i];
                int nz = bz + PAPI$DZ[i];
                LevelChunk nChunk = chunkSource.getChunkNow(nx >> 4, nz >> 4);
                if (nChunk == null) {
                    continue;
                }
                BlockState head = nChunk.getBlockState(pos.set(nx, by + 1, nz));
                if (!papi$isVillagerPassable(head)) {
                    continue;
                }
                BlockState feet = nChunk.getBlockState(pos.set(nx, by, nz));
                if (papi$isVillagerPassable(feet)) {
                    return false;
                }
                if (headClearAbove && papi$isVillagerPassable(nChunk.getBlockState(pos.set(nx, by + 2, nz)))) {
                    return false;
                }
            }
            return true;
        } finally {
            BlockPosPool.reset(mark);
        }
    }

    @WrapOperation(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void papi$lobotomizeTrappedVillagerBrain(
            Brain<Villager> brain,
            ServerLevel level,
            LivingEntity entity,
            Operation<Void> original
    ) {
        if (this.isBaby()
                || this.isTrading()
                || this.isSleeping()
                || this.isPassenger()
                || this.getVillagerXp() <= 0
                || this.hurtTime > 0
                || this.getLastHurtByMob() != null
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || brain.isActive(Activity.RAID)
                || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.HIDE)
                || brain.isActive(Activity.PANIC)
                || this.isInWater()
                || this.isInLava()
                || !this.onGround()
                || this.shouldRestock(level)) {
            this.papi$trapped = false;
            this.papi$freeChecks = 0;
            original.call(brain, level, entity);
            return;
        }

        long gameTime = level.getGameTime();
        double dx = this.getX() - this.papi$lastCheckX;
        double dy = this.getY() - this.papi$lastCheckY;
        double dz = this.getZ() - this.papi$lastCheckZ;
        boolean moved = Double.isNaN(this.papi$lastCheckX) || (dx * dx + dy * dy + dz * dz > 0.25D);
        if (moved) {
            this.papi$freeChecks = 0;
        }
        if (moved || gameTime >= this.papi$nextTrappedCheckTick) {
            boolean trappedNow = this.papi$checkIsBoxedIn(level);
            this.papi$trapped = trappedNow;
            this.papi$lastCheckX = this.getX();
            this.papi$lastCheckY = this.getY();
            this.papi$lastCheckZ = this.getZ();
            int baseInterval;
            if (trappedNow) {
                this.papi$freeChecks = 0;
                baseInterval = PAPI$BASE_RECHECK_INTERVAL;
            } else {
                int streak = ++this.papi$freeChecks;
                baseInterval = streak >= 2 ? PAPI$FREE_BACKOFF_INTERVAL : PAPI$BASE_RECHECK_INTERVAL;
            }
            this.papi$nextTrappedCheckTick = gameTime + baseInterval + (this.getId() & 31);
        }

        if (this.papi$trapped) {
            int interval = PapiMsptController.getVillagerLobotomyInterval();
            if (interval > 1 && Math.floorMod(this.tickCount + this.getId(), interval) != 0) {
                return;
            }
        }

        original.call(brain, level, entity);
    }
}
