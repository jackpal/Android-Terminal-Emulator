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
 * A terminal session, consisting of a VT100 terminal emulator and its
 * input and output streams.
 * <p>
 * You need to supply an {@link InputStream} and {@link OutputStream} to
 * provide input and output to the terminal.  For a locally running
 * program, these would typically point to a tty; for a telnet program
 * they might point to a network socket.
 * <p>
 * Call {@link #setTermIn} and {@link #setTermOut} to connect the input and
 * output streams to the emulator.  When all of your initialization is
 * complete, your initial screen size is known, and you're ready to
 * start VT100 emulation, call {@link #initializeEmulator} or {@link
 * #updateSize} with the number of rows and columns the terminal should
 * initially have.
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
     * Callback to be invoked when a {@link TermSession} finishes.
     *
     * @see TermSession#setUpdateCallback
     */
    public interface FinishCallback {
        /**
         * Callback function to be invoked when a {@link TermSession} finishes.
         *
         * @param session The <code>TermSession</code> which has finished.
         */
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

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows) {
        mTranscriptScreen = new TranscriptScreen(columns, TRANSCRIPT_ROWS, rows, mColorScheme);
        mEmulator = new TerminalEmulator(mTranscriptScreen, columns, rows, mTermOut, mColorScheme);
        mEmulator.setDefaultUTF8Mode(mDefaultUTF8Mode);

        mIsRunning = true;
        mPollingThread.start();
    }

    /**
     * Write data to the terminal output.  The written data will be consumed by
     * the emulation client as input.
     *
     * @param data The data to write to the terminal.
     */
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

    /**
     * Write a single Unicode code point to the terminal output.  The written
     * data will be consumed by the emulation client as input.
     *
     * @param codePoint The Unicode code point to write to the terminal.
     */
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

    /**
     * Get the {@link OutputStream} associated with this session.
     *
     * @return This session's {@link OutputStream}.
     */
    public OutputStream getTermOut() {
        return mTermOut;
    }

    /**
     * Set the {@link OutputStream} associated with this session.
     *
     * @param termOut This session's {@link OutputStream}.
     */
    public void setTermOut(OutputStream termOut) {
        mTermOut = termOut;
    }

    /**
     * Get the {@link InputStream} associated with this session.
     *
     * @return This session's {@link InputStream}.
     */
    public InputStream getTermIn() {
        return mTermIn;
    }

    /**
     * Set the {@link InputStream} associated with this session.
     *
     * @param termIn This session's {@link InputStream}.
     */
    public void setTermIn(InputStream termIn) {
        mTermIn = termIn;
    }

    /**
     * @return Whether the terminal emulation is currently running.
     */
    public boolean isRunning() {
        return mIsRunning;
    }

    TranscriptScreen getTranscriptScreen() {
        return mTranscriptScreen;
    }

    TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /**
     * Set an {@link UpdateCallback} to be invoked when the terminal emulator's
     * screen is changed.
     *
     * @param notify The {@link UpdateCallback} to be invoked on changes.
     */
    public void setUpdateCallback(UpdateCallback notify) {
        mNotify = notify;
    }

    /**
     * Notify the {@link UpdateCallback} registered by {@link
     * #setUpdateCallback setUpdateCallback} that the screen has changed.
     */
    protected void notifyUpdate() {
        if (mNotify != null) {
            mNotify.onUpdate();
        }
    }

    /**
     * Change the terminal's window size.  Will call {@link #initializeEmulator}
     * if the emulator is not yet running.
     * <p>
     * You should override this method if your application needs to be notified
     * when the screen size changes (for example, if you need to issue
     * <code>TIOCSWINSZ</code> to a tty to adjust the window size).  <em>If you
     * do override this method, you must call through to the superclass
     * implementation.</em>
     *
     * @param columns The number of columns in the terminal window.
     * @param rows The number of rows in the terminal window.
     */
    public void updateSize(int columns, int rows) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows);
        } else {
            mEmulator.updateSize(columns, rows);
        }
    }

    /**
     * Retrieve the terminal's screen and scrollback buffer.
     *
     * @return A {@link String} containing the contents of the screen and
     *         scrollback buffer.
     */
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

    /**
     * Write something directly to the terminal input, bypassing the
     * emulation client and the session's {@link InputStream}.
     *
     * @param buffer The data to be written to the terminal.
     * @param base The starting offset into the buffer of the data.
     * @param length The length of the data to be written.
     */
    protected void appendToEmulator(byte[] buffer, int base, int length) {
        mEmulator.append(buffer, base, length);
    }

    /**
     * Set the terminal emulator's color scheme (default colors).
     *
     * @param scheme The {@link ColorScheme} to be used.
     */
    public void setColorScheme(ColorScheme scheme) {
        mColorScheme = scheme;
        if (mEmulator == null) {
            return;
        }
        mEmulator.setColorScheme(scheme);
        mTranscriptScreen.setColorScheme(scheme);
    }

    /**
     * Set whether the terminal emulator should be in UTF-8 mode by default.
     * <p>
     * In UTF-8 mode, the terminal will handle UTF-8 sequences, allowing the
     * display of text in most of the world's languages, but applications must
     * encode C1 control characters and graphics drawing characters as the
     * corresponding UTF-8 sequences.
     *
     * @param utf8ByDefault Whether the terminal emulator should be in UTF-8
     *                      mode by default.
     */
    public void setDefaultUTF8Mode(boolean utf8ByDefault) {
        mDefaultUTF8Mode = utf8ByDefault;
        if (mEmulator == null) {
            return;
        }
        mEmulator.setDefaultUTF8Mode(utf8ByDefault);
    }

    /**
     * Reset the terminal emulator's state.
     */
    public void reset() {
        mEmulator.reset();
        notifyUpdate();
    }

    /**
     * Set a {@link FinishCallback} to be invoked once this terminal session is
     * finished.
     *
     * @param callback The {@link FinishCallback} to be invoked on finish.
     */
    public void setFinishCallback(FinishCallback callback) {
        mFinishCallback = callback;
    }

    /**
     * Finish this terminal session.  Frees resources used by the terminal
     * emulator.
     */
    public void finish() {
        mIsRunning = false;
        mTranscriptScreen.finish();
        if (mFinishCallback != null) {
            mFinishCallback.onSessionFinish(this);
        }
    }
}
