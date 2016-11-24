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

import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import jackpal.androidterm.compat.FileCompat;
import jackpal.androidterm.util.TermSettings;

import java.io.*;
import java.util.ArrayList;

/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys it's process group
 * upon stopping.
 */
public class ShellTermSession extends GenericTermSession {
    private int mProcId;
    private Thread mWatcherThread;

    private String mInitialCommand;

    private static final int PROCESS_EXITED = 1;
    private Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!isRunning()) {
                return;
            }
            if (msg.what == PROCESS_EXITED) {
                onProcessExit((Integer) msg.obj);
            }
        }
    };

    public ShellTermSession(TermSettings settings, String initialCommand) throws IOException {
        super(ParcelFileDescriptor.open(new File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE),
                settings, false);

        initializeSession();

        setTermOut(new ParcelFileDescriptor.AutoCloseOutputStream(mTermFd));
        setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(mTermFd));

        mInitialCommand = initialCommand;

        mWatcherThread = new Thread() {
            @Override
            public void run() {
                Log.i(TermDebug.LOG_TAG, "waiting for: " + mProcId);
                int result = TermExec.waitFor(mProcId);
                Log.i(TermDebug.LOG_TAG, "Subprocess exited: " + result);
                mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result));
            }
        };
        mWatcherThread.setName("Process watcher");
    }

    private void initializeSession() throws IOException {
        TermSettings settings = mSettings;

        String path = System.getenv("PATH");
        if (settings.doPathExtensions()) {
            String appendPath = settings.getAppendPath();
            if (appendPath != null && appendPath.length() > 0) {
                path = path + ":" + appendPath;
            }

            if (settings.allowPathPrepend()) {
                String prependPath = settings.getPrependPath();
                if (prependPath != null && prependPath.length() > 0) {
                    path = prependPath + ":" + path;
                }
            }
        }
        if (settings.verifyPath()) {
            path = checkPath(path);
        }
        String[] env = new String[3];
        env[0] = "TERM=" + settings.getTermType();
        env[1] = "PATH=" + path;
        env[2] = "HOME=" + settings.getHomePath();

        mProcId = createSubprocess(settings.getShell(), env);
    }

    private String checkPath(String path) {
        String[] dirs = path.split(":");
        StringBuilder checkedPath = new StringBuilder(path.length());
        for (String dirname : dirs) {
            File dir = new File(dirname);
            if (dir.isDirectory() && FileCompat.canExecute(dir)) {
                checkedPath.append(dirname);
                checkedPath.append(":");
            }
        }
        return checkedPath.substring(0, checkedPath.length()-1);
    }

    @Override
    public void initializeEmulator(int columns, int rows) {
        super.initializeEmulator(columns, rows);

        mWatcherThread.start();
        sendInitialCommand(mInitialCommand);
    }

    private void sendInitialCommand(String initialCommand) {
        if (initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

    private int createSubprocess(String shell, String[] env) throws IOException {
        ArrayList<String> argList = parse(shell);
        String arg0;
        String[] args;

        try {
            arg0 = argList.get(0);
            File file = new File(arg0);
            if (!file.exists()) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not found!");
                throw new FileNotFoundException(arg0);
            } else if (!FileCompat.canExecute(file)) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not executable!");
                throw new FileNotFoundException(arg0);
            }
            args = argList.toArray(new String[1]);
        } catch (Exception e) {
            argList = parse(mSettings.getFailsafeShell());
            arg0 = argList.get(0);
            args = argList.toArray(new String[1]);
        }

        return TermExec.createSubprocess(mTermFd, arg0, args, env);
    }

    private ArrayList<String> parse(String cmd) {
        final int PLAIN = 0;
        final int WHITESPACE = 1;
        final int INQUOTE = 2;
        int state = WHITESPACE;
        ArrayList<String> result =  new ArrayList<String>();
        int cmdLen = cmd.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cmdLen; i++) {
            char c = cmd.charAt(i);
            if (state == PLAIN) {
                if (Character.isWhitespace(c)) {
                    result.add(builder.toString());
                    builder.delete(0,builder.length());
                    state = WHITESPACE;
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    builder.append(c);
                }
            } else if (state == WHITESPACE) {
                if (Character.isWhitespace(c)) {
                    // do nothing
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    state = PLAIN;
                    builder.append(c);
                }
            } else if (state == INQUOTE) {
                if (c == '\\') {
                    if (i + 1 < cmdLen) {
                        i += 1;
                        builder.append(cmd.charAt(i));
                    }
                } else if (c == '"') {
                    state = PLAIN;
                } else {
                    builder.append(c);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }

    private void onProcessExit(int result) {
        onProcessExit();
    }

    @Override
    public void finish() {
        hangupProcessGroup();
        super.finish();
    }

    /**
     * Send SIGHUP to a process group, SIGHUP notifies a terminal client, that the terminal have been disconnected,
     * and usually results in client's death, unless it's process is a daemon or have been somehow else detached
     * from the terminal (for example, by the "nohup" utility).
     */
    void hangupProcessGroup() {
        TermExec.sendSignal(-mProcId, 1);
    }
}
