package net.minecraft.world.level.gameevent;

import java.util.function.BiConsumer;
import net.minecraft.world.phys.Vec3;

public interface GameEventDispatcher {
    GameEventDispatcher NOOP = new GameEventDispatcher() {
        public boolean isEmpty() {
            return true;
        }

        public void register(GameEventListener p_157843_) {
        }

        public void unregister(GameEventListener p_157845_) {
        }

        public boolean walkListeners(GameEvent p_223753_, Vec3 p_223754_, GameEvent.Context p_223755_, BiConsumer<GameEventListener, Vec3> p_223756_) {
            return false;
        }
    };

    boolean isEmpty();

    void register(GameEventListener pListener);

    void unregister(GameEventListener pListener);

    boolean walkListeners(GameEvent p_223748_, Vec3 p_223749_, GameEvent.Context p_223750_, BiConsumer<GameEventListener, Vec3> p_223751_);

    default boolean walkListeners(GameEvent event, net.minecraft.world.phys.SectorVec3 position, GameEvent.Context context, BiConsumer<GameEventListener, Vec3> visitor) {
        return this.walkListeners(event, position.toExactVec3(), context, visitor);
    }
}
