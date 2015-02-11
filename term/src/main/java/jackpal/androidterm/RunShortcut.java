/*
 * Copyright (C) 2015 Steven Luo
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

import jackpal.androidterm.util.ShortcutEncryption;

import java.security.GeneralSecurityException;

import android.content.Intent;
import android.util.Log;

public final class RunShortcut extends RemoteInterface {
    public static final String ACTION_RUN_SHORTCUT = "jackpal.androidterm.RUN_SHORTCUT";

    public static final String EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle";
    public static final String EXTRA_SHORTCUT_COMMAND = "jackpal.androidterm.iShortcutCommand";

    @Override
    protected void handleIntent() {
        TermService service = getTermService();
        if (service == null) {
            finish();
            return;
        }

        Intent myIntent = getIntent();
        String action = myIntent.getAction();
        if (action.equals(ACTION_RUN_SHORTCUT)) {
            String encCommand = myIntent.getStringExtra(EXTRA_SHORTCUT_COMMAND);
            if (encCommand == null) {
                Log.e(TermDebug.LOG_TAG, "No command provided in shortcut!");
                finish();
                return;
            }

            // Decrypt and verify the command
            ShortcutEncryption.Keys keys = ShortcutEncryption.getKeys(this);
            if (keys == null) {
                // No keys -- no valid shortcuts can exist
                Log.e(TermDebug.LOG_TAG, "No shortcut encryption keys found!");
                finish();
                return;
            }
            String command;
            try {
                command = ShortcutEncryption.decrypt(encCommand, keys);
            } catch (GeneralSecurityException e) {
                Log.e(TermDebug.LOG_TAG, "Invalid shortcut: " + e.toString());
                finish();
                return;
            }

            String handle = myIntent.getStringExtra(EXTRA_WINDOW_HANDLE);
            if (handle != null) {
                // Target the request at an existing window if open
                handle = appendToWindow(handle, command);
            } else {
                // Open a new window
                handle = openNewWindow(command);
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_WINDOW_HANDLE, handle);
            setResult(RESULT_OK, result);
        }

        finish();
    }
}
