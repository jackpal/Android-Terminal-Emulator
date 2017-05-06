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
        public static Typeface createFromFile(File path,Typeface defaultValue) {
            Typeface result=Typeface.createFromFile(path);
            if(result.equals(Typeface.DEFAULT)) {
                result=defaultValue;
            }
            return result;
        }
    }
    public static Typeface createFromFile(File path) {
        return createFromFile(path,Typeface.MONOSPACE);
    }

    public static Typeface createFromFile(File path, Typeface defaulValue) {
        if(jackpal.androidterm.emulatorview.compat.AndroidCompat.SDK < 4 ) {
            return defaulValue;
        }
        return Api4OrLater.createFromFile(path,defaulValue);
    }
}
