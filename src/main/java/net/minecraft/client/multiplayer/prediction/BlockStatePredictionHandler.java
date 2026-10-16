package net.minecraft.client.multiplayer.prediction;

import java.util.Map.Entry;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockStatePredictionHandler implements AutoCloseable {
    private final Object2ObjectOpenHashMap<BlockPos, BlockStatePredictionHandler.ServerVerifiedState> serverVerifiedStates = new Object2ObjectOpenHashMap<>();
    private int currentSequenceNr;
    private boolean isPredicting;

    public void retainKnownServerState(BlockPos pPos, BlockState pState, LocalPlayer pPlayer) {
        this.serverVerifiedStates.compute(pPos, (p_233862_, p_233863_) -> {
            return p_233863_ != null ? p_233863_.setSequence(this.currentSequenceNr) : new BlockStatePredictionHandler.ServerVerifiedState(this.currentSequenceNr, pState, pPlayer.exactPosition());
        });
    }

    public boolean updateKnownServerState(BlockPos pPos, BlockState pState) {
        BlockStatePredictionHandler.ServerVerifiedState blockstatepredictionhandler$serververifiedstate = this.serverVerifiedStates.get(pPos);
        if (blockstatepredictionhandler$serververifiedstate == null) {
            return false;
        } else {
            blockstatepredictionhandler$serververifiedstate.setBlockState(pState);
            return true;
        }
    }

    public void endPredictionsUpTo(int p_233857_, ClientLevel p_233858_) {
        ObjectIterator<Entry<BlockPos, ServerVerifiedState>> objectiterator = this.serverVerifiedStates.entrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<BlockPos, ServerVerifiedState> entry = objectiterator.next();
            BlockStatePredictionHandler.ServerVerifiedState blockstatepredictionhandler$serververifiedstate = entry.getValue();
            if (blockstatepredictionhandler$serververifiedstate.sequence <= p_233857_) {
                BlockPos blockpos = entry.getKey();
                objectiterator.remove();
                p_233858_.syncBlockState(blockpos, blockstatepredictionhandler$serververifiedstate.blockState, blockstatepredictionhandler$serververifiedstate.playerPos);
            }
        }
    }

    public BlockStatePredictionHandler startPredicting() {
        ++this.currentSequenceNr;
        this.isPredicting = true;
        return this;
    }

    public void close() {
        this.isPredicting = false;
    }

    public int currentSequence() {
        return this.currentSequenceNr;
    }

    public boolean isPredicting() {
        return this.isPredicting;
    }

    @OnlyIn(Dist.CLIENT)
    static class ServerVerifiedState {
        final SectorVec3 playerPos;
        int sequence;
        BlockState blockState;

        ServerVerifiedState(int pSequence, BlockState pBlockState, SectorVec3 pPlayerPos) {
            this.sequence = pSequence;
            this.blockState = pBlockState;
            this.playerPos = pPlayerPos;
        }

        BlockStatePredictionHandler.ServerVerifiedState setSequence(int pSequence) {
            this.sequence = pSequence;
            return this;
        }

        void setBlockState(BlockState pBlockState) {
            this.blockState = pBlockState;
        }
    }
}
