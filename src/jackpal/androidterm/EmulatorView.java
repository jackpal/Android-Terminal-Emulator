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

package jackpal.androidterm2;

import android.R;
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import jackpal.androidterm2.model.TextRenderer;
import jackpal.androidterm2.model.UpdateCallback;
import jackpal.androidterm2.session.TermSession;
import jackpal.androidterm2.session.TerminalEmulator;
import jackpal.androidterm2.session.TranscriptScreen;
import jackpal.androidterm2.util.TermSettings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A view on a transcript and a terminal emulator. Displays the text of the
 * transcript and the current cursor position of the terminal emulator.
 */
public class EmulatorView extends View implements GestureDetector.OnGestureListener {

    private final String TAG = "EmulatorView";
    private final boolean LOG_KEY_EVENTS = TermDebug.DEBUG && false;

    private TermSettings mSettings;
    private TermViewFlipper mViewFlipper;

    /**
     * We defer some initialization until we have been layed out in the view
     * hierarchy. The boolean tracks when we know what our size is.
     */
    private boolean mKnownSize;

    private int mVisibleWidth;
    private int mVisibleHeight;
    private Rect mVisibleRect = new Rect();

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

    /**
     * Used to receive data from the remote process.
     */
    private FileOutputStream mTermOut;

    private static final int SCREEN_CHECK_PERIOD = 1000;
    private static final int CURSOR_BLINK_PERIOD = 1000;

    private boolean mCursorVisible = true;

    private boolean mIsSelectingText = false;


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

    private GestureDetector mGestureDetector;
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

    public UpdateCallback getUpdateCallback() {
        return mUpdateNotify;
    }

    public EmulatorView(Context context, TermSession session, TermViewFlipper viewFlipper, DisplayMetrics metrics) {
        super(context);
        commonConstructor(session, viewFlipper);
        setDensity(metrics);
    }

    public void setDensity(DisplayMetrics metrics) {
        mDensity = metrics.density;
        mScaledDensity = metrics.scaledDensity;
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

    public void updatePrefs(TermSettings settings) {
        mSettings = settings;
        setTextSize((int) (mSettings.getFontSize() * mDensity));
        setCursorStyle(mSettings.getCursorStyle(), mSettings.getCursorBlink());
        setUseCookedIME(mSettings.useCookedIME());
        setColors();
    }

    public void setColors() {
        int[] scheme = mSettings.getColorScheme();
        mForeground = scheme[0];
        mBackground = scheme[1];
        updateText();
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
        return new InputConnection() {
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
                try {
                    for(int i = 0; i < n; i++) {
                        char c = text.charAt(i);
                        mapAndSend(c);
                    }
                    mTermOut.flush();
                } catch (IOException e) {
                    Log.e(TAG, "error writing ", e);
                }
            }

            private void mapAndSend(int c) throws IOException {
                int result = mKeyListener.mapControlChar(c);
                if (result < TermKeyListener.KEYCODE_OFFSET) {
                    mTermOut.write(result);
                } else {
                    mKeyListener.handleKeyCode(result - TermKeyListener.KEYCODE_OFFSET, mTermOut, getKeypadApplicationMode());
                }
            }

            public boolean beginBatchEdit() {
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "clearMetaKeyStates " + arg0);
                }
                return false;
            }

