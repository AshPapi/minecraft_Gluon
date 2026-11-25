package net.ashpapi.papioptimization.mixin.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.ashpapi.papioptimization.memory.PapiShapeCanonicalizer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WallBlock.class, priority = 1500)
public abstract class WallBlockShapeDeduplicationMixin {

    @Shadow @Final public static BooleanProperty UP;
    @Shadow @Final public static EnumProperty<WallSide> NORTH;
    @Shadow @Final public static EnumProperty<WallSide> EAST;
    @Shadow @Final public static EnumProperty<WallSide> SOUTH;
    @Shadow @Final public static EnumProperty<WallSide> WEST;

    @Unique
    private static final Map<Long, Function<BlockState, VoxelShape>> PAPI$WALL_SHAPE_FUNCTIONS = new ConcurrentHashMap<>(4);

    @Inject(method = "makeShapes", at = @At("HEAD"), cancellable = true)
    private void papi$deduplicateWallShapes(
            float postHeight,
            float lowWallHeight,
            CallbackInfoReturnable<Function<BlockState, VoxelShape>> cir
    ) {
        StateDefinition<Block, BlockState> stateDef = ((Block) (Object) this).getStateDefinition();
        if (stateDef == null
                || stateDef.getProperty("up") != UP
                || stateDef.getProperty("north") != NORTH
                || stateDef.getProperty("east") != EAST
                || stateDef.getProperty("south") != SOUTH
                || stateDef.getProperty("west") != WEST) {
            return;
        }
        long key = ((long) Float.floatToRawIntBits(postHeight) << 32)
                | (Float.floatToRawIntBits(lowWallHeight) & 0xFFFFFFFFL);
        Function<BlockState, VoxelShape> cached = PAPI$WALL_SHAPE_FUNCTIONS.computeIfAbsent(key, k -> {
            VoxelShape post = Block.column(8.0, 0.0, postHeight);
            Map<Direction, VoxelShape> low = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, lowWallHeight, 0.0, 11.0));
            Map<Direction, VoxelShape> tall = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, postHeight, 0.0, 11.0));
            WallSide[] sides = WallSide.values();
            VoxelShape[] table = new VoxelShape[162];

            for (int up = 0; up < 2; up++) {
                VoxelShape base = up == 1 ? post : Shapes.empty();
                for (int n = 0; n < 3; n++) {
                    VoxelShape sN = papi$appendSide(base, Direction.NORTH, sides[n], low, tall);
                    for (int e = 0; e < 3; e++) {
                        VoxelShape sE = papi$appendSide(sN, Direction.EAST, sides[e], low, tall);
                        for (int s = 0; s < 3; s++) {
                            VoxelShape sS = papi$appendSide(sE, Direction.SOUTH, sides[s], low, tall);
                            for (int w = 0; w < 3; w++) {
                                VoxelShape sW = papi$appendSide(sS, Direction.WEST, sides[w], low, tall);
                                int idx = up * 81 + n * 27 + e * 9 + s * 3 + w;
                                table[idx] = PapiShapeCanonicalizer.internShape(sW);
                            }
                        }
                    }
                }
            }

            return state -> {
                int idx = (state.getValue(UP) ? 81 : 0)
                        + state.getValue(NORTH).ordinal() * 27
                        + state.getValue(EAST).ordinal() * 9
                        + state.getValue(SOUTH).ordinal() * 3
                        + state.getValue(WEST).ordinal();
                return table[idx];
            };
        });
        cir.setReturnValue(cached);
    }

    @Unique
    private static VoxelShape papi$appendSide(
            VoxelShape current,
            Direction dir,
            WallSide side,
            Map<Direction, VoxelShape> low,
            Map<Direction, VoxelShape> tall
    ) {
        if (side == WallSide.LOW) {
            return Shapes.or(current, low.get(dir));
        }
        if (side == WallSide.TALL) {
            return Shapes.or(current, tall.get(dir));
        }
        return current;
    }
}
