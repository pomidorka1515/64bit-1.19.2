package net.minecraft.world.level.lighting;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

// Dummy light engine
public class LevelLightEngine {

	final LayerLightEventListener dummy = new LayerLightEventListener();
	
	protected final LevelHeightAccessor levelHeightAccessor;

	public LevelLightEngine(LightChunkGetter p_9305_, boolean b, boolean p_9307_) {
		this.levelHeightAccessor = p_9305_.getLevel();
	}

	public void enableLightSources(ChunkPos pos, boolean b) {

	}

	public void checkBlock(BlockPos pos) {

	}

	public void retainData(ChunkPos p_9331_, boolean b) {
		// TODO Auto-generated method stub

	}

   public int getLightSectionCount() {
      return this.levelHeightAccessor.getSectionsCount() + 2;
   }

   public int getMinLightSection() {
      return this.levelHeightAccessor.getMinSection() - 1;
   }

   public int getMaxLightSection() {
      return this.getMinLightSection() + this.getLightSectionCount();
   }

	public void updateSectionStatus(SectionPos p_9364_, boolean p_9365_) {
		// TODO Auto-generated method stub
		
	}

	public void queueSectionData(LightLayer block, SectionPos of, DataLayer dataLayer, boolean b) {
		// TODO Auto-generated method stub
		
	}
	
	public boolean hasLightWork() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void updateChunkStatus(ChunkPos pos) {
		// TODO Auto-generated method stub
		
	}
	
	public void tryScheduleUpdate() {
		// TODO Auto-generated method stub
		
	}

	public String getDebugData(LightLayer sky, SectionPos k) {
		// TODO Auto-generated method stub
		return "(Light engine removed)";
	}

	public LayerLightEventListener getLayerListener(LightLayer p_45518_) {
		return dummy;
	}

	public int getRawBrightness(BlockPos p_45525_, int p_45526_) {
		// TODO Auto-generated method stub
		return 15;
	}
	
}
