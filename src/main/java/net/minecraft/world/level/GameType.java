package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Abilities;

public enum GameType {
    SURVIVAL(0, "survival"),
    CREATIVE(1, "creative"),
    ADVENTURE(2, "adventure"),
    SPECTATOR(3, "spectator");

    public static final GameType DEFAULT_MODE = SURVIVAL;
    private static final int NOT_SET = -1;
    private final int id;
    private final String name;
    private final Component shortName;
    private final Component longName;

    private GameType(int p_46390_, String p_46391_) {
        this.id = p_46390_;
        this.name = p_46391_;
        this.shortName = Component.translatable("selectWorld.gameMode." + p_46391_);
        this.longName = Component.translatable("gameMode." + p_46391_);
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Component getLongDisplayName() {
        return this.longName;
    }

    public Component getShortDisplayName() {
        return this.shortName;
    }

    public void updatePlayerAbilities(Abilities pAbilities) {
        if (this == CREATIVE) {
            pAbilities.mayfly = true;
            pAbilities.instabuild = true;
            pAbilities.invulnerable = true;
        } else if (this == SPECTATOR) {
            pAbilities.mayfly = true;
            pAbilities.instabuild = false;
            pAbilities.invulnerable = true;
            pAbilities.flying = true;
        } else {
            pAbilities.mayfly = false;
            pAbilities.instabuild = false;
            pAbilities.invulnerable = false;
            pAbilities.flying = false;
        }

        pAbilities.mayBuild = !this.isBlockPlacingRestricted();
    }

    public boolean isBlockPlacingRestricted() {
        return this == ADVENTURE || this == SPECTATOR;
    }

    public boolean isCreative() {
        return this == CREATIVE;
    }

    public boolean isSurvival() {
        return this == SURVIVAL || this == ADVENTURE;
    }

    public static GameType byId(int pId) {
        return byId(pId, DEFAULT_MODE);
    }

    public static GameType byId(int pTargetId, GameType pFallback) {
        for (GameType gametype : values()) {
            if (gametype.id == pTargetId) {
                return gametype;
            }
        }

        return pFallback;
    }

    public static GameType byName(String pGamemodeName) {
        return byName(pGamemodeName, SURVIVAL);
    }

    public static GameType byName(String pTargetName, GameType pFallback) {
        for (GameType gametype : values()) {
            if (gametype.name.equals(pTargetName)) {
                return gametype;
            }
        }

        return pFallback;
    }

    public static int getNullableId(@Nullable GameType pGameType) {
        return pGameType != null ? pGameType.id : -1;
    }

    @Nullable
    public static GameType byNullableId(int pId) {
        return pId == -1 ? null : byId(pId);
    }
}
