/*
 * Copyright (C) 2015 Steven Luo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm.util;

import jackpal.androidterm.compat.Base64;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implementation of a simple authenticated encryption scheme suitable for
 * TEA shortcuts.
 *
 * The goals of the encryption are as follows:
 *
 *   (1) An unauthorized actor must not be able to create a valid text with
 *       contents of his choice;
 *   (2) An unauthorized actor must not be able to modify an existing text to
 *       change its contents in any way;
 *   (3) An unauthorized actor must not be able to discover the contents of
 *       an existing text.
 *
 * Conditions (1) and (2) ensure that an attacker cannot send commands of his
 * choosing to TEA via the shortcut mechanism, while condition (3) ensures that
 * an attacker cannot learn what commands are being sent via shortcuts even if
 * he can read saved shortcuts or sniff Android intents.
 *
 * We ensure these conditions using two cryptographic building blocks:
 *
 *   * a symmetric cipher (currently AES in CBC mode using PKCS#5 padding),
 *     which prevents someone without the encryption key from reading the
 *     contents of the shortcut; and
 *   * a message authentication code (currently HMAC-SHA256), which proves that
 *     the shortcut was created by someone with the MAC key.
 *
 * The security of these depends on the security of the keys, which must be
 * kept secret.  In this application, the keys are randomly generated and stored
 * in the application's private shared preferences.
 *
 * The encrypted string output by this scheme is of the form:
 *
 *     mac + ":" + iv + ":" cipherText
 *
 * where:
 *
 *   * cipherText is the Base64-encoded result of encrypting the data
 *     using the encryption key;
 *   * iv is a Base64-encoded, non-secret random number used as an
 *     initialization vector for the encryption algorithm;
 *   * mac is the Base64 encoding of MAC(MAC-key, iv + ":" + cipherText).
 */
public final class ShortcutEncryption {
    public static final String ENC_ALGORITHM = "AES";
    public static final String ENC_SYSTEM = ENC_ALGORITHM + "/CBC/PKCS5Padding";
    public static final int ENC_BLOCKSIZE = 16;
    public static final String MAC_ALGORITHM = "HmacSHA256";
    public static final int KEYLEN = 128;
    public static final int BASE64_DFLAGS = Base64.DEFAULT;
    public static final int BASE64_EFLAGS = Base64.NO_PADDING | Base64.NO_WRAP;

    private static final String SHORTCUT_KEYS_PREF = "shortcut_keys";

    private static final Pattern COLON = Pattern.compile(":");

    public static final class Keys {
        private final SecretKey encKey;
        private final SecretKey macKey;

        public Keys(SecretKey encKey, SecretKey macKey) {
            this.encKey = encKey;
            this.macKey = macKey;
        }

        public SecretKey getEncKey() {
            return encKey;
        }

        public SecretKey getMacKey() {
            return macKey;
        }

        /**
         * Outputs the keys as a string of the form
         *
         *     encKey + ":" + macKey
         *
         * where encKey and macKey are the Base64-encoded encryption and MAC
         * keys.
         */
        public String encode() {
            return encodeToBase64(encKey.getEncoded()) + ":" + encodeToBase64(macKey.getEncoded());
        }

        /**
         * Creates a new Keys object by decoding a string of the form output
         * from encode().
         */
        public static Keys decode(String encodedKeys) {
            String[] keys = COLON.split(encodedKeys);
            if (keys.length != 2) {
                throw new IllegalArgumentException("Invalid encoded keys!");
            }

            SecretKey encKey = new SecretKeySpec(decodeBase64(keys[0]), ENC_ALGORITHM);
            SecretKey macKey = new SecretKeySpec(decodeBase64(keys[1]), MAC_ALGORITHM);
            return new Keys(encKey, macKey);
        }
    }

    /**
     * Retrieves the shortcut encryption keys from preferences.
     */
    public static Keys getKeys(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String keyEnc = prefs.getString(SHORTCUT_KEYS_PREF, null);
        if (keyEnc == null) {
            return null;
        }

        try {
            return Keys.decode(keyEnc);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Saves shortcut encryption keys to preferences.
     */
    public static void saveKeys(Context ctx, Keys keys) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(SHORTCUT_KEYS_PREF, keys.encode());
        edit.commit();
    }

    /**
     * Generates new secret keys suitable for the encryption scheme described
     * above.
     *
     * @throws GeneralSecurityException if an error occurs during key generation.
     */
    public static Keys generateKeys() throws GeneralSecurityException {
        KeyGenerator gen = KeyGenerator.getInstance(ENC_ALGORITHM);
        gen.init(KEYLEN);
        SecretKey encKey = gen.generateKey();

        /* XXX: It's probably unnecessary to create a different keygen for the
         * MAC, but JCA's API design suggests we should just in case ... */
        gen = KeyGenerator.getInstance(MAC_ALGORITHM);
        gen.init(KEYLEN);
        SecretKey macKey = gen.generateKey();

        return new Keys(encKey, macKey);
    }

    /**
     * Decrypts a string encrypted using this algorithm and verifies that the
     * contents have not been tampered with.
     *
     * @param encrypted The string to decrypt, in the format described above.
     * @param keys The keys to verify and decrypt with.
     * @return The decrypted data.
     *
     * @throws GeneralSecurityException if the data is invalid, verification fails, or an error occurs during decryption.
     */
    public static String decrypt(String encrypted, Keys keys) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ENC_SYSTEM);
        String[] data = COLON.split(encrypted);
        if (data.length != 3) {
            throw new GeneralSecurityException("Invalid encrypted data!");
        }
        String mac = data[0];
        String iv = data[1];
        String cipherText = data[2];

