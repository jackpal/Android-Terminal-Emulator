package jackpal.androidterm.compat;

import android.annotation.TargetApi;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;

/**
 * Shim for detecting UI Mode
 */

public class UIModeCompat {
    private static class Api8OrLater {
        @TargetApi(8)
        public static boolean isUIModeTV(Context context) {
            boolean result=false;
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
                result=true;
            }
            uiModeManager=null;
            return result;
        }
    }

    public static boolean isUIModeTV(Context context) {
        boolean result=false;
        if(AndroidCompat.SDK >= 8) {
            result=Api8OrLater.isUIModeTV(context);
        }
        return result;
    }
}
