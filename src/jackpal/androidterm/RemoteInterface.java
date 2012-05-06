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

import jackpal.androidterm.util.TermSettings;

public class RemoteInterface extends Activity {
    private static final String ACTION_OPEN_NEW_WINDOW = "jackpal.androidterm.OPEN_NEW_WINDOW";
    private static final String ACTION_RUN_SCRIPT = "jackpal.androidterm.RUN_SCRIPT";

    public static final String EXTRA_REMOTE_OPEN_WINDOW = "jackpal.androidterm.remote_open_window";
    private static final String EXTRA_INITIAL_COMMAND = "jackpal.androidterm.iInitialCommand";

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
            openNewWindow(myIntent.getStringExtra(EXTRA_INITIAL_COMMAND));
        } else {
            // Intent sender may not have permissions, ignore any extras
            openNewWindow(null);
        }
    }

    private void openNewWindow(String iInitialCommand) {
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

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(this, Term.class));
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_REMOTE_OPEN_WINDOW, true);
        startActivity(intent);

        unbindService(mTSConnection);
        finish();
    }
}
