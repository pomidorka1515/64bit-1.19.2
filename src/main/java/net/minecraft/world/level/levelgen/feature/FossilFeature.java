package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableInt;

public class FossilFeature extends Feature<FossilFeatureConfiguration> {
    public FossilFeature(Codec<FossilFeatureConfiguration> pCodec) {
        super(pCodec);
    }

    public boolean place(FeaturePlaceContext<FossilFeatureConfiguration> pContext) {
        RandomSource randomsource = pContext.random();
        WorldGenLevel worldgenlevel = pContext.level();
        BlockPos blockpos = pContext.origin();
        Rotation rotation = Rotation.getRandom(randomsource);
        FossilFeatureConfiguration fossilfeatureconfiguration = pContext.config();
        int i = randomsource.nextInt(fossilfeatureconfiguration.fossilStructures.size());
        StructureTemplateManager structuretemplatemanager = worldgenlevel.getLevel().getServer().getStructureManager();
        StructureTemplate structuretemplate = structuretemplatemanager.getOrCreate(fossilfeatureconfiguration.fossilStructures.get(i));
        StructureTemplate structuretemplate1 = structuretemplatemanager.getOrCreate(fossilfeatureconfiguration.overlayStructures.get(i));
        ChunkPos chunkpos = new ChunkPos(blockpos);
        BoundingBox boundingbox = new BoundingBox(WorldBounds.subtractBlockOffset(chunkpos.getMinBlockX(), 16L), worldgenlevel.getMinBuildHeight(), WorldBounds.subtractBlockOffset(chunkpos.getMinBlockZ(), 16L), WorldBounds.addBlockOffset(chunkpos.getMaxBlockX(), 16L), worldgenlevel.getMaxBuildHeight(), WorldBounds.addBlockOffset(chunkpos.getMaxBlockZ(), 16L));
        StructurePlaceSettings structureplacesettings = (new StructurePlaceSettings()).setRotation(rotation).setBoundingBox(boundingbox).setRandom(randomsource);
        Vec3i vec3i = structuretemplate.getSize(rotation);
        BlockPos blockpos1 = blockpos.offset(-vec3i.getXLo() / 2, 0, -vec3i.getZLo() / 2);
        int j = blockpos.getY();

        for (int k = 0; k < vec3i.getXLo(); ++k) {
            for (int l = 0; l < vec3i.getZLo(); ++l) {
                j = Math.min(j, worldgenlevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, WorldBounds.addBlockOffset(blockpos1.getX(), k), WorldBounds.addBlockOffset(blockpos1.getZ(), l)));
            }
        }

        int i1 = Math.max(j - 15 - randomsource.nextInt(10), worldgenlevel.getMinBuildHeight() + 10);
        BlockPos blockpos2 = structuretemplate.getZeroPositionWithTransform(blockpos1.atY(i1), Mirror.NONE, rotation);
        if (countEmptyCorners(worldgenlevel, structuretemplate.getBoundingBox(structureplacesettings, blockpos2)) > fossilfeatureconfiguration.maxEmptyCornersAllowed) {
            return false;
        } else {
            structureplacesettings.clearProcessors();
            fossilfeatureconfiguration.fossilProcessors.value().list().forEach(structureplacesettings::addProcessor);
            structuretemplate.placeInWorld(worldgenlevel, blockpos2, blockpos2, structureplacesettings, randomsource, 4);
            structureplacesettings.clearProcessors();
            fossilfeatureconfiguration.overlayProcessors.value().list().forEach(structureplacesettings::addProcessor);
            structuretemplate1.placeInWorld(worldgenlevel, blockpos2, blockpos2, structureplacesettings, randomsource, 4);
            return true;
        }
    }

    private static int countEmptyCorners(WorldGenLevel pLevel, BoundingBox pBoundingBox) {
        MutableInt mutableint = new MutableInt(0);
        pBoundingBox.forAllCorners((p_204749_) -> {
            BlockState blockstate = pLevel.getBlockState(p_204749_);
            if (blockstate.isAir() || blockstate.is(Blocks.LAVA) || blockstate.is(Blocks.WATER)) {
                mutableint.add(1);
            }
        });
        return mutableint.getValue();
    }
}
