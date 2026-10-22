package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public interface LevelAccessor extends CommonLevelAccessor, LevelTimeAccess {
    default long dayTime() {
        return this.getLevelData().getDayTime();
    }

    long nextSubTickCount();

    LevelTickAccess<Block> getBlockTicks();

    private <T> ScheduledTick<T> createTick(BlockPos pPos, T pType, int pDelay, TickPriority pPriority) {
        return new ScheduledTick<>(pType, pPos, this.getLevelData().getGameTime() + (long) pDelay, pPriority, this.nextSubTickCount());
    }

    private <T> ScheduledTick<T> createTick(BlockPos pPos, T pType, int pDelay) {
        return new ScheduledTick<>(pType, pPos, this.getLevelData().getGameTime() + (long) pDelay, this.nextSubTickCount());
    }

    default void scheduleTick(BlockPos pPos, Block pBlock, int pDelay, TickPriority pPriority) {
        this.getBlockTicks().schedule(this.createTick(pPos, pBlock, pDelay, pPriority));
    }

    default void scheduleTick(BlockPos pPos, Block pBlock, int pDelay) {
        this.getBlockTicks().schedule(this.createTick(pPos, pBlock, pDelay));
    }

    LevelTickAccess<Fluid> getFluidTicks();

    default void scheduleTick(BlockPos pPos, Fluid pFluid, int pDelay, TickPriority pPriority) {
        this.getFluidTicks().schedule(this.createTick(pPos, pFluid, pDelay, pPriority));
    }

    default void scheduleTick(BlockPos pPos, Fluid pFluid, int pDelay) {
        this.getFluidTicks().schedule(this.createTick(pPos, pFluid, pDelay));
    }

    LevelData getLevelData();

    DifficultyInstance getCurrentDifficultyAt(BlockPos pPos);

    @Nullable
    MinecraftServer getServer();

    default Difficulty getDifficulty() {
        return this.getLevelData().getDifficulty();
    }

    ChunkSource getChunkSource();

    default boolean hasChunk(long pChunkX, long pChunkZ) {
        return this.getChunkSource().hasChunk(pChunkX, pChunkZ);
    }

    default boolean hasChunk(net.minecraft.core.Int128 pChunkX, net.minecraft.core.Int128 pChunkZ) {
        return this.getChunkSource().hasChunk(pChunkX, pChunkZ);
    }

    RandomSource getRandom();

    default void blockUpdated(BlockPos pPos, Block pBlock) {
    }

    default void neighborShapeChanged(Direction p_220411_, BlockState p_220412_, BlockPos p_220413_, BlockPos p_220414_, int p_220415_, int p_220416_) {
        NeighborUpdater.executeShapeUpdate(this, p_220411_, p_220412_, p_220413_, p_220414_, p_220415_, p_220416_ - 1);
    }

    void playSound(@Nullable Player pPlayer, BlockPos pPos, SoundEvent pSound, SoundSource pCategory, float pVolume, float pPitch);

    void addParticle(ParticleOptions pParticleData, double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed);

    /** Exact particle origin for callers that already have split world coordinates. */
    default void addParticle(ParticleOptions options, SectorVec3 position, double xd, double yd, double zd) {
        this.addParticle(options, position.toApproximateVec3().x, position.y(), position.toApproximateVec3().z,
                xd, yd, zd);
    }

    void levelEvent(@Nullable Player pPlayer, int pType, BlockPos pPos, int pData);

    default void levelEvent(int pType, BlockPos pPos, int pData) {
        this.levelEvent((Player) null, pType, pPos, pData);
    }

    void gameEvent(GameEvent pEvent, Vec3 pPosition, GameEvent.Context pContext);

    /** Exact game-event origin for precision-sensitive callers. */
    default void gameEvent(GameEvent event, SectorVec3 position, GameEvent.Context context) {
        this.gameEvent(event, position.toExactVec3(), context);
    }

    default void gameEvent(@Nullable Entity pEntity, GameEvent pEvent, Vec3 pPosition) {
        this.gameEvent(pEvent, pPosition, new GameEvent.Context(pEntity, (BlockState) null));
    }

    default void gameEvent(@Nullable Entity pEntity, GameEvent pEvent, BlockPos pPos) {
        this.gameEvent(pEvent, pPos, new GameEvent.Context(pEntity, (BlockState) null));
    }

    default void gameEvent(GameEvent pEvent, BlockPos pPos, GameEvent.Context pContext) {
        this.gameEvent(pEvent, SectorVec3.fromBlockAndFraction(pPos.getX(), 0.5D, pPos.getY() + 0.5D, pPos.getZ(), 0.5D), pContext);
    }
}
