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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/**
 * A terminal emulator activity.
 */

public class Term extends Activity {
    /**
     * Set to true to add debugging code and logging.
     */
    public static final boolean DEBUG = false;

    /**
     * Set to true to log each character received from the remote process to the
     * android log, which makes it easier to debug some kinds of problems with
     * emulating escape sequences and control codes.
     */
    public static final boolean LOG_CHARACTERS_FLAG = DEBUG && false;

    /**
     * Set to true to log unknown escape sequences.
     */
    public static final boolean LOG_UNKNOWN_ESCAPE_SEQUENCES = DEBUG && false;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    public static final String LOG_TAG = "Term";

    /**
     * Our main view. Displays the emulated terminal screen.
     */
    private EmulatorView mEmulatorView;

    /**
     * The pseudo-teletype (pty) file descriptor that we use to communicate with
     * another process, typically a shell.
     */
    private FileDescriptor mTermFd;

    /**
     * Used to send data to the remote process.
     */
    private FileOutputStream mTermOut;

    /**
     * A key listener that tracks the modifier keys and allows the full ASCII
     * character set to be entered.
     */
    private TermKeyListener mKeyListener;

    /**
     * The name of our emulator view in the view resource.
     */
    private static final int EMULATOR_VIEW = R.id.emulatorView;

    private int mStatusBar = 0;
    private int mCursorStyle = 0;
    private int mCursorBlink = 0;
    private int mFontSize = 9;
    private int mColorId = 2;
    private int mControlKeyId = 0;
    private int mUseCookedIME = 0;

    private static final String STATUSBAR_KEY = "statusbar";
    private static final String CURSORSTYLE_KEY = "cursorstyle";
    private static final String CURSORBLINK_KEY = "cursorblink";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String COLOR_KEY = "color";
    private static final String CONTROLKEY_KEY = "controlkey";
    private static final String IME_KEY = "ime";
    private static final String SHELL_KEY = "shell";
    private static final String INITIALCOMMAND_KEY = "initialcommand";

    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;
    public static final int BLUE =  0xff344ebd;
    public static final int GREEN = 0xff00ff00;
    public static final int AMBER = 0xffffb651;
    public static final int RED =   0xffff0113;

    private static final int[][] COLOR_SCHEMES = {
        {BLACK, WHITE}, {WHITE, BLACK}, {WHITE, BLUE}, {GREEN, BLACK}, {AMBER, BLACK}, {RED, BLACK}};

    private static final int[] CONTROL_KEY_SCHEMES = {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_AT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN
    };
    private static final String[] CONTROL_KEY_NAME = {
        "Ball", "@", "Left-Alt", "Right-Alt", "Vol-Up", "Vol-Dn"
    };

    private int mControlKeyCode;

    private final static String DEFAULT_SHELL = "/system/bin/sh -";
    private String mShell;

    private final static String DEFAULT_INITIAL_COMMAND =
        "export PATH=/data/local/bin:$PATH";
    private String mInitialCommand;

    private SharedPreferences mPrefs;

    private final static int COPY_ALL_ID = 0;
    private final static int PASTE_ID = 1;

    private boolean mAlreadyStarted = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.e(Term.LOG_TAG, "onCreate");
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        readPrefs();

        setContentView(R.layout.term_activity);

        mEmulatorView = (EmulatorView) findViewById(EMULATOR_VIEW);

        startListening();

        mKeyListener = new TermKeyListener();

        mEmulatorView.setFocusable(true);
        mEmulatorView.setFocusableInTouchMode(true);
        mEmulatorView.requestFocus();
        mEmulatorView.register(this, mKeyListener);

        registerForContextMenu(mEmulatorView);

        updatePrefs();
        mAlreadyStarted = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTermFd != null) {
            Exec.close(mTermFd);
            mTermFd = null;
        }
    }

    private void startListening() {
        int[] processId = new int[1];

        createSubprocess(processId);
        final int procId = processId[0];

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            }
        };

        Runnable watchForDeath = new Runnable() {

            public void run() {
                Log.i(Term.LOG_TAG, "waiting for: " + procId);
               int result = Exec.waitFor(procId);
                Log.i(Term.LOG_TAG, "Subprocess exited: " + result);
                handler.sendEmptyMessage(result);
             }

        };
        Thread watcher = new Thread(watchForDeath);
        watcher.start();

        mTermOut = new FileOutputStream(mTermFd);

        mEmulatorView.initialize(mTermFd, mTermOut);

        sendInitialCommand();
    }

    private void sendInitialCommand() {
        String initialCommand = mInitialCommand;
        if (initialCommand == null || initialCommand.equals("")) {
            initialCommand = DEFAULT_INITIAL_COMMAND;
        }
        if (initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

    private void restart() {
        startActivity(getIntent());
        finish();
    }

    private void write(String data) {
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
        String shell = mShell;
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

    private void readPrefs() {
        mStatusBar = readIntPref(STATUSBAR_KEY, mStatusBar, 1);
        // mCursorStyle = readIntPref(CURSORSTYLE_KEY, mCursorStyle, 2);
        // mCursorBlink = readIntPref(CURSORBLINK_KEY, mCursorBlink, 1);
        mFontSize = readIntPref(FONTSIZE_KEY, mFontSize, 20);
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.length - 1);
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId,
                CONTROL_KEY_SCHEMES.length - 1);
        mUseCookedIME = readIntPref(IME_KEY, mUseCookedIME, 1);
        {
            String newShell = readStringPref(SHELL_KEY, mShell);
            if ((newShell == null) || ! newShell.equals(mShell)) {
                if (mShell != null) {
                    Log.i(Term.LOG_TAG, "New shell set. Restarting.");
                    restart();
                }
                mShell = newShell;
            }
        }
        {
            String newInitialCommand = readStringPref(INITIALCOMMAND_KEY,
                    mInitialCommand);
            if ((newInitialCommand == null)
                    || ! newInitialCommand.equals(mInitialCommand)) {
                if (mInitialCommand != null) {
                    Log.i(Term.LOG_TAG, "New initial command set. Restarting.");
                    restart();
                }
                mInitialCommand = newInitialCommand;
            }
        }
    }

    private void updatePrefs() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mEmulatorView.setTextSize((int) (mFontSize * metrics.density));
        mEmulatorView.setCursorStyle(mCursorStyle, mCursorBlink);
        mEmulatorView.setUseCookedIME(mUseCookedIME != 0);
        setColors();
        mControlKeyCode = CONTROL_KEY_SCHEMES[mControlKeyId];
        {
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            final int FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int desiredFlag = mStatusBar != 0 ? 0 : FULLSCREEN;
            if (desiredFlag != (params.flags & FULLSCREEN)) {
                if (mAlreadyStarted) {
                    // Can't switch to/from fullscreen after
                    // starting the activity.
                    restart();
                } else {
                    win.setFlags(desiredFlag, FULLSCREEN);
                }
            }
        }
    }

    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt(
                mPrefs.getString(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }

    private String readStringPref(String key, String defaultValue) {
        return mPrefs.getString(key, defaultValue);
    }

    public int getControlKeyCode() {
        return mControlKeyCode;
    }

    @Override
    public void onResume() {
        super.onResume();
        readPrefs();
        updatePrefs();
        mEmulatorView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mEmulatorView.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mEmulatorView.updateSize(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_preferences) {
            doPreferences();
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript();
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      menu.setHeaderTitle(R.string.edit_text);
      menu.add(0, COPY_ALL_ID, 0, R.string.copy_all);
      menu.add(0, PASTE_ID, 0,  R.string.paste);
      if (!canPaste()) {
          menu.getItem(PASTE_ID).setEnabled(false);
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
          switch (item.getItemId()) {
          case COPY_ALL_ID:
            doCopyAll();
            return true;
          case PASTE_ID:
            doPaste();
            return true;
          default:
            return super.onContextItemSelected(item);
          }
        }

    private boolean canPaste() {
        ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clip.hasText()) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }

    private void setColors() {
        int[] scheme = COLOR_SCHEMES[mColorId];
        mEmulatorView.setColors(scheme[0], scheme[1]);
    }

    private void doResetTerminal() {
        restart();
    }

    private void doEmailTranscript() {
        // Don't really want to supply an address, but
        // currently it's required, otherwise we get an
        // exception.
        String addr = "user@example.com";
        Intent intent =
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"
                        + addr));

        intent.putExtra("body", mEmulatorView.getTranscriptText().trim());
        startActivity(intent);
    }

    private void doCopyAll() {
        ClipboardManager clip = (ClipboardManager)
             getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setText(mEmulatorView.getTranscriptText().trim());
    }

    private void doPaste() {
        ClipboardManager clip = (ClipboardManager)
         getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence paste = clip.getText();
        byte[] utf8;
        try {
            utf8 = paste.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(Term.LOG_TAG, "UTF-8 encoding not found.");
            return;
        }
        try {
            mTermOut.write(utf8);
        } catch (IOException e) {
            Log.e(Term.LOG_TAG, "could not write paste text to terminal.");
        }
    }

    private void doDocumentKeys() {
        String controlKey = CONTROL_KEY_NAME[mControlKeyId];
        new AlertDialog.Builder(this).
            setTitle("Press " + controlKey + " and Key").
            setMessage(controlKey + " Space ==> Control-@ (NUL)\n"
                    + controlKey + " A..Z ==> Control-A..Z\n"
                    + controlKey + " 1 ==> Control-[ (ESC)\n"
                    + controlKey + " 5 ==> Control-_\n"
                    + controlKey + " . ==> Control-\\\n"
                    + controlKey + " 0 ==> Control-]\n"
                    + controlKey + " 6 ==> Control-^").
            show();
     }

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

    }
}


/**
 * An abstract screen interface. A terminal screen stores lines of text. (The
 * reason to abstract it is to allow different implementations, and to hide
 * implementation details from clients.)
 */
interface Screen {

    /**
     * Set line wrap flag for a given row. Affects how lines are logically
     * wrapped when changing screen size or converting to a transcript.
     */
    void setLineWrap(int row);

    /**
     * Store byte b into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param b ASCII character to store
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    void set(int x, int y, byte b, int foreColor, int backColor);

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     */
    void scroll(int topMargin, int bottomMargin, int foreColor, int backColor);

