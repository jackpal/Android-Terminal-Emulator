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

package jackpal.androidterm.sample.pathbroadcasts;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder;

public class PathReceiver extends BroadcastReceiver {
    /**
     * Called when a broadcast matching the declared intent filters is
     * received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        /* Unpack our sample bin/ and sbin/ if not already done */
        File binDir = setupBinDir(context);
        File sbinDir = setupSbinDir(context);

        String packageName = context.getPackageName();

        String action = intent.getAction();

        /**
         * You need to declare the permission
         * jackpal.androidterm.permission.APPEND_TO_PATH
         * to receive this broadcast.
         */
        if (action.equals("jackpal.androidterm.broadcast.APPEND_TO_PATH")) {
            /* The directory we want appended goes into the result extras */
            Bundle result = getResultExtras(true);

            /**
             * By convention, entries are indexed by package name.
             *
             * If you need to impose an ordering constraint for some reason,
             * you may prepend a number to your package name -- for example,
             * 50-com.example.awesomebin or 00-net.busybox.android.
             */
            result.putString(packageName, binDir.getAbsolutePath());

            setResultCode(Activity.RESULT_OK);
        }

        /**
         * You need to declare the permission
         * jackpal.androidterm.permission.PREPEND_TO_PATH
         * to receive this broadcast.
         *
         * This is intended for packages like BusyBox installers which need
         * to override existing system commands; otherwise, you should listen
         * for the APPEND_TO_PATH broadcast instead.
         */
        if (action.equals("jackpal.androidterm.broadcast.PREPEND_TO_PATH")) {
            /* The directory we want prepended goes into the result extras */
            Bundle result = getResultExtras(true);
            result.putString(packageName, sbinDir.getAbsolutePath());
            setResultCode(Activity.RESULT_OK);
        }
    }

    private File setupBinDir(Context context) {
        String dataDir = getDataDir(context);
        File binDir = new File(dataDir, "bin");
        if (!binDir.exists()) {
            try {
                binDir.mkdir();
                chmod("755", binDir.getAbsolutePath());
            } catch (Exception e) {
            }
        }

        File hello = new File(binDir, "hello");
        if (!hello.exists()) {
            try {
                InputStream src = context.getAssets().open("hello");
                FileOutputStream dst = new FileOutputStream(hello);
                copyStream(dst, src);
                chmod("755", hello.getAbsolutePath());
            } catch (Exception e) {
            }
        }

        return binDir;
    }

    private File setupSbinDir(Context context) {
        String dataDir = getDataDir(context);
        File sbinDir = new File(dataDir, "sbin");
        if (!sbinDir.exists()) {
            try {
                sbinDir.mkdir();
                chmod("755", sbinDir.getAbsolutePath());
            } catch (Exception e) {
            }
        }

        File ls = new File(sbinDir, "ls");
        if (!ls.exists()) {
            try {
                InputStream src = context.getAssets().open("ls");
                FileOutputStream dst = new FileOutputStream(ls);
                copyStream(dst, src);
                chmod("755", ls.getAbsolutePath());
            } catch (Exception e) {
            }
        }

        return sbinDir;
    }

    private String getDataDir(Context context) {
        /* On API 4 and later, you can just do this */
        // return context.getApplicationInfo().dataDir;

        String packageName = context.getPackageName();
        PackageManager pm = context.getPackageManager();
        String dataDir = null;
        try {
            dataDir = pm.getApplicationInfo(packageName, 0).dataDir;
        } catch (Exception e) {
            // Won't happen -- we know we're installed
        }
        return dataDir;
    }

    private void copyStream(OutputStream dst, InputStream src) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = 0;
        while ((bytesRead = src.read(buffer)) >= 0) {
            dst.write(buffer, 0, bytesRead);
        }
        dst.close();
    }

    private void chmod(String... args) throws IOException {
        String[] cmdline = new String[args.length + 1];
        cmdline[0] = "/system/bin/chmod";
        System.arraycopy(args, 0, cmdline, 1, args.length);
        new ProcessBuilder(cmdline).start();
    }
}
