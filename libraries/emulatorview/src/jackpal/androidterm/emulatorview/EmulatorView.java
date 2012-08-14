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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.annotation.TargetApi;
import android.content.Context;
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
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import jackpal.androidterm.emulatorview.compat.AndroidCompat;

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
    private final String TAG = "EmulatorView";
    private final boolean LOG_KEY_EVENTS = false;
    private final boolean LOG_IME = false;

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
     * Our transcript. Contains the screen and the transcript.
     */
    private TranscriptScreen mTranscriptScreen;

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

    private int mCursorStyle;
    private int mCursorBlink;

    /**
     * Color scheme (default foreground/background colors).
     */
    private ColorScheme mColorScheme = BaseTextRenderer.defaultColorScheme;

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

    private static final int CURSOR_BLINK_PERIOD = 1000;

    private boolean mCursorVisible = true;

    private boolean mIsSelectingText = false;

    private boolean mBackKeySendsCharacter = false;
    private int mControlKeyCode;
	private int mFnKeyCode;
    private boolean mIsControlKeySent = false;
    private boolean mIsFnKeySent = false;

    private float mDensity;

    private float mScaledDensity;
    private static final int SELECT_TEXT_OFFSET_Y = -40;
    private int mSelXAnchor = -1;
    private int mSelYAnchor = -1;
    private int mSelX1 = -1;
    private int mSelY1 = -1;
    private int mSelX2 = -1;
    private int mSelY2 = -1;

    private boolean mIsActive = false;

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
    private float mScrollRemainder;

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
    private int mBackKeyCharacter = 0;
    
    private boolean mAltSendsEsc = false;
	private TermKeyListener mTermKeyListener = null;
	
	

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
    }

    /**
     * Attach a {@link TermSession} to this view.
     *
     * @param session The {@link TermSession} this view will be displaying.
     */
    public void attachSession(TermSession session) {
        mTextRenderer = null;
        mCursorPaint = new Paint();
        mCursorPaint.setARGB(255,128,128,128);
        mBackgroundPaint = new Paint();
        mTopRow = 0;
        mLeftColumn = 0;
        mGestureDetector = new GestureDetector(this);
        // mGestureDetector.setIsLongpressEnabled(false);
        setVerticalScrollBarEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mTermSession = session;


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
        mIsActive = true;
        updateSize(false);
        if (mCursorBlink != 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        }
    }

    /**
     * Inform the view that it is no longer visible on the screen.
     */
    public void onPause() {
        if (mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
        mIsActive = false;
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
        return new InputConnection() {
        	TermKeyListener mKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
            private boolean mInBatchEdit;
            /**
             * Used to handle composing text requests
             */
            private int mCursor;
            private int mComposingTextStart;
            private int mComposingTextEnd;
            private int mSelectedTextStart;
            private int mSelectedTextEnd;

            private void sendChar(int c) {
                try {
                    mapAndSend(c);
                } catch (IOException ex) {

                }
            }

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
                int charCode = mKeyListener.mapControlChars(c);
                byte[] charCodes = TermKeyListener.lookupDirectMap(charCode, getKeypadApplicationMode(), false);
                mTermSession.write(charCodes,0,charCodes.length);
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
                mInBatchEdit = true;
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
                mInBatchEdit = false;
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

            public int getCursorCapsMode(int arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "getCursorCapsMode(" + arg0 + ")");
                }
                return 0;
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

            @TargetApi(11)
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
        return (mEmulator != null) ? mEmulator.getKeypadApplicationMode() : false;
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
        return mTranscriptScreen.getActiveRows();
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
        return mTranscriptScreen.getActiveRows() + mTopRow - mRows;
    }

    /**
     * Call this to initialize the view.
     */
    private void initialize() {
        TermSession session = mTermSession;

        updateText();

        mTranscriptScreen = session.getTranscriptScreen();
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
     * Page the terminal view (scroll it up or down by <code>delta</code>
     * screenfuls).
     *
     * @param delta The number of screens to scroll. Positive means scroll down,
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
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param fontSize the new font size, in density-independent pixels.
     */
    public void setTextSize(int fontSize) {
        mTextSize = (int) (fontSize * mDensity);
        updateText();
    }

    /**
     * Sets style information about the cursor.
     *
     * @param style The style of the cursor.
     * @param blink Whether the cursor should blink.
     */
    public void setCursorStyle(int style, int blink) {
        mCursorStyle = style;
        if (blink != 0 && mCursorBlink == 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        } else if (blink == 0 && mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
        mCursorBlink = blink;
    }

    /**
     * Sets the IME mode ("cooked" or "raw").
     *
     * @param useCookedIME Whether the IME should be used in cooked mode.
     */
    public void setUseCookedIME(boolean useCookedIME) {
        mUseCookedIme = useCookedIME;
    }

    // Begin GestureDetector.OnGestureListener methods

    public boolean onSingleTapUp(MotionEvent e) {
        if (mExtGestureListener != null && mExtGestureListener.onSingleTapUp(e)) {
            return true;
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
        if (mExtGestureListener != null && mExtGestureListener.onFling(e1, e2, velocityX, velocityY)) {
            return true;
        }
        // TODO: add animation man's (non animated) fling
        mScrollRemainder = 0.0f;
        onScroll(e1, e2, 0.1f * velocityX, -0.1f * velocityY);
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

    @SuppressWarnings("deprecation")
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
                ClipboardManager clip = (ClipboardManager)
                     getContext().getApplicationContext()
                         .getSystemService(Context.CLIPBOARD_SERVICE);
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
        if (mTermKeyListener == null) {
        	mTermKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
        }
        boolean isHandled = mTermKeyListener.keyDown(event);
        if (isHandled) {
        	if (mTermKeyListener.getCharSequence() != null) {
        		byte[] seq = mTermKeyListener.getCharSequence();
            	mTermSession.write(seq, 0, seq.length);
        	}
        } else if (isSystemKey(keyCode, event)) {
            if (! isInterceptedSystemKey(keyCode) ) {
                // Don't intercept the system keys
                isHandled = super.onKeyDown(keyCode, event);
            }
        }
        return isHandled;
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
        //MetaKeyKeyListener.handleKeyDown(metaState, keyCode, event)
        boolean isHandled = mTermKeyListener.keyUp(event);
        if (isHandled) {
        	//noop;
        } else if (isSystemKey(keyCode, event)) {
        	if ( ! isInterceptedSystemKey(keyCode)) {
        		return super.onKeyUp(keyCode, event);
        	}
        }
        
        clearSpecialKeyStatus();
        return isHandled;
    }


    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey " + keyCode);
            }
            mTermKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    private boolean handleFnKey(int keyCode, boolean down) {
        if (keyCode == mFnKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleFnKey " + keyCode);
            }
            mTermKeyListener.handleFnKey(down);
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
            mTermKeyListener.handleControlKey(false);
        }
        if (mIsFnKeySent) {
            mIsFnKeySent = false;
            mTermKeyListener.handleFnKey(false);
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
        mVisibleColumns = (int) (((float) mVisibleWidth) / mCharacterWidth);

        mTopOfScreenMargin = mTextRenderer.getTopMargin();
        mRows = Math.max(1, (h - mTopOfScreenMargin) / mCharacterHeight);
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

        canvas.drawRect(0, 0, w, h, mBackgroundPaint);
        float x = -mLeftColumn * mCharacterWidth;
        float y = mCharacterHeight + mTopOfScreenMargin;
        int endLine = mTopRow + mRows;
        int cx = mEmulator.getCursorCol();
        int cy = mEmulator.getCursorRow();
        for (int i = mTopRow; i < endLine; i++) {
            int cursorX = -1;
            if (i == cy && mCursorVisible) {
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
            mTranscriptScreen.drawText(i, canvas, x, y, mTextRenderer, cursorX, selx1, selx2, mImeBuffer);
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
        mTermKeyListener.handleControlKey(true);
    }

    /**
     * Send an Fn key event to the terminal.  The Fn modifier key can be used to
     * generate various special characters and escape codes.
     */
    public void sendFnKey() {
        mIsFnKeySent = true;
        mTermKeyListener.handleFnKey(true);
    }

    /**
     * Set the key code to be sent when the Back key is pressed.
     */
    public void setBackKeyCharacter(int keyCode) {
        mBackKeyCharacter = keyCode;
        mTermKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
        mBackKeySendsCharacter = (keyCode != 0);
    }

    /**
     * Set the keycode corresponding to the Ctrl key.
     */
    public void setControlKeyCode(int keyCode) {    	
        mControlKeyCode = keyCode;
        mTermKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
    }

    /**
     * Set the keycode corresponding to the Fn key.
     */
    public void setFnKeyCode(int keyCode) {
    	mFnKeyCode = keyCode;
    	mTermKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
    }

    public void setAltSendsEsc(boolean flag) {    	
    	mAltSendsEsc = flag;
    	mTermKeyListener = new TermKeyListener(mControlKeyCode, mFnKeyCode, mBackKeyCharacter, mAltSendsEsc, getKeypadApplicationMode());
    }
}

abstract class BaseTextRenderer implements TextRenderer {
    protected int[] mForePaint = {
            0xff000000, // Black
            0xffcc0000, // Red
            0xff00cc00, // green
            0xffcccc00, // yellow
            0xff0000cc, // blue
            0xffcc00cc, // magenta
            0xff00cccc, // cyan
            0xffcccccc, // "white"/light gray -- is overridden by constructor
            0xff666666, // bright black/dark gray
            0xffff0000, // red
            0xff00ff00, // green
            0xffffff00, // yellow
            0xff0000ff, // blue
            0xffff00ff, // magenta
            0xff00ffff, // cyan
            0xffffffff  // white
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
    static final ColorScheme defaultColorScheme =
            new ColorScheme(7, 0xffcccccc, 0, 0xff000000);

    public BaseTextRenderer(ColorScheme scheme) {
        if (scheme == null) {
            scheme = defaultColorScheme;
        }
        setDefaultColors(scheme.getForeColorIndex(), scheme.getForeColor(),
                scheme.getBackColorIndex(), scheme.getBackColor());
    }

    private void setDefaultColors(int forePaintIndex, int forePaintColor,
            int backPaintIndex, int backPaintColor) {
        mForePaint[forePaintIndex] = forePaintColor;
        mBackPaint[backPaintIndex] = backPaintColor;
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

    public Bitmap4x8FontRenderer(Resources resources, ColorScheme scheme) {
        super(scheme);
        int fontResource = AndroidCompat.SDK <= 3 ? R.drawable.atari_small
                : R.drawable.atari_small_nodpi;
        mFont = BitmapFactory.decodeResource(resources,fontResource);
        mPaint = new Paint();
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public float getCharacterWidth() {
        return kCharacterWidth;
    }

    public int getCharacterHeight() {
        return kCharacterHeight;
    }

    public int getTopMargin() {
        return 0;
    }

    public void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, int runWidth, char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor) {
        setColorMatrix(mForePaint[foreColor],
                cursor ? mCursorPaint : mBackPaint[backColor & 7]);
        int destX = (int) x + kCharacterWidth * lineOffset;
        int destY = (int) y;
        Rect srcRect = new Rect();
        Rect destRect = new Rect();
        destRect.top = (destY - kCharacterHeight);
        destRect.bottom = destY;
        for(int i = 0; i < count; i++) {
            // XXX No Unicode support in bitmap font
            char c = (char) (text[i + index] & 0xff);
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
    public PaintRenderer(int fontSize, ColorScheme scheme) {
        super(scheme);
        mTextPaint = new Paint();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) FloatMath.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) FloatMath.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
    }

    public void drawTextRun(Canvas canvas, float x, float y, int lineOffset,
            int runWidth, char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor) {
        if (cursor) {
            mTextPaint.setColor(mCursorPaint);
        } else {
            mTextPaint.setColor(mBackPaint[backColor & 0x7]);
        }
        float left = x + lineOffset * mCharWidth;
        canvas.drawRect(left, y + mCharAscent - mCharDescent,
                left + runWidth * mCharWidth, y,
                mTextPaint);
        boolean bold = ( foreColor & 0x8 ) != 0;
        boolean underline = (backColor & 0x8) != 0;
        if (bold) {
            mTextPaint.setFakeBoldText(true);
        }
        if (underline) {
            mTextPaint.setUnderlineText(true);
        }
        mTextPaint.setColor(mForePaint[foreColor]);
        canvas.drawText(text, index, count, left, y - mCharDescent, mTextPaint);
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

    public float getCharacterWidth() {
        return mCharWidth;
    }

    public int getTopMargin() {
        return mCharDescent;
    }


    private Paint mTextPaint;
    private float mCharWidth;
    private int mCharHeight;
    private int mCharAscent;
    private int mCharDescent;
    private static final char[] EXAMPLE_CHAR = {'X'};
    }

class TermKeyListener {
	/**
	 * This class is responsible for the handling of key events. It consumes
	 * key events and when the key events generate characters, then one or more are made
	 * available for consumption.
	 */

	private final ModifierKey mControlKey;
	private final ModifierKey mFnKey;
	private final ModifierKey mCapsKey;
	private final ModifierKey mAltKey;
	private final int mBackBehavior;
	private final boolean mAllowToggle;
	private final boolean mAppMode;
	private byte[] mCharcodes;
	private boolean mAltSendsEscape;
	private Integer mDeadChar;

	TermKeyListener(int controlKey, int fnKey, int backBehavior, boolean altSendsEscape, boolean appMode) {
		mAppMode = appMode;
		mControlKey = new ModifierKey(controlKey);
		mFnKey = new ModifierKey(fnKey);
		mAltKey = new ModifierKey(KeyEvent.KEYCODE_ALT_LEFT);
		mCapsKey = new ModifierKey(KeyEvent.KEYCODE_CAPS_LOCK);
		mBackBehavior = backBehavior;
		mAllowToggle = false;
		mAltSendsEscape = altSendsEscape;
		this.resetKeys();
	}

	public void handleControlKey(boolean down) {
		mControlKey.handleModifierKey(down);
	}

	public void handleFnKey(boolean down) {
		mFnKey.handleModifierKey(down);
	}
	/**
	 * Resets the KeyStateMachine into its default state
	 * 
	 */
	public void resetKeys() {
		mControlKey.reset();
		mFnKey.reset();
		mCapsKey.reset();
		mAltKey.reset();
		mCharcodes = null;
	}

	/**
	 * Returns the effective state of the metakeys as a bitvector.
	 * 
	 * This method does not change the state of the KeyStateMachine. It strictly
	 * combines the stored modifier key state with the MetaState bitvector
	 * passed in.
	 */

	public int getEffectiveMetaState(int metaState) {
		boolean effectiveCaps = mAllowToggle && mCapsKey.isActive();
		boolean effectiveAlt = mAllowToggle && mAltKey.isActive();
		boolean effectiveCtrl = mAllowToggle && mControlKey.isActive();
		boolean effectiveFn = mAllowToggle && mFnKey.isActive();
		// this construct ors the META states together depending on the booleans
		// for google foo the ? is called the ternary operator.
		// I prefer it because it forces me to supply an alternative to the
		// consequence
		// of the condition.
		return metaState | (effectiveCaps ? KeyEvent.META_SHIFT_MASK : 0)
				| (effectiveAlt ? KeyEvent.META_ALT_MASK : 0)
				| (effectiveCtrl ? KeyEvent.META_CTRL_MASK : 0)
				| (effectiveFn ? KeyEvent.META_FUNCTION_ON : 0);
	}

	/**
	 * returns true when the KeyEvent e is the conclusion of a key sequence and
	 * a character sequence can be generated. The character sequence is then
	 * stored in mCharSequence. When a new KeyEvent sequenced has commenced then
	 * mCharSequence is truncated.
	 * 
	 * @return boolean.
	 */

//	private static final ByteBuffer BYTE_TO_INT = ByteBuffer.allocate(8);
	private static final byte ESC = 0x1b;
	

	private static int packKeyCode(int keyCode) {
		return keyCode + C.KEYCODE_OFFSET;
	}
	
	private static int unpackCharCode(int charCode) {
		return charCode - C.KEYCODE_OFFSET;
	}
	
	private static boolean isPackedCharCode(int charCode) {
		return (charCode >= C.KEYCODE_OFFSET);
	}
	
    private static String handleSpecialKeyCode(int packedCharCode, boolean appMode) {
    	//the keycode is packed by mapControlChar(). Since it doesn't map to a single char, but to a 
    	//String it needs to be handled here.
    	//Depending on the app mode.
    	String code = null;
    	int specialKeyCode = unpackCharCode(packedCharCode) ;
    	if (specialKeyCode >= 0 && specialKeyCode < C.specialKeyCharSeq.length) {            
            if (appMode) {
                code = C.appSpecialKeyCharSeq[specialKeyCode];
            }
            if (code == null) {
                code = C.specialKeyCharSeq[specialKeyCode];
            }
        }
        return code;
    }
	
    public int mapControlChars(int c) {
    	int effectiveChar;
    	if (mControlKey.isActive()) {
    		effectiveChar = mapControlChar(c);
    	} else if (mFnKey.isActive()) {
    		effectiveChar = mapFnChar(c);
    	} else {
    		effectiveChar = c;
    	}
    	return effectiveChar;    		
    }
    
	public int mapControlChar(int charCode) {
        // Search is the control key.
        return
        (charCode >= 'a' && charCode <= 'z') ?
            (char) (charCode - 'a' + '\001') :
        (charCode >= 'A' && charCode <= 'Z') ?
            (char) (charCode - 'A' + '\001') :
        (charCode == ' ' || charCode == '2') ?
            0 :
        (charCode == '[' || charCode == '3') ?
            27 : // ^[ (Esc)
        (charCode == '\\' || charCode == '4') ?
            28 :
        (charCode == ']' || charCode == '5') ?
            29 :
        (charCode == '^' || charCode == '6') ?
            30 : // control-^
        (charCode == '_' || charCode == '7') ?
            31 :
        (charCode == '8') ?
            127 : // DEL
        (charCode == '9') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F11:
        (charCode == '0') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F12:
            charCode;
	}

	private int mapFnChar(int charCode) {
		return (charCode == 'w' || charCode == 'W') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_UP:
         (charCode == 'a' || charCode == 'A') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_LEFT:
         (charCode == 's' || charCode == 'S') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_DOWN:
         (charCode == 'd' || charCode == 'D') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_RIGHT:
         (charCode == 'p' || charCode == 'P') ?
            C.KEYCODE_OFFSET + C.KEYCODE_PAGE_UP:
         (charCode == 'n' || charCode == 'N') ?
            C.KEYCODE_OFFSET + C.KEYCODE_PAGE_DOWN:
         (charCode == 't' || charCode == 'T') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_TAB:
         (charCode == 'l' || charCode == 'L') ?
            '|':
         (charCode == 'u' || charCode == 'U') ?
            '_':
         (charCode == 'e' || charCode == 'E') ?
            27: // ^[ (Esc)
         (charCode == '.') ?
            28: // ^\
         (charCode > '0' && charCode <= '9') ?
            // F1-F9
            (char)(charCode + C.KEYCODE_OFFSET + C.KEYCODE_F1 - 1):
         (charCode == '0') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F10:
         (charCode == 'i' || charCode == 'I') ?
            C.KEYCODE_OFFSET + C.KEYCODE_INSERT:
         (charCode == 'x' || charCode == 'X') ?
            C.KEYCODE_OFFSET + C.KEYCODE_FORWARD_DEL:
         (charCode == 'h' || charCode == 'H') ?
            C.KEYCODE_OFFSET + C.KEYCODE_MOVE_HOME:
         (charCode == 'f' || charCode == 'F') ?
            C.KEYCODE_OFFSET + C.KEYCODE_MOVE_END:
            charCode;
	}

	private boolean ctrlActive(int metaState) {
		return mControlKey.isActive() || ((metaState & KeyEvent.META_CTRL_ON) != 0);
	}

	private boolean fnActive(int metaState) {
		return mFnKey.isActive() || ((metaState & KeyEvent.META_FUNCTION_ON) != 0);
	}

	private static final int NO_CHAR = KeyCharacterMap.COMBINING_ACCENT;

	private int handleDeadKey(KeyEvent e, int metaState, int masks) {
		int charcode;
		int charCode = e.getUnicodeChar(metaState & (~masks));
		if ((charCode & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			mDeadChar = charCode;
			charcode = NO_CHAR;
		} else if (mDeadChar != null) {
			int c = KeyEvent.getDeadChar(mDeadChar, charCode);
			charcode = (c != 0) ? NO_CHAR : c;
			mDeadChar = null;
		} else if (charCode == 0) {
			charcode = NO_CHAR;
		} else {
			charcode = (ctrlActive(metaState) ?
					mapControlChar(charCode) :
				fnActive(metaState) ? mapFnChar(charCode) :
					charCode);
			assert(charcode != NO_CHAR);
		}
		return charcode;
	}
	
	private boolean handleCharEvent(KeyEvent e) {
		int metaState = getEffectiveMetaState(e.getMetaState());
		//The CTRL Key must be masked when using e.getUnicodeChar() for some reason.
		//FIXME Please document why CTRL must be masked.
		//We want to mask the Alt key because of the mAltSendsEscape flag, but only when Alt is pressed.
		boolean prefixEscFlag = (mAltSendsEscape && ((metaState & KeyEvent.META_ALT_MASK) != 0));
		int unicodeMask = KeyEvent.META_CTRL_MASK | (prefixEscFlag ? KeyEvent.META_ALT_MASK : 0);
		byte[] directMapping = lookupDirectMap(packKeyCode(e.getKeyCode()), mAppMode, prefixEscFlag);
		if (directMapping != null) { // don't handle the key event any further when there is a direct map entry.
			mCharcodes = directMapping; 
		} else if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			mCharcodes = new byte[] { (byte) mBackBehavior };
		} else {
			int charCode = handleDeadKey(e, metaState, unicodeMask);
			mCharcodes = lookupDirectMap(charCode, mAppMode, prefixEscFlag);
		}
		return true;
	}
	
	public static byte[] lookupDirectMap(int charCode, boolean appMode, boolean prefixEsc) {
		ArrayList<Byte> escSeq = new ArrayList<Byte>();
		if (isPackedCharCode(charCode)) {
			String data = handleSpecialKeyCode(charCode, appMode);
			if (data != null) {
				try {
					byte[] x = data.getBytes("UTF-8");										
					if (prefixEsc) { escSeq.add(ESC); }
					for (int i=0; i<x.length; i++) {
						escSeq.add(x[i]);
					}
				} catch (UnsupportedEncodingException ex) {
					//ignore
				}
			} else {
				escSeq = null;
			}
		} else if (charCode != NO_CHAR) {
			if (prefixEsc) { escSeq.add(ESC); }
			escSeq.add((byte) charCode);
		} else {
			escSeq = null;
		}
		byte[] data = null;
		if (escSeq != null) {
			data = new byte[escSeq.size()];
			int i = 0;
			for (byte e: escSeq) {
				data[i++] = e;
			}			
		} 
		return data;
	}

	public byte[] getCharSequence() {
		return mCharcodes;
	}
	
	public boolean keyDown(KeyEvent e) {
		final int keycode = e.getKeyCode();
		return mCapsKey.handleModifierKey(keycode, true)
				|| mAltKey.handleModifierKey(keycode, true)
				|| mFnKey.handleModifierKey(keycode, true)
				|| mControlKey.handleModifierKey(keycode, true)
				|| handleCharEvent(e);
	}

	public boolean keyUp(KeyEvent e) {
		final int keycode = e.getKeyCode();
		return mCapsKey.handleModifierKey(keycode, false)
				|| mAltKey.handleModifierKey(keycode, false)
				|| mFnKey.handleModifierKey(keycode, false)
				|| mControlKey.handleModifierKey(keycode, false);
	}
}

/**
 * The state engine for a modifier key. Can be pressed, released, locked, and so
 * on.
 * 
 */
class ModifierKey {

	private int mState;

	private static final int UNPRESSED = 0;

	private static final int PRESSED = 1;

	private static final int RELEASED = 2;

	private static final int USED = 3;

	private static final int LOCKED = 4;

	private final int mKeyCode;

	/**
	 * Construct a modifier key. UNPRESSED by default.
	 * 
	 */
	public ModifierKey(int keyCode) {
		mState = UNPRESSED;
		mKeyCode = keyCode;
	}

	public boolean handleModifierKey(int incomingKeyCode, boolean down) {
		if (incomingKeyCode == mKeyCode) {
			if (down) {
				this.onPress();
			} else {
				this.onRelease();
			}
			return true;
		} else {
			return false;
		}
	}

	public void handleModifierKey(boolean down) {
		this.handleModifierKey(mKeyCode, down);
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

	public void reset() {
		mState = UNPRESSED;
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