    /**
     * Block copy characters from one position in the screen to another. The two
     * positions can overlap. All characters of the source and destination must
     * be within the bounds of the screen, or else an InvalidParemeterException
     * will be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w width
     * @param h height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    void blockCopy(int sx, int sy, int w, int h, int dx, int dy);

    /**
     * Block set characters. All characters must be within the bounds of the
     * screen, or else and InvalidParemeterException will be thrown. Typically
     * this is called with a "val" argument of 32 to clear a block of
     * characters.
     *
     * @param sx source X
     * @param sy source Y
     * @param w width
     * @param h height
     * @param val value to set.
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    void blockSet(int sx, int sy, int w, int h, int val, int foreColor, int
            backColor);

    /**
     * Get the contents of the transcript buffer as a text string.
     *
     * @return the contents of the transcript buffer.
     */
    String getTranscriptText();

    /**
     * Resize the screen
     * @param columns
     * @param rows
     */
    void resize(int columns, int rows, int foreColor, int backColor);
}


/**
 * A TranscriptScreen is a screen that remembers data that's been scrolled. The
 * old data is stored in a ring buffer to minimize the amount of copying that
 * needs to be done. The transcript does its own drawing, to avoid having to
 * expose its internal data structures.
 */
class TranscriptScreen implements Screen {
    private static final String TAG = "TranscriptScreen";

    /**
     * The width of the transcript, in characters. Fixed at initialization.
     */
    private int mColumns;

    /**
     * The total number of rows in the transcript and the screen. Fixed at
     * initialization.
     */
    private int mTotalRows;

    /**
     * The number of rows in the active portion of the transcript. Doesn't
     * include the screen.
     */
    private int mActiveTranscriptRows;

    /**
     * Which row is currently the topmost line of the transcript. Used to
     * implement a circular buffer.
     */
    private int mHead;

    /**
     * The number of active rows, includes both the transcript and the screen.
     */
    private int mActiveRows;

    /**
     * The number of rows in the screen.
     */
    private int mScreenRows;

    /**
     * The data for both the screen and the transcript. The first mScreenRows *
     * mLineWidth characters are the screen, the rest are the transcript.
     * The low byte encodes the ASCII character, the high byte encodes the
     * foreground and background colors, plus underline and bold.
     */
    private char[] mData;

    /**
     * The data's stored as color-encoded chars, but the drawing routines require chars, so we
     * need a temporary buffer to hold a row's worth of characters.
     */
    private char[] mRowBuffer;

    /**
     * Flags that keep track of whether the current line logically wraps to the
     * next line. This is used when resizing the screen and when copying to the
     * clipboard or an email attachment
     */

    private boolean[] mLineWrap;

    /**
     * Create a transcript screen.
     *
     * @param columns the width of the screen in characters.
     * @param totalRows the height of the entire text area, in rows of text.
     * @param screenRows the height of just the screen, not including the
     *        transcript that holds lines that have scrolled off the top of the
     *        screen.
     */
    public TranscriptScreen(int columns, int totalRows, int screenRows,
            int foreColor, int backColor) {
        init(columns, totalRows, screenRows, foreColor, backColor);
    }

    private void init(int columns, int totalRows, int screenRows, int foreColor, int backColor) {
        mColumns = columns;
        mTotalRows = totalRows;
        mActiveTranscriptRows = 0;
        mHead = 0;
        mActiveRows = screenRows;
        mScreenRows = screenRows;
        int totalSize = columns * totalRows;
        mData = new char[totalSize];
        blockSet(0, 0, mColumns, mScreenRows, ' ', foreColor, backColor);
        mRowBuffer = new char[columns];
        mLineWrap = new boolean[totalRows];
        consistencyCheck();
   }

    /**
     * Convert a row value from the public external coordinate system to our
     * internal private coordinate system. External coordinate system:
     * -mActiveTranscriptRows to mScreenRows-1, with the screen being
     * 0..mScreenRows-1 Internal coordinate system: 0..mScreenRows-1 rows of
     * mData are the visible rows. mScreenRows..mActiveRows - 1 are the
     * transcript, stored as a circular buffer.
     *
     * @param row a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private
     *         coordinate system.
     */
    private int externalToInternalRow(int row) {
        if (row < -mActiveTranscriptRows || row >= mScreenRows) {
            String errorMessage = "externalToInternalRow "+ row +
                " " + mActiveTranscriptRows + " " + mScreenRows;
            Log.e(TAG, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        if (row >= 0) {
            return row; // This is a visible row.
        }
        return mScreenRows
                + ((mHead + mActiveTranscriptRows + row) % mActiveTranscriptRows);
    }

    private int getOffset(int externalLine) {
        return externalToInternalRow(externalLine) * mColumns;
    }

    private int getOffset(int x, int y) {
        return getOffset(y) + x;
    }

    public void setLineWrap(int row) {
        mLineWrap[externalToInternalRow(row)] = true;
    }

    /**
     * Store byte b into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param b ASCII character to store
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    public void set(int x, int y, byte b, int foreColor, int backColor) {
        mData[getOffset(x, y)] = encode(b, foreColor, backColor);
    }

    private char encode(int b, int foreColor, int backColor) {
        return (char) ((foreColor << 12) | (backColor << 8) | b);
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     */
    public void scroll(int topMargin, int bottomMargin, int foreColor,
            int backColor) {
        // Separate out reasons so that stack crawls help us
        // figure out which condition was violated.
        if (topMargin > bottomMargin - 1) {
            throw new IllegalArgumentException();
        }

        if (topMargin > mScreenRows - 1) {
            throw new IllegalArgumentException();
        }

        if (bottomMargin > mScreenRows) {
            throw new IllegalArgumentException();
        }

        // Adjust the transcript so that the last line of the transcript
        // is ready to receive the newly scrolled data
        consistencyCheck();
        int expansionRows = Math.min(1, mTotalRows - mActiveRows);
        int rollRows = 1 - expansionRows;
        mActiveRows += expansionRows;
        mActiveTranscriptRows += expansionRows;
        if (mActiveTranscriptRows > 0) {
            mHead = (mHead + rollRows) % mActiveTranscriptRows;
        }
        consistencyCheck();

        // Block move the scroll line to the transcript
        int topOffset = getOffset(topMargin);
        int destOffset = getOffset(-1);
        System.arraycopy(mData, topOffset, mData, destOffset, mColumns);

        int topLine = externalToInternalRow(topMargin);
        int destLine = externalToInternalRow(-1);
        System.arraycopy(mLineWrap, topLine, mLineWrap, destLine, 1);

        // Block move the scrolled data up
        int numScrollChars = (bottomMargin - topMargin - 1) * mColumns;
        System.arraycopy(mData, topOffset + mColumns, mData, topOffset,
                numScrollChars);
        int numScrollLines = (bottomMargin - topMargin - 1);
        System.arraycopy(mLineWrap, topLine + 1, mLineWrap, topLine,
                numScrollLines);

        // Erase the bottom line of the scroll region
        blockSet(0, bottomMargin - 1, mColumns, 1, ' ', foreColor, backColor);
        mLineWrap[externalToInternalRow(bottomMargin-1)] = false;
    }

    private void consistencyCheck() {
        checkPositive(mColumns);
        checkPositive(mTotalRows);
        checkRange(0, mActiveTranscriptRows, mTotalRows);
        if (mActiveTranscriptRows == 0) {
            checkEqual(mHead, 0);
        } else {
            checkRange(0, mHead, mActiveTranscriptRows-1);
        }
        checkEqual(mScreenRows + mActiveTranscriptRows, mActiveRows);
        checkRange(0, mScreenRows, mTotalRows);

        checkEqual(mTotalRows, mLineWrap.length);
        checkEqual(mTotalRows*mColumns, mData.length);
        checkEqual(mColumns, mRowBuffer.length);
    }

    private void checkPositive(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("checkPositive " + n);
        }
    }

    private void checkRange(int a, int b, int c) {
        if (a > b || b > c) {
            throw new IllegalArgumentException("checkRange " + a + " <= " + b + " <= " + c);
        }
    }

    private void checkEqual(int a, int b) {
        if (a != b) {
            throw new IllegalArgumentException("checkEqual " + a + " == " + b);
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two
     * positions can overlap. All characters of the source and destination must
     * be within the bounds of the screen, or else an InvalidParemeterException
     * will be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w width
     * @param h height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    public void blockCopy(int sx, int sy, int w, int h, int dx, int dy) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows
                || dx < 0 || dx + w > mColumns || dy < 0
                || dy + h > mScreenRows) {
            throw new IllegalArgumentException();
        }
        if (sy <= dy) {
            // Move in increasing order
            for (int y = 0; y < h; y++) {
                int srcOffset = getOffset(sx, sy + y);
                int dstOffset = getOffset(dx, dy + y);
                System.arraycopy(mData, srcOffset, mData, dstOffset, w);
            }
        } else {
            // Move in decreasing order
            for (int y = 0; y < h; y++) {
                int y2 = h - (y + 1);
                int srcOffset = getOffset(sx, sy + y2);
                int dstOffset = getOffset(dx, dy + y2);
                System.arraycopy(mData, srcOffset, mData, dstOffset, w);
            }
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the
     * screen, or else and InvalidParemeterException will be thrown. Typically
     * this is called with a "val" argument of 32 to clear a block of
     * characters.
     *
     * @param sx source X
     * @param sy source Y
     * @param w width
     * @param h height
     * @param val value to set.
     */
    public void blockSet(int sx, int sy, int w, int h, int val,
            int foreColor, int backColor) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
            throw new IllegalArgumentException();
        }
        char[] data = mData;
        char encodedVal = encode(val, foreColor, backColor);
        for (int y = 0; y < h; y++) {
            int offset = getOffset(sx, sy + y);
            for (int x = 0; x < w; x++) {
                data[offset + x] = encodedVal;
            }
        }
    }

    /**
     * Draw a row of text. Out-of-bounds rows are blank, not errors.
     *
     * @param row The row of text to draw.
     * @param canvas The canvas to draw to.
     * @param x The x coordinate origin of the drawing
     * @param y The y coordinate origin of the drawing
     * @param renderer The renderer to use to draw the text
     * @param cx the cursor X coordinate, -1 means don't draw it
     */
    public final void drawText(int row, Canvas canvas, float x, float y,
            TextRenderer renderer, int cx) {

        // Out-of-bounds rows are blank.
        if (row < -mActiveTranscriptRows || row >= mScreenRows) {
            return;
        }

        // Copy the data from the byte array to a char array so they can
        // be drawn.

        int offset = getOffset(row);
        char[] rowBuffer = mRowBuffer;
        char[] data = mData;
        int columns = mColumns;
        int lastColors = 0;
        int lastRunStart = -1;
        final int CURSOR_MASK = 0x10000;
        for (int i = 0; i < columns; i++) {
            char c = data[offset + i];
            int colors = (char) (c & 0xff00);
            if (cx == i) {
                // Set cursor background color:
                colors |= CURSOR_MASK;
            }
            rowBuffer[i] = (char) (c & 0x00ff);
            if (colors != lastColors) {
                if (lastRunStart >= 0) {
                    renderer.drawTextRun(canvas, x, y, lastRunStart, rowBuffer,
                            lastRunStart, i - lastRunStart,
                            (lastColors & CURSOR_MASK) != 0,
                            0xf & (lastColors >> 12), 0xf & (lastColors >> 8));
                }
                lastColors = colors;
                lastRunStart = i;
            }
        }
        if (lastRunStart >= 0) {
            renderer.drawTextRun(canvas, x, y, lastRunStart, rowBuffer,
                    lastRunStart, columns - lastRunStart,
                    (lastColors & CURSOR_MASK) != 0,
                    0xf & (lastColors >> 12), 0xf & (lastColors >> 8));
        }
     }

    /**
     * Get the count of active rows.
     *
     * @return the count of active rows.
     */
    public int getActiveRows() {
        return mActiveRows;
    }

    /**
     * Get the count of active transcript rows.
     *
     * @return the count of active transcript rows.
     */
    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }

