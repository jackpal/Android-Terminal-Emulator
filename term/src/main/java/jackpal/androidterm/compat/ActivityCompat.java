/*
 * Copyright (C) 2011 Steven Luo
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

package jackpal.androidterm.compat;

import android.app.Activity;

/**
 * Compatibility class for android.app.Activity
 */
public class ActivityCompat {
    private static class Api11OrLater {
        public static void invalidateOptionsMenu(Activity activity) {
            activity.invalidateOptionsMenu();
        }

        public static Object getActionBar(Activity activity) {
            return activity.getActionBar();
        }
    }

    public static void invalidateOptionsMenu(Activity activity) {
        if (AndroidCompat.SDK >= 11) {
            Api11OrLater.invalidateOptionsMenu(activity);
        }
    }

    public static ActionBarCompat getActionBar(Activity activity) {
        if (AndroidCompat.SDK < 11) {
            return null;
        }
        return ActionBarCompat.wrap(Api11OrLater.getActionBar(activity));
    }
}
