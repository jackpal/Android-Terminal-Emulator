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

import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.emulatorview.compat.Patterns;

import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.text.util.Linkify.MatchFilter;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Scroller;

/**
 * A view on a {@link TermSession}.  Displays the terminal emulator's screen,
 * provides access to its scrollback buffer, and passes input through to the
 * terminal emulator.
 * <p>
 * If this view is inflated from an XML layout, you need to call {@link
 * #attachSession attachSession} and {@link #setDensity setDensity} before using
 * the view.  If creating this view from code, use the {@link
 * #EmulatorView(Context, TermSession, DisplayMetrics)} constructor, which will
 * take care of this for you.
 */
public class EmulatorView extends View implements GestureDetector.OnGestureListener {
    private final static String TAG = "EmulatorView";
    private final static boolean LOG_KEY_EVENTS = false;
    private final static boolean LOG_IME = false;

    /**
     * We defer some initialization until we have been layed out in the view
     * hierarchy. The boolean tracks when we know what our size is.
     */
    private boolean mKnownSize;

    // Set if initialization was deferred because a TermSession wasn't attached
    private boolean mDeferInit = false;

    private int mVisibleWidth;
    private int mVisibleHeight;

    private TermSession mTermSession;

    /**
     * Total width of each character, in pixels
     */
    private float mCharacterWidth;

    /**
     * Total height of each character, in pixels
     */
    private int mCharacterHeight;

    /**
     * Top-of-screen margin
     */
    private int mTopOfScreenMargin;

    /**
     * Used to render text
     */
    private TextRenderer mTextRenderer;

    /**
     * Text size. Zero means 4 x 8 font.
     */
    private int mTextSize = 10;

    private int mCursorBlink;

    /**
     * Color scheme (default foreground/background colors).
     */
    private ColorScheme mColorScheme = BaseTextRenderer.defaultColorScheme;

    private Paint mForegroundPaint;

    private Paint mBackgroundPaint;

    private boolean mUseCookedIme;

    /**
     * Our terminal emulator.
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

    /*
     * The number of rows that are visible on the view
     */
    private int mVisibleRows;

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0
     */
    private int mTopRow;

    private int mLeftColumn;

    private static final int CURSOR_BLINK_PERIOD = 1000;

    private boolean mCursorVisible = true;

    private boolean mIsSelectingText = false;

    private boolean mBackKeySendsCharacter = false;
    private int mControlKeyCode;
    private int mFnKeyCode;
    private boolean mIsControlKeySent = false;
    private boolean mIsFnKeySent = false;

    private boolean mMouseTracking;

    private float mDensity;

    private float mScaledDensity;
    private static final int SELECT_TEXT_OFFSET_Y = -40;
    private int mSelXAnchor = -1;
    private int mSelYAnchor = -1;
    private int mSelX1 = -1;
    private int mSelY1 = -1;
    private int mSelX2 = -1;
    private int mSelY2 = -1;

    /**
     * Routing alt and meta keyCodes away from the IME allows Alt key processing to work on
     * the Asus Transformer TF101.
     * It doesn't seem to harm anything else, but it also doesn't seem to be
     * required on other platforms.
     *
     * This test should be refined as we learn more.
     */
    private final static boolean sTrapAltAndMeta = Build.MODEL.contains("Transformer TF101");

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

    private GestureDetector mGestureDetector;
    private GestureDetector.OnGestureListener mExtGestureListener;
    private Scroller mScroller;
    private Runnable mFlingRunner = new Runnable() {
        public void run() {
            if (mScroller.isFinished()) {
                return;
            }
            // Check whether mouse tracking was turned on during fling.
            if (isMouseTrackingActive()) {
                return;
            }

            boolean more = mScroller.computeScrollOffset();
            int newTopRow = mScroller.getCurrY();
            if (newTopRow != mTopRow) {
                mTopRow = newTopRow;
                invalidate();
            }

            if (more) {
                post(this);
            }

        }
    };

    /**
     *
     * A hash table of underlying URLs to implement clickable links.
     */
    private Hashtable<Integer,URLSpan[]> mLinkLayer = new Hashtable<Integer,URLSpan[]>();

    /**
     * Accept links that start with http[s]:
     */
    private static class HttpMatchFilter implements MatchFilter {
        public boolean acceptMatch(CharSequence s, int start, int end) {
            return startsWith(s, start, end, "http:") ||
                startsWith(s, start, end, "https:");
        }