    public String getTranscriptText() {
        return internalGetTranscriptText(true);
    }

    private String internalGetTranscriptText(boolean stripColors) {
        StringBuilder builder = new StringBuilder();
        char[] rowBuffer = mRowBuffer;
        char[] data = mData;
        int columns = mColumns;
        for (int row = -mActiveTranscriptRows; row < mScreenRows; row++) {
            int offset = getOffset(row);
            int lastPrintingChar = -1;
            for (int column = 0; column < columns; column++) {
                char c = data[offset + column];
                if (stripColors) {
                    c = (char) (c & 0xff);
                }
                if ((c & 0xff) != ' ') {
                    lastPrintingChar = column;
                }
                rowBuffer[column] = c;
            }
            if (mLineWrap[externalToInternalRow(row)]) {
                builder.append(rowBuffer, 0, columns);
            } else {
                builder.append(rowBuffer, 0, lastPrintingChar + 1);
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    public void resize(int columns, int rows, int foreColor, int backColor) {
        init(columns, mTotalRows, rows, foreColor, backColor);
    }
}

/**
 * Renders text into a screen. Contains all the terminal-specific knowlege and
 * state. Emulates a subset of the X Window System xterm terminal, which in turn
 * is an emulator for a subset of the Digital Equipment Corporation vt100
 * terminal. Missing functionality: text attributes (bold, underline, reverse
 * video, color) alternate screen cursor key and keypad escape sequences.
 */
class TerminalEmulator {

    /**
     * The cursor row. Numbered 0..mRows-1.
     */
    private int mCursorRow;

    /**
     * The cursor column. Numbered 0..mColumns-1.
     */
    private int mCursorCol;

    /**
     * The number of character rows in the terminal screen.
     */
    private int mRows;

    /**
     * The number of character columns in the terminal screen.
     */
    private int mColumns;

    /**
     * Used to send data to the remote process. Needed to implement the various
     * "report" escape sequences.
     */
    private FileOutputStream mTermOut;

    /**
     * Stores the characters that appear on the screen of the emulated terminal.
     */
    private Screen mScreen;

    /**
     * Keeps track of the current argument of the current escape sequence.
     * Ranges from 0 to MAX_ESCAPE_PARAMETERS-1. (Typically just 0 or 1.)
     */
    private int mArgIndex;

    /**
     * The number of parameter arguments. This name comes from the ANSI standard
     * for terminal escape codes.
     */
    private static final int MAX_ESCAPE_PARAMETERS = 16;

    /**
     * Holds the arguments of the current escape sequence.
     */
    private int[] mArgs = new int[MAX_ESCAPE_PARAMETERS];

    // Escape processing states:

    /**
     * Escape processing state: Not currently in an escape sequence.
     */
    private static final int ESC_NONE = 0;

    /**
     * Escape processing state: Have seen an ESC character
     */
    private static final int ESC = 1;

    /**
     * Escape processing state: Have seen ESC POUND
     */
    private static final int ESC_POUND = 2;

    /**
     * Escape processing state: Have seen ESC and a character-set-select char
     */
    private static final int ESC_SELECT_LEFT_PAREN = 3;

    /**
     * Escape processing state: Have seen ESC and a character-set-select char
     */
    private static final int ESC_SELECT_RIGHT_PAREN = 4;

    /**
     * Escape processing state: ESC [
     */
    private static final int ESC_LEFT_SQUARE_BRACKET = 5;

    /**
     * Escape processing state: ESC [ ?
     */
    private static final int ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK = 6;

    /**
     * True if the current escape sequence should continue, false if the current
     * escape sequence should be terminated. Used when parsing a single
     * character.
     */
    private boolean mContinueSequence;

    /**
     * The current state of the escape sequence state machine.
     */
    private int mEscapeState;

    /**
     * Saved state of the cursor row, Used to implement the save/restore cursor
     * position escape sequences.
     */
    private int mSavedCursorRow;

    /**
     * Saved state of the cursor column, Used to implement the save/restore
     * cursor position escape sequences.
     */
    private int mSavedCursorCol;

    // DecSet booleans

    /**
     * This mask indicates 132-column mode is set. (As opposed to 80-column
     * mode.)
     */
    private static final int K_132_COLUMN_MODE_MASK = 1 << 3;

    /**
     * This mask indicates that origin mode is set. (Cursor addressing is
     * relative to the absolute screen size, rather than the currently set top
     * and bottom margins.)
     */
    private static final int K_ORIGIN_MODE_MASK = 1 << 6;

    /**
     * This mask indicates that wraparound mode is set. (As opposed to
     * stop-at-right-column mode.)
     */
    private static final int K_WRAPAROUND_MODE_MASK = 1 << 7;

    /**
     * Holds multiple DECSET flags. The data is stored this way, rather than in
     * separate booleans, to make it easier to implement the save-and-restore
     * semantics. The various k*ModeMask masks can be used to extract and modify
     * the individual flags current states.
     */
    private int mDecFlags;

    /**
     * Saves away a snapshot of the DECSET flags. Used to implement save and
     * restore escape sequences.
     */
    private int mSavedDecFlags;

    // Modes set with Set Mode / Reset Mode

    /**
     * True if insert mode (as opposed to replace mode) is active. In insert
     * mode new characters are inserted, pushing existing text to the right.
     */
    private boolean mInsertMode;

    /**
     * Automatic newline mode. Configures whether pressing return on the
     * keyboard automatically generates a return as well. Not currently
     * implemented.
     */
    private boolean mAutomaticNewlineMode;

    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for
     * column i.
     */
    private boolean[] mTabStop;

    // The margins allow portions of the screen to be locked.

    /**
     * The top margin of the screen, for scrolling purposes. Ranges from 0 to
     * mRows-2.
     */
    private int mTopMargin;

    /**
     * The bottom margin of the screen, for scrolling purposes. Ranges from
     * mTopMargin + 2 to mRows. (Defines the first row after the scrolling
     * region.
     */
    private int mBottomMargin;

    /**
     * True if the next character to be emitted will be automatically wrapped to
     * the next line. Used to disambiguate the case where the cursor is
     * positioned on column mColumns-1.
     */
    private boolean mAboutToAutoWrap;

    /**
     * Used for debugging, counts how many chars have been processed.
     */
    private int mProcessedCharCount;

    /**
     * Foreground color, 0..7, mask with 8 for bold
     */
    private int mForeColor;

    /**
     * Background color, 0..7, mask with 8 for underline
     */
    private int mBackColor;

    private boolean mInverseColors;

    private boolean mbKeypadApplicationMode;

    private boolean mAlternateCharSet;

    /**
     * Construct a terminal emulator that uses the supplied screen
     *
     * @param screen the screen to render characters into.
     * @param columns the number of columns to emulate
     * @param rows the number of rows to emulate
     * @param termOut the output file descriptor that talks to the pseudo-tty.
     */
    public TerminalEmulator(Screen screen, int columns, int rows,
            FileOutputStream termOut) {
        mScreen = screen;
        mRows = rows;
        mColumns = columns;
        mTabStop = new boolean[mColumns];
        mTermOut = termOut;
        reset();
    }

    public void updateSize(int columns, int rows) {
        if (mRows == rows && mColumns == columns) {
            return;
        }
        if (columns <= 0) {
            throw new IllegalArgumentException("rows:" + columns);
        }

        if (rows <= 0) {
            throw new IllegalArgumentException("rows:" + rows);
        }

        String transcriptText = mScreen.getTranscriptText();

        mScreen.resize(columns, rows, mForeColor, mBackColor);

        if (mRows != rows) {
            mRows = rows;
            mTopMargin = 0;
            mBottomMargin = mRows;
        }
        if (mColumns != columns) {
            int oldColumns = mColumns;
            mColumns = columns;
            boolean[] oldTabStop = mTabStop;
            mTabStop = new boolean[mColumns];
            int toTransfer = Math.min(oldColumns, columns);
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer);
            while (mCursorCol >= columns) {
                mCursorCol -= columns;
                mCursorRow = Math.min(mBottomMargin-1, mCursorRow + 1);
            }
        }
        mCursorRow = 0;
        mCursorCol = 0;
        mAboutToAutoWrap = false;

        int end = transcriptText.length()-1;
        while ((end >= 0) && transcriptText.charAt(end) == '\n') {
            end--;
        }
        for(int i = 0; i <= end; i++) {
            byte c = (byte) transcriptText.charAt(i);
            if (c == '\n') {
                setCursorCol(0);
                doLinefeed();
            } else {
                emit(c);
            }
        }
    }

    /**
     * Get the cursor's current row.
     *
     * @return the cursor's current row.
     */
    public final int getCursorRow() {
        return mCursorRow;
    }

    /**
     * Get the cursor's current column.
     *
     * @return the cursor's current column.
     */
    public final int getCursorCol() {
        return mCursorCol;
    }

    public final boolean getKeypadApplicationMode() {
        return mbKeypadApplicationMode;
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < mColumns; i++) {
            mTabStop[i] = (i & 7) == 0 && i != 0;
        }
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param base the first index of the array to process
     * @param length the number of bytes in the array to process
     */
    public void append(byte[] buffer, int base, int length) {
        for (int i = 0; i < length; i++) {
            byte b = buffer[base + i];
            try {
                if (Term.LOG_CHARACTERS_FLAG) {
                    char printableB = (char) b;
                    if (b < 32 || b > 126) {
                        printableB = ' ';
                    }
                    Log.w(Term.LOG_TAG, "'" + Character.toString(printableB)
                            + "' (" + Integer.toString(b) + ")");
                }
                process(b);
                mProcessedCharCount++;
            } catch (Exception e) {
                Log.e(Term.LOG_TAG, "Exception while processing character "
                        + Integer.toString(mProcessedCharCount) + " code "
                        + Integer.toString(b), e);
            }
        }
    }

    private void process(byte b) {
        switch (b) {
        case 0: // NUL
            // Do nothing
            break;

        case 7: // BEL
            // Do nothing
            break;

        case 8: // BS
            setCursorCol(Math.max(0, mCursorCol - 1));
            break;

        case 9: // HT
            // Move to next tab stop, but not past edge of screen
            setCursorCol(nextTabStop(mCursorCol));
            break;

        case 13:
            setCursorCol(0);
            break;

        case 10: // CR
        case 11: // VT
        case 12: // LF
            doLinefeed();
            break;

        case 14: // SO:
            setAltCharSet(true);
            break;

        case 15: // SI:
            setAltCharSet(false);
            break;


        case 24: // CAN
        case 26: // SUB
            if (mEscapeState != ESC_NONE) {
                mEscapeState = ESC_NONE;
                emit((byte) 127);
            }
            break;

        case 27: // ESC
            // Always starts an escape sequence
            startEscapeSequence(ESC);
            break;

        case (byte) 0x9b: // CSI
            startEscapeSequence(ESC_LEFT_SQUARE_BRACKET);
            break;

        default:
            mContinueSequence = false;
            switch (mEscapeState) {
            case ESC_NONE:
                if (b >= 32) {
                    emit(b);
                }
                break;

            case ESC:
                doEsc(b);
                break;

            case ESC_POUND:
                doEscPound(b);
                break;

            case ESC_SELECT_LEFT_PAREN:
                doEscSelectLeftParen(b);
                break;

            case ESC_SELECT_RIGHT_PAREN:
                doEscSelectRightParen(b);
                break;

            case ESC_LEFT_SQUARE_BRACKET:
                doEscLeftSquareBracket(b);
                break;

            case ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK:
                doEscLSBQuest(b);
                break;

            default:
                unknownSequence(b);
                break;
            }
            if (!mContinueSequence) {
                mEscapeState = ESC_NONE;
            }
            break;
        }
    }

    private void setAltCharSet(boolean alternateCharSet) {
        mAlternateCharSet = alternateCharSet;
    }

    private int nextTabStop(int cursorCol) {
        for (int i = cursorCol; i < mColumns; i++) {
            if (mTabStop[i]) {
                return i;
            }
        }
        return mColumns - 1;
    }

    private void doEscLSBQuest(byte b) {
        int mask = getDecFlagsMask(getArg0(0));
        switch (b) {
        case 'h': // Esc [ ? Pn h - DECSET
            mDecFlags |= mask;
            break;

        case 'l': // Esc [ ? Pn l - DECRST
            mDecFlags &= ~mask;
            break;

        case 'r': // Esc [ ? Pn r - restore
            mDecFlags = (mDecFlags & ~mask) | (mSavedDecFlags & mask);
            break;

        case 's': // Esc [ ? Pn s - save
            mSavedDecFlags = (mSavedDecFlags & ~mask) | (mDecFlags & mask);
            break;

        default:
            parseArg(b);
            break;
        }

        // 132 column mode
        if ((mask & K_132_COLUMN_MODE_MASK) != 0) {
            // We don't actually set 132 cols, but we do want the
            // side effect of clearing the screen and homing the cursor.
            blockClear(0, 0, mColumns, mRows);
            setCursorRowCol(0, 0);
        }

        // origin mode
        if ((mask & K_ORIGIN_MODE_MASK) != 0) {
            // Home the cursor.
            setCursorPosition(0, 0);
        }
    }

    private int getDecFlagsMask(int argument) {
        if (argument >= 1 && argument <= 9) {
            return (1 << argument);
        }

        return 0;
    }

    private void startEscapeSequence(int escapeState) {
        mEscapeState = escapeState;
        mArgIndex = 0;
        for (int j = 0; j < MAX_ESCAPE_PARAMETERS; j++) {
            mArgs[j] = -1;
        }
    }

    private void doLinefeed() {
        int newCursorRow = mCursorRow + 1;
        if (newCursorRow >= mBottomMargin) {
            scroll();
            newCursorRow = mBottomMargin - 1;
        }
        setCursorRow(newCursorRow);
    }

    private void continueSequence() {
        mContinueSequence = true;
    }

    private void continueSequence(int state) {
        mEscapeState = state;
        mContinueSequence = true;
    }

    private void doEscSelectLeftParen(byte b) {
        doSelectCharSet(true, b);
    }

    private void doEscSelectRightParen(byte b) {
        doSelectCharSet(false, b);
    }

    private void doSelectCharSet(boolean isG0CharSet, byte b) {
        switch (b) {
        case 'A': // United Kingdom character set
            break;
        case 'B': // ASCII set
            break;
        case '0': // Special Graphics
            break;
        case '1': // Alternate character set
            break;
        case '2':
            break;
        default:
            unknownSequence(b);
        }
    }

    private void doEscPound(byte b) {
        switch (b) {
        case '8': // Esc # 8 - DECALN alignment test
            mScreen.blockSet(0, 0, mColumns, mRows, 'E',
                    getForeColor(), getBackColor());
            break;

        default:
            unknownSequence(b);
            break;
        }
    }

    private void doEsc(byte b) {
        switch (b) {
        case '#':
            continueSequence(ESC_POUND);
            break;

        case '(':
            continueSequence(ESC_SELECT_LEFT_PAREN);
            break;

        case ')':
            continueSequence(ESC_SELECT_RIGHT_PAREN);
            break;

        case '7': // DECSC save cursor
            mSavedCursorRow = mCursorRow;
            mSavedCursorCol = mCursorCol;
            break;

        case '8': // DECRC restore cursor
            setCursorRowCol(mSavedCursorRow, mSavedCursorCol);
            break;

        case 'D': // INDEX
            doLinefeed();
            break;

        case 'E': // NEL
            setCursorCol(0);
            doLinefeed();
            break;

        case 'F': // Cursor to lower-left corner of screen
            setCursorRowCol(0, mBottomMargin - 1);
            break;

        case 'H': // Tab set
            mTabStop[mCursorCol] = true;
            break;

        case 'M': // Reverse index
            if (mCursorRow == 0) {
                mScreen.blockCopy(0, mTopMargin + 1, mColumns, mBottomMargin
                        - (mTopMargin + 1), 0, mTopMargin);
                blockClear(0, mBottomMargin - 1, mColumns);
            } else {
                mCursorRow--;
            }

            break;

        case 'N': // SS2
            unimplementedSequence(b);
            break;

        case '0': // SS3
            unimplementedSequence(b);
            break;

        case 'P': // Device control string
            unimplementedSequence(b);
            break;

        case 'Z': // return terminal ID
            sendDeviceAttributes();
            break;

        case '[':
            continueSequence(ESC_LEFT_SQUARE_BRACKET);
            break;

        case '=': // DECKPAM
            mbKeypadApplicationMode = true;
            break;

        case '>' : // DECKPNM
            mbKeypadApplicationMode = false;
            break;

        default:
            unknownSequence(b);
            break;
        }
    }

    private void doEscLeftSquareBracket(byte b) {
        switch (b) {
        case '@': // ESC [ Pn @ - ICH Insert Characters
        {
            int charsAfterCursor = mColumns - mCursorCol;
            int charsToInsert = Math.min(getArg0(1), charsAfterCursor);
            int charsToMove = charsAfterCursor - charsToInsert;
            mScreen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1,
                    mCursorCol + charsToInsert, mCursorRow);
            blockClear(mCursorCol, mCursorRow, charsToInsert);
        }
            break;

        case 'A': // ESC [ Pn A - Cursor Up
            setCursorRow(Math.max(mTopMargin, mCursorRow - getArg0(1)));
            break;

        case 'B': // ESC [ Pn B - Cursor Down
            setCursorRow(Math.min(mBottomMargin - 1, mCursorRow + getArg0(1)));
            break;

        case 'C': // ESC [ Pn C - Cursor Right
            setCursorCol(Math.min(mColumns - 1, mCursorCol + getArg0(1)));
            break;

        case 'D': // ESC [ Pn D - Cursor Left
            setCursorCol(Math.max(0, mCursorCol - getArg0(1)));
            break;

        case 'G': // ESC [ Pn G - Cursor Horizontal Absolute
            setCursorCol(Math.min(Math.max(1, getArg0(1)), mColumns) - 1);
            break;

        case 'H': // ESC [ Pn ; H - Cursor Position
            setHorizontalVerticalPosition();
            break;

        case 'J': // ESC [ Pn J - Erase in Display
            switch (getArg0(0)) {
            case 0: // Clear below
                blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                blockClear(0, mCursorRow + 1, mColumns,
                        mBottomMargin - (mCursorRow + 1));
                break;

            case 1: // Erase from the start of the screen to the cursor.
                blockClear(0, mTopMargin, mColumns, mCursorRow - mTopMargin);
                blockClear(0, mCursorRow, mCursorCol + 1);
                break;

            case 2: // Clear all
                blockClear(0, mTopMargin, mColumns, mBottomMargin - mTopMargin);
                break;

            default:
                unknownSequence(b);
                break;
            }
            break;

        case 'K': // ESC [ Pn K - Erase in Line
            switch (getArg0(0)) {
            case 0: // Clear to right
                blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                break;

            case 1: // Erase start of line to cursor (including cursor)
                blockClear(0, mCursorRow, mCursorCol + 1);
                break;

            case 2: // Clear whole line
                blockClear(0, mCursorRow, mColumns);
                break;

            default:
                unknownSequence(b);
                break;
            }
            break;

        case 'L': // Insert Lines
        {
            int linesAfterCursor = mBottomMargin - mCursorRow;
            int linesToInsert = Math.min(getArg0(1), linesAfterCursor);
            int linesToMove = linesAfterCursor - linesToInsert;
            mScreen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0,
                    mCursorRow + linesToInsert);
            blockClear(0, mCursorRow, mColumns, linesToInsert);
        }
            break;

        case 'M': // Delete Lines
        {
            int linesAfterCursor = mBottomMargin - mCursorRow;
            int linesToDelete = Math.min(getArg0(1), linesAfterCursor);
            int linesToMove = linesAfterCursor - linesToDelete;
            mScreen.blockCopy(0, mCursorRow + linesToDelete, mColumns,
                    linesToMove, 0, mCursorRow);
            blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete);
        }
            break;

        case 'P': // Delete Characters
        {
            int charsAfterCursor = mColumns - mCursorCol;
            int charsToDelete = Math.min(getArg0(1), charsAfterCursor);
            int charsToMove = charsAfterCursor - charsToDelete;
            mScreen.blockCopy(mCursorCol + charsToDelete, mCursorRow,
                    charsToMove, 1, mCursorCol, mCursorRow);
            blockClear(mCursorCol + charsToMove, mCursorRow, charsToDelete);
        }
            break;

        case 'T': // Mouse tracking
            unimplementedSequence(b);
            break;

        case '?': // Esc [ ? -- start of a private mode set
            continueSequence(ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK);
            break;

        case 'c': // Send device attributes
            sendDeviceAttributes();
            break;

        case 'd': // ESC [ Pn d - Vert Position Absolute
            setCursorRow(Math.min(Math.max(1, getArg0(1)), mRows) - 1);
            break;

        case 'f': // Horizontal and Vertical Position
            setHorizontalVerticalPosition();
            break;

        case 'g': // Clear tab stop
            switch (getArg0(0)) {
            case 0:
                mTabStop[mCursorCol] = false;
                break;

            case 3:
                for (int i = 0; i < mColumns; i++) {
                    mTabStop[i] = false;
                }
                break;

            default:
                // Specified to have no effect.
                break;
            }
            break;

        case 'h': // Set Mode
            doSetMode(true);
            break;

        case 'l': // Reset Mode
            doSetMode(false);
            break;

        case 'm': // Esc [ Pn m - character attributes.
            selectGraphicRendition();
            break;

        case 'r': // Esc [ Pn ; Pn r - set top and bottom margins
        {
            // The top margin defaults to 1, the bottom margin
            // (unusually for arguments) defaults to mRows.
            //
            // The escape sequence numbers top 1..23, but we
            // number top 0..22.
            // The escape sequence numbers bottom 2..24, and
            // so do we (because we use a zero based numbering
            // scheme, but we store the first line below the
            // bottom-most scrolling line.
            // As a result, we adjust the top line by -1, but
            // we leave the bottom line alone.
            //
            // Also require that top + 2 <= bottom

            int top = Math.max(0, Math.min(getArg0(1) - 1, mRows - 2));
            int bottom = Math.max(top + 2, Math.min(getArg1(mRows), mRows));
            mTopMargin = top;
            mBottomMargin = bottom;

            // The cursor is placed in the home position
            setCursorRowCol(mTopMargin, 0);
        }
            break;

        default:
            parseArg(b);
            break;
        }
    }

    private void selectGraphicRendition() {
        for (int i = 0; i <= mArgIndex; i++) {
            int code = mArgs[i];
            if ( code < 0) {
                if (mArgIndex > 0) {
                    continue;
                } else {
                    code = 0;
                }
            }
            if (code == 0) { // reset
                mInverseColors = false;
                mForeColor = 7;
                mBackColor = 0;
            } else if (code == 1) { // bold
                mForeColor |= 0x8;
            } else if (code == 4) { // underscore
                mBackColor |= 0x8;
            } else if (code == 7) { // inverse
                mInverseColors = true;
            } else if (code >= 30 && code <= 37) { // foreground color
                mForeColor = (mForeColor & 0x8) | (code - 30);
            } else if (code >= 40 && code <= 47) { // background color
                mBackColor = (mBackColor & 0x8) | (code - 40);
            } else {
                if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
                    Log.w(Term.LOG_TAG, String.format("SGR unknown code %d", code));
                }
            }
        }
    }

    private void blockClear(int sx, int sy, int w) {
        blockClear(sx, sy, w, 1);
    }

    private void blockClear(int sx, int sy, int w, int h) {
        mScreen.blockSet(sx, sy, w, h, ' ', getForeColor(), getBackColor());
    }

    private int getForeColor() {
        return mInverseColors ?
                ((mBackColor & 0x7) | (mForeColor & 0x8)) : mForeColor;
    }

    private int getBackColor() {
        return mInverseColors ?
                ((mForeColor & 0x7) | (mBackColor & 0x8)) : mBackColor;
    }

    private void doSetMode(boolean newValue) {
        int modeBit = getArg0(0);
        switch (modeBit) {
        case 4:
            mInsertMode = newValue;
            break;

        case 20:
            mAutomaticNewlineMode = newValue;
            break;

        default:
            unknownParameter(modeBit);
            break;
        }
    }

    private void setHorizontalVerticalPosition() {

        // Parameters are Row ; Column

        setCursorPosition(getArg1(1) - 1, getArg0(1) - 1);
    }

    private void setCursorPosition(int x, int y) {
        int effectiveTopMargin = 0;
        int effectiveBottomMargin = mRows;
        if ((mDecFlags & K_ORIGIN_MODE_MASK) != 0) {
            effectiveTopMargin = mTopMargin;
            effectiveBottomMargin = mBottomMargin;
        }
        int newRow =
                Math.max(effectiveTopMargin, Math.min(effectiveTopMargin + y,
                        effectiveBottomMargin - 1));
        int newCol = Math.max(0, Math.min(x, mColumns - 1));
        setCursorRowCol(newRow, newCol);
    }

    private void sendDeviceAttributes() {
        // This identifies us as a DEC vt100 with advanced
        // video options. This is what the xterm terminal
        // emulator sends.
        byte[] attributes =
                {
                /* VT100 */
                 (byte) 27, (byte) '[', (byte) '?', (byte) '1',
                 (byte) ';', (byte) '2', (byte) 'c'

                /* VT220
                (byte) 27, (byte) '[', (byte) '?', (byte) '6',
                (byte) '0',  (byte) ';',
                (byte) '1',  (byte) ';',
                (byte) '2',  (byte) ';',
                (byte) '6',  (byte) ';',
                (byte) '8',  (byte) ';',
                (byte) '9',  (byte) ';',
                (byte) '1',  (byte) '5', (byte) ';',
                (byte) 'c'
                */
                };

        write(attributes);
    }

    /**
     * Send data to the shell process
     * @param data
     */
    private void write(byte[] data) {
        try {
            mTermOut.write(data);
            mTermOut.flush();
        } catch (IOException e) {
            // Ignore exception
            // We don't really care if the receiver isn't listening.
            // We just make a best effort to answer the query.
        }
    }

    private void scroll() {
        mScreen.scroll(mTopMargin, mBottomMargin,
                getForeColor(), getBackColor());
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     * @param b The next ASCII character of the paramater sequence.
     */
    private void parseArg(byte b) {
        if (b >= '0' && b <= '9') {
            if (mArgIndex < mArgs.length) {
                int oldValue = mArgs[mArgIndex];
                int thisDigit = b - '0';
                int value;
                if (oldValue >= 0) {
                    value = oldValue * 10 + thisDigit;
                } else {
                    value = thisDigit;
                }
                mArgs[mArgIndex] = value;
            }
            continueSequence();
        } else if (b == ';') {
            if (mArgIndex < mArgs.length) {
                mArgIndex++;
            }
            continueSequence();
        } else {
            unknownSequence(b);
        }
    }

    private int getArg0(int defaultValue) {
        return getArg(0, defaultValue);
    }

    private int getArg1(int defaultValue) {
        return getArg(1, defaultValue);
    }

    private int getArg(int index, int defaultValue) {
        int result = mArgs[index];
        if (result < 0) {
            result = defaultValue;
        }
        return result;
    }

    private void unimplementedSequence(byte b) {
        if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unimplemented", b);
        }
        finishSequence();
    }

    private void unknownSequence(byte b) {
        if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unknown", b);
        }
        finishSequence();
    }

    private void unknownParameter(int parameter) {
        if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            StringBuilder buf = new StringBuilder();
            buf.append("Unknown parameter");
            buf.append(parameter);
            logError(buf.toString());
        }
    }

    private void logError(String errorType, byte b) {
        if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            StringBuilder buf = new StringBuilder();
            buf.append(errorType);
            buf.append(" sequence ");
            buf.append(" EscapeState: ");
            buf.append(mEscapeState);
            buf.append(" char: '");
            buf.append((char) b);
            buf.append("' (");
            buf.append(b);
            buf.append(")");
            boolean firstArg = true;
            for (int i = 0; i <= mArgIndex; i++) {
                int value = mArgs[i];
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false;
                        buf.append("args = ");
                    }
                    buf.append(String.format("%d; ", value));
                }
            }
            logError(buf.toString());
        }
    }

    private void logError(String error) {
        if (Term.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            Log.e(Term.LOG_TAG, error);
        }
        finishSequence();
    }

    private void finishSequence() {
        mEscapeState = ESC_NONE;
    }

    private boolean autoWrapEnabled() {
        // Always enable auto wrap, because it's useful on a small screen
        return true;
        // return (mDecFlags & K_WRAPAROUND_MODE_MASK) != 0;
    }

    /**
     * Send an ASCII character to the screen.
     *
     * @param b the ASCII character to display.
     */
    private void emit(byte b) {
        boolean autoWrap = autoWrapEnabled();

        if (autoWrap) {
            if (mCursorCol == mColumns - 1 && mAboutToAutoWrap) {
                mScreen.setLineWrap(mCursorRow);
                mCursorCol = 0;
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++;
                } else {
                    scroll();
                }
            }
        }

        if (mInsertMode) { // Move character to right one space
            int destCol = mCursorCol + 1;
            if (destCol < mColumns) {
                mScreen.blockCopy(mCursorCol, mCursorRow, mColumns - destCol,
                        1, destCol, mCursorRow);
            }
        }

        mScreen.set(mCursorCol, mCursorRow, b, getForeColor(), getBackColor());

        if (autoWrap) {
            mAboutToAutoWrap = (mCursorCol == mColumns - 1);
        }

        mCursorCol = Math.min(mCursorCol + 1, mColumns - 1);
    }

    private void setCursorRow(int row) {
        mCursorRow = row;
        mAboutToAutoWrap = false;
    }

    private void setCursorCol(int col) {
        mCursorCol = col;
        mAboutToAutoWrap = false;
    }

    private void setCursorRowCol(int row, int col) {
        mCursorRow = Math.min(row, mRows-1);
        mCursorCol = Math.min(col, mColumns-1);
        mAboutToAutoWrap = false;
    }

    /**
     * Reset the terminal emulator to its initial state.
     */
    public void reset() {
        mCursorRow = 0;
        mCursorCol = 0;
        mArgIndex = 0;
        mContinueSequence = false;
        mEscapeState = ESC_NONE;
        mSavedCursorRow = 0;
        mSavedCursorCol = 0;
        mDecFlags = 0;
        mSavedDecFlags = 0;
        mInsertMode = false;
        mAutomaticNewlineMode = false;
        mTopMargin = 0;
        mBottomMargin = mRows;
        mAboutToAutoWrap = false;
        mForeColor = 7;
        mBackColor = 0;
        mInverseColors = false;
        mbKeypadApplicationMode = false;
        mAlternateCharSet = false;
        // mProcessedCharCount is preserved unchanged.
        setDefaultTabStops();
        blockClear(0, 0, mColumns, mRows);
    }

    public String getTranscriptText() {
        return mScreen.getTranscriptText();
    }
}

