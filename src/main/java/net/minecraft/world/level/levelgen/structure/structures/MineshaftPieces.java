package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.SectorVec3;
import org.slf4j.Logger;

public class MineshaftPieces {
   static final Logger LOGGER = LogUtils.getLogger();
   private static final int DEFAULT_SHAFT_WIDTH = 3;
   private static final int DEFAULT_SHAFT_HEIGHT = 3;
   private static final int DEFAULT_SHAFT_LENGTH = 5;
   private static final int MAX_PILLAR_HEIGHT = 20;
   private static final int MAX_CHAIN_HEIGHT = 50;
   private static final int MAX_DEPTH = 7;
   private static final int MAX_CORRIDOR_SECTIONS = 4;
   private static final int MAX_ROOM_ENTRANCE_ATTEMPTS = 4;
   static final int MAX_PIECES = 50;
   public static final int MAGIC_START_Y = 50;

   private static MineshaftPieces.MineShaftPiece createRandomShaftPiece(StructurePieceAccessor p_227716_, RandomSource p_227717_, long p_227718_, int p_227719_, long p_227720_, @Nullable Direction p_227721_, int p_227722_, MineshaftStructure.Type p_227723_) {
      int i = p_227717_.nextInt(100);
      if (i >= 80) {
         BoundingBox boundingbox = MineshaftPieces.MineShaftCrossing.findCrossing(p_227716_, p_227717_, p_227718_, p_227719_, p_227720_, p_227721_);
         if (boundingbox != null) {
            return new MineshaftPieces.MineShaftCrossing(p_227722_, boundingbox, p_227721_, p_227723_);
         }
      } else if (i >= 70) {
         BoundingBox boundingbox1 = MineshaftPieces.MineShaftStairs.findStairs(p_227716_, p_227717_, p_227718_, p_227719_, p_227720_, p_227721_);
         if (boundingbox1 != null) {
            return new MineshaftPieces.MineShaftStairs(p_227722_, boundingbox1, p_227721_, p_227723_);
         }
      } else {
         BoundingBox boundingbox2 = MineshaftPieces.MineShaftCorridor.findCorridorSize(p_227716_, p_227717_, p_227718_, p_227719_, p_227720_, p_227721_);
         if (boundingbox2 != null) {
            return new MineshaftPieces.MineShaftCorridor(p_227722_, p_227717_, boundingbox2, p_227721_, p_227723_);
         }
      }

      return null;
   }

   static MineshaftPieces.MineShaftPiece generateAndAddPiece(StructurePiece p_227707_, StructurePieceAccessor p_227708_, RandomSource p_227709_, long p_227710_, int p_227711_, long p_227712_, Direction p_227713_, int p_227714_) {
      if (p_227714_ > MAX_DEPTH || p_227708_.size() >= MAX_PIECES) {
         return null;
      } else if (WorldBounds.within(p_227710_, p_227707_.getBoundingBox().minX(), 80L) && WorldBounds.within(p_227712_, p_227707_.getBoundingBox().minZ(), 80L)) {
         MineshaftStructure.Type mineshaftstructure$type = ((MineshaftPieces.MineShaftPiece)p_227707_).type;
         MineshaftPieces.MineShaftPiece mineshaftpieces$mineshaftpiece = createRandomShaftPiece(p_227708_, p_227709_, p_227710_, p_227711_, p_227712_, p_227713_, p_227714_ + 1, mineshaftstructure$type);
         if (mineshaftpieces$mineshaftpiece != null) {
            p_227708_.addPiece(mineshaftpieces$mineshaftpiece);
            mineshaftpieces$mineshaftpiece.addChildren(p_227707_, p_227708_, p_227709_);
         }

         return mineshaftpieces$mineshaftpiece;
      } else {
         return null;
      }
   }

   private static long addBlockOffset(long coordinate, long offset) {
      return WorldBounds.addBlockOffset(coordinate, offset);
   }

   private static BoundingBox moveBoundingBox(BoundingBox box, long x, int y, long z) {
      return new BoundingBox(addBlockOffset(box.minX(), x), box.minY() + y, addBlockOffset(box.minZ(), z), addBlockOffset(box.maxX(), x), box.maxY() + y, addBlockOffset(box.maxZ(), z));
   }

   private static long randomOffset(RandomSource random, long bound) {
      return bound <= 0L ? 0L : Math.floorMod(random.nextLong(), bound);
   }

   private static int corridorSections(BoundingBox box, Direction direction) {
      long span = direction.getAxis() == Direction.Axis.Z ? box.getZSpan() : box.getXSpan();
      return (int)Math.min((long)MAX_CORRIDOR_SECTIONS, Math.max(0L, span / 5L));
   }

   private static long midpoint(long min, long max) {
      return WorldBounds.middleBlockCoordinate(min, max);
   }

   public static class MineShaftCorridor extends MineshaftPieces.MineShaftPiece {
      private final boolean hasRails;
      private final boolean spiderCorridor;
      private boolean hasPlacedSpider;
      private final int numSections;

      public MineShaftCorridor(CompoundTag p_227737_) {
         super(StructurePieceType.MINE_SHAFT_CORRIDOR, p_227737_);
         this.hasRails = p_227737_.getBoolean("hr");
         this.spiderCorridor = p_227737_.getBoolean("sc");
         this.hasPlacedSpider = p_227737_.getBoolean("hps");
         this.numSections = Math.min(MAX_CORRIDOR_SECTIONS, Math.max(0, p_227737_.getInt("Num")));
      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext p_227806_, CompoundTag p_227807_) {
         super.addAdditionalSaveData(p_227806_, p_227807_);
         p_227807_.putBoolean("hr", this.hasRails);
         p_227807_.putBoolean("sc", this.spiderCorridor);
         p_227807_.putBoolean("hps", this.hasPlacedSpider);
         p_227807_.putLong("Num", this.numSections);
      }

