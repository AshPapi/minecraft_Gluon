package net.ashpapi.papioptimization.util;

import net.minecraft.core.Direction;

public final class DirectionConstants {
    public static final Direction[] ALL = Direction.values();
    public static final Direction[] HORIZONTAL = new Direction[]{
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    public static final Direction[] VERTICAL = new Direction[]{
            Direction.DOWN, Direction.UP
    };

    private DirectionConstants() {
    }
}
