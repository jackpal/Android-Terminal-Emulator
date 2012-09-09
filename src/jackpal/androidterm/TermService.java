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

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.content.Intent;
import android.util.Log;
import android.app.Notification;
import android.app.PendingIntent;

import jackpal.androidterm.emulatorview.TermSession;

import jackpal.androidterm.compat.ServiceForegroundCompat;
import jackpal.androidterm.util.SessionList;

public class TermService extends Service implements TermSession.FinishCallback
{
    /* Parallels the value of START_STICKY on API Level >= 5 */
    private static final int COMPAT_START_STICKY = 1;

    private static final int RUNNING_NOTIFICATION = 1;
    private ServiceForegroundCompat compat;

    private SessionList mTermSessions;

    public class TSBinder extends Binder {
        TermService getService() {
            Log.i("TermService", "Activity binding to service");
            return TermService.this;
        }
    }
    private final IBinder mTSBinder = new TSBinder();

    @Override
    public void onStart(Intent intent, int flags) {
    }

    /* This should be @Override if building with API Level >=5 */
    public int onStartCommand(Intent intent, int flags, int startId) {
        return COMPAT_START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i("TermService", "Activity called onBind()");
        return mTSBinder;
    }

    @Override
    public void onCreate() {
        compat = new ServiceForegroundCompat(this);
        mTermSessions = new SessionList();

        /* Put the service in the foreground. */
        Notification notification = new Notification(R.drawable.ic_stat_service_notification_icon, getText(R.string.service_notify_text), System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        Intent notifyIntent = new Intent(this, Term.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.application_terminal), getText(R.string.service_notify_text), pendingIntent);
        compat.startForeground(RUNNING_NOTIFICATION, notification);

        Log.d(TermDebug.LOG_TAG, "TermService started");
        return;
    }

    @Override
    public void onDestroy() {
        compat.stopForeground(true);
        for (TermSession session : mTermSessions) {
            /* Don't automatically remove from list of sessions -- we clear the
             * list below anyway and we could trigger
             * ConcurrentModificationException if we do */
            session.setFinishCallback(null);
            session.finish();
        }
        mTermSessions.clear();
        return;
    }

    public SessionList getSessions() {
        return mTermSessions;
    }

    public void onSessionFinish(TermSession session) {
        mTermSessions.remove(session);
    }
}
