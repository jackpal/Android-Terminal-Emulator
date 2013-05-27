package jackpal.androidterm.emulatorview.compat;

import android.text.AndroidCharacter;

/**
 * Definitions related to android.text.AndroidCharacter
 */
public class AndroidCharacterCompat {
    public static final int EAST_ASIAN_WIDTH_NEUTRAL = 0;
    public static final int EAST_ASIAN_WIDTH_AMBIGUOUS = 1;
    public static final int EAST_ASIAN_WIDTH_HALF_WIDTH = 2;
    public static final int EAST_ASIAN_WIDTH_FULL_WIDTH = 3;
    public static final int EAST_ASIAN_WIDTH_NARROW = 4;
    public static final int EAST_ASIAN_WIDTH_WIDE = 5;

    private static class Api8OrLater {
        public static int getEastAsianWidth(char c) {
            return AndroidCharacter.getEastAsianWidth(c);
        }
    }

    public static int getEastAsianWidth(char c) {
        if (AndroidCompat.SDK >= 8) {
            return Api8OrLater.getEastAsianWidth(c);
        } else {
            return EAST_ASIAN_WIDTH_NARROW;
        }
    }
}
