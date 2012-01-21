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

package jackpal.androidterm.emulatorview;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * A terminal session, consisting of a TerminalEmulator, a TranscriptScreen,
 * the PID of the process attached to the session, and the I/O streams used to
 * talk to the process.
 */
public class TermSession {
    private ColorScheme mColorScheme;
    private UpdateCallback mNotify;

    private OutputStream mTermOut;
    private InputStream mTermIn;

    private TranscriptScreen mTranscriptScreen;
    private TerminalEmulator mEmulator;

    private boolean mDefaultUTF8Mode;

    private Thread mPollingThread;
    private ByteQueue mByteQueue;
    private byte[] mReceiveBuffer;

    private CharBuffer mWriteCharBuffer;
    private ByteBuffer mWriteByteBuffer;
    private CharsetEncoder mUTF8Encoder;

    // Number of rows in the transcript
    private static final int TRANSCRIPT_ROWS = 10000;

    private static final int NEW_INPUT = 1;

    /**
     * Callback to be invoked when a TermSession finishes.
     */
    public interface FinishCallback {
        void onSessionFinish(TermSession session);
    }
    private FinishCallback mFinishCallback;

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

    public TermSession() {
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
    }

    public void initializeEmulator(int columns, int rows) {
        mTranscriptScreen = new TranscriptScreen(columns, TRANSCRIPT_ROWS, rows, mColorScheme);
        mEmulator = new TerminalEmulator(mTranscriptScreen, columns, rows, mTermOut, mColorScheme);
        mEmulator.setDefaultUTF8Mode(mDefaultUTF8Mode);

        mIsRunning = true;
        mPollingThread.start();
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

    public OutputStream getTermOut() {
        return mTermOut;
    }

    public void setTermOut(OutputStream termOut) {
        mTermOut = termOut;
    }

    public InputStream getTermIn() {
        return mTermIn;
    }

    public void setTermIn(InputStream termIn) {
        mTermIn = termIn;
    }

    public boolean isRunning() {
        return mIsRunning;
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

    protected void notifyUpdate() {
        if (mNotify != null) {
            mNotify.onUpdate();
        }
    }

    /* Override this method if you support terminal size setting, but do
       call through to the superclass method */
    public void updateSize(int columns, int rows) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows);
        } else {
            mEmulator.updateSize(columns, rows);
        }
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
        notifyUpdate();
    }

    public void setColorScheme(ColorScheme scheme) {
        mColorScheme = scheme;
        if (mEmulator == null) {
            return;
        }
        mEmulator.setColorScheme(scheme);
        mTranscriptScreen.setColorScheme(scheme);
    }

    public void setDefaultUTF8Mode(boolean utf8ByDefault) {
        mDefaultUTF8Mode = utf8ByDefault;
        if (mEmulator == null) {
            return;
        }
        mEmulator.setDefaultUTF8Mode(utf8ByDefault);
    }

    public void reset() {
        mEmulator.reset();
        notifyUpdate();
    }

    public void setFinishCallback(FinishCallback callback) {
        mFinishCallback = callback;
    }

    public void finish() {
        mIsRunning = false;
        mTranscriptScreen.finish();
        if (mFinishCallback != null) {
            mFinishCallback.onSessionFinish(this);
        }
    }
}