        private boolean startsWith(CharSequence s, int start, int end,
                String prefix) {
            int prefixLen = prefix.length();
            int fragmentLen = end - start;
            if (prefixLen > fragmentLen) {
                return false;
            }
            for (int i = 0; i < prefixLen; i++) {
                if (s.charAt(start + i) != prefix.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static MatchFilter sHttpMatchFilter = new HttpMatchFilter();

    /**
     * Convert any URLs in the current row into a URLSpan,
     * and store that result in a hash table of URLSpan entries.
     *
     * @param row The number of the row to check for links
     * @return The number of lines in a multi-line-wrap set of links
     */
    private int createLinks(int row)
    {
        TranscriptScreen transcriptScreen = mEmulator.getScreen();
        char [] line = transcriptScreen.getScriptLine(row);
        int lineCount = 1;

        //Nothing to do if there's no text.
        if(line == null)
            return lineCount;

        /* If this is not a basic line, the array returned from getScriptLine()
         * could have arbitrary garbage at the end -- find the point at which
         * the line ends and only include that in the text to linkify.
         *
         * XXX: The fact that the array returned from getScriptLine() on a
         * basic line contains no garbage is an implementation detail -- the
         * documented behavior explicitly allows garbage at the end! */
        int lineLen;
        boolean textIsBasic = transcriptScreen.isBasicLine(row);
        if (textIsBasic) {
            lineLen = line.length;
        } else {
            // The end of the valid data is marked by a NUL character
            for (lineLen = 0; line[lineLen] != 0; ++lineLen);
        }

        SpannableStringBuilder textToLinkify = new SpannableStringBuilder(new String(line, 0, lineLen));

        boolean lineWrap = transcriptScreen.getScriptLineWrap(row);

        //While the current line has a wrap
        while (lineWrap)
        {
            //Get next line
            int nextRow = row + lineCount;
            line = transcriptScreen.getScriptLine(nextRow);

            //If next line is blank, don't try and append
            if(line == null)
                break;

            boolean lineIsBasic = transcriptScreen.isBasicLine(nextRow);
            if (textIsBasic && !lineIsBasic) {
                textIsBasic = lineIsBasic;
            }
            if (lineIsBasic) {
                lineLen = line.length;
            } else {
                // The end of the valid data is marked by a NUL character
                for (lineLen = 0; line[lineLen] != 0; ++lineLen);
            }

            textToLinkify.append(new String(line, 0, lineLen));

            //Check if line after next is wrapped
            lineWrap = transcriptScreen.getScriptLineWrap(nextRow);
            ++lineCount;
        }

        Linkify.addLinks(textToLinkify, Patterns.WEB_URL,
            null, sHttpMatchFilter, null);
        URLSpan [] urls = textToLinkify.getSpans(0, textToLinkify.length(), URLSpan.class);
        if(urls.length > 0)
        {
            int columns = mColumns;

            //re-index row to 0 if it is negative
            int screenRow = row - mTopRow;

            //Create and initialize set of links
            URLSpan [][] linkRows = new URLSpan[lineCount][];
            for(int i=0; i<lineCount; ++i)
            {
                linkRows[i] = new URLSpan[columns];
                Arrays.fill(linkRows[i], null);
            }

            //For each URL:
            for(int urlNum=0; urlNum<urls.length; ++urlNum)
            {
                URLSpan url = urls[urlNum];
                int spanStart = textToLinkify.getSpanStart(url);
                int spanEnd = textToLinkify.getSpanEnd(url);

                // Build accurate indices for links
                int startRow;
                int startCol;
                int endRow;
                int endCol;
                if (textIsBasic) {
                    /* endRow/endCol must be the last character of the link,
                     * not one after -- otherwise endRow might be too large */
                    int spanLastPos = spanEnd - 1;
                    // Basic line -- can assume one char per column
                    startRow = spanStart / mColumns;
                    startCol = spanStart % mColumns;
                    endRow   = spanLastPos / mColumns;
                    endCol   = spanLastPos % mColumns;
                } else {
                    /* Iterate over the line to get starting and ending columns
                     * for this span */
                    startRow = 0;
                    startCol = 0;
                    for (int i = 0; i < spanStart; ++i) {
                        char c = textToLinkify.charAt(i);
                        if (Character.isHighSurrogate(c)) {
                            ++i;
                            startCol += UnicodeTranscript.charWidth(c, textToLinkify.charAt(i));
                        } else {
                            startCol += UnicodeTranscript.charWidth(c);
                        }
                        if (startCol >= columns) {
                            ++startRow;
                            startCol %= columns;
                        }
                    }

                    endRow = startRow;
                    endCol = startCol;
                    for (int i = spanStart; i < spanEnd; ++i) {
                        char c = textToLinkify.charAt(i);
                        if (Character.isHighSurrogate(c)) {
                            ++i;
                            endCol += UnicodeTranscript.charWidth(c, textToLinkify.charAt(i));
                        } else {
                            endCol += UnicodeTranscript.charWidth(c);
                        }
                        if (endCol >= columns) {
                            ++endRow;
                            endCol %= columns;
                        }
                    }
                }

                //Fill linkRows with the URL where appropriate
                for(int i=startRow; i <= endRow; ++i)
                {
                    int runStart = (i == startRow) ? startCol: 0;
                    int runEnd = (i == endRow) ? endCol : mColumns - 1;

                    Arrays.fill(linkRows[i], runStart, runEnd + 1, url);
                }
            }

            //Add links into the link layer for later retrieval
            for(int i=0; i<lineCount; ++i)
                mLinkLayer.put(screenRow + i, linkRows[i]);
        }
        return lineCount;
    }

    /**
     * Sends mouse wheel codes to terminal in response to fling.
     */
    private class MouseTrackingFlingRunner implements Runnable {
        private Scroller mScroller;
        private int mLastY;
        private MotionEvent mMotionEvent;

        public void fling(MotionEvent e, float velocityX, float velocityY) {
            float SCALE = 0.15f;
            mScroller.fling(0, 0,
                    -(int) (velocityX * SCALE), -(int) (velocityY * SCALE),
                    0, 0, -100, 100);
            mLastY = 0;
            mMotionEvent = e;
            post(this);
        }

        public void run() {
            if (mScroller.isFinished()) {
                return;
            }
            // Check whether mouse tracking was turned off during fling.
            if (!isMouseTrackingActive()) {
                return;
            }

            boolean more = mScroller.computeScrollOffset();
            int newY = mScroller.getCurrY();
            for (; mLastY < newY; mLastY++) {
                sendMouseEventCode(mMotionEvent, 65);
            }
            for (; mLastY > newY; mLastY--) {
                sendMouseEventCode(mMotionEvent, 64);
            }

            if (more) {
                post(this);
            }
        }
    };
    private MouseTrackingFlingRunner mMouseTrackingFlingRunner = new MouseTrackingFlingRunner();

    private float mScrollRemainder;
    private TermKeyListener mKeyListener;

    private String mImeBuffer = "";

    /**
     * Our message handler class. Implements a periodic callback.
     */
    private final Handler mHandler = new Handler();

    /**
     * Called by the TermSession when the contents of the view need updating
     */
    private UpdateCallback mUpdateNotify = new UpdateCallback() {
        public void onUpdate() {
            if ( mIsSelectingText ) {
                int rowShift = mEmulator.getScrollCounter();
                mSelY1 -= rowShift;
                mSelY2 -= rowShift;
                mSelYAnchor -= rowShift;
            }
            mEmulator.clearScrollCounter();
            ensureCursorVisible();
            invalidate();
        }
    };

    /**
     * Create an <code>EmulatorView</code> for a {@link TermSession}.
     *
     * @param context The {@link Context} for the view.
     * @param session The {@link TermSession} this view will be displaying.
     * @param metrics The {@link DisplayMetrics} of the screen on which the view
     *                will be displayed.
     */
    public EmulatorView(Context context, TermSession session, DisplayMetrics metrics) {
        super(context);
        attachSession(session);
        setDensity(metrics);
        commonConstructor(context);
    }

    /**
     * Constructor called when inflating this view from XML.
     * <p>
     * You should call {@link #attachSession attachSession} and {@link
     * #setDensity setDensity} before using an <code>EmulatorView</code> created
     * using this constructor.
     */
    public EmulatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonConstructor(context);
    }

    /**
     * Constructor called when inflating this view from XML with a
     * default style set.
     * <p>
     * You should call {@link #attachSession attachSession} and {@link
     * #setDensity setDensity} before using an <code>EmulatorView</code> created
     * using this constructor.
     */
    public EmulatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        commonConstructor(context);
    }

    private void commonConstructor(Context context) {
        // TODO: See if we want to use the API level 11 constructor to get new flywheel feature.
        mScroller = new Scroller(context);
        mMouseTrackingFlingRunner.mScroller = new Scroller(context);
    }

    /**
     * Attach a {@link TermSession} to this view.
     *
     * @param session The {@link TermSession} this view will be displaying.
     */
    public void attachSession(TermSession session) {
        mTextRenderer = null;
        mForegroundPaint = new Paint();
        mBackgroundPaint = new Paint();
        mTopRow = 0;
        mLeftColumn = 0;
        mGestureDetector = new GestureDetector(this);
        // mGestureDetector.setIsLongpressEnabled(false);
        setVerticalScrollBarEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mTermSession = session;

        mKeyListener = new TermKeyListener(session);
        session.setKeyListener(mKeyListener);

        // Do init now if it was deferred until a TermSession was attached
        if (mDeferInit) {
            mDeferInit = false;
            mKnownSize = true;
            initialize();
        }
    }

    /**
     * Update the screen density for the screen on which the view is displayed.
     *
     * @param metrics The {@link DisplayMetrics} of the screen.
     */
    public void setDensity(DisplayMetrics metrics) {
        if (mDensity == 0) {
            // First time we've known the screen density, so update font size
            mTextSize = (int) (mTextSize * metrics.density);
        }
        mDensity = metrics.density;
        mScaledDensity = metrics.scaledDensity;
    }

    /**
     * Inform the view that it is now visible on screen.
     */
    public void onResume() {
        updateSize(false);
        if (mCursorBlink != 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        }
        if (mKeyListener != null) {
            mKeyListener.onResume();
        }
    }

    /**
     * Inform the view that it is no longer visible on the screen.
     */
    public void onPause() {
        if (mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
        if (mKeyListener != null) {
            mKeyListener.onPause();
        }
    }

    /**
     * Set this <code>EmulatorView</code>'s color scheme.
     *
     * @param scheme The {@link ColorScheme} to use (use null for the default
     *               scheme).
     * @see TermSession#setColorScheme
     * @see ColorScheme
     */
    public void setColorScheme(ColorScheme scheme) {
        if (scheme == null) {
            mColorScheme = BaseTextRenderer.defaultColorScheme;
        } else {
            mColorScheme = scheme;
        }
        updateText();
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
        return new BaseInputConnection(this, true) {
            /**
             * Used to handle composing text requests
             */
            private int mCursor;
            private int mComposingTextStart;
            private int mComposingTextEnd;
            private int mSelectedTextStart;
            private int mSelectedTextEnd;

            private void sendText(CharSequence text) {
                int n = text.length();
                char c;
                try {
                    for(int i = 0; i < n; i++) {
                        c = text.charAt(i);
                        if (Character.isHighSurrogate(c)) {
                            int codePoint;
                            if (++i < n) {
                                codePoint = Character.toCodePoint(c, text.charAt(i));
                            } else {
                                // Unicode Replacement Glyph, aka white question mark in black diamond.
                                codePoint = '\ufffd';
                            }
                            mapAndSend(codePoint);
                        } else {
                            mapAndSend(c);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "error writing ", e);
                }
            }

            private void mapAndSend(int c) throws IOException {
                int result = mKeyListener.mapControlChar(c);
                if (result < TermKeyListener.KEYCODE_OFFSET) {
                    mTermSession.write(result);
                } else {
                    mKeyListener.handleKeyCode(result - TermKeyListener.KEYCODE_OFFSET, null, getKeypadApplicationMode());
                }
                clearSpecialKeyStatus();
            }

            public boolean beginBatchEdit() {
                if (LOG_IME) {
                    Log.w(TAG, "beginBatchEdit");
                }
                setImeBuffer("");
                mCursor = 0;
                mComposingTextStart = 0;
                mComposingTextEnd = 0;
                return true;
            }

            public boolean clearMetaKeyStates(int arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "clearMetaKeyStates " + arg0);
                }
                return false;
            }

            public boolean commitCompletion(CompletionInfo arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "commitCompletion " + arg0);
                }
                return false;
            }

            public boolean endBatchEdit() {
                if (LOG_IME) {
                    Log.w(TAG, "endBatchEdit");
                }
                return true;
            }

            public boolean finishComposingText() {
                if (LOG_IME) {
                    Log.w(TAG, "finishComposingText");
                }
                sendText(mImeBuffer);
                setImeBuffer("");
                mComposingTextStart = 0;
                mComposingTextEnd = 0;
                mCursor = 0;
                return true;
            }

            public int getCursorCapsMode(int reqModes) {
                if (LOG_IME) {
                    Log.w(TAG, "getCursorCapsMode(" + reqModes + ")");
                }
                int mode = 0;
                if ((reqModes & TextUtils.CAP_MODE_CHARACTERS) != 0) {
                    mode |= TextUtils.CAP_MODE_CHARACTERS;
                }
                return mode;
            }

            public ExtractedText getExtractedText(ExtractedTextRequest arg0,
                    int arg1) {
                if (LOG_IME) {
                    Log.w(TAG, "getExtractedText" + arg0 + "," + arg1);
                }
                return null;
            }

            public CharSequence getTextAfterCursor(int n, int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getTextAfterCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mImeBuffer.length() - mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor, mCursor + len);
            }

