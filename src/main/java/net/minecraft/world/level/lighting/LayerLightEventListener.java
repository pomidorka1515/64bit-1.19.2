package net.minecraft.world.level.lighting;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

public class LayerLightEventListener {

	final DataLayer dummyDataLayer = new DataLayer();
	
	{
		Arrays.fill(dummyDataLayer.getData(), (byte) 0xFF);
	}
	
	public int getLightValue(BlockPos p_45519_) {
		// TODO Auto-generated method stub
		return 14;
	}

	public DataLayer getDataLayerData(SectionPos of) {
		// TODO Auto-generated method stub
		return dummyDataLayer;
	}
	
}