            public boolean commitCompletion(CompletionInfo arg0) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "commitCompletion " + arg0);
                }
                return false;
            }

            public boolean endBatchEdit() {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "endBatchEdit");
                }
                mInBatchEdit = false;
                return true;
            }

            public boolean finishComposingText() {
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "getCursorCapsMode(" + arg0 + ")");
                }
                return 0;
            }

            public ExtractedText getExtractedText(ExtractedTextRequest arg0,
                    int arg1) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "getExtractedText" + arg0 + "," + arg1);
                }
                return null;
            }

            public CharSequence getTextAfterCursor(int n, int flags) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "getTextAfterCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mImeBuffer.length() - mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor, mCursor + len);
            }

            public CharSequence getTextBeforeCursor(int n, int flags) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "getTextBeforeCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor-len, mCursor);
            }

            public boolean performContextMenuAction(int arg0) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "performContextMenuAction" + arg0);
                }
                return true;
            }

            public boolean performPrivateCommand(String arg0, Bundle arg1) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "performPrivateCommand" + arg0 + "," + arg1);
                }
                return true;
            }

            public boolean reportFullscreenMode(boolean arg0) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "reportFullscreenMode" + arg0);
                }
                return true;
            }

            public boolean commitCorrection () {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "commitCorrection");
                }
                return true;
            }

            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                clearComposingText();
                sendText(text);
                setImeBuffer("");
                mCursor = 0;
                return true;
            }

            private void clearComposingText() {
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
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "performEditorAction(" + actionCode + ")");
                }
                if (actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // The "return" key has been pressed on the IME.
                    sendText("\n");
                }
                return true;
            }

            public boolean sendKeyEvent(KeyEvent event) {
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "setComposingText(\"" + text + "\", " + newCursorPosition + ")");
                }
                setImeBuffer(mImeBuffer.substring(0, mComposingTextStart) +
                    text + mImeBuffer.substring(mComposingTextEnd));
                mComposingTextEnd = mComposingTextStart + text.length();
                mCursor = newCursorPosition > 0 ? mComposingTextEnd + newCursorPosition - 1
                        : mComposingTextStart - newCursorPosition;
                return true;
            }

            public boolean setSelection(int start, int end) {
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
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
                if (TermDebug.LOG_IME) {
                    Log.w(TAG, "getSelectedText " + flags);
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

    public boolean getKeypadApplicationMode() {
        return mEmulator.getKeypadApplicationMode();
    }

    private void commonConstructor(TermSession session, TermViewFlipper viewFlipper) {
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

        initialize(session, viewFlipper);
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
     * @param session The terminal session this view will be displaying
     */
    private void initialize(TermSession session, TermViewFlipper viewFlipper) {
        mTermSession = session;
        mTranscriptScreen = session.getTranscriptScreen();
        mEmulator = session.getEmulator();
        mTermOut = session.getTermOut();

        mViewFlipper = viewFlipper;

        mKeyListener = new TermKeyListener();
        mTextSize = 10;
        mForeground = TermSettings.WHITE;
        mBackground = TermSettings.BLACK;
        updateText();

        requestFocus();
    }

    public TermSession getTermSession() {
        return mTermSession;
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

    public void setUseCookedIME(boolean useCookedIME) {
        mUseCookedIme = useCookedIME;
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
        if (Math.abs(velocityX) > Math.abs(velocityY)) {
            // Assume user wanted side to side movement
            if (velocityX > 0) {
                // Left to right swipe -- previous window
                mViewFlipper.showPrevious();
            } else {
                // Right to left swipe -- next window
                mViewFlipper.showNext();
            }
        } else {
            // TODO: add animation man's (non animated) fling
            mScrollRemainder = 0.0f;
            onScroll(e1, e2, 2 * velocityX, -2 * velocityY);
        }
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
            // Don't intercept the system keys
            return super.onKeyDown(keyCode, event);
        }

        // Translate the keyCode into an ASCII character.

        try {
            mKeyListener.keyDown(keyCode, event, mTermOut,
                    getKeypadApplicationMode());
        } catch (IOException e) {
            // Ignore I/O exceptions
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
        } else if (handleFnKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyUp(keyCode, event);
        }

        mKeyListener.keyUp(keyCode);
        return true;
    }


    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mSettings.getControlKeyCode()) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey " + keyCode);
            }
            mKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    private boolean handleFnKey(int keyCode, boolean down) {
        if (keyCode == mSettings.getFnKeyCode()) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleFnKey " + keyCode);
            }
            mKeyListener.handleFnKey(down);
            return true;
        }
        return false;
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
    }

    private void updateSize(int w, int h) {
        mColumns = Math.max(1, (int) (((float) w) / mCharacterWidth));
        mRows = Math.max(1, h / mCharacterHeight);
        mVisibleColumns = (int) (((float) mVisibleWidth) / mCharacterWidth);

        mTermSession.updateSize(mColumns, mRows);

        // Reset our paging:
        mTopRow = 0;
        mLeftColumn = 0;

        invalidate();
    }

    public void updateSize(boolean force) {
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

    public boolean getSelectingText() {
        return mIsSelectingText;
    }

    public String getSelectedText() {
        return mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2);
    }
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
                jackpal.androidterm2.R.drawable.atari_small);
        mPaint = new Paint();
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public float getCharacterWidth() {
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
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
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

    public float getCharacterWidth() {
        return mCharWidth;
    }


    private Paint mTextPaint;
    private float mCharWidth;
    private int mCharHeight;
    private int mCharAscent;
    private int mCharDescent;
    private static final char[] EXAMPLE_CHAR = {'X'};
    }

/**
 * An ASCII key listener. Supports control characters and escape. Keeps track of
 * the current state of the alt, shift, and control keys.
 */
class TermKeyListener {
    /**
     * Android key codes that are defined in the Android 2.3 API.
     * We want to recognize these codes, because they will be sent to our
     * app when we run on Android 2.3 systems.
     * But we don't want to accidentally use 2.3-specific APIs.
     * So we compile against the Android 1.6 APIs, and have a copy of the codes here.
     */

