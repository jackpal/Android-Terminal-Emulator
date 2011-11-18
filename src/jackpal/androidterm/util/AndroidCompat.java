package jackpal.androidterm.util;

import android.text.AndroidCharacter;

/**
 * Provides APIs post Android version 3.
 *
 */
public class AndroidCompat {
    public final static int SDK =
        Integer.valueOf(android.os.Build.VERSION.SDK);

    /**
     * The classes here each take advantage of the fact that the VM does
     * not attempt to load a class until it's accessed, and the verifier
     * does not run until a class is loaded.  By keeping the methods which
     * are unavailable on older platforms in subclasses which are only ever
     * accessed on platforms where they are available, we can preserve
     * compatibility with older platforms without resorting to reflection.
     *
     * See http://developer.android.com/resources/articles/backward-compatibility.html
     * and http://android-developers.blogspot.com/2010/07/how-to-have-your-cupcake-and-eat-it-too.html
     * for further discussion of this technique.
     */

    /**
     * Definitions related to android.text.AndroidCharacter
     */
    public static class AndroidCharacterComp {
        public static final int EAST_ASIAN_WIDTH_NEUTRAL = 0;
        public static final int EAST_ASIAN_WIDTH_AMBIGUOUS = 1;
        public static final int EAST_ASIAN_WIDTH_HALF_WIDTH = 2;
        public static final int EAST_ASIAN_WIDTH_FULL_WIDTH = 3;
        public static final int EAST_ASIAN_WIDTH_NARROW = 4;
        public static final int EAST_ASIAN_WIDTH_WIDE = 5;

        private static class Api8OrLater {
            public static void initialize() {
                // Does nothing -- call this to force the class to try to load
            }

            public static int getEastAsianWidth(char c) {
                return AndroidCharacter.getEastAsianWidth(c);
            }
        }

        public static int getEastAsianWidth(char c) {
            if (SDK >= 8) {
                return Api8OrLater.getEastAsianWidth(c);
            } else {
                return EAST_ASIAN_WIDTH_NARROW;
            }
        }
    }
}