/**
 * Text renderer interface
 */

interface TextRenderer {
    int getCharacterWidth();
    int getCharacterHeight();
    void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, char[] text,
            int index, int count, boolean cursor, int foreColor, int backColor);
}

abstract class BaseTextRenderer implements TextRenderer {
    protected int[] mForePaint = {
            0xff000000, // Black
            0xffff0000, // Red
            0xff00ff00, // green
            0xffffff00, // yellow
            0xff0000ff, // blue
            0xffff00ff, // magenta
            0xff00ffff, // cyan
            0xffffffff  // white -- is overridden by constructor
    };
    protected int[] mBackPaint = {
            0xff000000, // Black -- is overridden by constructor
            0xffcc0000, // Red
            0xff00cc00, // green
            0xffcccc00, // yellow
            0xff0000cc, // blue
            0xffff00cc, // magenta
            0xff00cccc, // cyan
            0xffffffff  // white
    };
    protected final static int mCursorPaint = 0xff808080;

    public BaseTextRenderer(int forePaintColor, int backPaintColor) {
        mForePaint[7] = forePaintColor;
        mBackPaint[0] = backPaintColor;

    }
}

class Bitmap4x8FontRenderer extends BaseTextRenderer {
    private final static int kCharacterWidth = 4;
    private final static int kCharacterHeight = 8;
    private Bitmap mFont;
    private int mCurrentForeColor;
    private int mCurrentBackColor;
    private float[] mColorMatrix;
    private Paint mPaint;
    private static final float BYTE_SCALE = 1.0f / 255.0f;

