package jackpal.androidterm.compat;

import android.view.MenuItem;

/**
 * Definitions related to android.view.MenuItem
 */
public class MenuItemCompat {
    public static final int SHOW_AS_ACTION_NEVER = 0;
    public static final int SHOW_AS_ACTION_IF_ROOM = 1;
    public static final int SHOW_AS_ACTION_ALWAYS = 2;
    public static final int SHOW_AS_ACTION_WITH_TEXT = 4;

    private static class Api11OrLater {
        public static void setShowAsAction(MenuItem item, int actionEnum) {
            item.setShowAsAction(actionEnum);
        }
    }

    public static void setShowAsAction(MenuItem item, int actionEnum) {
        if (AndroidCompat.SDK >= 11) {
            Api11OrLater.setShowAsAction(item, actionEnum);
        }
    }
}
