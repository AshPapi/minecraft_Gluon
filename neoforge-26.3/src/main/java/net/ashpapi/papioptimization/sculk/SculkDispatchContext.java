package net.ashpapi.papioptimization.sculk;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SculkDispatchContext implements GameEventListenerRegistry.ListenerVisitor {
    private static final ThreadLocal<SculkDispatchContext> LOCAL =
            ThreadLocal.withInitial(SculkDispatchContext::new);

    public boolean inUse;
    private ServerLevel level;
    private Holder<GameEvent> gameEvent;
    private Vec3 position;
    private GameEvent.Context context;
    private @Nullable ArrayList<GameEvent.ListenerInfo> distanceQueue;

    public static SculkDispatchContext acquire(
            ServerLevel level,
            Holder<GameEvent> gameEvent,
            Vec3 position,
            GameEvent.Context context
    ) {
        SculkDispatchContext ctx = LOCAL.get();
        if (ctx.inUse) {
            ctx = new SculkDispatchContext();
        }
        ctx.inUse = true;
        ctx.level = level;
        ctx.gameEvent = gameEvent;
        ctx.position = position;
        ctx.context = context;
        return ctx;
    }

    @Override
    public void visit(GameEventListener listener, Vec3 listenerPos) {
        if (listener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
            ArrayList<GameEvent.ListenerInfo> q = this.distanceQueue;
            if (q == null) {
                q = new ArrayList<>(4);
                this.distanceQueue = q;
            }
            q.add(new GameEvent.ListenerInfo(this.gameEvent, this.position, this.context, listener, listenerPos));
        } else {
            listener.handleGameEvent(this.level, this.gameEvent, this.context, this.position);
        }
    }

    public @Nullable List<GameEvent.ListenerInfo> getDistanceQueue() {
        ArrayList<GameEvent.ListenerInfo> q = this.distanceQueue;
        return (q != null && !q.isEmpty()) ? q : null;
    }

    public void release() {
        if (this.distanceQueue != null) {
            this.distanceQueue.clear();
        }
        this.level = null;
        this.gameEvent = null;
        this.position = null;
        this.context = null;
        this.inUse = false;
    }
}