      public MineShaftCorridor(int p_227731_, RandomSource p_227732_, BoundingBox p_227733_, Direction p_227734_, MineshaftStructure.Type p_227735_) {
         super(StructurePieceType.MINE_SHAFT_CORRIDOR, p_227731_, p_227735_, p_227733_);
         this.setOrientation(p_227734_);
         this.hasRails = p_227732_.nextInt(3) == 0;
         this.spiderCorridor = !this.hasRails && p_227732_.nextInt(23) == 0;
         this.numSections = corridorSections(p_227733_, p_227734_);

      }

      @Nullable
      public static BoundingBox findCorridorSize(StructurePieceAccessor p_227799_, RandomSource p_227800_, long p_227801_, int p_227802_, long p_227803_, Direction p_227804_) {
         for(int i = p_227800_.nextInt(3) + 2; i > 0; --i) {
            int j = i * 5;
            BoundingBox boundingbox;
            switch (p_227804_) {
               case NORTH:
               default:
                  boundingbox = new BoundingBox(0, 0, -(j - 1), 2, 2, 0);
                  break;
               case SOUTH:
                  boundingbox = new BoundingBox(0, 0, 0, 2, 2, j - 1);
                  break;
               case WEST:
                  boundingbox = new BoundingBox(-(j - 1), 0, 0, 0, 2, 2);
                  break;
               case EAST:
                  boundingbox = new BoundingBox(0, 0, 0, j - 1, 2, 2);
            }

            boundingbox = moveBoundingBox(boundingbox, p_227801_, p_227802_, p_227803_);
            if (p_227799_.findCollisionPiece(boundingbox) == null) {
               return boundingbox;
            }
         }

         return null;
      }

