package net.minecraft.util;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptTest {
    @Test
    void signedHexMatchesIndependentIntegerOracle() throws Exception {
        Method format = Crypt.class.getDeclaredMethod("signedHex", byte[].class);
        format.setAccessible(true);
        Random random = new Random(1088);
        for (int size : new int[] {1, 2, 20}) {
            for (int i = 0; i < 10000; ++i) {
                byte[] bytes = new byte[size];
                random.nextBytes(bytes);
                checkHex(format, bytes);
            }
            byte[] bytes = new byte[size];
            checkHex(format, bytes);
            bytes[size - 1] = 1;
            checkHex(format, bytes);
            Arrays.fill(bytes, (byte) -1);
            checkHex(format, bytes);
            Arrays.fill(bytes, (byte) 0);
            bytes[0] = (byte) 128;
            checkHex(format, bytes);
            Arrays.fill(bytes, (byte) -1);
            bytes[0] = 127;
            checkHex(format, bytes);
        }
    }

    private static void checkHex(Method format, byte[] bytes) throws Exception {
        byte[] original = bytes.clone();
        assertEquals(new BigInteger(bytes).toString(16), format.invoke(null, (Object) bytes));
        assertArrayEquals(original, bytes);
    }

    @Test
    void authenticationDigestRetainsInputOrderEncodingAndSignedHex() throws Exception {
        var pair = Crypt.generateKeyPair();
        var secret = Crypt.generateSecretKey();
        for (String id : new String[] {"", "server", "\u00e9\u00ff", "Notch", "jeb_", "simon"}) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(id.getBytes(StandardCharsets.ISO_8859_1));
            digest.update(secret.getEncoded());
            digest.update(pair.getPublic().getEncoded());
            assertEquals(new BigInteger(digest.digest()).toString(16),
                    Crypt.digestData(id, pair.getPublic(), secret));
        }
    }
}
