package net.minecraft.world.level.gameevent;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class EuclideanGameEventDispatcher implements GameEventDispatcher {
    private final List<GameEventListener> listeners = Lists.newArrayList();
    private final Set<GameEventListener> listenersToRemove = Sets.newHashSet();
    private final List<GameEventListener> listenersToAdd = Lists.newArrayList();
    private boolean processing;
    private final ServerLevel level;

    public EuclideanGameEventDispatcher(ServerLevel pLevel) {
        this.level = pLevel;
    }

    public boolean isEmpty() {
        return this.listeners.isEmpty();
    }

    public void register(GameEventListener pListener) {
        if (this.processing) {
            this.listenersToAdd.add(pListener);
        } else {
            this.listeners.add(pListener);
        }

        DebugPackets.sendGameEventListenerInfo(this.level, pListener);
    }

    public void unregister(GameEventListener pListener) {
        if (this.processing) {
            this.listenersToRemove.add(pListener);
        } else {
            this.listeners.remove(pListener);
        }
    }

    public boolean walkListeners(GameEvent p_223692_, Vec3 p_223693_, GameEvent.Context p_223694_, BiConsumer<GameEventListener, Vec3> p_223695_) {
        return this.walkListeners(p_223692_, net.minecraft.world.phys.SectorVec3.fromApproximate(p_223693_), p_223694_, p_223695_);
    }

    @Override
    public boolean walkListeners(GameEvent p_223692_, net.minecraft.world.phys.SectorVec3 p_223693_, GameEvent.Context p_223694_, BiConsumer<GameEventListener, Vec3> p_223695_) {
        this.processing = true;
        boolean flag = false;

        try {
            Iterator<GameEventListener> iterator = this.listeners.iterator();

            while (iterator.hasNext()) {
                GameEventListener gameeventlistener = iterator.next();
                if (this.listenersToRemove.remove(gameeventlistener)) {
                    iterator.remove();
                } else {
                    Optional<Vec3> optional = getPostableListenerPosition(this.level, p_223693_, gameeventlistener);
                    if (optional.isPresent()) {
                        p_223695_.accept(gameeventlistener, optional.get());
                        flag = true;
                    }
                }
            }
        } finally {
            this.processing = false;
        }

        if (!this.listenersToAdd.isEmpty()) {
            this.listeners.addAll(this.listenersToAdd);
            this.listenersToAdd.clear();
        }

        if (!this.listenersToRemove.isEmpty()) {
            this.listeners.removeAll(this.listenersToRemove);
            this.listenersToRemove.clear();
        }

        return flag;
    }

    private static Optional<Vec3> getPostableListenerPosition(ServerLevel p_223688_, net.minecraft.world.phys.SectorVec3 p_223689_, GameEventListener p_223690_) {
        Optional<net.minecraft.world.phys.SectorVec3> optional = p_223690_.getListenerSource().exactPosition(p_223688_);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else {
            double d0 = optional.get().relativeTo(p_223689_).lengthSqr();
            int i = p_223690_.getListenerRadius() * p_223690_.getListenerRadius();
            return d0 > (double) i ? Optional.empty() : optional.map(net.minecraft.world.phys.SectorVec3::toApproximateVec3);
        }
    }
}
