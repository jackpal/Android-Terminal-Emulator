package jackpal.androidterm.emulatorview.compat;

import android.annotation.TargetApi;
import android.graphics.Typeface;

import java.io.File;

/**
 * Typeface creation shim
 */

public class TypefaceCompat {
    private static class Api4OrLater {
        @TargetApi(4)
        public static Typeface createFromFile(File path) {
            return Typeface.createFromFile(path);
        }
    }
    public static Typeface createFromFile(File path) {
        if(AndroidCompat.SDK < 4 ) {
            return Typeface.MONOSPACE;
        }
        return Api4OrLater.createFromFile(path);
    }
}