    public Bitmap4x8FontRenderer(Resources resources,
            int forePaintColor, int backPaintColor) {
        super(forePaintColor, backPaintColor);
        mFont = BitmapFactory.decodeResource(resources,
                R.drawable.atari_small);
        mPaint = new Paint();
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public int getCharacterWidth() {
        return kCharacterWidth;
    }

    public int getCharacterHeight() {
        return kCharacterHeight;
    }

    public void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor) {
        setColorMatrix(mForePaint[foreColor & 7],
                cursor ? mCursorPaint : mBackPaint[backColor & 7]);
        int destX = (int) x + kCharacterWidth * lineOffset;
        int destY = (int) y;
        Rect srcRect = new Rect();
        Rect destRect = new Rect();
        destRect.top = (destY - kCharacterHeight);
        destRect.bottom = destY;
        for(int i = 0; i < count; i++) {
            char c = text[i + index];
            if ((cursor || (c != 32)) && (c < 128)) {
                int cellX = c & 31;
                int cellY = (c >> 5) & 3;
                int srcX = cellX * kCharacterWidth;
                int srcY = cellY * kCharacterHeight;
                srcRect.set(srcX, srcY,
                        srcX + kCharacterWidth, srcY + kCharacterHeight);
                destRect.left = destX;
                destRect.right = destX + kCharacterWidth;
                canvas.drawBitmap(mFont, srcRect, destRect, mPaint);
            }
            destX += kCharacterWidth;
        }
    }

