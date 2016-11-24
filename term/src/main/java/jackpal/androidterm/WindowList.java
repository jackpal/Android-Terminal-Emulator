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

package jackpal.androidterm;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import jackpal.androidterm.compat.ActionBarCompat;
import jackpal.androidterm.compat.ActivityCompat;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.util.SessionList;

public class WindowList extends ListActivity {
    private SessionList sessions;
    private WindowListAdapter mWindowListAdapter;
    private TermService mTermService;

    /**
     * View which isn't automatically in the pressed state if its parent is
     * pressed.  This allows the window's entry to be pressed without the close
     * button being triggered.
     * Idea and code shamelessly borrowed from the Android browser's tabs list.
     *
     * Used by layout xml.
     */
    public static class CloseButton extends ImageView {
        public CloseButton(Context context) {
            super(context);
        }

        public CloseButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public CloseButton(Context context, AttributeSet attrs, int style) {
            super(context, attrs, style);
        }

        @Override
        public void setPressed(boolean pressed) {
            if (pressed && ((View) getParent()).isPressed()) {
                return;
            }
            super.setPressed(pressed);
        }
    }

    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            populateList();
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ListView listView = getListView();
        View newWindow = getLayoutInflater().inflate(R.layout.window_list_new_window, listView, false);
        listView.addHeaderView(newWindow, null, true);

        setResult(RESULT_CANCELED);

        // Display up indicator on action bar home button
        if (AndroidCompat.SDK >= 11) {
            ActionBarCompat bar = ActivityCompat.getActionBar(this);
            if (bar != null) {
                bar.setDisplayOptions(ActionBarCompat.DISPLAY_HOME_AS_UP, ActionBarCompat.DISPLAY_HOME_AS_UP);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent TSIntent = new Intent(this, TermService.class);
        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            Log.w(TermDebug.LOG_TAG, "bind to service failed!");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        WindowListAdapter adapter = mWindowListAdapter;
        if (sessions != null) {
            sessions.removeCallback(adapter);
            sessions.removeTitleChangedListener(adapter);
        }
        if (adapter != null) {
            adapter.setSessions(null);
        }
        unbindService(mTSConnection);
    }

    private void populateList() {
        sessions = mTermService.getSessions();
        WindowListAdapter adapter = mWindowListAdapter;

        if (adapter == null) {
            adapter = new WindowListAdapter(sessions);
            setListAdapter(adapter);
            mWindowListAdapter = adapter;
        } else {
            adapter.setSessions(sessions);
        }
        sessions.addCallback(adapter);
        sessions.addTitleChangedListener(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent data = new Intent();
        data.putExtra(Term.EXTRA_WINDOW_ID, position-1);
        setResult(RESULT_OK, data);
        finish();
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
