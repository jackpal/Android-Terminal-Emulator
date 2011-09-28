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

package jackpal.androidterm2.session;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import jackpal.androidterm2.Exec;
import jackpal.androidterm2.TermDebug;
import jackpal.androidterm2.model.UpdateCallback;
import jackpal.androidterm2.util.ByteQueue;
import jackpal.androidterm2.util.TermSettings;

/**
 * A terminal session, consisting of a TerminalEmulator, a TranscriptScreen,
 * the PID of the process attached to the session, and the I/O streams used to
 * talk to the process.
 */
public class TermSession {
    private TermSettings mSettings;
    private UpdateCallback mNotify;

    private int mProcId;
    private FileDescriptor mTermFd;
    private FileOutputStream mTermOut;
    private FileInputStream mTermIn;

    private TranscriptScreen mTranscriptScreen;
    private TerminalEmulator mEmulator;

    private Thread mPollingThread;
    private ByteQueue mByteQueue;
    private byte[] mReceiveBuffer;

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;
    private static final String DEFAULT_SHELL = "/system/bin/sh -";
    private static final String DEFAULT_INITIAL_COMMAND =
        "export PATH=/data/local/bin:$PATH";

    // Number of rows in the transcript
    private static final int TRANSCRIPT_ROWS = 10000;

    private static final int NEW_INPUT = 1;

    private boolean mIsRunning = false;
    private Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!mIsRunning) {
                return;
            }
            if (msg.what == NEW_INPUT) {
                readFromProcess();
            }
        }
    };

    public TermSession(TermSettings settings, UpdateCallback notify, String initialCommand) {
        mSettings = settings;
        mNotify = notify;

        int[] processId = new int[1];

        createSubprocess(processId);
        mProcId = processId[0];
        mTermOut = new FileOutputStream(mTermFd);
        mTermIn = new FileInputStream(mTermFd);

        mTranscriptScreen = new TranscriptScreen(DEFAULT_COLUMNS, TRANSCRIPT_ROWS, DEFAULT_ROWS, 0, 7);
        mEmulator = new TerminalEmulator(mTranscriptScreen, DEFAULT_COLUMNS, DEFAULT_ROWS, mTermOut);

        mIsRunning = true;

        Thread watcher = new Thread() {
             @Override
             public void run() {
                Log.i(TermDebug.LOG_TAG, "waiting for: " + mProcId);
                int result = Exec.waitFor(mProcId);
                Log.i(TermDebug.LOG_TAG, "Subprocess exited: " + result);
                mMsgHandler.sendEmptyMessage(result);
             }
        };
        watcher.setName("Process watcher");
        watcher.start();

        mReceiveBuffer = new byte[4 * 1024];
        mByteQueue = new ByteQueue(4 * 1024);

        mPollingThread = new Thread() {
            private byte[] mBuffer = new byte[4096];

            @Override
            public void run() {
                try {
                    while(true) {
                        int read = mTermIn.read(mBuffer);
                        if (read == -1) {
                            // EOF -- process exited
                            return;
                        }
                        mByteQueue.write(mBuffer, 0, read);
                        mMsgHandler.sendMessage(
                                mMsgHandler.obtainMessage(NEW_INPUT));
                    }
                } catch (IOException e) {
                } catch (InterruptedException e) {
                }
            }
        };
        mPollingThread.setName("Input reader");
        mPollingThread.start();

        sendInitialCommand(initialCommand);
    }

    private void sendInitialCommand(String initialCommand) {
        if (initialCommand == null || initialCommand.equals("")) {
            initialCommand = DEFAULT_INITIAL_COMMAND;
        }
        if (initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

    public void write(String data) {
        try {
            mTermOut.write(data.getBytes());
            mTermOut.flush();
        } catch (IOException e) {
            // Ignore exception
            // We don't really care if the receiver isn't listening.
            // We just make a best effort to answer the query.
        }
    }

    private void createSubprocess(int[] processId) {
        String shell = mSettings.getShell();
        if (shell == null || shell.equals("")) {
            shell = DEFAULT_SHELL;
        }
        ArrayList<String> args = parse(shell);
        String arg0 = args.get(0);
        String arg1 = null;
        String arg2 = null;
        if (args.size() >= 2) {
            arg1 = args.get(1);
        }
        if (args.size() >= 3) {
            arg2 = args.get(2);
        }
        mTermFd = Exec.createSubprocess(arg0, arg1, arg2, processId);
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

    public FileOutputStream getTermOut() {
        return mTermOut;
    }

    public TranscriptScreen getTranscriptScreen() {
        return mTranscriptScreen;
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    public void setUpdateCallback(UpdateCallback notify) {
        mNotify = notify;
    }

    public void updateSize(int columns, int rows) {
        // Inform the attached pty of our new size:
        Exec.setPtyWindowSize(mTermFd, rows, columns, 0, 0);
        mEmulator.updateSize(columns, rows);
    }

    public String getTranscriptText() {
        return mTranscriptScreen.getTranscriptText();
    }

    /**
     * Look for new input from the ptty, send it to the terminal emulator.
     */
    private void readFromProcess() {
        int bytesAvailable = mByteQueue.getBytesAvailable();
        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
        try {
            int bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
            mEmulator.append(mReceiveBuffer, 0, bytesRead);
        } catch (InterruptedException e) {
        }

        if (mNotify != null) {
            mNotify.onUpdate();
        }
    }

    public void finish() {
        Exec.hangupProcessGroup(mProcId);
        Exec.close(mTermFd);
        mIsRunning = false;
        mTranscriptScreen.finish();
    }
}
