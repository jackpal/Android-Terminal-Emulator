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

import java.io.File;
import java.util.UUID;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import jackpal.androidterm.emulatorview.TermSession;

import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

/*
 * New procedure for launching a command in ATE.
 * Build the path and arguments into a Uri and set that into Intent.data.
 * intent.data(new Uri.Builder().setScheme("file").setPath(path).setFragment(arguments))
 * 
 * The old procedure of using Intent.Extra is still available but is discouraged.
 */
public class RemoteInterface extends Activity {
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
        String action = myIntent.getAction();
        if (action.equals(ACTION_RUN_SCRIPT)) {
            /* Someone with the appropriate permissions has asked us to
               run a script */
            String handle = myIntent.getStringExtra(EXTRA_WINDOW_HANDLE);
            String command=null;
            /*
             * First look in Intent.data for the path; if not there, revert to
             * the EXTRA_INITIAL_COMMAND location.
             */
            Uri uri=myIntent.getData();
            if(uri!=null) // scheme[path][arguments]
            {
              String s=uri.getScheme();
              if(s!=null && s.toLowerCase().equals("file"))
              {
                command=uri.getPath();
                // Allow for the command to be contained within the arguments string.
                if(command==null) command="";
                if(!command.equals("")) command=quoteForBash(command);
                // Append any arguments.
                if(null!=(s=uri.getFragment())) command+=" "+s;
              }
            }
            // If Intent.data not used then fall back to old method.
            if(command==null) command=myIntent.getStringExtra(EXTRA_INITIAL_COMMAND);
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
        else if (action.equals(Intent.ACTION_SEND)
                && myIntent.hasExtra(Intent.EXTRA_STREAM)) {
          /* "permission.RUN_SCRIPT" not required as this is merely opening a new window. */
            Object extraStream = myIntent.getExtras().get(Intent.EXTRA_STREAM);
            if (extraStream instanceof Uri) {
                String path = ((Uri) extraStream).getPath();
                File file = new File(path);
                String dirPath = file.isDirectory() ? path : file.getParent();
                openNewWindow("cd " + quoteForBash(dirPath));
            }
        } else {
            // Intent sender may not have permissions, ignore any extras
            openNewWindow(null);
        }

        unbindService(mTSConnection);
        finish();
    }

    /**
     *  Quote a string so it can be used as a parameter in bash and similar shells.
     */
    private String quoteForBash(String s) {
        StringBuilder builder = new StringBuilder();
        String specialChars = "\"\\$`!";
        builder.append('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (specialChars.indexOf(c) >= 0) {
                builder.append('\\');
            }
            builder.append(c);
        }
        builder.append('"');
        return builder.toString();
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