    private void setColorMatrix(int foreColor, int backColor) {
        if ((foreColor != mCurrentForeColor)
                || (backColor != mCurrentBackColor)
                || (mColorMatrix == null)) {
            mCurrentForeColor = foreColor;
            mCurrentBackColor = backColor;
            if (mColorMatrix == null) {
                mColorMatrix = new float[20];
                mColorMatrix[18] = 1.0f; // Just copy Alpha
            }
            for (int component = 0; component < 3; component++) {
                int rightShift = (2 - component) << 3;
                int fore = 0xff & (foreColor >> rightShift);
                int back = 0xff & (backColor >> rightShift);
                int delta = back - fore;
                mColorMatrix[component * 6] = delta * BYTE_SCALE;
                mColorMatrix[component * 5 + 4] = fore;
            }
            mPaint.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
        }
    }
}

class PaintRenderer extends BaseTextRenderer {
    public PaintRenderer(int fontSize, int forePaintColor, int backPaintColor) {
        super(forePaintColor, backPaintColor);
        mTextPaint = new Paint();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) Math.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) Math.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = (int) mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
    }

    public void drawTextRun(Canvas canvas, float x, float y, int lineOffset,
            char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor) {
        if (cursor) {
            mTextPaint.setColor(mCursorPaint);
        } else {
            mTextPaint.setColor(mBackPaint[backColor & 0x7]);
        }
        float left = x + lineOffset * mCharWidth;
        canvas.drawRect(left, y + mCharAscent,
                left + count * mCharWidth, y + mCharDescent,
                mTextPaint);
        boolean bold = ( foreColor & 0x8 ) != 0;
        boolean underline = (backColor & 0x8) != 0;
        if (bold) {
            mTextPaint.setFakeBoldText(true);
        }
        if (underline) {
            mTextPaint.setUnderlineText(true);
        }
        mTextPaint.setColor(mForePaint[foreColor & 0x7]);
        canvas.drawText(text, index, count, left, y, mTextPaint);
        if (bold) {
            mTextPaint.setFakeBoldText(false);
        }
        if (underline) {
            mTextPaint.setUnderlineText(false);
        }
    }

    public int getCharacterHeight() {
        return mCharHeight;
    }

    public int getCharacterWidth() {
        return mCharWidth;
    }


    private Paint mTextPaint;
    private int mCharWidth;
    private int mCharHeight;
    private int mCharAscent;
    private int mCharDescent;
    private static final char[] EXAMPLE_CHAR = {'X'};
    }

/**
 * A multi-thread-safe produce-consumer byte array.
 * Only allows one producer and one consumer.
 */

class ByteQueue {
    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public int getBytesAvailable() {
        synchronized(this) {
            return mStoredBytes;
        }
    }

    public int read(byte[] buffer, int offset, int length)
        throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return 0;
        }
        synchronized(this) {
            while (mStoredBytes == 0) {
                wait();
            }
            int totalRead = 0;
            int bufferLength = mBuffer.length;
            boolean wasFull = bufferLength == mStoredBytes;
            while (length > 0 && mStoredBytes > 0) {
                int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
                int bytesToCopy = Math.min(length, oneRun);
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
                mHead += bytesToCopy;
                if (mHead >= bufferLength) {
                    mHead = 0;
                }
                mStoredBytes -= bytesToCopy;
                length -= bytesToCopy;
                offset += bytesToCopy;
                totalRead += bytesToCopy;
            }
            if (wasFull) {
                notify();
            }
            return totalRead;
        }
    }

    public void write(byte[] buffer, int offset, int length)
    throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return;
        }
        synchronized(this) {
            int bufferLength = mBuffer.length;
            boolean wasEmpty = mStoredBytes == 0;
            while (length > 0) {
                while(bufferLength == mStoredBytes) {
                    wait();
                }
                int tail = mHead + mStoredBytes;
                int oneRun;
                if (tail >= bufferLength) {
                    tail = tail - bufferLength;
                    oneRun = mHead - tail;
                } else {
                    oneRun = bufferLength - tail;
                }
                int bytesToCopy = Math.min(oneRun, length);
                System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
                offset += bytesToCopy;
                mStoredBytes += bytesToCopy;
                length -= bytesToCopy;
            }
            if (wasEmpty) {
                notify();
            }
        }
    }

    private byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
}
/**
 * A view on a transcript and a terminal emulator. Displays the text of the
 * transcript and the current cursor position of the terminal emulator.
 */
class EmulatorView extends View implements GestureDetector.OnGestureListener {

    private final String TAG = "EmulatorView";
    private final boolean LOG_KEY_EVENTS = Term.DEBUG && false;

    private Term mTerm;

    /**
     * We defer some initialization until we have been layed out in the view
     * hierarchy. The boolean tracks when we know what our size is.
     */
    private boolean mKnownSize;

    private int mVisibleWidth;
    private int mVisibleHeight;
    private Rect mVisibleRect = new Rect();

    /**
     * Our transcript. Contains the screen and the transcript.
     */
    private TranscriptScreen mTranscriptScreen;

    /**
     * Number of rows in the transcript.
     */
    private static final int TRANSCRIPT_ROWS = 10000;

    /**
     * Total width of each character, in pixels
     */
    private int mCharacterWidth;

