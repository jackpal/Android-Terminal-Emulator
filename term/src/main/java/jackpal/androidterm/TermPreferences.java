/*
 * Copyright (C) 2007 The Android Open Source Project
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

package jackpal.androidterm;

import jackpal.androidterm.compat.ActionBarCompat;
import jackpal.androidterm.compat.ActivityCompat;
import jackpal.androidterm.compat.AndroidCompat;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public class TermPreferences extends PreferenceActivity {
    private static final String ACTIONBAR_KEY = "actionbar";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Disable the action bar pref on older platforms without an action bar
        if (AndroidCompat.SDK < 11) {
            getPreferenceManager().findPreference(ACTIONBAR_KEY).setEnabled(false);
        }

        // Display up indicator on action bar home button
        if (AndroidCompat.SDK >= 11) {
            ActionBarCompat bar = ActivityCompat.getActionBar(this);
            if (bar != null) {
                bar.setDisplayOptions(ActionBarCompat.DISPLAY_HOME_AS_UP, ActionBarCompat.DISPLAY_HOME_AS_UP);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case ActionBarCompat.ID_HOME:
            // Action bar home button selected
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
