package net.minecraft.world.level.lighting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private final Set<SectionPos> sectionsWithSources = new HashSet<>();
   private final Set<SectionPos> sectionsToAddSourcesTo = new HashSet<>();
   private final Set<SectionPos> sectionsToRemoveSourcesFrom = new HashSet<>();
   private final Set<SectionPos> columnsWithSkySources = new HashSet<>();
   private volatile boolean hasSourceInconsistencies;

   protected SkyLightSectionStorage(LightChunkGetter chunkSource) {
      super(LightLayer.SKY, chunkSource, new SkyDataLayerStorageMap(new HashMap<>(), new HashMap<>(), Integer.MAX_VALUE));
   }

   protected int getLightValue(BlockPos blockPos) {
      return this.getLightValue(blockPos, false);
   }

   protected int getLightValue(BlockPos blockPos, boolean updating) {
      SectionPos sectionPos = SectionPos.of(blockPos);
      int sectionY = sectionPos.y();
      SkyDataLayerStorageMap data = updating ? this.updatingSectionData : this.visibleSectionData;
      int topSection = data.topSections.getOrDefault(SectionPos.getZeroNode(sectionPos), data.currentLowestY);
      if (topSection != data.currentLowestY && sectionY < topSection) {
         DataLayer layer = this.getDataLayer(data, sectionPos);
         BlockPos currentPos = blockPos;
         while (layer == null) {
            ++sectionY;
            if (sectionY >= topSection) return 15;
            currentPos = currentPos.above(16);
            sectionPos = sectionPos.offset(Direction.UP);
            layer = this.getDataLayer(data, sectionPos);
         }
         return layer.get(SectionPos.sectionRelative(currentPos.getX()), SectionPos.sectionRelative(currentPos.getY()), SectionPos.sectionRelative(currentPos.getZ()));
      }
      return updating && !this.lightOnInSection(sectionPos) ? 0 : 15;
   }

   protected void onNodeAdded(SectionPos sectionPos) {
      int sectionY = sectionPos.y();
      if (this.updatingSectionData.currentLowestY > sectionY) {
         this.updatingSectionData.currentLowestY = sectionY;
      }

      SectionPos column = SectionPos.getZeroNode(sectionPos);
      int topSection = this.updatingSectionData.topSections.getOrDefault(column, this.updatingSectionData.currentLowestY);
      if (topSection < sectionY + 1) {
         this.updatingSectionData.topSections.put(column, sectionY + 1);
         if (this.columnsWithSkySources.contains(column)) {
            this.queueAddSource(sectionPos);
            if (topSection > this.updatingSectionData.currentLowestY) {
               this.queueRemoveSource(SectionPos.of(sectionPos.x(), topSection - 1, sectionPos.z()));
            }
            this.recheckInconsistencyFlag();
         }
      }
   }

   private void queueRemoveSource(SectionPos sectionPos) {
      this.sectionsToRemoveSourcesFrom.add(sectionPos);
      this.sectionsToAddSourcesTo.remove(sectionPos);
   }

   private void queueAddSource(SectionPos sectionPos) {
      this.sectionsToAddSourcesTo.add(sectionPos);
      this.sectionsToRemoveSourcesFrom.remove(sectionPos);
   }

   private void recheckInconsistencyFlag() {
      this.hasSourceInconsistencies = !this.sectionsToAddSourcesTo.isEmpty() || !this.sectionsToRemoveSourcesFrom.isEmpty();
   }

   protected void onNodeRemoved(SectionPos sectionPos) {
      SectionPos column = SectionPos.getZeroNode(sectionPos);
      boolean sourcesEnabled = this.columnsWithSkySources.contains(column);
      if (sourcesEnabled) this.queueRemoveSource(sectionPos);

      int sectionY = sectionPos.y();
      if (this.updatingSectionData.topSections.getOrDefault(column, this.updatingSectionData.currentLowestY) == sectionY + 1) {
         SectionPos below = sectionPos;
         while (!this.storingLightForSection(below) && this.hasSectionsBelow(sectionY)) {
            --sectionY;
            below = below.offset(Direction.DOWN);
         }

         if (this.storingLightForSection(below)) {
            this.updatingSectionData.topSections.put(column, sectionY + 1);
            if (sourcesEnabled) this.queueAddSource(below);
         } else {
            this.updatingSectionData.topSections.remove(column);
         }
      }
      if (sourcesEnabled) this.recheckInconsistencyFlag();
   }

   protected void enableLightSources(SectionPos column, boolean enabled) {
      if (!WorldBounds.isValidChunk(column.x(), column.z())) return;

      this.runAllUpdates();
      if (enabled && this.columnsWithSkySources.add(column)) {
         int topSection = this.updatingSectionData.topSections.getOrDefault(column, this.updatingSectionData.currentLowestY);
         if (topSection != this.updatingSectionData.currentLowestY) {
            this.queueAddSource(SectionPos.of(column.x(), topSection - 1, column.z()));
            this.recheckInconsistencyFlag();
         }
      } else if (!enabled) {
         this.columnsWithSkySources.remove(column);
      }
   }

   protected boolean hasInconsistencies() {
      return super.hasInconsistencies() || this.hasSourceInconsistencies;
   }

   protected DataLayer createDataLayer(SectionPos sectionPos) {
      DataLayer queuedData = this.queuedSections.get(sectionPos);
      if (queuedData != null) return queuedData;

      SectionPos above = sectionPos.offset(Direction.UP);
      int topSection = this.updatingSectionData.topSections.getOrDefault(SectionPos.getZeroNode(sectionPos), this.updatingSectionData.currentLowestY);
      if (topSection != this.updatingSectionData.currentLowestY && above.y() < topSection) {
         DataLayer layer;
         while ((layer = this.getDataLayer(above, true)) == null) {
            above = above.offset(Direction.UP);
         }
         return repeatFirstLayer(layer);
      }
      return new DataLayer();
   }

   private static DataLayer repeatFirstLayer(DataLayer layer) {
      if (layer.isEmpty()) return new DataLayer();
      byte[] source = layer.getData();
      byte[] repeated = new byte[2048];
      for (int y = 0; y < 16; ++y) System.arraycopy(source, 0, repeated, y * 128, 128);
      return new DataLayer(repeated);
   }

   protected void markNewInconsistencies(LayerLightEngine<SkyDataLayerStorageMap, ?> engine, boolean updateSkyLight, boolean skipEdgeChecks) {
      super.markNewInconsistencies(engine, updateSkyLight, skipEdgeChecks);
      if (!updateSkyLight) return;

      for (SectionPos sectionPos : this.sectionsToAddSourcesTo) {
         int level = this.getLevel(sectionPos);
         if (level != 2 && !this.sectionsToRemoveSourcesFrom.contains(sectionPos) && this.sectionsWithSources.add(sectionPos)) {
            if (level == 1) {
               this.clearQueuedSectionBlocks(engine, sectionPos);
               if (this.changedSections.add(sectionPos)) this.updatingSectionData.copyDataLayer(sectionPos);
               Arrays.fill(this.getDataLayer(sectionPos, true).getData(), (byte)-1);

               long x = SectionPos.sectionToBlockCoord(sectionPos.x());
               int y = SectionPos.sectionToBlockCoord(sectionPos.y());
               long z = SectionPos.sectionToBlockCoord(sectionPos.z());
               for (Direction direction : HORIZONTALS) {
                  SectionPos neighbor = sectionPos.offset(direction);
                  if ((this.sectionsToRemoveSourcesFrom.contains(neighbor) || (!this.sectionsWithSources.contains(neighbor) && !this.sectionsToAddSourcesTo.contains(neighbor))) && this.storingLightForSection(neighbor)) {
                     for (int first = 0; first < 16; ++first) {
                        for (int second = 0; second < 16; ++second) {
                           BlockPos from;
                           BlockPos to;
                           switch (direction) {
                              case NORTH:
                                 from = new BlockPos(x + first, y + second, z);
                                 to = new BlockPos(x + first, y + second, z - 1);
                                 break;
                              case SOUTH:
                                 from = new BlockPos(x + first, y + second, z + 15);
                                 to = new BlockPos(x + first, y + second, z + 16);
                                 break;
                              case WEST:
                                 from = new BlockPos(x, y + first, z + second);
                                 to = new BlockPos(x - 1, y + first, z + second);
                                 break;
                              default:
                                 from = new BlockPos(x + 15, y + first, z + second);
                                 to = new BlockPos(x + 16, y + first, z + second);
                           }
                           engine.checkEdge(from, to, engine.computeLevelFromNeighbor(from, to, 0), true);
                        }
                     }
                  }
               }

               for (int xOffset = 0; xOffset < 16; ++xOffset) {
                  for (int zOffset = 0; zOffset < 16; ++zOffset) {
                     BlockPos from = new BlockPos(SectionPos.sectionToBlockCoord(sectionPos.x(), xOffset), SectionPos.sectionToBlockCoord(sectionPos.y()), SectionPos.sectionToBlockCoord(sectionPos.z(), zOffset));
                     BlockPos to = from.below();
                     engine.checkEdge(from, to, engine.computeLevelFromNeighbor(from, to, 0), true);
                  }
               }
            } else {
               for (int xOffset = 0; xOffset < 16; ++xOffset) {
                  for (int zOffset = 0; zOffset < 16; ++zOffset) {
                     BlockPos blockPos = new BlockPos(SectionPos.sectionToBlockCoord(sectionPos.x(), xOffset), SectionPos.sectionToBlockCoord(sectionPos.y(), 15), SectionPos.sectionToBlockCoord(sectionPos.z(), zOffset));
                     engine.checkEdge(null, blockPos, 0, true);
                  }
               }
            }
         }
      }
      this.sectionsToAddSourcesTo.clear();

      for (SectionPos sectionPos : this.sectionsToRemoveSourcesFrom) {
         if (this.sectionsWithSources.remove(sectionPos) && this.storingLightForSection(sectionPos)) {
            for (int xOffset = 0; xOffset < 16; ++xOffset) {
               for (int zOffset = 0; zOffset < 16; ++zOffset) {
                  BlockPos blockPos = new BlockPos(SectionPos.sectionToBlockCoord(sectionPos.x(), xOffset), SectionPos.sectionToBlockCoord(sectionPos.y(), 15), SectionPos.sectionToBlockCoord(sectionPos.z(), zOffset));
                  engine.checkEdge(null, blockPos, 15, false);
               }
            }
         }
      }
      this.sectionsToRemoveSourcesFrom.clear();
      this.hasSourceInconsistencies = false;
   }

   protected boolean hasSectionsBelow(int sectionY) {
      return sectionY >= this.updatingSectionData.currentLowestY;
   }

   protected boolean isAboveData(SectionPos sectionPos) {
      SectionPos column = SectionPos.getZeroNode(sectionPos);
      int topSection = this.updatingSectionData.topSections.getOrDefault(column, this.updatingSectionData.currentLowestY);
      return topSection == this.updatingSectionData.currentLowestY || sectionPos.y() >= topSection;
   }

   protected boolean lightOnInSection(SectionPos sectionPos) {
      return this.columnsWithSkySources.contains(SectionPos.getZeroNode(sectionPos));
   }

   protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyDataLayerStorageMap> {
      int currentLowestY;
      final Map<SectionPos, Integer> topSections;

      SkyDataLayerStorageMap(Map<SectionPos, DataLayer> layers, Map<SectionPos, Integer> topSections, int currentLowestY) {
         super(layers);
         this.topSections = topSections;
         this.currentLowestY = currentLowestY;
      }

      public SkyDataLayerStorageMap copy() {
         return new SkyDataLayerStorageMap(new HashMap<>(this.map), new HashMap<>(this.topSections), this.currentLowestY);
      }
   }
}