        // Verify that the ciphertext and IV haven't been tampered with first
        String dataToAuth = iv + ":" + cipherText;
        if (!computeMac(dataToAuth, keys.getMacKey()).equals(mac)) {
            throw new GeneralSecurityException("Incorrect MAC!");
        }

        // Decrypt the ciphertext
        byte[] ivBytes = decodeBase64(iv);
        cipher.init(Cipher.DECRYPT_MODE, keys.getEncKey(), new IvParameterSpec(ivBytes));
        byte[] bytes = cipher.doFinal(decodeBase64(cipherText));

        // Decode the plaintext bytes into a String
        CharsetDecoder decoder = Charset.defaultCharset().newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        /*
         * We are coding UTF-8 (guaranteed to be the default charset on
         * Android) to Java chars (UTF-16, 2 bytes per char).  For valid UTF-8
         * sequences, then:
         *     1 byte in UTF-8 (US-ASCII) -> 1 char in UTF-16
         *     2-3 bytes in UTF-8 (BMP)   -> 1 char in UTF-16
         *     4 bytes in UTF-8 (non-BMP) -> 2 chars in UTF-16 (surrogate pair)
         * The decoded output is therefore guaranteed to fit into a char
         * array the same length as the input byte array.
         */
        CharBuffer out = CharBuffer.allocate(bytes.length);
        CoderResult result = decoder.decode(ByteBuffer.wrap(bytes), out, true);
        if (result.isError()) {
            /* The input was supposed to be the result of encrypting a String,
             * so something is very wrong if it cannot be decoded into one! */
            throw new GeneralSecurityException("Corrupt decrypted data!");
        }
        decoder.flush(out);
        return out.flip().toString();
    }

    /**
     * Encrypts and authenticates a string using the algorithm described above.
     *
     * @param data The string containing the data to encrypt.
     * @param keys The keys to encrypt and authenticate with.
     * @return The encrypted data.
     *
     * @throws GeneralSecurityException if an error occurs during encryption.
     */
    public static String encrypt(String data, Keys keys) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ENC_SYSTEM);

        // Generate a random IV
        SecureRandom rng = new SecureRandom();
        byte[] ivBytes = new byte[ENC_BLOCKSIZE];
        rng.nextBytes(ivBytes);
        String iv = encodeToBase64(ivBytes);

        // Encrypt
        cipher.init(Cipher.ENCRYPT_MODE, keys.getEncKey(), new IvParameterSpec(ivBytes));
        byte[] bytes = data.getBytes();
        String cipherText = encodeToBase64(cipher.doFinal(bytes));

        // Calculate the MAC for the ciphertext and IV
        String dataToAuth = iv + ":" + cipherText;
        String mac = computeMac(dataToAuth, keys.getMacKey());

        return mac + ":" + dataToAuth;
    }

    /**
     * Computes the Base64-encoded Message Authentication Code for the
     * data using the provided key.
     *
     * @throws GeneralSecurityException if an error occurs during MAC computation.
     */
    private static String computeMac(String data, SecretKey key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(key);
        byte[] macBytes = mac.doFinal(data.getBytes());
        return encodeToBase64(macBytes);
    }

    /**
     * Encodes binary data to Base64 using the settings specified by
     * BASE64_EFLAGS.
     *
     * @return A String with the Base64-encoded data.
     */
    private static String encodeToBase64(byte[] data) {
        return Base64.encodeToString(data, BASE64_EFLAGS);
    }

    /**
     * Decodes Base64-encoded binary data using the settings specified by
     * BASE64_DFLAGS.
     *
     * @param data A String with the Base64-encoded data.
     * @return A newly-allocated byte[] array with the decoded data.
     */
    private static byte[] decodeBase64(String data) {
        return Base64.decode(data, BASE64_DFLAGS);
    }

    // Prevent instantiation
    private ShortcutEncryption() {
        throw new UnsupportedOperationException();
    }
}
