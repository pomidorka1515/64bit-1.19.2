package net.minecraft.client.renderer.chunk;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
class RenderChunk {
    private final Map<BlockPos, BlockEntity> blockEntities;
    @Nullable
    private final PalettedContainer<BlockState>[] sections;
    private final int minSection;
    private final boolean debug;
    private final LevelChunk wrapped;

    RenderChunk(LevelChunk pWrapped) {
        this.wrapped = pWrapped;
        this.minSection = pWrapped.getMinSection();
        this.debug = pWrapped.getLevel().isDebug();
        this.blockEntities = ImmutableMap.copyOf(pWrapped.getBlockEntities());
        if (pWrapped instanceof EmptyLevelChunk) {
            this.sections = null;
        } else {
            this.sections = copySections(pWrapped.getSections());
        }
    }

    static PalettedContainer<BlockState>[] copySections(LevelChunkSection[] sections) {
        PalettedContainer<BlockState>[] copies = new PalettedContainer[sections.length];
        for (int i = 0; i < sections.length; ++i) {
            copies[i] = sections[i].hasOnlyAir() ? null : sections[i].getStates().copy();
        }
        return copies;
    }

    static int sectionIndex(int blockY, int minSection) {
        return (blockY >> 4) - minSection;
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pPos) {
        return this.blockEntities.get(pPos);
    }

    public BlockState getBlockState(BlockPos pPos) {
        // Only section-local bits and the legacy debug-world pattern use this projection.
        long i = pPos.xLow();
        int j = pPos.getY();
        long k = pPos.zLow();
        if (this.debug) {
            BlockState blockstate = null;
            if (j == 60) {
                blockstate = Blocks.BARRIER.defaultBlockState();
            }

            if (j == 70) {
                blockstate = pPos.xFitsLong() && pPos.zFitsLong()
                        ? DebugLevelSource.getBlockStateFor(i, k) : Blocks.AIR.defaultBlockState();
            }

            return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
        } else if (this.sections == null) {
            return Blocks.AIR.defaultBlockState();
        } else {
            try {
                int l = sectionIndex(j, this.minSection);
                if (l >= 0 && l < this.sections.length) {
                    PalettedContainer<BlockState> palettedcontainer = this.sections[l];
                    if (palettedcontainer != null) {
                        return palettedcontainer.get((int) (i & 15), j & 15, (int) (k & 15));
                    }
                }

                return Blocks.AIR.defaultBlockState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Block being got");
                crashreportcategory.setDetail("Location", () -> {
                    return CrashReportCategory.formatLocation(this.wrapped, pPos);
                });
                throw new ReportedException(crashreport);
            }
        }
    }
}
