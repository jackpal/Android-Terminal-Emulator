package jackpal.androidterm.util;

import java.lang.reflect.Method;

import android.text.AndroidCharacter;
import android.util.Log;

/**
 * Provides APIs post Android version 3.
 *
 */
public class PostAndroid3Utils {
    private static String TAG = "PostAndroid3Utils";

    public final static int SDK =
        Integer.valueOf(android.os.Build.VERSION.SDK);
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

        private static boolean mGetEastAsianWidthInitialized;
        private static Method mGetEastAsianWidthMethod;
        /**
         * Calls AndroidCharacter.getEastAsianWidth if it exists,
         * otherwise returns EAST_ASIAN_WIDTH_NARROW.
         */
        public static int getEastAsianWidth(char c) {
            int result = EAST_ASIAN_WIDTH_NARROW;
            if (!mGetEastAsianWidthInitialized) {
                mGetEastAsianWidthInitialized = true;
                try {
                    Class<?>[] parameterTypes = new Class<?>[]{
                            char.class};
                    Method method = AndroidCharacter.class.getMethod(
                            "getEastAsianWidth", parameterTypes);
                    if (method.getGenericReturnType() != int.class) {
                        Log.e(TAG, "Unexpected return type for getEastAsianWidth");
                    } else {
                        mGetEastAsianWidthMethod = method;
                    }
                } catch (NoSuchMethodException e) {
                    // Pre Android API level 8
                }
            } else {
                if (mGetEastAsianWidthMethod != null) {
                    try {
                        Integer objectResult = (Integer) mGetEastAsianWidthMethod.invoke(
                                null, new Object[]{new Character(c)});
                        result = objectResult.intValue();
                    } catch(Exception e) {
                        Log.e(TAG, "Unexpected exception when calling getEastAsianWidth", e);
                    }
                }
            }
            return result;
        }
    }
}
