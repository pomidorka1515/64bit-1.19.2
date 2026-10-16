package net.minecraft.world.level.gameevent;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GameEvent {
    public static final GameEvent BLOCK_ACTIVATE = register("block_activate");
    public static final GameEvent BLOCK_ATTACH = register("block_attach");
    public static final GameEvent BLOCK_CHANGE = register("block_change");
    public static final GameEvent BLOCK_CLOSE = register("block_close");
    public static final GameEvent BLOCK_DEACTIVATE = register("block_deactivate");
    public static final GameEvent BLOCK_DESTROY = register("block_destroy");
    public static final GameEvent BLOCK_DETACH = register("block_detach");
    public static final GameEvent BLOCK_OPEN = register("block_open");
    public static final GameEvent BLOCK_PLACE = register("block_place");
    public static final GameEvent CONTAINER_CLOSE = register("container_close");
    public static final GameEvent CONTAINER_OPEN = register("container_open");
    public static final GameEvent DISPENSE_FAIL = register("dispense_fail");
    public static final GameEvent DRINK = register("drink");
    public static final GameEvent EAT = register("eat");
    public static final GameEvent ELYTRA_GLIDE = register("elytra_glide");
    public static final GameEvent ENTITY_DAMAGE = register("entity_damage");
    public static final GameEvent ENTITY_DIE = register("entity_die");
    public static final GameEvent ENTITY_INTERACT = register("entity_interact");
    public static final GameEvent ENTITY_PLACE = register("entity_place");
    public static final GameEvent ENTITY_ROAR = register("entity_roar");
    public static final GameEvent ENTITY_SHAKE = register("entity_shake");
    public static final GameEvent EQUIP = register("equip");
    public static final GameEvent EXPLODE = register("explode");
    public static final GameEvent FLAP = register("flap");
    public static final GameEvent FLUID_PICKUP = register("fluid_pickup");
    public static final GameEvent FLUID_PLACE = register("fluid_place");
    public static final GameEvent HIT_GROUND = register("hit_ground");
    public static final GameEvent INSTRUMENT_PLAY = register("instrument_play");
    public static final GameEvent ITEM_INTERACT_FINISH = register("item_interact_finish");
    public static final GameEvent ITEM_INTERACT_START = register("item_interact_start");
    public static final GameEvent JUKEBOX_PLAY = register("jukebox_play", 10);
    public static final GameEvent JUKEBOX_STOP_PLAY = register("jukebox_stop_play", 10);
    public static final GameEvent LIGHTNING_STRIKE = register("lightning_strike");
    public static final GameEvent NOTE_BLOCK_PLAY = register("note_block_play");
    public static final GameEvent PISTON_CONTRACT = register("piston_contract");
    public static final GameEvent PISTON_EXTEND = register("piston_extend");
    public static final GameEvent PRIME_FUSE = register("prime_fuse");
    public static final GameEvent PROJECTILE_LAND = register("projectile_land");
    public static final GameEvent PROJECTILE_SHOOT = register("projectile_shoot");
    public static final GameEvent SCULK_SENSOR_TENDRILS_CLICKING = register("sculk_sensor_tendrils_clicking");
    public static final GameEvent SHEAR = register("shear");
    public static final GameEvent SHRIEK = register("shriek", 32);
    public static final GameEvent SPLASH = register("splash");
    public static final GameEvent STEP = register("step");
    public static final GameEvent SWIM = register("swim");
    public static final GameEvent TELEPORT = register("teleport");
    public static final int DEFAULT_NOTIFICATION_RADIUS = 16;
    private final String name;
    private final int notificationRadius;
    private final Holder.Reference<GameEvent> builtInRegistryHolder = Registry.GAME_EVENT.createIntrusiveHolder(this);

    public GameEvent(String pName, int pNotificationRadius) {
        this.name = pName;
        this.notificationRadius = pNotificationRadius;
    }

    public String getName() {
        return this.name;
    }

    public int getNotificationRadius() {
        return this.notificationRadius;
    }

    private static GameEvent register(String pName) {
        return register(pName, 16);
    }

    private static GameEvent register(String pName, int pNotificationRadius) {
        return Registry.register(Registry.GAME_EVENT, pName, new GameEvent(pName, pNotificationRadius));
    }

    public String toString() {
        return "Game Event{ " + this.name + " , " + this.notificationRadius + "}";
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<GameEvent> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public boolean is(TagKey<GameEvent> pEventTag) {
        return this.builtInRegistryHolder.is(pEventTag);
    }

    public static record Context(@Nullable Entity sourceEntity, @Nullable BlockState affectedState) {
        public static GameEvent.Context of(@Nullable Entity pSourceEntity) {
            return new GameEvent.Context(pSourceEntity, (BlockState) null);
        }

        public static GameEvent.Context of(@Nullable BlockState pAffectedState) {
            return new GameEvent.Context((Entity) null, pAffectedState);
        }

        public static GameEvent.Context of(@Nullable Entity pSourceEntity, @Nullable BlockState pAffectedState) {
            return new GameEvent.Context(pSourceEntity, pAffectedState);
        }
    }

    public static final class Message implements Comparable<GameEvent.Message> {
        private final GameEvent gameEvent;
        private final Vec3 source;
        private final GameEvent.Context context;
        private final GameEventListener recipient;
        private final double distanceToRecipient;

        public Message(GameEvent pGameEvent, Vec3 pSource, GameEvent.Context pContext, GameEventListener pRecipient, Vec3 pRecipientPosition) {
            this.gameEvent = pGameEvent;
            this.source = pSource;
            this.context = pContext;
            this.recipient = pRecipient;
            this.distanceToRecipient = pSource.distanceToSqr(pRecipientPosition);
        }

        public int compareTo(GameEvent.Message pOther) {
            return Double.compare(this.distanceToRecipient, pOther.distanceToRecipient);
        }

        public GameEvent gameEvent() {
            return this.gameEvent;
        }

        public Vec3 source() {
            return this.source;
        }

        public GameEvent.Context context() {
            return this.context;
        }

        public GameEventListener recipient() {
            return this.recipient;
        }
    }
}