      public void addChildren(StructurePiece p_227795_, StructurePieceAccessor p_227796_, RandomSource p_227797_) {
         int i = this.getGenDepth();
         int j = p_227797_.nextInt(4);
         Direction direction = this.getOrientation();
         if (direction != null) {
            switch (direction) {
               case NORTH:
               default:
                  if (j <= 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, this.boundingBox.minX(), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), direction, i);
                  } else if (j == 2) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), this.boundingBox.minZ(), Direction.WEST, i);
                  } else {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), this.boundingBox.minZ(), Direction.EAST, i);
                  }
                  break;
               case SOUTH:
                  if (j <= 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, this.boundingBox.minX(), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), addBlockOffset(this.boundingBox.maxZ(), 1L), direction, i);
                  } else if (j == 2) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), WorldBounds.subtractBlockOffset(this.boundingBox.maxZ(), 3L), Direction.WEST, i);
                  } else {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), WorldBounds.subtractBlockOffset(this.boundingBox.maxZ(), 3L), Direction.EAST, i);
                  }
                  break;
               case WEST:
                  if (j <= 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), this.boundingBox.minZ(), direction, i);
                  } else if (j == 2) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, this.boundingBox.minX(), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
                  } else {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, this.boundingBox.minX(), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
                  }
                  break;
               case EAST:
                  if (j <= 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), this.boundingBox.minZ(), direction, i);
                  } else if (j == 2) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.maxX(), 3L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
                  } else {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.maxX(), 3L), this.boundingBox.minY() - 1 + p_227797_.nextInt(3), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
                  }
            }
         }

         if (i < 8) {
            if (direction != Direction.NORTH && direction != Direction.SOUTH) {
               for(int section = 0; section < this.numSections - 1; ++section) {
                  long i1 = addBlockOffset(this.boundingBox.minX(), 3L + (long)section * 5L);
                  int j1 = p_227797_.nextInt(5);
                  if (j1 == 0) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, i1, this.boundingBox.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i + 1);
                  } else if (j1 == 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, i1, this.boundingBox.minY(), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i + 1);
                  }
               }
            } else {
               for(int section = 0; section < this.numSections - 1; ++section) {
                  long k = addBlockOffset(this.boundingBox.minZ(), 3L + (long)section * 5L);
                  int l = p_227797_.nextInt(5);
                  if (l == 0) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), k, Direction.WEST, i + 1);
                  } else if (l == 1) {
                     MineshaftPieces.generateAndAddPiece(p_227795_, p_227796_, p_227797_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY(), k, Direction.EAST, i + 1);
                  }
               }
            }
         }

      }

      protected boolean createChest(WorldGenLevel p_227787_, BoundingBox p_227788_, RandomSource p_227789_, int p_227790_, int p_227791_, int p_227792_, ResourceLocation p_227793_) {
         BlockPos blockpos = this.getWorldPos(p_227790_, p_227791_, p_227792_);
         if (p_227788_.isInside(blockpos) && p_227787_.getBlockState(blockpos).isAir() && !p_227787_.getBlockState(blockpos.below()).isAir()) {
            BlockState blockstate = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, p_227789_.nextBoolean() ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);
            this.placeBlock(p_227787_, blockstate, p_227790_, p_227791_, p_227792_, p_227788_);
            MinecartChest minecartchest = new MinecartChest(p_227787_.getLevel(),
                  SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.5D, (double)blockpos.getY() + 0.5D,
                        blockpos.getZ(), 0.5D));
            minecartchest.setLootTable(p_227793_, p_227789_.nextLong());
            p_227787_.addFreshEntity(minecartchest);
            return true;
         } else {
            return false;
         }
      }

      public void postProcess(WorldGenLevel p_227743_, StructureManager p_227744_, ChunkGenerator p_227745_, RandomSource p_227746_, BoundingBox p_227747_, ChunkPos p_227748_, BlockPos p_227749_) {
         if (!this.isInInvalidLocation(p_227743_, p_227747_)) {
            int i = 0;
            int j = 2;
            int k = 0;
            int l = 2;
            int i1 = this.numSections * 5 - 1;
            BlockState blockstate = this.type.getPlanksState();
            this.generateBox(p_227743_, p_227747_, 0, 0, 0, 2, 1, i1, CAVE_AIR, CAVE_AIR, false);
            this.generateMaybeBox(p_227743_, p_227747_, p_227746_, 0.8F, 0, 2, 0, 2, 2, i1, CAVE_AIR, CAVE_AIR, false, false);
            if (this.spiderCorridor) {
               this.generateMaybeBox(p_227743_, p_227747_, p_227746_, 0.6F, 0, 0, 0, 2, 1, i1, Blocks.COBWEB.defaultBlockState(), CAVE_AIR, false, true);
            }

            for(int j1 = 0; j1 < this.numSections; ++j1) {
               int k1 = 2 + j1 * 5;
               this.placeSupport(p_227743_, p_227747_, 0, 0, k1, 2, 2, p_227746_);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.1F, 0, 2, k1 - 1);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.1F, 2, 2, k1 - 1);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.1F, 0, 2, k1 + 1);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.1F, 2, 2, k1 + 1);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.05F, 0, 2, k1 - 2);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.05F, 2, 2, k1 - 2);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.05F, 0, 2, k1 + 2);
               this.maybePlaceCobWeb(p_227743_, p_227747_, p_227746_, 0.05F, 2, 2, k1 + 2);
               if (p_227746_.nextInt(100) == 0) {
                  this.createChest(p_227743_, p_227747_, p_227746_, 2, 0, k1 - 1, BuiltInLootTables.ABANDONED_MINESHAFT);
               }

               if (p_227746_.nextInt(100) == 0) {
                  this.createChest(p_227743_, p_227747_, p_227746_, 0, 0, k1 + 1, BuiltInLootTables.ABANDONED_MINESHAFT);
               }

               if (this.spiderCorridor && !this.hasPlacedSpider) {
                  int l1 = 1;
                  int i2 = k1 - 1 + p_227746_.nextInt(3);
                  BlockPos blockpos = this.getWorldPos(1, 0, i2);
                  if (p_227747_.isInside(blockpos) && this.isInterior(p_227743_, 1, 0, i2, p_227747_)) {
                     this.hasPlacedSpider = true;
                     p_227743_.setBlock(blockpos, Blocks.SPAWNER.defaultBlockState(), 2);
                     BlockEntity blockentity = p_227743_.getBlockEntity(blockpos);
                     if (blockentity instanceof SpawnerBlockEntity) {
                        ((SpawnerBlockEntity)blockentity).getSpawner().setEntityId(EntityType.CAVE_SPIDER);
                     }
                  }
               }
            }

            for(int j2 = 0; j2 <= 2; ++j2) {
               for(int l2 = 0; l2 <= i1; ++l2) {
                  this.setPlanksBlock(p_227743_, p_227747_, blockstate, j2, -1, l2);
               }
            }

            int k2 = 2;
            this.placeDoubleLowerOrUpperSupport(p_227743_, p_227747_, 0, -1, 2);
            if (this.numSections > 1) {
               int i3 = i1 - 2;
               this.placeDoubleLowerOrUpperSupport(p_227743_, p_227747_, 0, -1, i3);
            }

            if (this.hasRails) {
               BlockState blockstate1 = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

               for(int j3 = 0; j3 <= i1; ++j3) {
                  BlockState blockstate2 = this.getBlock(p_227743_, 1, -1, j3, p_227747_);
                  if (!blockstate2.isAir() && blockstate2.isSolidRender(p_227743_, this.getWorldPos(1, -1, j3))) {
                     float f = this.isInterior(p_227743_, 1, 0, j3, p_227747_) ? 0.7F : 0.9F;
                     this.maybeGenerateBlock(p_227743_, p_227747_, p_227746_, f, 1, 0, j3, blockstate1);
                  }
               }
            }

         }
      }

      private void placeDoubleLowerOrUpperSupport(WorldGenLevel p_227757_, BoundingBox p_227758_, int p_227759_, int p_227760_, int p_227761_) {
         BlockState blockstate = this.type.getWoodState();
         BlockState blockstate1 = this.type.getPlanksState();
         if (this.getBlock(p_227757_, p_227759_, p_227760_, p_227761_, p_227758_).is(blockstate1.getBlock())) {
            this.fillPillarDownOrChainUp(p_227757_, blockstate, p_227759_, p_227760_, p_227761_, p_227758_);
         }

         if (this.getBlock(p_227757_, p_227759_ + 2, p_227760_, p_227761_, p_227758_).is(blockstate1.getBlock())) {
            this.fillPillarDownOrChainUp(p_227757_, blockstate, p_227759_ + 2, p_227760_, p_227761_, p_227758_);
         }

      }

      protected void fillColumnDown(WorldGenLevel p_227813_, BlockState p_227814_, int p_227815_, int p_227816_, int p_227817_, BoundingBox p_227818_) {
         BlockPos.MutableBlockPos blockpos$mutableblockpos = this.getWorldPos(p_227815_, p_227816_, p_227817_);
         if (p_227818_.isInside(blockpos$mutableblockpos)) {
            int i = blockpos$mutableblockpos.getY();

            while(this.isReplaceableByStructures(p_227813_.getBlockState(blockpos$mutableblockpos)) && blockpos$mutableblockpos.getY() > p_227813_.getMinBuildHeight() + 1) {
               blockpos$mutableblockpos.move(Direction.DOWN);
            }

            if (this.canPlaceColumnOnTopOf(p_227813_, blockpos$mutableblockpos, p_227813_.getBlockState(blockpos$mutableblockpos))) {
               while(blockpos$mutableblockpos.getY() < i) {
                  blockpos$mutableblockpos.move(Direction.UP);
                  p_227813_.setBlock(blockpos$mutableblockpos, p_227814_, 2);
               }

            }
         }
      }

      protected void fillPillarDownOrChainUp(WorldGenLevel p_227820_, BlockState p_227821_, int p_227822_, int p_227823_, int p_227824_, BoundingBox p_227825_) {
         BlockPos.MutableBlockPos blockpos$mutableblockpos = this.getWorldPos(p_227822_, p_227823_, p_227824_);
         if (p_227825_.isInside(blockpos$mutableblockpos)) {
            int i = blockpos$mutableblockpos.getY();
            int j = 1;
            boolean flag = true;

            for(boolean flag1 = true; flag || flag1; ++j) {
               if (flag) {
                  blockpos$mutableblockpos.setY(i - j);
                  BlockState blockstate = p_227820_.getBlockState(blockpos$mutableblockpos);
                  boolean flag2 = this.isReplaceableByStructures(blockstate) && !blockstate.is(Blocks.LAVA);
                  if (!flag2 && this.canPlaceColumnOnTopOf(p_227820_, blockpos$mutableblockpos, blockstate)) {
                     fillColumnBetween(p_227820_, p_227821_, blockpos$mutableblockpos, i - j + 1, i);
                     return;
                  }

                  flag = j <= 20 && flag2 && blockpos$mutableblockpos.getY() > p_227820_.getMinBuildHeight() + 1;
               }

               if (flag1) {
                  blockpos$mutableblockpos.setY(i + j);
                  BlockState blockstate1 = p_227820_.getBlockState(blockpos$mutableblockpos);
                  boolean flag3 = this.isReplaceableByStructures(blockstate1);
                  if (!flag3 && this.canHangChainBelow(p_227820_, blockpos$mutableblockpos, blockstate1)) {
                     p_227820_.setBlock(blockpos$mutableblockpos.setY(i + 1), this.type.getFenceState(), 2);
                     fillColumnBetween(p_227820_, Blocks.CHAIN.defaultBlockState(), blockpos$mutableblockpos, i + 2, i + j);
                     return;
                  }

                  flag1 = j <= 50 && flag3 && blockpos$mutableblockpos.getY() < p_227820_.getMaxBuildHeight() - 1;
               }
            }

         }
      }

      private static void fillColumnBetween(WorldGenLevel p_227751_, BlockState p_227752_, BlockPos.MutableBlockPos p_227753_, int p_227754_, int p_227755_) {
         for(int i = p_227754_; i < p_227755_; ++i) {
            p_227751_.setBlock(p_227753_.setY(i), p_227752_, 2);
         }

      }

      private boolean canPlaceColumnOnTopOf(LevelReader p_227739_, BlockPos p_227740_, BlockState p_227741_) {
         return p_227741_.isFaceSturdy(p_227739_, p_227740_, Direction.UP);
      }

      private boolean canHangChainBelow(LevelReader p_227809_, BlockPos p_227810_, BlockState p_227811_) {
         return Block.canSupportCenter(p_227809_, p_227810_, Direction.DOWN) && !(p_227811_.getBlock() instanceof FallingBlock);
      }

      private void placeSupport(WorldGenLevel p_227770_, BoundingBox p_227771_, int p_227772_, int p_227773_, int p_227774_, int p_227775_, int p_227776_, RandomSource p_227777_) {
         if (this.isSupportingBox(p_227770_, p_227771_, p_227772_, p_227776_, p_227775_, p_227774_)) {
            BlockState blockstate = this.type.getPlanksState();
            BlockState blockstate1 = this.type.getFenceState();
            this.generateBox(p_227770_, p_227771_, p_227772_, p_227773_, p_227774_, p_227772_, p_227775_ - 1, p_227774_, blockstate1.setValue(FenceBlock.WEST, Boolean.valueOf(true)), CAVE_AIR, false);
            this.generateBox(p_227770_, p_227771_, p_227776_, p_227773_, p_227774_, p_227776_, p_227775_ - 1, p_227774_, blockstate1.setValue(FenceBlock.EAST, Boolean.valueOf(true)), CAVE_AIR, false);
            if (p_227777_.nextInt(4) == 0) {
               this.generateBox(p_227770_, p_227771_, p_227772_, p_227775_, p_227774_, p_227772_, p_227775_, p_227774_, blockstate, CAVE_AIR, false);
               this.generateBox(p_227770_, p_227771_, p_227776_, p_227775_, p_227774_, p_227776_, p_227775_, p_227774_, blockstate, CAVE_AIR, false);
            } else {
               this.generateBox(p_227770_, p_227771_, p_227772_, p_227775_, p_227774_, p_227776_, p_227775_, p_227774_, blockstate, CAVE_AIR, false);
               this.maybeGenerateBlock(p_227770_, p_227771_, p_227777_, 0.05F, p_227772_ + 1, p_227775_, p_227774_ - 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
               this.maybeGenerateBlock(p_227770_, p_227771_, p_227777_, 0.05F, p_227772_ + 1, p_227775_, p_227774_ + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            }

         }
      }

      private void maybePlaceCobWeb(WorldGenLevel p_227779_, BoundingBox p_227780_, RandomSource p_227781_, float p_227782_, int p_227783_, int p_227784_, int p_227785_) {
         if (this.isInterior(p_227779_, p_227783_, p_227784_, p_227785_, p_227780_) && p_227781_.nextFloat() < p_227782_ && this.hasSturdyNeighbours(p_227779_, p_227780_, p_227783_, p_227784_, p_227785_, 2)) {
            this.placeBlock(p_227779_, Blocks.COBWEB.defaultBlockState(), p_227783_, p_227784_, p_227785_, p_227780_);
         }

      }

      private boolean hasSturdyNeighbours(WorldGenLevel p_227763_, BoundingBox p_227764_, int p_227765_, int p_227766_, int p_227767_, int p_227768_) {
         BlockPos.MutableBlockPos blockpos$mutableblockpos = this.getWorldPos(p_227765_, p_227766_, p_227767_);
         int i = 0;

         for(Direction direction : Direction.values()) {
            blockpos$mutableblockpos.move(direction);
            if (p_227764_.isInside(blockpos$mutableblockpos) && p_227763_.getBlockState(blockpos$mutableblockpos).isFaceSturdy(p_227763_, blockpos$mutableblockpos, direction.getOpposite())) {
               ++i;
               if (i >= p_227768_) {
                  return true;
               }
            }

            blockpos$mutableblockpos.move(direction.getOpposite());
         }

         return false;
      }
   }

   public static class MineShaftCrossing extends MineshaftPieces.MineShaftPiece {
      private final Direction direction;
      private final boolean isTwoFloored;

      public MineShaftCrossing(CompoundTag p_227834_) {
         super(StructurePieceType.MINE_SHAFT_CROSSING, p_227834_);
         // Crossings are axis-aligned, but their generation coordinates are
         // piece-relative like every other StructurePiece.
         this.setOrientation(Direction.SOUTH);
         this.isTwoFloored = p_227834_.getBoolean("tf");
         this.direction = Direction.from2DDataValue(p_227834_.getInt("D"));
      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext p_227862_, CompoundTag p_227863_) {
         super.addAdditionalSaveData(p_227862_, p_227863_);
         p_227863_.putBoolean("tf", this.isTwoFloored);
         p_227863_.putInt("D", this.direction.get2DDataValue());
      }

      public MineShaftCrossing(int p_227829_, BoundingBox p_227830_, @Nullable Direction p_227831_, MineshaftStructure.Type p_227832_) {
         super(StructurePieceType.MINE_SHAFT_CROSSING, p_227829_, p_227832_, p_227830_);
         // The bounding box is already world-aligned.  SOUTH maps local x/z
         // directly from its minimum corner without mirroring either axis.
         this.setOrientation(Direction.SOUTH);
         this.direction = p_227831_;
         this.isTwoFloored = p_227830_.getYSpan() > 3;
      }

      @Nullable
      public static BoundingBox findCrossing(StructurePieceAccessor p_227855_, RandomSource p_227856_, long p_227857_, int p_227858_, long p_227859_, Direction p_227860_) {
         int i;
         if (p_227856_.nextInt(4) == 0) {
            i = 6;
         } else {
            i = 2;
         }

         BoundingBox boundingbox;
         switch (p_227860_) {
            case NORTH:
            default:
               boundingbox = new BoundingBox(-1, 0, -4, 3, i, 0);
               break;
            case SOUTH:
               boundingbox = new BoundingBox(-1, 0, 0, 3, i, 4);
               break;
            case WEST:
               boundingbox = new BoundingBox(-4, 0, -1, 0, i, 3);
               break;
            case EAST:
               boundingbox = new BoundingBox(0, 0, -1, 4, i, 3);
         }

         boundingbox = moveBoundingBox(boundingbox, p_227857_, p_227858_, p_227859_);
         return p_227855_.findCollisionPiece(boundingbox) != null ? null : boundingbox;
      }

      public void addChildren(StructurePiece p_227851_, StructurePieceAccessor p_227852_, RandomSource p_227853_) {
         int i = this.getGenDepth();
         switch (this.direction) {
            case NORTH:
            default:
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.WEST, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.EAST, i);
               break;
            case SOUTH:
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.WEST, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.EAST, i);
               break;
            case WEST:
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.WEST, i);
               break;
            case EAST:
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY(), addBlockOffset(this.boundingBox.minZ(), 1L), Direction.EAST, i);
         }

         if (this.isTwoFloored) {
            if (p_227853_.nextBoolean()) {
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() + 3 + 1, WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
            }

            if (p_227853_.nextBoolean()) {
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() + 3 + 1, addBlockOffset(this.boundingBox.minZ(), 1L), Direction.WEST, i);
            }

            if (p_227853_.nextBoolean()) {
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY() + 3 + 1, addBlockOffset(this.boundingBox.minZ(), 1L), Direction.EAST, i);
            }

            if (p_227853_.nextBoolean()) {
               MineshaftPieces.generateAndAddPiece(p_227851_, p_227852_, p_227853_, addBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() + 3 + 1, addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
            }
         }

      }

      public void postProcess(WorldGenLevel p_227836_, StructureManager p_227837_, ChunkGenerator p_227838_, RandomSource p_227839_, BoundingBox p_227840_, ChunkPos p_227841_, BlockPos p_227842_) {
         if (!this.isInInvalidLocation(p_227836_, p_227840_)) {
            BlockState blockstate = this.type.getPlanksState();
            int maxY = this.boundingBox.getYSpan() - 1;
            if (this.isTwoFloored) {
               this.generateBox(p_227836_, p_227840_, 1, 0, 0, 3, 2, 4, CAVE_AIR, CAVE_AIR, false);
               this.generateBox(p_227836_, p_227840_, 0, 0, 1, 4, 2, 3, CAVE_AIR, CAVE_AIR, false);
               this.generateBox(p_227836_, p_227840_, 1, maxY - 2, 0, 3, maxY, 4, CAVE_AIR, CAVE_AIR, false);
               this.generateBox(p_227836_, p_227840_, 0, maxY - 2, 1, 4, maxY, 3, CAVE_AIR, CAVE_AIR, false);
               this.generateBox(p_227836_, p_227840_, 1, 3, 1, 3, 3, 3, CAVE_AIR, CAVE_AIR, false);
            } else {
               this.generateBox(p_227836_, p_227840_, 1, 0, 0, 3, maxY, 4, CAVE_AIR, CAVE_AIR, false);
               this.generateBox(p_227836_, p_227840_, 0, 0, 1, 4, maxY, 3, CAVE_AIR, CAVE_AIR, false);
            }

            this.placeSupportPillar(p_227836_, p_227840_, 1, 0, 1, maxY);
            this.placeSupportPillar(p_227836_, p_227840_, 1, 0, 3, maxY);
            this.placeSupportPillar(p_227836_, p_227840_, 3, 0, 1, maxY);
            this.placeSupportPillar(p_227836_, p_227840_, 3, 0, 3, maxY);

            for(int j = 0; j <= 4; ++j) {
               for(int k = 0; k <= 4; ++k) {
                  this.setPlanksBlock(p_227836_, p_227840_, blockstate, j, -1, k);
               }
            }

         }
      }

      private void placeSupportPillar(WorldGenLevel p_227844_, BoundingBox p_227845_, long p_227846_, int p_227847_, long p_227848_, int p_227849_) {
         if (!this.getBlock(p_227844_, p_227846_, p_227849_ + 1, p_227848_, p_227845_).isAir()) {
            this.generateBox(p_227844_, p_227845_, p_227846_, p_227847_, p_227848_, p_227846_, p_227849_, p_227848_, this.type.getPlanksState(), CAVE_AIR, false);
         }

      }
   }

   abstract static class MineShaftPiece extends StructurePiece {
      protected MineshaftStructure.Type type;

      public MineShaftPiece(StructurePieceType p_227867_, int p_227868_, MineshaftStructure.Type p_227869_, BoundingBox p_227870_) {
         super(p_227867_, p_227868_, p_227870_);
         this.type = p_227869_;
      }

      public MineShaftPiece(StructurePieceType p_227872_, CompoundTag p_227873_) {
         super(p_227872_, p_227873_);
         this.type = MineshaftStructure.Type.byId(p_227873_.getInt("MST"));
      }

      @Override
      protected boolean canBeReplaced(LevelReader p_227885_, long p_227886_, int p_227887_, long p_227888_, BoundingBox p_227889_) {
         BlockState blockstate = this.getBlock(p_227885_, p_227886_, p_227887_, p_227888_, p_227889_);
         return !blockstate.is(this.type.getPlanksState().getBlock()) && !blockstate.is(this.type.getWoodState().getBlock()) && !blockstate.is(this.type.getFenceState().getBlock()) && !blockstate.is(Blocks.CHAIN);
      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext p_227898_, CompoundTag p_227899_) {
         p_227899_.putInt("MST", this.type.ordinal());
      }

      protected boolean isSupportingBox(BlockGetter p_227875_, BoundingBox p_227876_, int p_227877_, int p_227878_, int p_227879_, int p_227880_) {
         for(int i = p_227877_; i <= p_227878_; ++i) {
            if (this.getBlock(p_227875_, i, p_227879_ + 1, p_227880_, p_227876_).isAir()) {
               return false;
            }
         }

         return true;
      }

      protected boolean isInInvalidLocation(LevelAccessor p_227882_, BoundingBox p_227883_) {
         long i = Math.max(WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), p_227883_.minX());
         int j = Math.max(this.boundingBox.minY() - 1, p_227883_.minY());
         long k = Math.max(WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), p_227883_.minZ());
         long l = Math.min(addBlockOffset(this.boundingBox.maxX(), 1L), p_227883_.maxX());
         int i1 = Math.min(this.boundingBox.maxY() + 1, p_227883_.maxY());
         long j1 = Math.min(addBlockOffset(this.boundingBox.maxZ(), 1L), p_227883_.maxZ());
         if (!WorldBounds.isAscendingBlockRange(i, l) || !WorldBounds.isAscendingBlockRange(k, j1)) {
            return false;
         }

         BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(midpoint(i, l), (j + i1) / 2, midpoint(k, j1));
         if (p_227882_.getBiome(blockpos$mutableblockpos).is(BiomeTags.MINESHAFT_BLOCKING)) {
            return true;
         } else {
            for(long k1 = i; ; k1 = addBlockOffset(k1, 1L)) {
               for(long l1 = k; ; l1 = addBlockOffset(l1, 1L)) {
                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(k1, j, l1)).getMaterial().isLiquid()) {
                     return true;
                  }

                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(k1, i1, l1)).getMaterial().isLiquid()) {
                     return true;
                  }

                  if (!WorldBounds.canAdvanceBlock(l1, j1)) {
                     break;
                  }
               }

               if (!WorldBounds.canAdvanceBlock(k1, l)) {
                  break;
               }
            }

            for(long i2 = i; ; i2 = addBlockOffset(i2, 1L)) {
               for(int k2 = j; k2 <= i1; ++k2) {
                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(i2, k2, k)).getMaterial().isLiquid()) {
                     return true;
                  }

                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(i2, k2, j1)).getMaterial().isLiquid()) {
                     return true;
                  }
               }

               if (!WorldBounds.canAdvanceBlock(i2, l)) {
                  break;
               }
            }

            for(long j2 = k; ; j2 = addBlockOffset(j2, 1L)) {
               for(int l2 = j; l2 <= i1; ++l2) {
                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(i, l2, j2)).getMaterial().isLiquid()) {
                     return true;
                  }

                  if (p_227882_.getBlockState(blockpos$mutableblockpos.set(l, l2, j2)).getMaterial().isLiquid()) {
                     return true;
                  }
               }

               if (!WorldBounds.canAdvanceBlock(j2, j1)) {
                  break;
               }
            }

            return false;
         }
      }

      protected void setPlanksBlock(WorldGenLevel p_227891_, BoundingBox p_227892_, BlockState p_227893_, long p_227894_, int p_227895_, long p_227896_) {
         if (this.isInterior(p_227891_, p_227894_, p_227895_, p_227896_, p_227892_)) {
            BlockPos blockpos = this.getWorldPos(p_227894_, p_227895_, p_227896_);
            BlockState blockstate = p_227891_.getBlockState(blockpos);
            if (!blockstate.isFaceSturdy(p_227891_, blockpos, Direction.UP)) {
               p_227891_.setBlock(blockpos, p_227893_, 2);
            }

         }
      }
   }

   public static class MineShaftRoom extends MineshaftPieces.MineShaftPiece {
      private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

      public MineShaftRoom(int p_227902_, RandomSource p_227903_, long p_227904_, long p_227905_, MineshaftStructure.Type p_227906_) {
         super(StructurePieceType.MINE_SHAFT_ROOM, p_227902_, p_227906_, new BoundingBox(p_227904_, 50, p_227905_, addBlockOffset(p_227904_, 7L + p_227903_.nextInt(6)), 54 + p_227903_.nextInt(6), addBlockOffset(p_227905_, 7L + p_227903_.nextInt(6))));
         this.setOrientation(Direction.SOUTH);
         this.type = p_227906_;
      }

      public MineShaftRoom(CompoundTag p_227908_) {
         super(StructurePieceType.MINE_SHAFT_ROOM, p_227908_);
         // Rooms use local offsets from the world-aligned bounding-box minimum.
         this.setOrientation(Direction.SOUTH);
         BoundingBox.CODEC.listOf().parse(NbtOps.INSTANCE, p_227908_.getList("Entrances", 11)).resultOrPartial(MineshaftPieces.LOGGER::error).ifPresent(this.childEntranceBoxes::addAll);
      }

      public void addChildren(StructurePiece p_227922_, StructurePieceAccessor p_227923_, RandomSource p_227924_) {
         int i = this.getGenDepth();
         int j = this.boundingBox.getYSpan() - 3 - 1;
         if (j <= 0) {
            j = 1;
         }

         long xSpan = this.boundingBox.getXSpan();
         for(long k = 0L, attempts = 0L; attempts < MAX_ROOM_ENTRANCE_ATTEMPTS && k < xSpan - 2L; k = WorldBounds.addBlockOffset(k, 4L), ++attempts) {
            k = WorldBounds.addBlockOffset(k, randomOffset(p_227924_, xSpan));
            if (k > xSpan - 3L) {
               break;
            }

            MineshaftPieces.MineShaftPiece mineshaftpieces$mineshaftpiece = MineshaftPieces.generateAndAddPiece(p_227922_, p_227923_, p_227924_, addBlockOffset(this.boundingBox.minX(), k), this.boundingBox.minY() + p_227924_.nextInt(j) + 1, WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
            if (mineshaftpieces$mineshaftpiece != null) {
               BoundingBox boundingbox = mineshaftpieces$mineshaftpiece.getBoundingBox();
               this.childEntranceBoxes.add(new BoundingBox(boundingbox.minX(), boundingbox.minY(), this.boundingBox.minZ(), boundingbox.maxX(), boundingbox.maxY(), addBlockOffset(this.boundingBox.minZ(), 1L)));
            }
         }

         for(long k = 0L, attempts = 0L; attempts < MAX_ROOM_ENTRANCE_ATTEMPTS && k < xSpan - 2L; k = WorldBounds.addBlockOffset(k, 4L), ++attempts) {
            k = WorldBounds.addBlockOffset(k, randomOffset(p_227924_, xSpan));
            if (k > xSpan - 3L) {
               break;
            }

            MineshaftPieces.MineShaftPiece mineshaftpieces$mineshaftpiece1 = MineshaftPieces.generateAndAddPiece(p_227922_, p_227923_, p_227924_, addBlockOffset(this.boundingBox.minX(), k), this.boundingBox.minY() + p_227924_.nextInt(j) + 1, addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
            if (mineshaftpieces$mineshaftpiece1 != null) {
               BoundingBox boundingbox1 = mineshaftpieces$mineshaftpiece1.getBoundingBox();
               this.childEntranceBoxes.add(new BoundingBox(boundingbox1.minX(), boundingbox1.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.maxZ(), 1L), boundingbox1.maxX(), boundingbox1.maxY(), this.boundingBox.maxZ()));
            }
         }

         long zSpan = this.boundingBox.getZSpan();
         for(long k = 0L, attempts = 0L; attempts < MAX_ROOM_ENTRANCE_ATTEMPTS && k < zSpan - 2L; k = WorldBounds.addBlockOffset(k, 4L), ++attempts) {
            k = WorldBounds.addBlockOffset(k, randomOffset(p_227924_, zSpan));
            if (k > zSpan - 3L) {
               break;
            }

            MineshaftPieces.MineShaftPiece mineshaftpieces$mineshaftpiece2 = MineshaftPieces.generateAndAddPiece(p_227922_, p_227923_, p_227924_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY() + p_227924_.nextInt(j) + 1, addBlockOffset(this.boundingBox.minZ(), k), Direction.WEST, i);
            if (mineshaftpieces$mineshaftpiece2 != null) {
               BoundingBox boundingbox2 = mineshaftpieces$mineshaftpiece2.getBoundingBox();
               this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.minX(), boundingbox2.minY(), boundingbox2.minZ(), addBlockOffset(this.boundingBox.minX(), 1L), boundingbox2.maxY(), boundingbox2.maxZ()));
            }
         }

         for(long k = 0L, attempts = 0L; attempts < MAX_ROOM_ENTRANCE_ATTEMPTS && k < zSpan - 2L; k = WorldBounds.addBlockOffset(k, 4L), ++attempts) {
            k = WorldBounds.addBlockOffset(k, randomOffset(p_227924_, zSpan));
            if (k > zSpan - 3L) {
               break;
            }

            StructurePiece structurepiece = MineshaftPieces.generateAndAddPiece(p_227922_, p_227923_, p_227924_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY() + p_227924_.nextInt(j) + 1, addBlockOffset(this.boundingBox.minZ(), k), Direction.EAST, i);
            if (structurepiece != null) {
               BoundingBox boundingbox3 = structurepiece.getBoundingBox();
               this.childEntranceBoxes.add(new BoundingBox(WorldBounds.subtractBlockOffset(this.boundingBox.maxX(), 1L), boundingbox3.minY(), boundingbox3.minZ(), this.boundingBox.maxX(), boundingbox3.maxY(), boundingbox3.maxZ()));
            }
         }

      }

      public void postProcess(WorldGenLevel p_227914_, StructureManager p_227915_, ChunkGenerator p_227916_, RandomSource p_227917_, BoundingBox p_227918_, ChunkPos p_227919_, BlockPos p_227920_) {
         if (!this.isInInvalidLocation(p_227914_, p_227918_)) {
            long maxX = this.boundingBox.getXSpan() - 1L;
            int maxY = this.boundingBox.getYSpan() - 1;
            long maxZ = this.boundingBox.getZSpan() - 1L;
            this.generateBox(p_227914_, p_227918_, 0, 1, 0, maxX, Math.min(3, maxY), maxZ, CAVE_AIR, CAVE_AIR, false);

            for(BoundingBox boundingbox : this.childEntranceBoxes) {
               this.generateBox(p_227914_, p_227918_, WorldBounds.signedDifferenceAsLong(boundingbox.minX(), this.boundingBox.minX()), boundingbox.maxY() - this.boundingBox.minY() - 2, WorldBounds.signedDifferenceAsLong(boundingbox.minZ(), this.boundingBox.minZ()), WorldBounds.signedDifferenceAsLong(boundingbox.maxX(), this.boundingBox.minX()), boundingbox.maxY() - this.boundingBox.minY(), WorldBounds.signedDifferenceAsLong(boundingbox.maxZ(), this.boundingBox.minZ()), CAVE_AIR, CAVE_AIR, false);
            }

            this.generateUpperHalfSphere(p_227914_, p_227918_, 0, 4, 0, maxX, maxY, maxZ, CAVE_AIR, false);
         }
      }

      public void move(int p_227910_, int p_227911_, int p_227912_) {
         super.move(p_227910_, p_227911_, p_227912_);

         for(BoundingBox boundingbox : this.childEntranceBoxes) {
            boundingbox.move(p_227910_, p_227911_, p_227912_);
         }

      }

      protected void addAdditionalSaveData(StructurePieceSerializationContext p_227926_, CompoundTag p_227927_) {
         super.addAdditionalSaveData(p_227926_, p_227927_);
         BoundingBox.CODEC.listOf().encodeStart(NbtOps.INSTANCE, this.childEntranceBoxes).resultOrPartial(MineshaftPieces.LOGGER::error).ifPresent((p_227930_) -> {
            p_227927_.put("Entrances", p_227930_);
         });
      }
   }

   public static class MineShaftStairs extends MineshaftPieces.MineShaftPiece {
      public MineShaftStairs(int p_227932_, BoundingBox p_227933_, Direction p_227934_, MineshaftStructure.Type p_227935_) {
         super(StructurePieceType.MINE_SHAFT_STAIRS, p_227932_, p_227935_, p_227933_);
         this.setOrientation(p_227934_);
      }

      public MineShaftStairs(CompoundTag p_227937_) {
         super(StructurePieceType.MINE_SHAFT_STAIRS, p_227937_);
      }

      @Nullable
      public static BoundingBox findStairs(StructurePieceAccessor p_227951_, RandomSource p_227952_, long p_227953_, int p_227954_, long p_227955_, Direction p_227956_) {
         BoundingBox boundingbox;
         switch (p_227956_) {
            case NORTH:
            default:
               boundingbox = new BoundingBox(0, -5, -8, 2, 2, 0);
               break;
            case SOUTH:
               boundingbox = new BoundingBox(0, -5, 0, 2, 2, 8);
               break;
            case WEST:
               boundingbox = new BoundingBox(-8, -5, 0, 0, 2, 2);
               break;
            case EAST:
               boundingbox = new BoundingBox(0, -5, 0, 8, 2, 2);
         }

         boundingbox = moveBoundingBox(boundingbox, p_227953_, p_227954_, p_227955_);
         return p_227951_.findCollisionPiece(boundingbox) != null ? null : boundingbox;
      }

      public void addChildren(StructurePiece p_227947_, StructurePieceAccessor p_227948_, RandomSource p_227949_) {
         int i = this.getGenDepth();
         Direction direction = this.getOrientation();
         if (direction != null) {
            switch (direction) {
               case NORTH:
               default:
                  MineshaftPieces.generateAndAddPiece(p_227947_, p_227948_, p_227949_, this.boundingBox.minX(), this.boundingBox.minY(), WorldBounds.subtractBlockOffset(this.boundingBox.minZ(), 1L), Direction.NORTH, i);
                  break;
               case SOUTH:
                  MineshaftPieces.generateAndAddPiece(p_227947_, p_227948_, p_227949_, this.boundingBox.minX(), this.boundingBox.minY(), addBlockOffset(this.boundingBox.maxZ(), 1L), Direction.SOUTH, i);
                  break;
               case WEST:
                  MineshaftPieces.generateAndAddPiece(p_227947_, p_227948_, p_227949_, WorldBounds.subtractBlockOffset(this.boundingBox.minX(), 1L), this.boundingBox.minY(), this.boundingBox.minZ(), Direction.WEST, i);
                  break;
               case EAST:
                  MineshaftPieces.generateAndAddPiece(p_227947_, p_227948_, p_227949_, addBlockOffset(this.boundingBox.maxX(), 1L), this.boundingBox.minY(), this.boundingBox.minZ(), Direction.EAST, i);
            }
         }

      }

      public void postProcess(WorldGenLevel p_227939_, StructureManager p_227940_, ChunkGenerator p_227941_, RandomSource p_227942_, BoundingBox p_227943_, ChunkPos p_227944_, BlockPos p_227945_) {
         if (!this.isInInvalidLocation(p_227939_, p_227943_)) {
            this.generateBox(p_227939_, p_227943_, 0, 5, 0, 2, 7, 1, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(p_227939_, p_227943_, 0, 0, 7, 2, 2, 8, CAVE_AIR, CAVE_AIR, false);

            for(int i = 0; i < 5; ++i) {
               this.generateBox(p_227939_, p_227943_, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, CAVE_AIR, CAVE_AIR, false);
            }

         }
      }
   }
}