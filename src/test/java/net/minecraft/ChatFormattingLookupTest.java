package net.minecraft;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatFormattingLookupTest {
    @Test
    void lookupPreservesEveryCharacterIncludingUnicodeLowercasing() {
        for (int i = 0; i <= Character.MAX_VALUE; ++i) {
            char lowered = Character.toString((char)i).toLowerCase(Locale.ROOT).charAt(0);
            ChatFormatting expected = null;
            for (ChatFormatting formatting : ChatFormatting.values()) if (formatting.getChar() == lowered) expected = formatting;
            assertSame(expected, ChatFormatting.getByCode((char)i));
        }
    }
}
