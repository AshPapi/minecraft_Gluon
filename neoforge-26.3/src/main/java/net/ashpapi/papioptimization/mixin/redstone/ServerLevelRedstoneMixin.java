package net.ashpapi.papioptimization.mixin.redstone;

import net.ashpapi.papioptimization.access.PapiServerLevelRedstone;
import net.ashpapi.papioptimization.redstone.PapiRedstoneWavefront;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelRedstoneMixin implements PapiServerLevelRedstone {

    @Unique
    private PapiRedstoneWavefront.@Nullable Solver papi$redstoneSolver;

    @Override
    public PapiRedstoneWavefront.Solver papi$getRedstoneSolver() {
        PapiRedstoneWavefront.Solver solver = this.papi$redstoneSolver;
        if (solver == null) {
            solver = new PapiRedstoneWavefront.Solver();
            this.papi$redstoneSolver = solver;
        }
        return solver;
    }
}