    /**
     * Total height of each character, in pixels
     */
    private int mCharacterHeight;

    /**
     * Used to render text
     */
    private TextRenderer mTextRenderer;

    /**
     * Text size. Zero means 4 x 8 font.
     */
    private int mTextSize;

    private int mCursorStyle;
    private int mCursorBlink;

    /**
     * Foreground color.
     */
    private int mForeground;

    /**
     * Background color.
     */
    private int mBackground;

    /**
     * Used to paint the cursor
     */
    private Paint mCursorPaint;

    private Paint mBackgroundPaint;

    private boolean mUseCookedIme;

    /**
     * Our terminal emulator. We use this to get the current cursor position.
     */
    private TerminalEmulator mEmulator;

    /**
     * The number of rows of text to display.
     */
    private int mRows;

    /**
     * The number of columns of text to display.
     */
    private int mColumns;

    /**
     * The number of columns that are visible on the display.
     */

    private int mVisibleColumns;

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0
     */
    private int mTopRow;

    private int mLeftColumn;

    private FileDescriptor mTermFd;
    /**
     * Used to receive data from the remote process.
     */
    private FileInputStream mTermIn;

    private FileOutputStream mTermOut;

    private ByteQueue mByteQueue;

    /**
     * Used to temporarily hold data received from the remote process. Allocated
     * once and used permanently to minimize heap thrashing.
     */
    private byte[] mReceiveBuffer;

    /**
     * Our private message id, which we use to receive new input from the
     * remote process.
     */
    private static final int UPDATE = 1;

    private static final int SCREEN_CHECK_PERIOD = 1000;
    private static final int CURSOR_BLINK_PERIOD = 1000;

    private boolean mCursorVisible = true;

    /**
     * Used to poll if the view has changed size. Wish there was a better way to do this.
     */
    private Runnable mCheckSize = new Runnable() {

        public void run() {
            updateSize(false);
            mHandler.postDelayed(this, SCREEN_CHECK_PERIOD);
        }
    };

    private Runnable mBlinkCursor = new Runnable() {
        public void run() {
            if (mCursorBlink != 0) {
                mCursorVisible = ! mCursorVisible;
                mHandler.postDelayed(this, CURSOR_BLINK_PERIOD);
            } else {
                mCursorVisible = true;
            }
            // Perhaps just invalidate the character with the cursor.
            invalidate();
        }
    };

    /**
     * Thread that polls for input from the remote process
     */

    private Thread mPollingThread;

    private GestureDetector mGestureDetector;
    private float mScrollRemainder;
    private TermKeyListener mKeyListener;

