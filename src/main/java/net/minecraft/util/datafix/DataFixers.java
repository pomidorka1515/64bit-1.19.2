package net.minecraft.util.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class DataFixers {
    private static final DataFixer dataFixer = createFixerUpper();
    public static final int BLENDING_VERSION = 3088;

    private DataFixers() {
    }

    public static DataFixer getDataFixer() {
        return dataFixer;
    }

    private static DataFixer createFixerUpper() {
        // This fork uses its own save format and never migrates vanilla data.
        return new DataFixer() {
            @Override
            public <T> Dynamic<T> update(DSL.TypeReference pType, Dynamic<T> pInput, int pVersion, int pNewVersion) {
                return pInput;
            }

            @Override
            public Schema getSchema(int pVersion) {
                throw new UnsupportedOperationException("Data fixer schemas are disabled");
            }
        };
    }
}