            public CharSequence getTextBeforeCursor(int n, int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getTextBeforeCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor-len, mCursor);
            }

            public boolean performContextMenuAction(int arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "performContextMenuAction" + arg0);
                }
                return true;
            }

            public boolean performPrivateCommand(String arg0, Bundle arg1) {
                if (LOG_IME) {
                    Log.w(TAG, "performPrivateCommand" + arg0 + "," + arg1);
                }
                return true;
            }

            public boolean reportFullscreenMode(boolean arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "reportFullscreenMode" + arg0);
                }
                return true;
            }

            public boolean commitCorrection (CorrectionInfo correctionInfo) {
                if (LOG_IME) {
                    Log.w(TAG, "commitCorrection");
                }
                return true;
            }

            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (LOG_IME) {
                    Log.w(TAG, "commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                clearComposingText();
                sendText(text);
                setImeBuffer("");
                mCursor = 0;
                return true;
            }

            private void clearComposingText() {
                int len = mImeBuffer.length();
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    mComposingTextEnd = mComposingTextStart = 0;
                    return;
                }
                setImeBuffer(mImeBuffer.substring(0, mComposingTextStart) +
                    mImeBuffer.substring(mComposingTextEnd));
                if (mCursor < mComposingTextStart) {
                    // do nothing
                } else if (mCursor < mComposingTextEnd) {
                    mCursor = mComposingTextStart;
                } else {
                    mCursor -= mComposingTextEnd - mComposingTextStart;
                }
                mComposingTextEnd = mComposingTextStart = 0;
            }

            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (LOG_IME) {
                    Log.w(TAG, "deleteSurroundingText(" + leftLength +
                            "," + rightLength + ")");
                }
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

            public boolean performEditorAction(int actionCode) {
                if (LOG_IME) {
                    Log.w(TAG, "performEditorAction(" + actionCode + ")");
                }
                if (actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // The "return" key has been pressed on the IME.
                    sendText("\r");
                }
                return true;
            }

            public boolean sendKeyEvent(KeyEvent event) {
                if (LOG_IME) {
                    Log.w(TAG, "sendKeyEvent(" + event + ")");
                }
                // Some keys are sent here rather than to commitText.
                // In particular, del and the digit keys are sent here.
                // (And I have reports that the HTC Magic also sends Return here.)
                // As a bit of defensive programming, handle every key.
                dispatchKeyEvent(event);
                return true;
            }

            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingText(\"" + text + "\", " + newCursorPosition + ")");
                }
                int len = mImeBuffer.length();
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    return false;
                }
                setImeBuffer(mImeBuffer.substring(0, mComposingTextStart) +
                    text + mImeBuffer.substring(mComposingTextEnd));
                mComposingTextEnd = mComposingTextStart + text.length();
                mCursor = newCursorPosition > 0 ? mComposingTextEnd + newCursorPosition - 1
                        : mComposingTextStart - newCursorPosition;
                return true;
            }

            public boolean setSelection(int start, int end) {
                if (LOG_IME) {
                    Log.w(TAG, "setSelection" + start + "," + end);
                }
                int length = mImeBuffer.length();
                if (start == end && start > 0 && start < length) {
                    mSelectedTextStart = mSelectedTextEnd = 0;
                    mCursor = start;
                } else if (start < end && start > 0 && end < length) {
                    mSelectedTextStart = start;
                    mSelectedTextEnd = end;
                    mCursor = start;
                }
                return true;
            }

            public boolean setComposingRegion(int start, int end) {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingRegion " + start + "," + end);
                }
                if (start < end && start > 0 && end < mImeBuffer.length()) {
                    clearComposingText();
                    mComposingTextStart = start;
                    mComposingTextEnd = end;
                }
                return true;
            }

            public CharSequence getSelectedText(int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getSelectedText " + flags);
                }
                int len = mImeBuffer.length();
                if (mSelectedTextEnd >= len || mSelectedTextStart > mSelectedTextEnd) {
                    return "";
                }
                return mImeBuffer.substring(mSelectedTextStart, mSelectedTextEnd+1);
            }

        };
    }

    private void setImeBuffer(String buffer) {
        if (!buffer.equals(mImeBuffer)) {
            invalidate();
        }
        mImeBuffer = buffer;
    }

    /**
     * Get the terminal emulator's keypad application mode.
     */
    public boolean getKeypadApplicationMode() {
        return mEmulator.getKeypadApplicationMode();
    }

    /**
     * Set a {@link android.view.GestureDetector.OnGestureListener
     * GestureDetector.OnGestureListener} to receive gestures performed on this
     * view.  Can be used to implement additional
     * functionality via touch gestures or override built-in gestures.
     *
     * @param listener The {@link
     *                 android.view.GestureDetector.OnGestureListener
     *                 GestureDetector.OnGestureListener} which will receive
     *                 gestures.
     */
    public void setExtGestureListener(GestureDetector.OnGestureListener listener) {
        mExtGestureListener = listener;
    }

    /**
     * Compute the vertical range that the vertical scrollbar represents.
     */
    @Override
    protected int computeVerticalScrollRange() {
        return mEmulator.getScreen().getActiveRows();
    }

    /**
     * Compute the vertical extent of the horizontal scrollbar's thumb within
     * the vertical range. This value is used to compute the length of the thumb
     * within the scrollbar's track.
     */
    @Override
    protected int computeVerticalScrollExtent() {
        return mRows;
    }

    /**
     * Compute the vertical offset of the vertical scrollbar's thumb within the
     * horizontal range. This value is used to compute the position of the thumb
     * within the scrollbar's track.
     */
    @Override
    protected int computeVerticalScrollOffset() {
        return mEmulator.getScreen().getActiveRows() + mTopRow - mRows;
    }

    /**
     * Call this to initialize the view.
     */
    private void initialize() {
        TermSession session = mTermSession;

        updateText();

        mEmulator = session.getEmulator();
        session.setUpdateCallback(mUpdateNotify);

        requestFocus();
    }

    /**
     * Get the {@link TermSession} corresponding to this view.
     *
     * @return The {@link TermSession} object for this view.
     */
    public TermSession getTermSession() {
        return mTermSession;
    }

    /**
     * Get the width of the visible portion of this view.
     *
     * @return The width of the visible portion of this view, in pixels.
     */
    public int getVisibleWidth() {
        return mVisibleWidth;
    }

    /**
     * Get the height of the visible portion of this view.
     *
     * @return The height of the visible portion of this view, in pixels.
     */
    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    /**
     * Gets the visible number of rows for the view, useful when updating Ptysize with the correct number of rows/columns
     * @return The rows for the visible number of rows, this is calculate in updateSize(int w, int h), please call
     * updateSize(true) if the view changed, to get the correct calculation before calling this.
     */
    public int getVisibleRows()
    {
      return mVisibleRows;
    }

    /**
     * Gets the visible number of columns for the view, again useful to get when updating PTYsize
     * @return the columns for the visisble view, please call updateSize(true) to re-calculate this if the view has changed
     */
    public int getVisibleColumns()
    {
      return mVisibleColumns;
    }


    /**
     * Page the terminal view (scroll it up or down by <code>delta</code>
     * screenfuls).
     *
     * @param delta The number of screens to scroll. Positive means scroll down,
     *        negative means scroll up.
     */
    public void page(int delta) {
        mTopRow =
                Math.min(0, Math.max(-(mEmulator.getScreen()
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
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param fontSize the new font size, in density-independent pixels.
     */
    public void setTextSize(int fontSize) {
        mTextSize = (int) (fontSize * mDensity);
        updateText();
    }

    /**
     * Sets the IME mode ("cooked" or "raw").
     *
     * @param useCookedIME Whether the IME should be used in cooked mode.
     */
    public void setUseCookedIME(boolean useCookedIME) {
        mUseCookedIme = useCookedIME;
    }

    /**
     * Returns true if mouse events are being sent as escape sequences to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return mEmulator.getMouseTrackingMode() != 0 && mMouseTracking;
    }

    /**
     * Send a single mouse event code to the terminal.
     */
    private void sendMouseEventCode(MotionEvent e, int button_code) {
        int x = (int)(e.getX() / mCharacterWidth) + 1;
        int y = (int)((e.getY()-mTopOfScreenMargin) / mCharacterHeight) + 1;
        // Clip to screen, and clip to the limits of 8-bit data.
        boolean out_of_bounds =
            x < 1 || y < 1 ||
            x > mColumns || y > mRows ||
            x > 255-32 || y > 255-32;
        //Log.d(TAG, "mouse button "+x+","+y+","+button_code+",oob="+out_of_bounds);
        if(button_code < 0 || button_code > 255-32) {
            Log.e(TAG, "mouse button_code out of range: "+button_code);
            return;
        }
        if(!out_of_bounds) {
            byte[] data = {
                '\033', '[', 'M',
                (byte)(32 + button_code),
                (byte)(32 + x),
                (byte)(32 + y) };
            mTermSession.write(data, 0, data.length);
        }
    }

    // Begin GestureDetector.OnGestureListener methods

    public boolean onSingleTapUp(MotionEvent e) {
        if (mExtGestureListener != null && mExtGestureListener.onSingleTapUp(e)) {
            return true;
        }

        if (isMouseTrackingActive()) {
            sendMouseEventCode(e, 0); // BTN1 press
            sendMouseEventCode(e, 3); // release
        }

        requestFocus();
        return true;
    }

    public void onLongPress(MotionEvent e) {
        // XXX hook into external gesture listener
        showContextMenu();
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2,
            float distanceX, float distanceY) {
        if (mExtGestureListener != null && mExtGestureListener.onScroll(e1, e2, distanceX, distanceY)) {
            return true;
        }

        distanceY += mScrollRemainder;
        int deltaRows = (int) (distanceY / mCharacterHeight);
        mScrollRemainder = distanceY - deltaRows * mCharacterHeight;

        if (isMouseTrackingActive()) {
            // Send mouse wheel events to terminal.
            for (; deltaRows>0; deltaRows--) {
                sendMouseEventCode(e1, 65);
            }
            for (; deltaRows<0; deltaRows++) {
                sendMouseEventCode(e1, 64);
            }
            return true;
        }

        mTopRow =
            Math.min(0, Math.max(-(mEmulator.getScreen()
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
        mTopRow = -mEmulator.getScreen().getActiveTranscriptRows();
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (mExtGestureListener != null && mExtGestureListener.onFling(e1, e2, velocityX, velocityY)) {
            return true;
        }

        mScrollRemainder = 0.0f;
        if (isMouseTrackingActive()) {
            mMouseTrackingFlingRunner.fling(e1, velocityX, velocityY);
        } else {
            float SCALE = 0.25f;
            mScroller.fling(0, mTopRow,
                    -(int) (velocityX * SCALE), -(int) (velocityY * SCALE),
                    0, 0,
                    -mEmulator.getScreen().getActiveTranscriptRows(), 0);
            // onScroll(e1, e2, 0.1f * velocityX, -0.1f * velocityY);
            post(mFlingRunner);
        }
        return true;
    }

    public void onShowPress(MotionEvent e) {
        if (mExtGestureListener != null) {
            mExtGestureListener.onShowPress(e);
        }
    }

    public boolean onDown(MotionEvent e) {
        if (mExtGestureListener != null && mExtGestureListener.onDown(e)) {
            return true;
        }
        mScrollRemainder = 0.0f;
        return true;
    }

    // End GestureDetector.OnGestureListener methods

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsSelectingText) {
            return onTouchEventWhileSelectingText(ev);
        } else {
            return mGestureDetector.onTouchEvent(ev);
        }
    }

    private boolean onTouchEventWhileSelectingText(MotionEvent ev) {
        int action = ev.getAction();
        int cx = (int)(ev.getX() / mCharacterWidth);
        int cy = Math.max(0,
                (int)((ev.getY() + SELECT_TEXT_OFFSET_Y * mScaledDensity)
                        / mCharacterHeight) + mTopRow);
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mSelXAnchor = cx;
            mSelYAnchor = cy;
            mSelX1 = cx;
            mSelY1 = cy;
            mSelX2 = mSelX1;
            mSelY2 = mSelY1;
            break;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_UP:
            int minx = Math.min(mSelXAnchor, cx);
            int maxx = Math.max(mSelXAnchor, cx);
            int miny = Math.min(mSelYAnchor, cy);
            int maxy = Math.max(mSelYAnchor, cy);
            mSelX1 = minx;
            mSelY1 = miny;
            mSelX2 = maxx;
            mSelY2 = maxy;
            if (action == MotionEvent.ACTION_UP) {
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getContext().getApplicationContext());
                clip.setText(getSelectedText().trim());
                toggleSelectingText();
            }
            invalidate();
            break;
        default:
            toggleSelectingText();
            invalidate();
            break;
        }
        return true;
    }

    /**
     * Called when a key is pressed in the view.
     *
     * @param keyCode The keycode of the key which was pressed.
     * @param event A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyDown " + keyCode);
        }
        if (handleControlKey(keyCode, true)) {
            return true;
        } else if (handleFnKey(keyCode, true)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            if (! isInterceptedSystemKey(keyCode) ) {
                // Don't intercept the system keys
                return super.onKeyDown(keyCode, event);
            }
        }

        // Translate the keyCode into an ASCII character.

        try {
            int oldCombiningAccent = mKeyListener.getCombiningAccent();
            int oldCursorMode = mKeyListener.getCursorMode();
            mKeyListener.keyDown(keyCode, event, getKeypadApplicationMode(),
                    TermKeyListener.isEventFromToggleDevice(event));
            if (mKeyListener.getCombiningAccent() != oldCombiningAccent
                    || mKeyListener.getCursorMode() != oldCursorMode) {
                invalidate();
            }
        } catch (IOException e) {
            // Ignore I/O exceptions
        }
        return true;
    }

    /** Do we want to intercept this system key? */
    private boolean isInterceptedSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK && mBackKeySendsCharacter;
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyUp " + keyCode);
        }
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (handleFnKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            if ( ! isInterceptedSystemKey(keyCode) ) {
                return super.onKeyUp(keyCode, event);
            }
        }

        mKeyListener.keyUp(keyCode, event);
        clearSpecialKeyStatus();
        return true;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (sTrapAltAndMeta) {
            boolean altEsc = mKeyListener.getAltSendsEsc();
            boolean altOn = (event.getMetaState() & KeyEvent.META_ALT_ON) != 0;
            boolean metaOn = (event.getMetaState() & KeyEvent.META_META_ON) != 0;
            boolean altPressed = (keyCode == KeyEvent.KEYCODE_ALT_LEFT)
                    || (keyCode == KeyEvent.KEYCODE_ALT_RIGHT);
            boolean altActive = mKeyListener.isAltActive();
            if (altEsc && (altOn || altPressed || altActive || metaOn)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return onKeyDown(keyCode, event);
                } else {
                    return onKeyUp(keyCode, event);
                }
            }
        }

        if (handleHardwareControlKey(keyCode, event)) {
            return true;
        }

        if (mKeyListener.isCtrlActive()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return onKeyDown(keyCode, event);
            } else {
                return onKeyUp(keyCode, event);
            }
        }

        return super.onKeyPreIme(keyCode, event);
    };

    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey " + keyCode);
            }
            mKeyListener.handleControlKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean handleHardwareControlKey(int keyCode, KeyEvent event) {
        if (keyCode == KeycodeConstants.KEYCODE_CTRL_LEFT ||
            keyCode == KeycodeConstants.KEYCODE_CTRL_RIGHT) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleHardwareControlKey " + keyCode);
            }
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            mKeyListener.handleHardwareControlKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean handleFnKey(int keyCode, boolean down) {
        if (keyCode == mFnKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleFnKey " + keyCode);
            }
            mKeyListener.handleFnKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }

    private void clearSpecialKeyStatus() {
        if (mIsControlKeySent) {
            mIsControlKeySent = false;
            mKeyListener.handleControlKey(false);
            invalidate();
        }
        if (mIsFnKeySent) {
            mIsFnKeySent = false;
            mKeyListener.handleFnKey(false);
            invalidate();
        }
    }

    private void updateText() {
        ColorScheme scheme = mColorScheme;
        if (mTextSize > 0) {
            mTextRenderer = new PaintRenderer(mTextSize, scheme);
        }
        else {
            mTextRenderer = new Bitmap4x8FontRenderer(getResources(), scheme);
        }

        mForegroundPaint.setColor(scheme.getForeColor());
        mBackgroundPaint.setColor(scheme.getBackColor());
        mCharacterWidth = mTextRenderer.getCharacterWidth();
        mCharacterHeight = mTextRenderer.getCharacterHeight();

        updateSize(true);
    }

    /**
     * This is called during layout when the size of this view has changed. If
     * you were just added to the view hierarchy, you're called with the old
     * values of 0.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mTermSession == null) {
            // Not ready, defer until TermSession is attached
            mDeferInit = true;
            return;
        }

        if (!mKnownSize) {
            mKnownSize = true;
            initialize();
        } else {
            updateSize(false);
        }
    }

    private void updateSize(int w, int h) {
        mColumns = Math.max(1, (int) (((float) w) / mCharacterWidth));
        mVisibleColumns = Math.max(1, (int) (((float) mVisibleWidth) / mCharacterWidth));

        mTopOfScreenMargin = mTextRenderer.getTopMargin();
        mRows = Math.max(1, (h - mTopOfScreenMargin) / mCharacterHeight);
        mVisibleRows = Math.max(1, (mVisibleHeight - mTopOfScreenMargin) / mCharacterHeight);
        mTermSession.updateSize(mColumns, mRows);

        // Reset our paging:
        mTopRow = 0;
        mLeftColumn = 0;

        invalidate();
    }

    /**
     * Update the view's idea of its size.
     *
     * @param force Whether a size adjustment should be performed even if the
     *              view's size has not changed.
     */
    public void updateSize(boolean force) {
        //Need to clear saved links on each display refresh
        mLinkLayer.clear();
        if (mKnownSize) {
            int w = getWidth();
            int h = getHeight();
            // Log.w("Term", "(" + w + ", " + h + ")");
            if (force || w != mVisibleWidth || h != mVisibleHeight) {
                mVisibleWidth = w;
                mVisibleHeight = h;
                updateSize(mVisibleWidth, mVisibleHeight);
            }
        }
    }

    /**
     * Draw the view to the provided {@link Canvas}.
     *
     * @param canvas The {@link Canvas} to draw the view to.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        updateSize(false);

        if (mEmulator == null) {
            // Not ready yet
            return;
        }

        int w = getWidth();
        int h = getHeight();

        boolean reverseVideo = mEmulator.getReverseVideo();
        mTextRenderer.setReverseVideo(reverseVideo);

        Paint backgroundPaint =
                reverseVideo ? mForegroundPaint : mBackgroundPaint;
        canvas.drawRect(0, 0, w, h, backgroundPaint);
        float x = -mLeftColumn * mCharacterWidth;
        float y = mCharacterHeight + mTopOfScreenMargin;
        int endLine = mTopRow + mRows;
        int cx = mEmulator.getCursorCol();
        int cy = mEmulator.getCursorRow();
        boolean cursorVisible = mCursorVisible && mEmulator.getShowCursor();
        String effectiveImeBuffer = mImeBuffer;
        int combiningAccent = mKeyListener.getCombiningAccent();
        if (combiningAccent != 0) {
            effectiveImeBuffer += String.valueOf((char) combiningAccent);
        }
        int cursorStyle = mKeyListener.getCursorMode();

        int linkLinesToSkip = 0; //for multi-line links

        for (int i = mTopRow; i < endLine; i++) {
            int cursorX = -1;
            if (i == cy && cursorVisible) {
                cursorX = cx;
            }
            int selx1 = -1;
            int selx2 = -1;
            if ( i >= mSelY1 && i <= mSelY2 ) {
                if ( i == mSelY1 ) {
                    selx1 = mSelX1;
                }
                if ( i == mSelY2 ) {
                    selx2 = mSelX2;
                } else {
                    selx2 = mColumns;
                }
            }
            mEmulator.getScreen().drawText(i, canvas, x, y, mTextRenderer, cursorX, selx1, selx2, effectiveImeBuffer, cursorStyle);
            y += mCharacterHeight;
            //if no lines to skip, create links for the line being drawn
            if(linkLinesToSkip == 0)
                linkLinesToSkip = createLinks(i);

            //createLinks always returns at least 1
            --linkLinesToSkip;
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

    /**
     * Toggle text selection mode in the view.
     */
    public void toggleSelectingText() {
        mIsSelectingText = ! mIsSelectingText;
        setVerticalScrollBarEnabled( ! mIsSelectingText );
        if ( ! mIsSelectingText ) {
            mSelX1 = -1;
            mSelY1 = -1;
            mSelX2 = -1;
            mSelY2 = -1;
        }
    }

    /**
     * Whether the view is currently in text selection mode.
     */
    public boolean getSelectingText() {
        return mIsSelectingText;
    }

    /**
     * Get selected text.
     *
     * @return A {@link String} with the selected text.
     */
    public String getSelectedText() {
        return mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2);
    }

    /**
     * Send a Ctrl key event to the terminal.
     */
    public void sendControlKey() {
        mIsControlKeySent = true;
        mKeyListener.handleControlKey(true);
        invalidate();
    }

    /**
     * Send an Fn key event to the terminal.  The Fn modifier key can be used to
     * generate various special characters and escape codes.
     */
    public void sendFnKey() {
        mIsFnKeySent = true;
        mKeyListener.handleFnKey(true);
        invalidate();
    }

    /**
     * Set the key code to be sent when the Back key is pressed.
     */
    public void setBackKeyCharacter(int keyCode) {
        mKeyListener.setBackKeyCharacter(keyCode);
        mBackKeySendsCharacter = (keyCode != 0);
    }

    /**
     * Set whether to prepend the ESC keycode to the character when when pressing
     * the ALT Key.
     * @param flag
     */
    public void setAltSendsEsc(boolean flag) {
        mKeyListener.setAltSendsEsc(flag);
    }

    /**
     * Set the keycode corresponding to the Ctrl key.
     */
    public void setControlKeyCode(int keyCode) {
        mControlKeyCode = keyCode;
    }

    /**
     * Set the keycode corresponding to the Fn key.
     */
    public void setFnKeyCode(int keyCode) {
        mFnKeyCode = keyCode;
    }

    public void setTermType(String termType) {
         mKeyListener.setTermType(termType);
    }

    /**
     * Set whether mouse events should be sent to the terminal as escape codes.
     */
    public void setMouseTracking(boolean flag) {
        mMouseTracking = flag;
    }


    /**
     * Get the URL for the link displayed at the specified screen coordinates.
     *
     * @param x The x coordinate being queried (from 0 to screen width)
     * @param y The y coordinate being queried (from 0 to screen height)
     * @return The URL for the link at the specified screen coordinates, or
     *         null if no link exists there.
     */
    public String getURLat(float x, float y)
    {
        float w = getWidth();
        float h = getHeight();

        //Check for division by zero
        //If width or height is zero, there are probably no links around, so return null.
        if(w == 0 || h == 0)
            return null;

        //Get fraction of total screen
        float x_pos = x / w;
        float y_pos = y / h;

        //Convert to integer row/column index
        int row = (int)Math.floor(y_pos * mRows);
        int col = (int)Math.floor(x_pos * mColumns);

        //Grab row from link layer
        URLSpan [] linkRow = mLinkLayer.get(row);
        URLSpan link;

        //If row exists, and link exists at column, return it
        if(linkRow != null && (link = linkRow[col]) != null)
            return link.getURL();
        else
            return null;
    }
}