    /** Key code constant: Unknown key code. */
    public static final int KEYCODE_UNKNOWN         = 0;
    /** Key code constant: Soft Left key.
     * Usually situated below the display on phones and used as a multi-function
     * feature key for selecting a software defined function shown on the bottom left
     * of the display. */
    public static final int KEYCODE_SOFT_LEFT       = 1;
    /** Key code constant: Soft Right key.
     * Usually situated below the display on phones and used as a multi-function
     * feature key for selecting a software defined function shown on the bottom right
     * of the display. */
    public static final int KEYCODE_SOFT_RIGHT      = 2;
    /** Key code constant: Home key.
     * This key is handled by the framework and is never delivered to applications. */
    public static final int KEYCODE_HOME            = 3;
    /** Key code constant: Back key. */
    public static final int KEYCODE_BACK            = 4;
    /** Key code constant: Call key. */
    public static final int KEYCODE_CALL            = 5;
    /** Key code constant: End Call key. */
    public static final int KEYCODE_ENDCALL         = 6;
    /** Key code constant: '0' key. */
    public static final int KEYCODE_0               = 7;
    /** Key code constant: '1' key. */
    public static final int KEYCODE_1               = 8;
    /** Key code constant: '2' key. */
    public static final int KEYCODE_2               = 9;
    /** Key code constant: '3' key. */
    public static final int KEYCODE_3               = 10;
    /** Key code constant: '4' key. */
    public static final int KEYCODE_4               = 11;
    /** Key code constant: '5' key. */
    public static final int KEYCODE_5               = 12;
    /** Key code constant: '6' key. */
    public static final int KEYCODE_6               = 13;
    /** Key code constant: '7' key. */
    public static final int KEYCODE_7               = 14;
    /** Key code constant: '8' key. */
    public static final int KEYCODE_8               = 15;
    /** Key code constant: '9' key. */
    public static final int KEYCODE_9               = 16;
    /** Key code constant: '*' key. */
    public static final int KEYCODE_STAR            = 17;
    /** Key code constant: '#' key. */
    public static final int KEYCODE_POUND           = 18;
    /** Key code constant: Directional Pad Up key.
     * May also be synthesized from trackball motions. */
    public static final int KEYCODE_DPAD_UP         = 19;
    /** Key code constant: Directional Pad Down key.
     * May also be synthesized from trackball motions. */
    public static final int KEYCODE_DPAD_DOWN       = 20;
    /** Key code constant: Directional Pad Left key.
     * May also be synthesized from trackball motions. */
    public static final int KEYCODE_DPAD_LEFT       = 21;
    /** Key code constant: Directional Pad Right key.
     * May also be synthesized from trackball motions. */
    public static final int KEYCODE_DPAD_RIGHT      = 22;
    /** Key code constant: Directional Pad Center key.
     * May also be synthesized from trackball motions. */
    public static final int KEYCODE_DPAD_CENTER     = 23;
    /** Key code constant: Volume Up key.
     * Adjusts the speaker volume up. */
    public static final int KEYCODE_VOLUME_UP       = 24;
    /** Key code constant: Volume Down key.
     * Adjusts the speaker volume down. */
    public static final int KEYCODE_VOLUME_DOWN     = 25;
    /** Key code constant: Power key. */
    public static final int KEYCODE_POWER           = 26;
    /** Key code constant: Camera key.
     * Used to launch a camera application or take pictures. */
    public static final int KEYCODE_CAMERA          = 27;
    /** Key code constant: Clear key. */
    public static final int KEYCODE_CLEAR           = 28;
    /** Key code constant: 'A' key. */
    public static final int KEYCODE_A               = 29;
    /** Key code constant: 'B' key. */
    public static final int KEYCODE_B               = 30;
    /** Key code constant: 'C' key. */
    public static final int KEYCODE_C               = 31;
    /** Key code constant: 'D' key. */
    public static final int KEYCODE_D               = 32;
    /** Key code constant: 'E' key. */
    public static final int KEYCODE_E               = 33;
    /** Key code constant: 'F' key. */
    public static final int KEYCODE_F               = 34;
    /** Key code constant: 'G' key. */
    public static final int KEYCODE_G               = 35;
    /** Key code constant: 'H' key. */
    public static final int KEYCODE_H               = 36;
    /** Key code constant: 'I' key. */
    public static final int KEYCODE_I               = 37;
    /** Key code constant: 'J' key. */
    public static final int KEYCODE_J               = 38;
    /** Key code constant: 'K' key. */
    public static final int KEYCODE_K               = 39;
    /** Key code constant: 'L' key. */
    public static final int KEYCODE_L               = 40;
    /** Key code constant: 'M' key. */
    public static final int KEYCODE_M               = 41;
    /** Key code constant: 'N' key. */
    public static final int KEYCODE_N               = 42;
    /** Key code constant: 'O' key. */
    public static final int KEYCODE_O               = 43;
    /** Key code constant: 'P' key. */
    public static final int KEYCODE_P               = 44;
    /** Key code constant: 'Q' key. */
    public static final int KEYCODE_Q               = 45;
    /** Key code constant: 'R' key. */
    public static final int KEYCODE_R               = 46;
    /** Key code constant: 'S' key. */
    public static final int KEYCODE_S               = 47;
    /** Key code constant: 'T' key. */
    public static final int KEYCODE_T               = 48;
    /** Key code constant: 'U' key. */
    public static final int KEYCODE_U               = 49;
    /** Key code constant: 'V' key. */
    public static final int KEYCODE_V               = 50;
    /** Key code constant: 'W' key. */
    public static final int KEYCODE_W               = 51;
    /** Key code constant: 'X' key. */
    public static final int KEYCODE_X               = 52;
    /** Key code constant: 'Y' key. */
    public static final int KEYCODE_Y               = 53;
    /** Key code constant: 'Z' key. */
    public static final int KEYCODE_Z               = 54;
    /** Key code constant: ',' key. */
    public static final int KEYCODE_COMMA           = 55;
    /** Key code constant: '.' key. */
    public static final int KEYCODE_PERIOD          = 56;
    /** Key code constant: Left Alt modifier key. */
    public static final int KEYCODE_ALT_LEFT        = 57;
    /** Key code constant: Right Alt modifier key. */
    public static final int KEYCODE_ALT_RIGHT       = 58;
    /** Key code constant: Left Shift modifier key. */
    public static final int KEYCODE_SHIFT_LEFT      = 59;
    /** Key code constant: Right Shift modifier key. */
    public static final int KEYCODE_SHIFT_RIGHT     = 60;
    /** Key code constant: Tab key. */
    public static final int KEYCODE_TAB             = 61;
    /** Key code constant: Space key. */
    public static final int KEYCODE_SPACE           = 62;
    /** Key code constant: Symbol modifier key.
     * Used to enter alternate symbols. */
    public static final int KEYCODE_SYM             = 63;
    /** Key code constant: Explorer special function key.
     * Used to launch a browser application. */
    public static final int KEYCODE_EXPLORER        = 64;
    /** Key code constant: Envelope special function key.
     * Used to launch a mail application. */
    public static final int KEYCODE_ENVELOPE        = 65;
    /** Key code constant: Enter key. */
    public static final int KEYCODE_ENTER           = 66;
    /** Key code constant: Backspace key.
     * Deletes characters before the insertion point, unlike {@link #KEYCODE_FORWARD_DEL}. */
    public static final int KEYCODE_DEL             = 67;
    /** Key code constant: '`' (backtick) key. */
    public static final int KEYCODE_GRAVE           = 68;
    /** Key code constant: '-'. */
    public static final int KEYCODE_MINUS           = 69;
    /** Key code constant: '=' key. */
    public static final int KEYCODE_EQUALS          = 70;
    /** Key code constant: '[' key. */
    public static final int KEYCODE_LEFT_BRACKET    = 71;
    /** Key code constant: ']' key. */
    public static final int KEYCODE_RIGHT_BRACKET   = 72;
    /** Key code constant: '\' key. */
    public static final int KEYCODE_BACKSLASH       = 73;
    /** Key code constant: ';' key. */
    public static final int KEYCODE_SEMICOLON       = 74;
    /** Key code constant: ''' (apostrophe) key. */
    public static final int KEYCODE_APOSTROPHE      = 75;
    /** Key code constant: '/' key. */
    public static final int KEYCODE_SLASH           = 76;
    /** Key code constant: '@' key. */
    public static final int KEYCODE_AT              = 77;
    /** Key code constant: Number modifier key.
     * Used to enter numeric symbols.
     * This key is not Num Lock; it is more like {@link #KEYCODE_ALT_LEFT} and is
     * interpreted as an ALT key by {@link android.text.method.MetaKeyKeyListener}. */
    public static final int KEYCODE_NUM             = 78;
    /** Key code constant: Headset Hook key.
     * Used to hang up calls and stop media. */
    public static final int KEYCODE_HEADSETHOOK     = 79;
    /** Key code constant: Camera Focus key.
     * Used to focus the camera. */
    public static final int KEYCODE_FOCUS           = 80;   // *Camera* focus
    /** Key code constant: '+' key. */
    public static final int KEYCODE_PLUS            = 81;
    /** Key code constant: Menu key. */
    public static final int KEYCODE_MENU            = 82;
    /** Key code constant: Notification key. */
    public static final int KEYCODE_NOTIFICATION    = 83;
    /** Key code constant: Search key. */
    public static final int KEYCODE_SEARCH          = 84;
    /** Key code constant: Play/Pause media key. */
    public static final int KEYCODE_MEDIA_PLAY_PAUSE= 85;
    /** Key code constant: Stop media key. */
    public static final int KEYCODE_MEDIA_STOP      = 86;
    /** Key code constant: Play Next media key. */
    public static final int KEYCODE_MEDIA_NEXT      = 87;
    /** Key code constant: Play Previous media key. */
    public static final int KEYCODE_MEDIA_PREVIOUS  = 88;
    /** Key code constant: Rewind media key. */
    public static final int KEYCODE_MEDIA_REWIND    = 89;
    /** Key code constant: Fast Forward media key. */
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    /** Key code constant: Mute key.
     * Mutes the microphone, unlike {@link #KEYCODE_VOLUME_MUTE}. */
    public static final int KEYCODE_MUTE            = 91;
    /** Key code constant: Page Up key. */
    public static final int KEYCODE_PAGE_UP         = 92;
    /** Key code constant: Page Down key. */
    public static final int KEYCODE_PAGE_DOWN       = 93;
    /** Key code constant: Picture Symbols modifier key.
     * Used to switch symbol sets (Emoji, Kao-moji). */
    public static final int KEYCODE_PICTSYMBOLS     = 94;   // switch symbol-sets (Emoji,Kao-moji)
    /** Key code constant: Switch Charset modifier key.
     * Used to switch character sets (Kanji, Katakana). */
    public static final int KEYCODE_SWITCH_CHARSET  = 95;   // switch char-sets (Kanji,Katakana)
    /** Key code constant: A Button key.
     * On a game controller, the A button should be either the button labeled A
     * or the first button on the upper row of controller buttons. */
    public static final int KEYCODE_BUTTON_A        = 96;
    /** Key code constant: B Button key.
     * On a game controller, the B button should be either the button labeled B
     * or the second button on the upper row of controller buttons. */
    public static final int KEYCODE_BUTTON_B        = 97;
    /** Key code constant: C Button key.
     * On a game controller, the C button should be either the button labeled C
     * or the third button on the upper row of controller buttons. */
    public static final int KEYCODE_BUTTON_C        = 98;
    /** Key code constant: X Button key.
     * On a game controller, the X button should be either the button labeled X
     * or the first button on the lower row of controller buttons. */
    public static final int KEYCODE_BUTTON_X        = 99;
    /** Key code constant: Y Button key.
     * On a game controller, the Y button should be either the button labeled Y
     * or the second button on the lower row of controller buttons. */
    public static final int KEYCODE_BUTTON_Y        = 100;
    /** Key code constant: Z Button key.
     * On a game controller, the Z button should be either the button labeled Z
     * or the third button on the lower row of controller buttons. */
    public static final int KEYCODE_BUTTON_Z        = 101;
    /** Key code constant: L1 Button key.
     * On a game controller, the L1 button should be either the button labeled L1 (or L)
     * or the top left trigger button. */
    public static final int KEYCODE_BUTTON_L1       = 102;
    /** Key code constant: R1 Button key.
     * On a game controller, the R1 button should be either the button labeled R1 (or R)
     * or the top right trigger button. */
    public static final int KEYCODE_BUTTON_R1       = 103;
    /** Key code constant: L2 Button key.
     * On a game controller, the L2 button should be either the button labeled L2
     * or the bottom left trigger button. */
    public static final int KEYCODE_BUTTON_L2       = 104;
    /** Key code constant: R2 Button key.
     * On a game controller, the R2 button should be either the button labeled R2
     * or the bottom right trigger button. */
    public static final int KEYCODE_BUTTON_R2       = 105;
    /** Key code constant: Left Thumb Button key.
     * On a game controller, the left thumb button indicates that the left (or only)
     * joystick is pressed. */
    public static final int KEYCODE_BUTTON_THUMBL   = 106;
    /** Key code constant: Right Thumb Button key.
     * On a game controller, the right thumb button indicates that the right
     * joystick is pressed. */
    public static final int KEYCODE_BUTTON_THUMBR   = 107;
    /** Key code constant: Start Button key.
     * On a game controller, the button labeled Start. */
    public static final int KEYCODE_BUTTON_START    = 108;
    /** Key code constant: Select Button key.
     * On a game controller, the button labeled Select. */
    public static final int KEYCODE_BUTTON_SELECT   = 109;
    /** Key code constant: Mode Button key.
     * On a game controller, the button labeled Mode. */
    public static final int KEYCODE_BUTTON_MODE     = 110;
    /** Key code constant: Escape key. */
    public static final int KEYCODE_ESCAPE          = 111;
    /** Key code constant: Forward Delete key.
     * Deletes characters ahead of the insertion point, unlike {@link #KEYCODE_DEL}. */
    public static final int KEYCODE_FORWARD_DEL     = 112;
    /** Key code constant: Left Control modifier key. */
    public static final int KEYCODE_CTRL_LEFT       = 113;
    /** Key code constant: Right Control modifier key. */
    public static final int KEYCODE_CTRL_RIGHT      = 114;
    /** Key code constant: Caps Lock modifier key. */
    public static final int KEYCODE_CAPS_LOCK       = 115;
    /** Key code constant: Scroll Lock key. */
    public static final int KEYCODE_SCROLL_LOCK     = 116;
    /** Key code constant: Left Meta modifier key. */
    public static final int KEYCODE_META_LEFT       = 117;
    /** Key code constant: Right Meta modifier key. */
    public static final int KEYCODE_META_RIGHT      = 118;
    /** Key code constant: Function modifier key. */
    public static final int KEYCODE_FUNCTION        = 119;
    /** Key code constant: System Request / Print Screen key. */
    public static final int KEYCODE_SYSRQ           = 120;
    /** Key code constant: Break / Pause key. */
    public static final int KEYCODE_BREAK           = 121;
    /** Key code constant: Home Movement key.
     * Used for scrolling or moving the cursor around to the start of a line
     * or to the top of a list. */
    public static final int KEYCODE_MOVE_HOME       = 122;
    /** Key code constant: End Movement key.
     * Used for scrolling or moving the cursor around to the end of a line
     * or to the bottom of a list. */
    public static final int KEYCODE_MOVE_END        = 123;
    /** Key code constant: Insert key.
     * Toggles insert / overwrite edit mode. */
    public static final int KEYCODE_INSERT          = 124;
    /** Key code constant: Forward key.
     * Navigates forward in the history stack.  Complement of {@link #KEYCODE_BACK}. */
    public static final int KEYCODE_FORWARD         = 125;
    /** Key code constant: Play media key. */
    public static final int KEYCODE_MEDIA_PLAY      = 126;
    /** Key code constant: Pause media key. */
    public static final int KEYCODE_MEDIA_PAUSE     = 127;
    /** Key code constant: Close media key.
     * May be used to close a CD tray, for example. */
    public static final int KEYCODE_MEDIA_CLOSE     = 128;
    /** Key code constant: Eject media key.
     * May be used to eject a CD tray, for example. */
    public static final int KEYCODE_MEDIA_EJECT     = 129;
    /** Key code constant: Record media key. */
    public static final int KEYCODE_MEDIA_RECORD    = 130;
    /** Key code constant: F1 key. */
    public static final int KEYCODE_F1              = 131;
    /** Key code constant: F2 key. */
    public static final int KEYCODE_F2              = 132;
    /** Key code constant: F3 key. */
    public static final int KEYCODE_F3              = 133;
    /** Key code constant: F4 key. */
    public static final int KEYCODE_F4              = 134;
    /** Key code constant: F5 key. */
    public static final int KEYCODE_F5              = 135;
    /** Key code constant: F6 key. */
    public static final int KEYCODE_F6              = 136;
    /** Key code constant: F7 key. */
    public static final int KEYCODE_F7              = 137;
    /** Key code constant: F8 key. */
    public static final int KEYCODE_F8              = 138;
    /** Key code constant: F9 key. */
    public static final int KEYCODE_F9              = 139;
    /** Key code constant: F10 key. */
    public static final int KEYCODE_F10             = 140;
    /** Key code constant: F11 key. */
    public static final int KEYCODE_F11             = 141;
    /** Key code constant: F12 key. */
    public static final int KEYCODE_F12             = 142;
    /** Key code constant: Num Lock modifier key.
     * This is the Num Lock key; it is different from {@link #KEYCODE_NUM}.
     * This key generally modifies the behavior of other keys on the numeric keypad. */
    public static final int KEYCODE_NUM_LOCK        = 143;
    /** Key code constant: Numeric keypad '0' key. */
    public static final int KEYCODE_NUMPAD_0        = 144;
    /** Key code constant: Numeric keypad '1' key. */
    public static final int KEYCODE_NUMPAD_1        = 145;
    /** Key code constant: Numeric keypad '2' key. */
    public static final int KEYCODE_NUMPAD_2        = 146;
    /** Key code constant: Numeric keypad '3' key. */
    public static final int KEYCODE_NUMPAD_3        = 147;
    /** Key code constant: Numeric keypad '4' key. */
    public static final int KEYCODE_NUMPAD_4        = 148;
    /** Key code constant: Numeric keypad '5' key. */
    public static final int KEYCODE_NUMPAD_5        = 149;
    /** Key code constant: Numeric keypad '6' key. */
    public static final int KEYCODE_NUMPAD_6        = 150;
    /** Key code constant: Numeric keypad '7' key. */
    public static final int KEYCODE_NUMPAD_7        = 151;
    /** Key code constant: Numeric keypad '8' key. */
    public static final int KEYCODE_NUMPAD_8        = 152;
    /** Key code constant: Numeric keypad '9' key. */
    public static final int KEYCODE_NUMPAD_9        = 153;
    /** Key code constant: Numeric keypad '/' key (for division). */
    public static final int KEYCODE_NUMPAD_DIVIDE   = 154;
    /** Key code constant: Numeric keypad '*' key (for multiplication). */
    public static final int KEYCODE_NUMPAD_MULTIPLY = 155;
    /** Key code constant: Numeric keypad '-' key (for subtraction). */
    public static final int KEYCODE_NUMPAD_SUBTRACT = 156;
    /** Key code constant: Numeric keypad '+' key (for addition). */
    public static final int KEYCODE_NUMPAD_ADD      = 157;
    /** Key code constant: Numeric keypad '.' key (for decimals or digit grouping). */
    public static final int KEYCODE_NUMPAD_DOT      = 158;
    /** Key code constant: Numeric keypad ',' key (for decimals or digit grouping). */
    public static final int KEYCODE_NUMPAD_COMMA    = 159;
    /** Key code constant: Numeric keypad Enter key. */
    public static final int KEYCODE_NUMPAD_ENTER    = 160;
    /** Key code constant: Numeric keypad '=' key. */
    public static final int KEYCODE_NUMPAD_EQUALS   = 161;
    /** Key code constant: Numeric keypad '(' key. */
    public static final int KEYCODE_NUMPAD_LEFT_PAREN = 162;
    /** Key code constant: Numeric keypad ')' key. */
    public static final int KEYCODE_NUMPAD_RIGHT_PAREN = 163;
    /** Key code constant: Volume Mute key.
     * Mutes the speaker, unlike {@link #KEYCODE_MUTE}.
     * This key should normally be implemented as a toggle such that the first press
     * mutes the speaker and the second press restores the original volume. */
    public static final int KEYCODE_VOLUME_MUTE     = 164;
    /** Key code constant: Info key.
     * Common on TV remotes to show additional information related to what is
     * currently being viewed. */
    public static final int KEYCODE_INFO            = 165;
    /** Key code constant: Channel up key.
     * On TV remotes, increments the television channel. */
    public static final int KEYCODE_CHANNEL_UP      = 166;
    /** Key code constant: Channel down key.
     * On TV remotes, decrements the television channel. */
    public static final int KEYCODE_CHANNEL_DOWN    = 167;
    /** Key code constant: Zoom in key. */
    public static final int KEYCODE_ZOOM_IN         = 168;
    /** Key code constant: Zoom out key. */
    public static final int KEYCODE_ZOOM_OUT        = 169;
    /** Key code constant: TV key.
     * On TV remotes, switches to viewing live TV. */
    public static final int KEYCODE_TV              = 170;
    /** Key code constant: Window key.
     * On TV remotes, toggles picture-in-picture mode or other windowing functions. */
    public static final int KEYCODE_WINDOW          = 171;
    /** Key code constant: Guide key.
     * On TV remotes, shows a programming guide. */
    public static final int KEYCODE_GUIDE           = 172;
    /** Key code constant: DVR key.
     * On some TV remotes, switches to a DVR mode for recorded shows. */
    public static final int KEYCODE_DVR             = 173;
    /** Key code constant: Bookmark key.
     * On some TV remotes, bookmarks content or web pages. */
    public static final int KEYCODE_BOOKMARK        = 174;
    /** Key code constant: Toggle captions key.
     * Switches the mode for closed-captioning text, for example during television shows. */
    public static final int KEYCODE_CAPTIONS        = 175;
    /** Key code constant: Settings key.
     * Starts the system settings activity. */
    public static final int KEYCODE_SETTINGS        = 176;
    /** Key code constant: TV power key.
     * On TV remotes, toggles the power on a television screen. */
    public static final int KEYCODE_TV_POWER        = 177;
    /** Key code constant: TV input key.
     * On TV remotes, switches the input on a television screen. */
    public static final int KEYCODE_TV_INPUT        = 178;
    /** Key code constant: Set-top-box power key.
     * On TV remotes, toggles the power on an external Set-top-box. */
    public static final int KEYCODE_STB_POWER       = 179;
    /** Key code constant: Set-top-box input key.
     * On TV remotes, switches the input mode on an external Set-top-box. */
    public static final int KEYCODE_STB_INPUT       = 180;
    /** Key code constant: A/V Receiver power key.
     * On TV remotes, toggles the power on an external A/V Receiver. */
    public static final int KEYCODE_AVR_POWER       = 181;
    /** Key code constant: A/V Receiver input key.
     * On TV remotes, switches the input mode on an external A/V Receiver. */
    public static final int KEYCODE_AVR_INPUT       = 182;
    /** Key code constant: Red "programmable" key.
     * On TV remotes, acts as a contextual/programmable key. */
    public static final int KEYCODE_PROG_RED        = 183;
    /** Key code constant: Green "programmable" key.
     * On TV remotes, actsas a contextual/programmable key. */
    public static final int KEYCODE_PROG_GREEN      = 184;
    /** Key code constant: Yellow "programmable" key.
     * On TV remotes, acts as a contextual/programmable key. */
    public static final int KEYCODE_PROG_YELLOW     = 185;
    /** Key code constant: Blue "programmable" key.
     * On TV remotes, acts as a contextual/programmable key. */
    public static final int KEYCODE_PROG_BLUE       = 186;

