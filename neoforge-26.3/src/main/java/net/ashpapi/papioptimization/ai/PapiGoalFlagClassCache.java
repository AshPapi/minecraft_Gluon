package net.ashpapi.papioptimization.ai;

import net.minecraft.world.entity.ai.goal.Goal;

public final class PapiGoalFlagClassCache {

    private static final ClassValue<Boolean> STANDARD_GET_FLAGS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getFlags").getDeclaringClass() == Goal.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    private PapiGoalFlagClassCache() {
    }

    public static boolean hasStandardGetFlags(Class<?> cls) {
        return STANDARD_GET_FLAGS.get(cls);
    }
}
