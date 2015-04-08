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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;

public class TermPreferences extends PreferenceActivity {
    private static final String ACTIONBAR_KEY = "actionbar";
    private static final String CATEGORY_SCREEN_KEY = "screen";

    private static final String CUSTOM_FONT_CHOOSER="custom_font_filepath";

    private static final int RESULT_CODE_FONT_CHOOSER=1;


    protected void updateFontSummary() {
        Preference fontSelector=findPreference(CUSTOM_FONT_CHOOSER);
        String currentFont= PreferenceManager.getDefaultSharedPreferences(this).getString(CUSTOM_FONT_CHOOSER, "");
        if(currentFont=="") {
            fontSelector.setSummary(this.getString(R.string.custom_font_filepath_summary));
        }else{
            fontSelector.setSummary(currentFont+" "+this.getString(R.string.custom_font_filepath_summary_reset));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Remove the action bar pref on older platforms without an action bar
        if (AndroidCompat.SDK < 11) {
            Preference actionBarPref = findPreference(ACTIONBAR_KEY);
             PreferenceCategory screenCategory =
                    (PreferenceCategory) findPreference(CATEGORY_SCREEN_KEY);
             if ((actionBarPref != null) && (screenCategory != null)) {
                 screenCategory.removePreference(actionBarPref);
             }
        }

        // Display up indicator on action bar home button
        if (AndroidCompat.V11ToV20) {
            ActionBarCompat bar = ActivityCompat.getActionBar(this);
            if (bar != null) {
                bar.setDisplayOptions(ActionBarCompat.DISPLAY_HOME_AS_UP, ActionBarCompat.DISPLAY_HOME_AS_UP);
            }
        }

        /// Init custom font chooser
        Preference fontSelector=findPreference(CUSTOM_FONT_CHOOSER);
        updateFontSummary();
        final PreferenceActivity that=this;
        fontSelector.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        String currentFont= PreferenceManager.getDefaultSharedPreferences(that).getString(CUSTOM_FONT_CHOOSER, "");
                        if(currentFont=="") {
                            Intent intent = new Intent("android.intent.action.GET_CONTENT");
                            intent.setType("*/*");
                            startActivityForResult(intent, RESULT_CODE_FONT_CHOOSER);
                        }else{
                            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(that).edit();
                            editor.putString(CUSTOM_FONT_CHOOSER, "");
                            editor.commit();
                            updateFontSummary();
                        }
                        return true;
                    }
                }
        );
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode==RESULT_OK) {
            if(requestCode==RESULT_CODE_FONT_CHOOSER) {
                String path= data.getData().getPath();
                if(new File(path).exists()) {
                    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                    editor.putString(CUSTOM_FONT_CHOOSER, path);
                    editor.commit();
                    updateFontSummary();
                }else{
                    Toast.makeText(this,R.string.custom_font_filepath_summary_error,Toast.LENGTH_LONG).show();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
