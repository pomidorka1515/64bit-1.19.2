package net.minecraft.world.level.lighting;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> extends SectionTracker {
   protected static final int LIGHT_AND_DATA = 0;
   protected static final int LIGHT_ONLY = 1;
   protected static final int EMPTY = 2;
   protected static final DataLayer EMPTY_DATA = new DataLayer();
   private static final Direction[] DIRECTIONS = Direction.values();
   private final LightLayer layer;
   private final LightChunkGetter chunkSource;
   protected final Set<SectionPos> dataSectionSet = new HashSet<>();
   protected final Set<SectionPos> toMarkNoData = new HashSet<>();
   protected final Set<SectionPos> toMarkData = new HashSet<>();
   protected volatile M visibleSectionData;
   protected final M updatingSectionData;
   protected final Set<SectionPos> changedSections = new HashSet<>();
   protected final Set<SectionPos> sectionsAffectedByLightUpdates = new HashSet<>();
   protected final Map<SectionPos, DataLayer> queuedSections = Collections.synchronizedMap(new HashMap<>());
   private final Set<SectionPos> untrustedSections = new HashSet<>();
   private final Set<SectionPos> columnsToRetainQueuedDataFor = new HashSet<>();
   private final Set<SectionPos> toRemove = new HashSet<>();
   protected volatile boolean hasToRemove;

   protected LayerLightSectionStorage(LightLayer p_75745_, LightChunkGetter p_75746_, M p_75747_) {
      super(3, 16, 256);
      this.layer = p_75745_;
      this.chunkSource = p_75746_;
      this.updatingSectionData = p_75747_;
      this.visibleSectionData = p_75747_.copy();
      this.visibleSectionData.disableCache();
   }

   protected boolean storingLightForSection(SectionPos p_75792_) {
      return this.getDataLayer(p_75792_, true) != null;
   }

   @Nullable
   protected DataLayer getDataLayer(SectionPos p_75759_, boolean p_75760_) {
      return this.getDataLayer((M)(p_75760_ ? this.updatingSectionData : this.visibleSectionData), p_75759_);
   }

   @Nullable
   protected DataLayer getDataLayer(M p_75762_, SectionPos p_75763_) {
      return p_75762_.getLayer(p_75763_);
   }

   @Nullable
   public DataLayer getDataLayerData(SectionPos p_75794_) {
      DataLayer datalayer = this.queuedSections.get(p_75794_);
      return datalayer != null ? datalayer : this.getDataLayer(p_75794_, false);
   }

   protected abstract int getLightValue(BlockPos p_75786_);

   protected int getStoredLevel(BlockPos p_75796_) {
      SectionPos i = SectionPos.of(p_75796_);
      DataLayer datalayer = this.getDataLayer(i, true);
      // Unloaded edge sections legitimately have no data layer.  Treat them as
      // unlit rather than allowing a render/light query to throw an NPE.
      return datalayer == null ? 0 : datalayer.get(SectionPos.sectionRelative(p_75796_.getX()), SectionPos.sectionRelative(p_75796_.getY()), SectionPos.sectionRelative(p_75796_.getZ()));
   }

   protected void setStoredLevel(BlockPos p_75773_, int p_75774_) {
      SectionPos i = SectionPos.of(p_75773_);
      if (this.changedSections.add(i)) {
         this.updatingSectionData.copyDataLayer(i);
      }

      DataLayer datalayer = this.getDataLayer(i, true);
      datalayer.set(SectionPos.sectionRelative(p_75773_.getX()), SectionPos.sectionRelative(p_75773_.getY()), SectionPos.sectionRelative(p_75773_.getZ()), p_75774_);
      SectionPos.aroundAndAtBlockPos(p_75773_, this.sectionsAffectedByLightUpdates::add);
   }

   protected int getLevel(SectionPos p_75781_) {
      if (p_75781_ == null) {
         return 2;
      } else if (this.dataSectionSet.contains(p_75781_)) {
         return 0;
      } else {
         return !this.toRemove.contains(p_75781_) && this.updatingSectionData.hasLayer(p_75781_) ? 1 : 2;
      }
   }

   protected int getLevelFromSource(SectionPos p_75771_) {
      if (this.toMarkNoData.contains(p_75771_)) {
         return 2;
      } else {
         return !this.dataSectionSet.contains(p_75771_) && !this.toMarkData.contains(p_75771_) ? 2 : 0;
      }
   }

   protected void setLevel(SectionPos p_75749_, int p_75750_) {
      int i = this.getLevel(p_75749_);
      if (i != 0 && p_75750_ == 0) {
         this.dataSectionSet.add(p_75749_);
         this.toMarkData.remove(p_75749_);
      }

      if (i == 0 && p_75750_ != 0) {
         this.dataSectionSet.remove(p_75749_);
         this.toMarkNoData.remove(p_75749_);
      }

      if (i >= 2 && p_75750_ != 2) {
         if (this.toRemove.contains(p_75749_)) {
            this.toRemove.remove(p_75749_);
         } else {
            this.updatingSectionData.setLayer(p_75749_, this.createDataLayer(p_75749_));
            this.changedSections.add(p_75749_);
            this.onNodeAdded(p_75749_);
            long j = p_75749_.x();
            int k = p_75749_.y();
            long l = p_75749_.z();

            for(int i1 = -1; i1 <= 1; ++i1) {
               for(int j1 = -1; j1 <= 1; ++j1) {
                  for(int k1 = -1; k1 <= 1; ++k1) {
                     this.sectionsAffectedByLightUpdates.add(SectionPos.of(j + j1, k + k1, l + i1));
                  }
               }
            }
         }
      }

      if (i != 2 && p_75750_ >= 2) {
         this.toRemove.add(p_75749_);
      }

      this.hasToRemove = !this.toRemove.isEmpty();
   }

   protected DataLayer createDataLayer(SectionPos p_75797_) {
      DataLayer datalayer = this.queuedSections.get(p_75797_);
      return datalayer != null ? datalayer : new DataLayer();
   }

   protected void clearQueuedSectionBlocks(LayerLightEngine<?, ?> p_75765_, SectionPos p_75766_) {
      if (p_75765_.getQueueSize() != 0) {
         if (p_75765_.getQueueSize() < 8192) {
            p_75765_.removeIf((p_75753_) -> {
               return SectionPos.of(p_75753_).equals(p_75766_);
            });
         } else {
            long i = SectionPos.sectionToBlockCoord(p_75766_.x());
            int j = SectionPos.sectionToBlockCoord(p_75766_.y());
            long k = SectionPos.sectionToBlockCoord(p_75766_.z());

            for(int l = 0; l < 16; ++l) {
               for(int i1 = 0; i1 < 16; ++i1) {
                  for(int j1 = 0; j1 < 16; ++j1) {
                     BlockPos k1 = new BlockPos(i + l, j + i1, k + j1);
                     p_75765_.removeFromQueue(k1);
                  }
               }
            }

         }
      }
   }

   protected boolean hasInconsistencies() {
      return this.hasToRemove;
   }

   protected void markNewInconsistencies(LayerLightEngine<M, ?> engine, boolean updateSkyLight, boolean skipEdgeChecks) {
      if (this.hasInconsistencies() || !this.queuedSections.isEmpty()) {
         for (SectionPos sectionPos : this.toRemove) {
            this.clearQueuedSectionBlocks(engine, sectionPos);
            DataLayer queuedData = this.queuedSections.remove(sectionPos);
            DataLayer removedData = this.updatingSectionData.removeLayer(sectionPos);
            if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(sectionPos))) {
               if (queuedData != null) {
                  this.queuedSections.put(sectionPos, queuedData);
               } else if (removedData != null) {
                  this.queuedSections.put(sectionPos, removedData);
               }
            }
         }

         this.updatingSectionData.clearCache();
         for (SectionPos sectionPos : this.toRemove) {
            this.onNodeRemoved(sectionPos);
         }
         this.toRemove.clear();
         this.hasToRemove = false;

         for (Map.Entry<SectionPos, DataLayer> entry : this.queuedSections.entrySet()) {
            SectionPos sectionPos = entry.getKey();
            if (this.storingLightForSection(sectionPos)) {
               DataLayer dataLayer = entry.getValue();
               if (this.updatingSectionData.getLayer(sectionPos) != dataLayer) {
                  this.clearQueuedSectionBlocks(engine, sectionPos);
                  this.updatingSectionData.setLayer(sectionPos, dataLayer);
                  this.changedSections.add(sectionPos);
               }
            }
         }

         this.updatingSectionData.clearCache();
         if (!skipEdgeChecks) {
            for (SectionPos sectionPos : this.queuedSections.keySet()) {
               this.checkEdgesForSection(engine, sectionPos);
            }
         } else {
            for (SectionPos sectionPos : this.untrustedSections) {
               this.checkEdgesForSection(engine, sectionPos);
            }
         }

         this.untrustedSections.clear();
         Iterator<Map.Entry<SectionPos, DataLayer>> iterator = this.queuedSections.entrySet().iterator();
         while (iterator.hasNext()) {
            Map.Entry<SectionPos, DataLayer> entry = iterator.next();
            if (this.storingLightForSection(entry.getKey())) {
               iterator.remove();
            }
         }
      }
   }

   private void checkEdgesForSection(LayerLightEngine<M, ?> engine, SectionPos sectionPos) {
      if (!this.storingLightForSection(sectionPos)) return;

      long x = SectionPos.sectionToBlockCoord(sectionPos.x());
      int y = SectionPos.sectionToBlockCoord(sectionPos.y());
      long z = SectionPos.sectionToBlockCoord(sectionPos.z());
      for (Direction direction : DIRECTIONS) {
         SectionPos neighborSection = sectionPos.offset(direction);
         if (this.queuedSections.containsKey(neighborSection) || !this.storingLightForSection(neighborSection)) continue;

         for (int first = 0; first < 16; ++first) {
            for (int second = 0; second < 16; ++second) {
               BlockPos from;
               BlockPos to;
               switch (direction) {
                  case DOWN:
                     from = new BlockPos(x + second, y, z + first);
                     to = new BlockPos(x + second, y - 1, z + first);
                     break;
                  case UP:
                     from = new BlockPos(x + second, y + 15, z + first);
                     to = new BlockPos(x + second, y + 16, z + first);
                     break;
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
               engine.checkEdge(from, to, engine.computeLevelFromNeighbor(from, to, engine.getLevel(from)), false);
               engine.checkEdge(to, from, engine.computeLevelFromNeighbor(to, from, engine.getLevel(to)), false);
            }
         }
      }
   }

   protected void onNodeAdded(SectionPos p_75798_) {
   }

   protected void onNodeRemoved(SectionPos p_75799_) {
   }

   protected void enableLightSources(SectionPos p_75775_, boolean p_75776_) {
   }

   public void retainData(SectionPos p_75783_, boolean p_75784_) {
      if (p_75784_) {
         this.columnsToRetainQueuedDataFor.add(p_75783_);
      } else {
         this.columnsToRetainQueuedDataFor.remove(p_75783_);
      }

   }

   protected void queueSectionData(SectionPos p_75755_, @Nullable DataLayer p_75756_, boolean p_75757_) {
      if (p_75756_ != null) {
         this.queuedSections.put(p_75755_, p_75756_);
         if (!p_75757_) {
            this.untrustedSections.add(p_75755_);
         }
      } else {
         this.queuedSections.remove(p_75755_);
      }

   }

   protected void updateSectionStatus(SectionPos p_75788_, boolean p_75789_) {
      boolean flag = this.dataSectionSet.contains(p_75788_);
      if (!flag && !p_75789_) {
         this.toMarkData.add(p_75788_);
         this.checkEdge(null, p_75788_, 0, true);
      }

      if (flag && p_75789_) {
         this.toMarkNoData.add(p_75788_);
         this.checkEdge(null, p_75788_, 2, false);
      }

   }

   protected void runAllUpdates() {
      if (this.hasWork()) {
         this.runUpdates(Integer.MAX_VALUE);
      }

   }

   protected void swapSectionMap() {
      if (!this.changedSections.isEmpty()) {
         M m = this.updatingSectionData.copy();
         m.disableCache();
         this.visibleSectionData = m;
         this.changedSections.clear();
      }

      if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
         for (SectionPos sectionPos : this.sectionsAffectedByLightUpdates) {
            this.chunkSource.onLightUpdate(this.layer, sectionPos);
         }

         this.sectionsAffectedByLightUpdates.clear();
      }

   }
}