    private static final int LAST_KEYCODE           = KEYCODE_PROG_BLUE;

    private String[] mKeyCodes = new String[256];
    private String[] mAppKeyCodes = new String[256];

    private void initKeyCodes() {
        mKeyCodes[KEYCODE_DPAD_CENTER] = "\015";
        mKeyCodes[KEYCODE_DPAD_UP] = "\033[A";
        mKeyCodes[KEYCODE_DPAD_DOWN] = "\033[B";
        mKeyCodes[KEYCODE_DPAD_RIGHT] = "\033[C";
        mKeyCodes[KEYCODE_DPAD_LEFT] = "\033[D";
        mKeyCodes[KEYCODE_F1] = "\033[OP";
        mKeyCodes[KEYCODE_F2] = "\033[OQ";
        mKeyCodes[KEYCODE_F3] = "\033[OR";
        mKeyCodes[KEYCODE_F4] = "\033[OS";
        mKeyCodes[KEYCODE_F5] = "\033[15~";
        mKeyCodes[KEYCODE_F6] = "\033[17~";
        mKeyCodes[KEYCODE_F7] = "\033[18~";
        mKeyCodes[KEYCODE_F8] = "\033[19~";
        mKeyCodes[KEYCODE_F9] = "\033[20~";
        mKeyCodes[KEYCODE_F10] = "\033[21~";
        mKeyCodes[KEYCODE_F11] = "\033[23~";
        mKeyCodes[KEYCODE_F12] = "\033[24~";
        mKeyCodes[KEYCODE_SYSRQ] = "\033[32~"; // Sys Request / Print
        // Is this Scroll lock? mKeyCodes[Cancel] = "\033[33~";
        mKeyCodes[KEYCODE_BREAK] = "\033[34~"; // Pause/Break

        mKeyCodes[KEYCODE_TAB] = "\011";
        mKeyCodes[KEYCODE_ENTER] = "\015";
        mKeyCodes[KEYCODE_ESCAPE] = "\033";

        mKeyCodes[KEYCODE_INSERT] = "\033[2~";
        mKeyCodes[KEYCODE_FORWARD_DEL] = "\033[3~";
        mKeyCodes[KEYCODE_MOVE_HOME] = "\033[1~";
        mKeyCodes[KEYCODE_MOVE_END] = "\033[4~";
        mKeyCodes[KEYCODE_PAGE_UP] = "\033[5~";
        mKeyCodes[KEYCODE_PAGE_DOWN] = "\033[6~";
        mKeyCodes[KEYCODE_DEL]= "\177";
        mKeyCodes[KEYCODE_NUM_LOCK] = "\033OP";
        mKeyCodes[KEYCODE_NUMPAD_DIVIDE] = "/";
        mKeyCodes[KEYCODE_NUMPAD_MULTIPLY] = "*";
        mKeyCodes[KEYCODE_NUMPAD_SUBTRACT] = "-";
        mKeyCodes[KEYCODE_NUMPAD_ADD] = "+";
        mKeyCodes[KEYCODE_NUMPAD_ENTER] = "\015";
        mKeyCodes[KEYCODE_NUMPAD_EQUALS] = "=";
        mKeyCodes[KEYCODE_NUMPAD_DOT] = ".";
        mKeyCodes[KEYCODE_NUMPAD_COMMA] = ",";
        mKeyCodes[KEYCODE_NUMPAD_0] = "0";
        mKeyCodes[KEYCODE_NUMPAD_1] = "1";
        mKeyCodes[KEYCODE_NUMPAD_2] = "2";
        mKeyCodes[KEYCODE_NUMPAD_3] = "3";
        mKeyCodes[KEYCODE_NUMPAD_4] = "4";
        mKeyCodes[KEYCODE_NUMPAD_5] = "5";
        mKeyCodes[KEYCODE_NUMPAD_6] = "6";
        mKeyCodes[KEYCODE_NUMPAD_7] = "7";
        mKeyCodes[KEYCODE_NUMPAD_8] = "8";
        mKeyCodes[KEYCODE_NUMPAD_9] = "9";

        mAppKeyCodes[KEYCODE_DPAD_UP] = "\033OA";
        mAppKeyCodes[KEYCODE_DPAD_DOWN] = "\033OB";
        mAppKeyCodes[KEYCODE_DPAD_RIGHT] = "\033OC";
        mAppKeyCodes[KEYCODE_DPAD_LEFT] = "\033OD";
        mAppKeyCodes[KEYCODE_NUMPAD_DIVIDE] = "\033Oo";
        mAppKeyCodes[KEYCODE_NUMPAD_MULTIPLY] = "\033Oj";
        mAppKeyCodes[KEYCODE_NUMPAD_SUBTRACT] = "\033Om";
        mAppKeyCodes[KEYCODE_NUMPAD_ADD] = "\033Ok";
        mAppKeyCodes[KEYCODE_NUMPAD_ENTER] = "\033OM";
        mAppKeyCodes[KEYCODE_NUMPAD_EQUALS] = "\033OX";
        mAppKeyCodes[KEYCODE_NUMPAD_DOT] = "\033On";
        mAppKeyCodes[KEYCODE_NUMPAD_COMMA] = "\033Ol";
        mAppKeyCodes[KEYCODE_NUMPAD_0] = "\033Op";
        mAppKeyCodes[KEYCODE_NUMPAD_1] = "\033Oq";
        mAppKeyCodes[KEYCODE_NUMPAD_2] = "\033Or";
        mAppKeyCodes[KEYCODE_NUMPAD_3] = "\033Os";
        mAppKeyCodes[KEYCODE_NUMPAD_4] = "\033Ot";
        mAppKeyCodes[KEYCODE_NUMPAD_5] = "\033Ou";
        mAppKeyCodes[KEYCODE_NUMPAD_6] = "\033Ov";
        mAppKeyCodes[KEYCODE_NUMPAD_7] = "\033Ow";
        mAppKeyCodes[KEYCODE_NUMPAD_8] = "\033Ox";
        mAppKeyCodes[KEYCODE_NUMPAD_9] = "\033Oy";
    }

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