    /**
     * Our message handler class. Implements a periodic callback.
     */
    private final Handler mHandler = new Handler() {
        /**
         * Handle the callback message. Call our enclosing class's update
         * method.
         *
         * @param msg The callback message.
         */
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UPDATE) {
                update();
            }
        }
    };

    public EmulatorView(Context context) {
        super(context);
        commonConstructor();
    }

    public void onResume() {
        updateSize(false);
        mHandler.postDelayed(mCheckSize, SCREEN_CHECK_PERIOD);
        if (mCursorBlink != 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        }
    }

    public void onPause() {
        mHandler.removeCallbacks(mCheckSize);
        if (mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
    }

    public void register(Term term, TermKeyListener listener) {
        mTerm = term;
        mKeyListener = listener;
    }

    public void setColors(int foreground, int background) {
        mForeground = foreground;
        mBackground = background;
        updateText();
    }

    public String getTranscriptText() {
        return mEmulator.getTranscriptText();
    }

    public void resetTerminal() {
        mEmulator.reset();
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = mUseCookedIme ?
                EditorInfo.TYPE_CLASS_TEXT :
                EditorInfo.TYPE_NULL;
        return new BaseInputConnection(this, false) {

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                sendText(text);
                return true;
            }

            @Override
            public boolean performEditorAction(int actionCode) {
                if(actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // The "return" key has been pressed on the IME.
                    sendText("\n");
                    return true;
                }
                return false;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Some keys are sent here rather than to commitText.
                    // In particular, del and the digit keys are sent here.
                    // (And I have reports that the HTC Magic also sends Return here.)
                    // As a bit of defensive programming, handle every
                    // key with an ASCII meaning.
                    int keyCode = event.getKeyCode();
                    if (keyCode >= 0 && keyCode < KEYCODE_CHARS.length()) {
                        char c = KEYCODE_CHARS.charAt(keyCode);
                        if (c > 0) {
                            sendChar(c);
                        } else {
                            // Handle IME arrow key events
                            switch (keyCode) {
                              case KeyEvent.KEYCODE_DPAD_UP:      // Up Arrow
                              case KeyEvent.KEYCODE_DPAD_DOWN:    // Down Arrow
                              case KeyEvent.KEYCODE_DPAD_LEFT:    // Left Arrow
                              case KeyEvent.KEYCODE_DPAD_RIGHT:   // Right Arrow
                                super.sendKeyEvent(event);
                                break;
                              default:
                                break;
                            }  // switch (keyCode)
                        }
                    }
                }
                return true;
            }

            private final String KEYCODE_CHARS =
                "\000\000\000\000\000\000\000" + "0123456789*#"
                + "\000\000\000\000\000\000\000\000\000\000"
                + "abcdefghijklmnopqrstuvwxyz,."
                + "\000\000\000\000"
                + "\011 "   // tab, space
                + "\000\000\000" // sym .. envelope
                + "\015\177" // enter, del
                + "`-=[]\\;'/@"
                + "\000\000\000"
                + "+";

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                return true;
            }

            @Override
            public boolean setSelection(int start, int end) {
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (leftLength > 0) {
                    for (int i = 0; i < leftLength; i++) {
                        sendKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                    }
                } else if ((leftLength == 0) && (rightLength == 0)) {
                    // Delete key held down / repeating
                    sendKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                }
                // TODO: handle forward deletes.
                return true;
            }

            private void sendChar(int c) {
                try {
                    mapAndSend(c);
                } catch (IOException ex) {

                }
            }
            private void sendText(CharSequence text) {
                int n = text.length();
                try {
                    for(int i = 0; i < n; i++) {
                        char c = text.charAt(i);
                        mapAndSend(c);
                    }
                } catch (IOException e) {
                }
            }

            private void mapAndSend(int c) throws IOException {
                mTermOut.write(
                        mKeyListener.mapControlChar(c));
            }
        };
    }

    public boolean getKeypadApplicationMode() {
        return mEmulator.getKeypadApplicationMode();
    }

    public EmulatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmulatorView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        // TypedArray a =
        //        context.obtainStyledAttributes(android.R.styleable.View);
        // initializeScrollbars(a);
        // a.recycle();
        commonConstructor();
    }

    private void commonConstructor() {
        mTextRenderer = null;
        mCursorPaint = new Paint();
        mCursorPaint.setARGB(255,128,128,128);
        mBackgroundPaint = new Paint();
        mTopRow = 0;
        mLeftColumn = 0;
        mGestureDetector = new GestureDetector(this);
        // mGestureDetector.setIsLongpressEnabled(false);
        setVerticalScrollBarEnabled(true);
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mTranscriptScreen.getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mTranscriptScreen.getActiveRows() + mTopRow - mRows;
    }

    /**
     * Call this to initialize the view.
     *
     * @param termFd the file descriptor
     * @param termOut the output stream for the pseudo-teletype
     */
    public void initialize(FileDescriptor termFd, FileOutputStream termOut) {
        mTermOut = termOut;
        mTermFd = termFd;
        mTextSize = 10;
        mForeground = Term.WHITE;
        mBackground = Term.BLACK;
        updateText();
        mTermIn = new FileInputStream(mTermFd);
        mReceiveBuffer = new byte[4 * 1024];
        mByteQueue = new ByteQueue(4 * 1024);
    }

    /**
     * Accept a sequence of bytes (typically from the pseudo-tty) and process
     * them.
     *
     * @param buffer a byte array containing bytes to be processed
     * @param base the index of the first byte in the buffer to process
     * @param length the number of bytes to process
     */
    public void append(byte[] buffer, int base, int length) {
        mEmulator.append(buffer, base, length);
        ensureCursorVisible();
        invalidate();
    }

    /**
     * Page the terminal view (scroll it up or down by delta screenfulls.)
     *
     * @param delta the number of screens to scroll. Positive means scroll down,
     *        negative means scroll up.
     */
    public void page(int delta) {
        mTopRow =
                Math.min(0, Math.max(-(mTranscriptScreen
                        .getActiveTranscriptRows()), mTopRow + mRows * delta));
        invalidate();
    }

    /**
     * Page the terminal view horizontally.
     *
     * @param deltaColumns the number of columns to scroll. Positive scrolls to
     *        the right.
     */
    public void pageHorizontal(int deltaColumns) {
        mLeftColumn =
                Math.max(0, Math.min(mLeftColumn + deltaColumns, mColumns
                        - mVisibleColumns));
        invalidate();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns
     *
     * @param fontSize the new font size, in pixels.
     */
    public void setTextSize(int fontSize) {
        mTextSize = fontSize;
        updateText();
    }

    public void setCursorStyle(int style, int blink) {
        mCursorStyle = style;
        if (blink != 0 && mCursorBlink == 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        } else if (blink == 0 && mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
        mCursorBlink = blink;
    }

    public void setUseCookedIME(boolean useRawIME) {
        mUseCookedIme = useRawIME;
    }

    // Begin GestureDetector.OnGestureListener methods

    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    public void onLongPress(MotionEvent e) {
        showContextMenu();
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2,
            float distanceX, float distanceY) {
        distanceY += mScrollRemainder;
        int deltaRows = (int) (distanceY / mCharacterHeight);
        mScrollRemainder = distanceY - deltaRows * mCharacterHeight;
        mTopRow =
            Math.min(0, Math.max(-(mTranscriptScreen
                    .getActiveTranscriptRows()), mTopRow + deltaRows));
        invalidate();

        return true;
   }

    public void onSingleTapConfirmed(MotionEvent e) {
    }

    public boolean onJumpTapDown(MotionEvent e1, MotionEvent e2) {
       // Scroll to bottom
       mTopRow = 0;
       invalidate();
       return true;
    }

    public boolean onJumpTapUp(MotionEvent e1, MotionEvent e2) {
        // Scroll to top
        mTopRow = -mTranscriptScreen.getActiveTranscriptRows();
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        // TODO: add animation man's (non animated) fling
        mScrollRemainder = 0.0f;
        onScroll(e1, e2, 2 * velocityX, -2 * velocityY);
        return true;
    }

    public void onShowPress(MotionEvent e) {
    }

    public boolean onDown(MotionEvent e) {
        mScrollRemainder = 0.0f;
        return true;
    }

    // End GestureDetector.OnGestureListener methods

    @Override public boolean onTouchEvent(MotionEvent ev) {
        return mGestureDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyDown " + keyCode);
        }
        if (handleControlKey(keyCode, true)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyDown(keyCode, event);
        } else if (handleDPad(keyCode, true)) {
            return true;
        }

        // Translate the keyCode into an ASCII character.
        int letter = mKeyListener.keyDown(keyCode, event);

        if (letter >= 0) {
            try {
                mTermOut.write(letter);
            } catch (IOException e) {
                // Ignore I/O exceptions
            }
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyUp " + keyCode);
        }
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyUp(keyCode, event);
        } else if (handleDPad(keyCode, false)) {
            return true;
        }

        mKeyListener.keyUp(keyCode);
        return true;
    }


    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mTerm.getControlKeyCode()) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey " + keyCode);
            }
            mKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    /**
     * Handle dpad left-right-up-down events. Don't handle
     * dpad-center, that's our control key.
     * @param keyCode
     * @param down
     */
    private boolean handleDPad(int keyCode, boolean down) {
        if (keyCode < KeyEvent.KEYCODE_DPAD_UP ||
                keyCode > KeyEvent.KEYCODE_DPAD_CENTER) {
            return false;
        }

        if (down) {
            try {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    mTermOut.write('\r');
                } else {
                    char code;
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        code = 'A';
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        code = 'B';
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        code = 'D';
                        break;
                    default:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        code = 'C';
                        break;
                    }
                    mTermOut.write(27); // ESC
                    if (getKeypadApplicationMode()) {
                        mTermOut.write('O');
                    } else {
                        mTermOut.write('[');
                    }
                    mTermOut.write(code);
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        return true;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }

    private void updateText() {
        if (mTextSize > 0) {
            mTextRenderer = new PaintRenderer(mTextSize, mForeground,
                    mBackground);
        }
        else {
            mTextRenderer = new Bitmap4x8FontRenderer(getResources(),
                    mForeground, mBackground);
        }
        mBackgroundPaint.setColor(mBackground);
        mCharacterWidth = mTextRenderer.getCharacterWidth();
        mCharacterHeight = mTextRenderer.getCharacterHeight();

        updateSize(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        boolean oldKnownSize = mKnownSize;
        if (!mKnownSize) {
            mKnownSize = true;
        }
        updateSize(false);
        if (!oldKnownSize) {
            // Set up a thread to read input from the
            // pseudo-teletype:

            mPollingThread = new Thread(new Runnable() {

                public void run() {
                    try {
                        while(true) {
                            int read = mTermIn.read(mBuffer);
                            mByteQueue.write(mBuffer, 0, read);
                            mHandler.sendMessage(
                                    mHandler.obtainMessage(UPDATE));
                        }
                    } catch (IOException e) {
                    } catch (InterruptedException e) {
                    }
                }
                private byte[] mBuffer = new byte[4096];
            });
            mPollingThread.setName("Input reader");
            mPollingThread.start();
        }
    }

    private void updateSize(int w, int h) {
        mColumns = Math.max(1, w / mCharacterWidth);
        mRows = Math.max(1, h / mCharacterHeight);
        mVisibleColumns = mVisibleWidth / mCharacterWidth;

        // Inform the attached pty of our new size:
        Exec.setPtyWindowSize(mTermFd, mRows, mColumns, w, h);


        if (mTranscriptScreen != null) {
            mEmulator.updateSize(mColumns, mRows);
        } else {
            mTranscriptScreen =
                    new TranscriptScreen(mColumns, TRANSCRIPT_ROWS, mRows, 0, 7);
            mEmulator =
                    new TerminalEmulator(mTranscriptScreen, mColumns, mRows,
                            mTermOut);
        }

        // Reset our paging:
        mTopRow = 0;
        mLeftColumn = 0;

        invalidate();
    }

    void updateSize(boolean force) {
        if (mKnownSize) {
            getWindowVisibleDisplayFrame(mVisibleRect);
            int w = mVisibleRect.width();
            int h = mVisibleRect.height();
            // Log.w("Term", "(" + w + ", " + h + ")");
            if (force || w != mVisibleWidth || h != mVisibleHeight) {
                mVisibleWidth = w;
                mVisibleHeight = h;
                updateSize(mVisibleWidth, mVisibleHeight);
            }
        }
    }

    /**
     * Look for new input from the ptty, send it to the terminal emulator.
     */
    private void update() {
        int bytesAvailable = mByteQueue.getBytesAvailable();
        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
        try {
            int bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
            append(mReceiveBuffer, 0, bytesRead);
        } catch (InterruptedException e) {
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateSize(false);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, mBackgroundPaint);
        float x = -mLeftColumn * mCharacterWidth;
        float y = mCharacterHeight;
        int endLine = mTopRow + mRows;
        int cx = mEmulator.getCursorCol();
        int cy = mEmulator.getCursorRow();
        for (int i = mTopRow; i < endLine; i++) {
            int cursorX = -1;
            if (i == cy && mCursorVisible) {
                cursorX = cx;
            }
            mTranscriptScreen.drawText(i, canvas, x, y, mTextRenderer, cursorX);
            y += mCharacterHeight;
        }
    }

    private void ensureCursorVisible() {
        mTopRow = 0;
        if (mVisibleColumns > 0) {
            int cx = mEmulator.getCursorCol();
            int visibleCursorX = mEmulator.getCursorCol() - mLeftColumn;
            if (visibleCursorX < 0) {
                mLeftColumn = cx;
            } else if (visibleCursorX >= mVisibleColumns) {
                mLeftColumn = (cx - mVisibleColumns) + 1;
            }
        }
    }
}


/**
 * An ASCII key listener. Supports control characters and escape. Keeps track of
 * the current state of the alt, shift, and control keys.
 */
class TermKeyListener {
    /**
     * The state engine for a modifier key. Can be pressed, released, locked,
     * and so on.
     *
     */
    private class ModifierKey {

        private int mState;

        private static final int UNPRESSED = 0;

        private static final int PRESSED = 1;

        private static final int RELEASED = 2;

        private static final int USED = 3;

        private static final int LOCKED = 4;

        /**
         * Construct a modifier key. UNPRESSED by default.
         *
         */
        public ModifierKey() {
            mState = UNPRESSED;
        }

        public void onPress() {
            switch (mState) {
            case PRESSED:
                // This is a repeat before use
                break;
            case RELEASED:
                mState = LOCKED;
                break;
            case USED:
                // This is a repeat after use
                break;
            case LOCKED:
                mState = UNPRESSED;
                break;
            default:
                mState = PRESSED;
                break;
            }
        }

        public void onRelease() {
            switch (mState) {
            case USED:
                mState = UNPRESSED;
                break;
            case PRESSED:
                mState = RELEASED;
                break;
            default:
                // Leave state alone
                break;
            }
        }

        public void adjustAfterKeypress() {
            switch (mState) {
            case PRESSED:
                mState = USED;
                break;
            case RELEASED:
                mState = UNPRESSED;
                break;
            default:
                // Leave state alone
                break;
            }
        }

        public boolean isActive() {
            return mState != UNPRESSED;
        }
    }

    private ModifierKey mAltKey = new ModifierKey();

    private ModifierKey mCapKey = new ModifierKey();

    private ModifierKey mControlKey = new ModifierKey();

    /**
     * Construct a term key listener.
     *
     */
    public TermKeyListener() {
    }

    public void handleControlKey(boolean down) {
        if (down) {
            mControlKey.onPress();
        } else {
            mControlKey.onRelease();
        }
    }

    public int mapControlChar(int ch) {
        int result = ch;
        if (mControlKey.isActive()) {
            // Search is the control key.
            if (result >= 'a' && result <= 'z') {
                result = (char) (result - 'a' + '\001');
            } else if (result == ' ') {
                result = 0;
            } else if ((result == '[') || (result == '1')) {
                result = 27;
            } else if ((result == '\\') || (result == '.')) {
                result = 28;
            } else if ((result == ']') || (result == '0')) {
                result = 29;
            } else if ((result == '^') || (result == '6')) {
                result = 30; // control-^
            } else if ((result == '_') || (result == '5')) {
                result = 31;
            }
        }

        if (result > -1) {
            mAltKey.adjustAfterKeypress();
            mCapKey.adjustAfterKeypress();
            mControlKey.adjustAfterKeypress();
        }
        return result;
    }

    /**
     * Handle a keyDown event.
     *
     * @param keyCode the keycode of the keyDown event
     * @return the ASCII byte to transmit to the pseudo-teletype, or -1 if this
     *         event does not produce an ASCII byte.
     */
    public int keyDown(int keyCode, KeyEvent event) {
        int result = -1;
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_RIGHT:
        case KeyEvent.KEYCODE_ALT_LEFT:
            mAltKey.onPress();
            break;

        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            mCapKey.onPress();
            break;

        case KeyEvent.KEYCODE_ENTER:
            // Convert newlines into returns. The vt100 sends a
            // '\r' when the 'Return' key is pressed, but our
            // KeyEvent translates this as a '\n'.
            result = '\r';
            break;

        case KeyEvent.KEYCODE_DEL:
            // Convert DEL into 127 (instead of 8)
            result = 127;
            break;

        default: {
            result = event.getUnicodeChar(
                   (mCapKey.isActive() ? KeyEvent.META_SHIFT_ON : 0) |
                   (mAltKey.isActive() ? KeyEvent.META_ALT_ON : 0));
            break;
            }
        }

        result = mapControlChar(result);

        return result;
    }

    /**
     * Handle a keyUp event.
     *
     * @param keyCode the keyCode of the keyUp event
     */
    public void keyUp(int keyCode) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
            mAltKey.onRelease();
            break;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            mCapKey.onRelease();
            break;
        default:
            // Ignore other keyUps
            break;
        }
    }
}
