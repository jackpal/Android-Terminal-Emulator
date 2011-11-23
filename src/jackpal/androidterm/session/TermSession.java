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

package jackpal.androidterm.session;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import jackpal.androidterm.Exec;
import jackpal.androidterm.TermDebug;
import jackpal.androidterm.model.SessionFinishCallback;
import jackpal.androidterm.model.UpdateCallback;
import jackpal.androidterm.util.ByteQueue;
import jackpal.androidterm.util.TermSettings;

/**
 * A terminal session, consisting of a TerminalEmulator, a TranscriptScreen,
 * the PID of the process attached to the session, and the I/O streams used to
 * talk to the process.
 */
public class TermSession {
    private TermSettings mSettings;
    private UpdateCallback mNotify;
    private SessionFinishCallback mFinishCallback;

    private int mProcId;
    private FileDescriptor mTermFd;
    private FileOutputStream mTermOut;
    private FileInputStream mTermIn;

    private TranscriptScreen mTranscriptScreen;
    private TerminalEmulator mEmulator;

    private Thread mPollingThread;
    private ByteQueue mByteQueue;
    private byte[] mReceiveBuffer;

    private CharBuffer mWriteCharBuffer;
    private ByteBuffer mWriteByteBuffer;
    private CharsetEncoder mUTF8Encoder;

    private String mProcessExitMessage;

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;

    // Number of rows in the transcript
    private static final int TRANSCRIPT_ROWS = 10000;

    private static final int NEW_INPUT = 1;
    private static final int PROCESS_EXITED = 2;

    private boolean mIsRunning = false;
    private Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!mIsRunning) {
                return;
            }
            if (msg.what == NEW_INPUT) {
                readFromProcess();
            } else if (msg.what == PROCESS_EXITED) {
                onProcessExit((Integer) msg.obj);
            }
        }
    };

    public TermSession(TermSettings settings, SessionFinishCallback finishCallback, String initialCommand) {
        mSettings = settings;
        mFinishCallback = finishCallback;

        int[] processId = new int[1];

        createSubprocess(processId);
        mProcId = processId[0];
        mTermOut = new FileOutputStream(mTermFd);
        mTermIn = new FileInputStream(mTermFd);

        int[] colorScheme = settings.getColorScheme();
        mTranscriptScreen = new TranscriptScreen(DEFAULT_COLUMNS, TRANSCRIPT_ROWS, DEFAULT_ROWS, colorScheme[0], colorScheme[2]);
        mEmulator = new TerminalEmulator(settings, mTranscriptScreen, DEFAULT_COLUMNS, DEFAULT_ROWS, mTermOut);

        mIsRunning = true;

        Thread watcher = new Thread() {
             @Override
             public void run() {
                Log.i(TermDebug.LOG_TAG, "waiting for: " + mProcId);
                int result = Exec.waitFor(mProcId);
                Log.i(TermDebug.LOG_TAG, "Subprocess exited: " + result);
                mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result));
             }
        };
        watcher.setName("Process watcher");
        watcher.start();

        mWriteCharBuffer = CharBuffer.allocate(2);
        mWriteByteBuffer = ByteBuffer.allocate(4);
        mUTF8Encoder = Charset.forName("UTF-8").newEncoder();
        mUTF8Encoder.onMalformedInput(CodingErrorAction.REPLACE);
        mUTF8Encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

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
        if (initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

    public void write(String data) {
        try {
            mTermOut.write(data.getBytes("UTF-8"));
            mTermOut.flush();
        } catch (IOException e) {
            // Ignore exception
            // We don't really care if the receiver isn't listening.
            // We just make a best effort to answer the query.
        }
    }

    public void write(int codePoint) {
        CharBuffer charBuf = mWriteCharBuffer;
        ByteBuffer byteBuf = mWriteByteBuffer;
        CharsetEncoder encoder = mUTF8Encoder;
        try {
            charBuf.clear();
            byteBuf.clear();
            Character.toChars(codePoint, charBuf.array(), 0);
            encoder.reset();
            encoder.encode(charBuf, byteBuf, true);
            encoder.flush(byteBuf);
            mTermOut.write(byteBuf.array(), 0, byteBuf.position()-1);
            mTermOut.flush();
        } catch (IOException e) {
            // Ignore exception
        }
    }

    private void createSubprocess(int[] processId) {
        String shell = mSettings.getShell();
        ArrayList<String> argList = parse(shell);
        String arg0 = argList.get(0);
        String[] args = argList.toArray(new String[1]);

        String termType = mSettings.getTermType();
        String[] env = new String[1];
        env[0] = "TERM=" + termType;

        mTermFd = Exec.createSubprocess(arg0, args, env, processId);
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

    public void updatePrefs(TermSettings settings) {
        mSettings = settings;
        mEmulator.updatePrefs(settings);

        int[] colorScheme = settings.getColorScheme();
        mTranscriptScreen.setDefaultColors(colorScheme[0], colorScheme[2]);
    }

    public void reset() {
        mEmulator.reset();
        if (mNotify != null) {
            mNotify.onUpdate();
        }
    }

    /* XXX We should really get this ourselves from the resource bundle, but
       we cannot hold a context */
    public void setProcessExitMessage(String message) {
        mProcessExitMessage = message;
    }

    private void onProcessExit(int result) {
        if (mSettings.closeWindowOnProcessExit()) {
            if (mFinishCallback != null) {
                mFinishCallback.onSessionFinish(this);
            }
            finish();
        } else if (mProcessExitMessage != null) {
            try {
                byte[] msg = ("\r\n[" + mProcessExitMessage + "]").getBytes("UTF-8");
                mEmulator.append(msg, 0, msg.length);
                mNotify.onUpdate();
            } catch (UnsupportedEncodingException e) {
                // Never happens
            }
        }
    }

    public void finish() {
        Exec.hangupProcessGroup(mProcId);
        Exec.close(mTermFd);
        mIsRunning = false;
        mTranscriptScreen.finish();
    }
}
