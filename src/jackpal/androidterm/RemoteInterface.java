/*
 * Copyright (C) 2012 Steven Luo
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

import java.util.UUID;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import jackpal.androidterm.emulatorview.TermSession;

import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

public class RemoteInterface extends Activity {
    private static final String ACTION_OPEN_NEW_WINDOW = "jackpal.androidterm.OPEN_NEW_WINDOW";
    private static final String ACTION_RUN_SCRIPT = "jackpal.androidterm.RUN_SCRIPT";

    static final String PRIVACT_OPEN_NEW_WINDOW = "jackpal.androidterm.private.OPEN_NEW_WINDOW";
    static final String PRIVACT_SWITCH_WINDOW = "jackpal.androidterm.private.SWITCH_WINDOW";

    private static final String EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle";
    private static final String EXTRA_INITIAL_COMMAND = "jackpal.androidterm.iInitialCommand";

    static final String PRIVEXTRA_TARGET_WINDOW = "jackpal.androidterm.private.target_window";

    private TermSettings mSettings;

    private TermService mTermService;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            handleIntent();
        }

        public void onServiceDisconnected(ComponentName className) {
            mTermService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(getResources(), prefs);

        Intent TSIntent = new Intent(this, TermService.class);
        startService(TSIntent);
        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            Log.e(TermDebug.LOG_TAG, "bind to service failed!");
            finish();
        }
    }

    private void handleIntent() {
        TermService service = mTermService;
        if (service == null) {
            finish();
            return;
        }

        Intent myIntent = getIntent();
        if (myIntent.getAction().equals(ACTION_RUN_SCRIPT)) {
            /* Someone with the appropriate permissions has asked us to
               run a script */
            String handle = myIntent.getStringExtra(EXTRA_WINDOW_HANDLE);
            if (handle != null) {
                // Target the request at an existing window if open
                handle = appendToWindow(handle, myIntent.getStringExtra(EXTRA_INITIAL_COMMAND));
            } else {
                // Open a new window
                handle = openNewWindow(myIntent.getStringExtra(EXTRA_INITIAL_COMMAND));
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_WINDOW_HANDLE, handle);
            setResult(RESULT_OK, result);
        } else {
            // Intent sender may not have permissions, ignore any extras
            openNewWindow(null);
        }

        unbindService(mTSConnection);
        finish();
    }

    private String openNewWindow(String iInitialCommand) {
        TermService service = mTermService;

        String initialCommand = mSettings.getInitialCommand();
        if (iInitialCommand != null) {
            if (initialCommand != null) {
                initialCommand += "\r" + iInitialCommand;
            } else {
                initialCommand = iInitialCommand;
            }
        }

        TermSession session = Term.createTermSession(this, mSettings, initialCommand);
        session.setFinishCallback(service);
        service.getSessions().add(session);

        String handle = UUID.randomUUID().toString();
        ((ShellTermSession) session).setHandle(handle);

        Intent intent = new Intent(PRIVACT_OPEN_NEW_WINDOW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        return handle;
    }

    private String appendToWindow(String handle, String iInitialCommand) {
        TermService service = mTermService;

        // Find the target window
        SessionList sessions = service.getSessions();
        ShellTermSession target = null;
        int index;
        for (index = 0; index < sessions.size(); ++index) {
            ShellTermSession session = (ShellTermSession) sessions.get(index);
            String h = session.getHandle();
            if (h != null && h.equals(handle)) {
                target = session;
                break;
            }
        }

        if (target == null) {
            // Target window not found, open a new one
            return openNewWindow(iInitialCommand);
        }

        if (iInitialCommand != null) {
            target.write(iInitialCommand);
            target.write('\r');
        }

        Intent intent = new Intent(PRIVACT_SWITCH_WINDOW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PRIVEXTRA_TARGET_WINDOW, index);
        startActivity(intent);

        return handle;
    }
}
