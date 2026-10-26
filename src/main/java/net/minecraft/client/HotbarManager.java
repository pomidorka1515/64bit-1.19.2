package net.minecraft.client;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class HotbarManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int NUM_HOTBAR_GROUPS = 9;
    private final File optionsFile;
    private final Hotbar[] hotbars = new Hotbar[9];
    private boolean loaded;

    public HotbarManager(File pGameDirectory) {
        this.optionsFile = new File(pGameDirectory, "hotbar.nbt");

        for (int i = 0; i < 9; ++i) {
            this.hotbars[i] = new Hotbar();
        }
    }

    private void load() {
        try {
            CompoundTag compoundtag = NbtIo.read(this.optionsFile);
            if (compoundtag == null) {
                return;
            }


            for (int i = 0; i < 9; ++i) {
                this.hotbars[i].fromTag(compoundtag.getList(String.valueOf(i), 10));
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load creative mode options", (Throwable) exception);
        }
    }

    public void save() {
        try {
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());

            for (int i = 0; i < 9; ++i) {
                compoundtag.put(String.valueOf(i), this.get(i).createTag());
            }

            NbtIo.write(compoundtag, this.optionsFile);
        } catch (Exception exception) {
            LOGGER.error("Failed to save creative mode options", (Throwable) exception);
        }
    }

    public Hotbar get(int pIndex) {
        if (!this.loaded) {
            this.load();
            this.loaded = true;
        }

        return this.hotbars[pIndex];
    }
}
