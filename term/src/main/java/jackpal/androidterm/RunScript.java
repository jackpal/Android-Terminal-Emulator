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

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/*
 * New procedure for launching a command in ATE.
 * Build the path and arguments into a Uri and set that into Intent.data.
 * intent.data(new Uri.Builder().setScheme("file").setPath(path).setFragment(arguments))
 *
 * The old procedure of using Intent.Extra is still available but is discouraged.
 */
public final class RunScript extends RemoteInterface {
    private static final String ACTION_RUN_SCRIPT = "jackpal.androidterm.RUN_SCRIPT";

    private static final String EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle";
    private static final String EXTRA_INITIAL_COMMAND = "jackpal.androidterm.iInitialCommand";

    @Override
    protected void handleIntent() {
        TermService service = getTermService();
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

            finish();
        } else {
            super.handleIntent();
        }
    }
}
