package jackpal.androidterm.compat;

import android.annotation.TargetApi;
import android.graphics.Typeface;

import java.io.File;

import jackpal.androidterm.emulatorview.compat.*;

/**
 * Typeface creation shim
 */

public class TypefaceCompat {
    private static class Api4OrLater {
        @TargetApi(4)
        public static Typeface createFromFile(File path) {
            Typeface result=Typeface.createFromFile(path);
            if(result.equals(Typeface.DEFAULT)) {
                result=Typeface.MONOSPACE;
            }
            return result;
        }
    }
    public static Typeface createFromFile(File path) {
        if(jackpal.androidterm.emulatorview.compat.AndroidCompat.SDK < 4 ) {
            return Typeface.MONOSPACE;
        }
        return Api4OrLater.createFromFile(path);
    }
}