    private ModifierKey mFnKey = new ModifierKey();

    private boolean mCapsLock;

    static public final int KEYCODE_OFFSET = 1000;

    /**
     * Construct a term key listener.
     *
     */
    public TermKeyListener() {
        initKeyCodes();
    }

    public void handleControlKey(boolean down) {
        if (down) {
            mControlKey.onPress();
        } else {
            mControlKey.onRelease();
        }
    }

    public void handleFnKey(boolean down) {
        if (down) {
            mFnKey.onPress();
        } else {
            mFnKey.onRelease();
        }
    }

    public int mapControlChar(int ch) {
        int result = ch;
        if (mControlKey.isActive()) {
            // Search is the control key.
            if (result >= 'a' && result <= 'z') {
                result = (char) (result - 'a' + '\001');
            } else if (result >= 'A' && result <= 'Z') {
                result = (char) (result - 'A' + '\001');
            } else if (result == ' ' || result == '2') {
                result = 0;
            } else if (result == '[' || result == '3') {
                result = 27; // ^[ (Esc)
            } else if (result == '\\' || result == '4') {
                result = 28;
            } else if (result == ']' || result == '5') {
                result = 29;
            } else if (result == '^' || result == '6') {
                result = 30; // control-^
            } else if (result == '_' || result == '7') {
                result = 31;
            } else if (result == '8') {
                result = 127; // DEL
            } else if (result == '9') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_F11;
            } else if (result == '0') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_F12;
            }
        } else if (mFnKey.isActive()) {
            if (result == 'w' || result == 'W') {
                result = KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_UP;
            } else if (result == 'a' || result == 'A') {
                result = KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_LEFT;
            } else if (result == 's' || result == 'S') {
                result = KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_DOWN;
            } else if (result == 'd' || result == 'D') {
                result = KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_RIGHT;
            } else if (result == 'p' || result == 'P') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_PAGE_UP;
            } else if (result == 'n' || result == 'N') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_PAGE_DOWN;
            } else if (result == 't' || result == 'T') {
                result = KEYCODE_OFFSET + KeyEvent.KEYCODE_TAB;
            } else if (result == 'l' || result == 'L') {
                result = '|';
            } else if (result == 'u' || result == 'U') {
                result = '_';
            } else if (result == 'e' || result == 'E') {
                result = 27; // ^[ (Esc)
            } else if (result == '.') {
                result = 28; // ^\
            } else if (result > '0' && result <= '9') {
                // F1-F9
                result = (char)(result + KEYCODE_OFFSET + TermKeyListener.KEYCODE_F1 - 1);
            } else if (result == '0') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_F10;
            } else if (result == 'i' || result == 'I') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_INSERT;
            } else if (result == 'x' || result == 'X') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_FORWARD_DEL;
            } else if (result == 'h' || result == 'H') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_MOVE_HOME;
            } else if (result == 'f' || result == 'F') {
                result = KEYCODE_OFFSET + TermKeyListener.KEYCODE_MOVE_END;
            }
        }

        if (result > -1) {
            mAltKey.adjustAfterKeypress();
            mCapKey.adjustAfterKeypress();
            mControlKey.adjustAfterKeypress();
            mFnKey.adjustAfterKeypress();
        }

        return result;
    }

    /**
     * Handle a keyDown event.
     *
     * @param keyCode the keycode of the keyDown event
     *
     */
    public void keyDown(int keyCode, KeyEvent event, OutputStream out, boolean appMode) throws IOException {
        if (handleKeyCode(keyCode, out, appMode)) {
            return;
        }
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

        case KEYCODE_CTRL_LEFT:
        case KEYCODE_CTRL_RIGHT:
            mControlKey.onPress();
            break;

        case KEYCODE_CAPS_LOCK:
            if (event.getRepeatCount() == 0) {
                mCapsLock = !mCapsLock;
            }
            break;

        default: {
            result = event.getUnicodeChar(
                   (mCapKey.isActive() || mCapsLock ? KeyEvent.META_SHIFT_ON : 0) |
                   (mAltKey.isActive() ? KeyEvent.META_ALT_ON : 0));
            break;
            }
        }

        result = mapControlChar(result);

        if (result >= KEYCODE_OFFSET) {
            handleKeyCode(result - KEYCODE_OFFSET, out, appMode);
        } else if (result >= 0) {
            out.write(result);
        }
    }

    public boolean handleKeyCode(int keyCode, OutputStream out, boolean appMode) throws IOException {
        if (keyCode >= 0 && keyCode < mKeyCodes.length) {
            String code = null;
            if (appMode) {
                code = mAppKeyCodes[keyCode];
            }
            if (code == null) {
                code = mKeyCodes[keyCode];
            }
            if (code != null) {
                int length = code.length();
                for (int i = 0; i < length; i++) {
                    out.write(code.charAt(i));
                }
                return true;
            }
        }
        return false;
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

        case KEYCODE_CTRL_LEFT:
        case KEYCODE_CTRL_RIGHT:
            mControlKey.onRelease();
            break;

        default:
            // Ignore other keyUps
            break;
        }
    }
}
