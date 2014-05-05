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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

import android.util.Log;

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and
 * state. Emulates a subset of the X Window System xterm terminal, which in turn
 * is an emulator for a subset of the Digital Equipment Corporation vt100
 * terminal. Missing functionality: text attributes (bold, underline, reverse
 * video, color) alternate screen cursor key and keypad escape sequences.
 */
class TerminalEmulator {
    public void setKeyListener(TermKeyListener l) {
        mKeyListener = l;
    }
    private TermKeyListener mKeyListener;
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
     * Stores the characters that appear on the screen of the emulated terminal.
     */
    private TranscriptScreen mMainBuffer;
    private TranscriptScreen mAltBuffer;
    private TranscriptScreen mScreen;

    /**
     * The terminal session this emulator is bound to.
     */
    private TermSession mSession;

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

    /**
     * Holds OSC arguments, which can be strings.
     */
    private byte[] mOSCArg = new byte[MAX_OSC_STRING_LENGTH];

    private int mOSCArgLength;

    private int mOSCArgTokenizerIndex;

    /**
     * Don't know what the actual limit is, this seems OK for now.
     */
    private static final int MAX_OSC_STRING_LENGTH = 512;

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
     * Escape processing state: ESC %
     */
    private static final int ESC_PERCENT = 7;

    /**
     * Escape processing state: ESC ] (AKA OSC - Operating System Controls)
     */
    private static final int ESC_RIGHT_SQUARE_BRACKET = 8;

    /**
     * Escape processing state: ESC ] (AKA OSC - Operating System Controls)
     */
    private static final int ESC_RIGHT_SQUARE_BRACKET_ESC = 9;

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

    private int mSavedEffect;

    private int mSavedDecFlags_DECSC_DECRC;


    // DecSet booleans

    /**
     * This mask indicates 132-column mode is set. (As opposed to 80-column
     * mode.)
     */
    private static final int K_132_COLUMN_MODE_MASK = 1 << 3;

    /**
     * DECSCNM - set means reverse video (light background.)
     */
    private static final int K_REVERSE_VIDEO_MASK = 1 << 5;

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
     * This mask indicates that the cursor should be shown. DECTCEM
     */

    private static final int K_SHOW_CURSOR_MASK = 1 << 25;

    /** This mask is the subset of DecSet bits that are saved / restored by
     * the DECSC / DECRC commands
     */
    private static final int K_DECSC_DECRC_MASK =
            K_ORIGIN_MODE_MASK | K_WRAPAROUND_MODE_MASK;

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

    /**
     * The current DECSET mouse tracking mode, zero for no mouse tracking.
     */
    private int mMouseTrackingMode;

    // Modes set with Set Mode / Reset Mode

    /**
     * True if insert mode (as opposed to replace mode) is active. In insert
     * mode new characters are inserted, pushing existing text to the right.
     */
    private boolean mInsertMode;

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
     * The width of the last emitted spacing character.  Used to place
     * combining characters into the correct column.
     */
    private int mLastEmittedCharWidth = 0;

    /**
     * True if we just auto-wrapped and no character has been emitted on this
     * line yet.  Used to ensure combining characters following a character
     * at the edge of the screen are stored in the proper place.
     */
    private boolean mJustWrapped = false;

    /**
     * Used for debugging, counts how many chars have been processed.
     */
    private int mProcessedCharCount;

    /**
     * Foreground color, 0..255
     */
    private int mForeColor;
    private int mDefaultForeColor;

    /**
     * Background color, 0..255
     */
    private int mBackColor;
    private int mDefaultBackColor;

    /**
     * Current TextStyle effect
     */
    private int mEffect;

    private boolean mbKeypadApplicationMode;

    /** false == G0, true == G1 */
    private boolean mAlternateCharSet;

    private final static int CHAR_SET_UK = 0;
    private final static int CHAR_SET_ASCII = 1;
    private final static int CHAR_SET_SPECIAL_GRAPHICS = 2;
    private final static int CHAR_SET_ALT_STANDARD = 3;
    private final static int CHAR_SET_ALT_SPECIAL_GRAPICS = 4;

    /** What is the current graphics character set. [0] == G0, [1] == G1 */
    private int[] mCharSet = new int[2];

    /** Derived from mAlternateCharSet and mCharSet.
     *  True if we're supposed to be drawing the special graphics.
     */
    private boolean mUseAlternateCharSet;

    /**
     * Special graphics character set
     */
    private static final char[] mSpecialGraphicsCharMap = new char[128];
    static {
        for (char i = 0; i < 128; ++i) {
            mSpecialGraphicsCharMap[i] = i;
        }
        mSpecialGraphicsCharMap['_'] = ' ';	// Blank
        mSpecialGraphicsCharMap['b'] = 0x2409;	// Tab
        mSpecialGraphicsCharMap['c'] = 0x240C;	// Form feed
        mSpecialGraphicsCharMap['d'] = 0x240D;	// Carriage return
        mSpecialGraphicsCharMap['e'] = 0x240A;	// Line feed
        mSpecialGraphicsCharMap['h'] = 0x2424;	// New line
        mSpecialGraphicsCharMap['i'] = 0x240B;	// Vertical tab/"lantern"
        mSpecialGraphicsCharMap['}'] = 0x00A3;	// Pound sterling symbol
        mSpecialGraphicsCharMap['f'] = 0x00B0;	// Degree symbol
        mSpecialGraphicsCharMap['`'] = 0x2B25;	// Diamond
        mSpecialGraphicsCharMap['~'] = 0x2022;	// Bullet point
        mSpecialGraphicsCharMap['y'] = 0x2264;	// Less-than-or-equals sign (<=)
        mSpecialGraphicsCharMap['|'] = 0x2260;	// Not equals sign (!=)
        mSpecialGraphicsCharMap['z'] = 0x2265;	// Greater-than-or-equals sign (>=)
        mSpecialGraphicsCharMap['g'] = 0x00B1;	// Plus-or-minus sign (+/-)
        mSpecialGraphicsCharMap['{'] = 0x03C0;	// Lowercase Greek letter pi
        mSpecialGraphicsCharMap['.'] = 0x25BC;	// Down arrow
        mSpecialGraphicsCharMap[','] = 0x25C0;	// Left arrow
        mSpecialGraphicsCharMap['+'] = 0x25B6;	// Right arrow
        mSpecialGraphicsCharMap['-'] = 0x25B2;	// Up arrow
        mSpecialGraphicsCharMap['h'] = '#';	// Board of squares
        mSpecialGraphicsCharMap['a'] = 0x2592;	// Checkerboard
        mSpecialGraphicsCharMap['0'] = 0x2588;	// Solid block
        mSpecialGraphicsCharMap['q'] = 0x2500;	// Horizontal line (box drawing)
        mSpecialGraphicsCharMap['x'] = 0x2502;	// Vertical line (box drawing)
        mSpecialGraphicsCharMap['m'] = 0x2514;	// Lower left hand corner (box drawing)
        mSpecialGraphicsCharMap['j'] = 0x2518;	// Lower right hand corner (box drawing)
        mSpecialGraphicsCharMap['l'] = 0x250C;	// Upper left hand corner (box drawing)
        mSpecialGraphicsCharMap['k'] = 0x2510;	// Upper right hand corner (box drawing)
        mSpecialGraphicsCharMap['w'] = 0x252C;	// T pointing downwards (box drawing)
        mSpecialGraphicsCharMap['u'] = 0x2524;	// T pointing leftwards (box drawing)
        mSpecialGraphicsCharMap['t'] = 0x251C;	// T pointing rightwards (box drawing)
        mSpecialGraphicsCharMap['v'] = 0x2534;	// T pointing upwards (box drawing)
        mSpecialGraphicsCharMap['n'] = 0x253C;	// Large plus/lines crossing (box drawing)
        mSpecialGraphicsCharMap['o'] = 0x23BA;	// Horizontal scanline 1
        mSpecialGraphicsCharMap['p'] = 0x23BB;	// Horizontal scanline 3
        mSpecialGraphicsCharMap['r'] = 0x23BC;	// Horizontal scanline 7
        mSpecialGraphicsCharMap['s'] = 0x23BD;	// Horizontal scanline 9
    }

    /**
     * Used for moving selection up along with the scrolling text
     */
    private int mScrollCounter = 0;

    /**
     * UTF-8 support
     */
    private static final int UNICODE_REPLACEMENT_CHAR = 0xfffd;
    private boolean mDefaultUTF8Mode = false;
    private boolean mUTF8Mode = false;
    private boolean mUTF8EscapeUsed = false;
    private int mUTF8ToFollow = 0;
    private ByteBuffer mUTF8ByteBuffer;
    private CharBuffer mInputCharBuffer;
    private CharsetDecoder mUTF8Decoder;
    private UpdateCallback mUTF8ModeNotify;

    /** This is not accurate, but it makes the terminal more useful on
     * small screens.
     */
    private final static boolean DEFAULT_TO_AUTOWRAP_ENABLED = true;

    /**
     * Construct a terminal emulator that uses the supplied screen
     *
     * @param session the terminal session the emulator is attached to
     * @param screen the screen to render characters into.
     * @param columns the number of columns to emulate
     * @param rows the number of rows to emulate
     * @param scheme the default color scheme of this emulator
     */
    public TerminalEmulator(TermSession session, TranscriptScreen screen, int columns, int rows, ColorScheme scheme) {
        mSession = session;
        mMainBuffer = screen;
        mScreen = mMainBuffer;
        mAltBuffer = new TranscriptScreen(columns, rows, rows, scheme);
        mRows = rows;
        mColumns = columns;
        mTabStop = new boolean[mColumns];

        setColorScheme(scheme);

        mUTF8ByteBuffer = ByteBuffer.allocate(4);
        mInputCharBuffer = CharBuffer.allocate(2);
        mUTF8Decoder = Charset.forName("UTF-8").newDecoder();
        mUTF8Decoder.onMalformedInput(CodingErrorAction.REPLACE);
        mUTF8Decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        reset();
    }

    public TranscriptScreen getScreen() {
        return mScreen;
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

        TranscriptScreen screen = mScreen;
        TranscriptScreen altScreen;
        if (screen != mMainBuffer) {
            altScreen = mMainBuffer;
        } else {
            altScreen = mAltBuffer;
        }

        // Try to resize the screen without getting the transcript
        int[] cursor = { mCursorCol, mCursorRow };
        boolean fastResize = screen.fastResize(columns, rows, cursor);

        GrowableIntArray cursorColor = null;
        String charAtCursor = null;
        GrowableIntArray colors = null;
        String transcriptText = null;
        if (!fastResize) {
            /* Save the character at the cursor (if one exists) and store an
             * ASCII ESC character at the cursor's location
             * This is an epic hack that lets us restore the cursor later...
             */
            cursorColor = new GrowableIntArray(1);
            charAtCursor = screen.getSelectedText(cursorColor, mCursorCol, mCursorRow, mCursorCol, mCursorRow);
            screen.set(mCursorCol, mCursorRow, 27, 0);

            colors = new GrowableIntArray(1024);
            transcriptText = screen.getTranscriptText(colors);
            screen.resize(columns, rows, getStyle());
        }

        boolean altFastResize = true;
        GrowableIntArray altColors = null;
        String altTranscriptText = null;
        if (altScreen != null) {
            altFastResize = altScreen.fastResize(columns, rows, null);

            if (!altFastResize) {
                altColors = new GrowableIntArray(1024);
                altTranscriptText = altScreen.getTranscriptText(altColors);
                altScreen.resize(columns, rows, getStyle());
            }
        }

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
        }

        if (!altFastResize) {
            boolean wasAboutToAutoWrap = mAboutToAutoWrap;

            // Restore the contents of the inactive screen's buffer
            mScreen = altScreen;
            mCursorRow = 0;
            mCursorCol = 0;
            mAboutToAutoWrap = false;

            int end = altTranscriptText.length()-1;
            /* Unlike for the main transcript below, don't trim off trailing
             * newlines -- the alternate transcript lacks a cursor marking, so
             * we might introduce an unwanted vertical shift in the screen
             * contents this way */
            char c, cLow;
            int colorOffset = 0;
            for (int i = 0; i <= end; i++) {
                c = altTranscriptText.charAt(i);
                int style = altColors.at(i-colorOffset);
                if (Character.isHighSurrogate(c)) {
                    cLow = altTranscriptText.charAt(++i);
                    emit(Character.toCodePoint(c, cLow), style);
                    ++colorOffset;
                } else if (c == '\n') {
                    setCursorCol(0);
                    doLinefeed();
                } else {
                    emit(c, style);
                }
            }

            mScreen = screen;
            mAboutToAutoWrap = wasAboutToAutoWrap;
        }

        if (fastResize) {
            // Only need to make sure the cursor is in the right spot
            if (cursor[0] >= 0 && cursor[1] >= 0) {
                mCursorCol = cursor[0];
                mCursorRow = cursor[1];
            } else {
                // Cursor scrolled off screen, reset the cursor to top left
                mCursorCol = 0;
                mCursorRow = 0;
            }

            return;
        }

        mCursorRow = 0;
        mCursorCol = 0;
        mAboutToAutoWrap = false;

        int newCursorRow = -1;
        int newCursorCol = -1;
        int newCursorTranscriptPos = -1;
        int end = transcriptText.length()-1;
        while ((end >= 0) && transcriptText.charAt(end) == '\n') {
            end--;
        }
        char c, cLow;
        int colorOffset = 0;
        for(int i = 0; i <= end; i++) {
            c = transcriptText.charAt(i);
            int style = colors.at(i-colorOffset);
            if (Character.isHighSurrogate(c)) {
                cLow = transcriptText.charAt(++i);
                emit(Character.toCodePoint(c, cLow), style);
                ++colorOffset;
            } else if (c == '\n') {
                setCursorCol(0);
                doLinefeed();
            } else if (c == 27) {
                /* We marked the cursor location with ESC earlier, so this
                   is the place to restore the cursor to */
                newCursorRow = mCursorRow;
                newCursorCol = mCursorCol;
                newCursorTranscriptPos = screen.getActiveRows();
                if (charAtCursor != null && charAtCursor.length() > 0) {
                    // Emit the real character that was in this spot
                    int encodedCursorColor = cursorColor.at(0);
                    emit(charAtCursor.toCharArray(), 0, charAtCursor.length(), encodedCursorColor);
                }
            } else {
                emit(c, style);
            }
        }

        // If we marked a cursor location, move the cursor there now
        if (newCursorRow != -1 && newCursorCol != -1) {
            mCursorRow = newCursorRow;
            mCursorCol = newCursorCol;

            /* Adjust for any scrolling between the time we marked the cursor
               location and now */
            int scrollCount = screen.getActiveRows() - newCursorTranscriptPos;
            if (scrollCount > 0 && scrollCount <= newCursorRow) {
                mCursorRow -= scrollCount;
            } else if (scrollCount > newCursorRow) {
                // Cursor scrolled off screen -- reset to top left corner
                mCursorRow = 0;
                mCursorCol = 0;
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

    public final boolean getReverseVideo() {
        return (mDecFlags & K_REVERSE_VIDEO_MASK) != 0;
    }

    public final boolean getShowCursor() {
        return (mDecFlags & K_SHOW_CURSOR_MASK) != 0;
    }

    public final boolean getKeypadApplicationMode() {
        return mbKeypadApplicationMode;
    }

    /**
     * Get the current DECSET mouse tracking mode, zero for no mouse tracking.
     *
     * @return the current DECSET mouse tracking mode.
     */
    public final int getMouseTrackingMode() {
        return mMouseTrackingMode;
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
        if (EmulatorDebug.LOG_CHARACTERS_FLAG) {
            Log.d(EmulatorDebug.LOG_TAG, "In: '" + EmulatorDebug.bytesToString(buffer, base, length) + "'");
        }
        for (int i = 0; i < length; i++) {
            byte b = buffer[base + i];
            try {
                process(b);
                mProcessedCharCount++;
            } catch (Exception e) {
                Log.e(EmulatorDebug.LOG_TAG, "Exception while processing character "
                        + Integer.toString(mProcessedCharCount) + " code "
                        + Integer.toString(b), e);
            }
        }
    }

    private void process(byte b) {
        process(b, true);
    }

    private void process(byte b, boolean doUTF8) {
        // Let the UTF-8 decoder try to handle it if we're in UTF-8 mode
        if (doUTF8 && mUTF8Mode && handleUTF8Sequence(b)) {
            return;
        }

        // Handle C1 control characters
        if ((b & 0x80) == 0x80 && (b & 0x7f) <= 0x1f) {
            /* ESC ((code & 0x7f) + 0x40) is the two-byte escape sequence
               corresponding to a particular C1 code */
            process((byte) 27, false);
            process((byte) ((b & 0x7f) + 0x40), false);
            return;
        }

        switch (b) {
        case 0: // NUL
            // Do nothing
            break;

        case 7: // BEL
            /* If in an OSC sequence, BEL may terminate a string; otherwise do
             * nothing */
            if (mEscapeState == ESC_RIGHT_SQUARE_BRACKET) {
                doEscRightSquareBracket(b);
            }
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
            // Starts an escape sequence unless we're parsing a string
            if (mEscapeState != ESC_RIGHT_SQUARE_BRACKET) {
                startEscapeSequence(ESC);
            } else {
                doEscRightSquareBracket(b);
            }
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
                doEscLeftSquareBracket(b); // CSI
                break;

            case ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK:
                doEscLSBQuest(b); // CSI ?
                break;

            case ESC_PERCENT:
                doEscPercent(b);
                break;

            case ESC_RIGHT_SQUARE_BRACKET:
                doEscRightSquareBracket(b);
                break;

            case ESC_RIGHT_SQUARE_BRACKET_ESC:
                doEscRightSquareBracketEsc(b);
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

    private boolean handleUTF8Sequence(byte b) {
        if (mUTF8ToFollow == 0 && (b & 0x80) == 0) {
            // ASCII character -- we don't need to handle this
            return false;
        }

        if (mUTF8ToFollow > 0) {
            if ((b & 0xc0) != 0x80) {
                /* Not a UTF-8 continuation byte (doesn't begin with 0b10)
                   Replace the entire sequence with the replacement char */
                mUTF8ToFollow = 0;
                mUTF8ByteBuffer.clear();
                emit(UNICODE_REPLACEMENT_CHAR);

                /* The Unicode standard (section 3.9, definition D93) requires
                 * that we now attempt to process this byte as though it were
                 * the beginning of another possibly-valid sequence */
                return handleUTF8Sequence(b);
            }

            mUTF8ByteBuffer.put(b);
            if (--mUTF8ToFollow == 0) {
                // Sequence complete -- decode and emit it
                ByteBuffer byteBuf = mUTF8ByteBuffer;
                CharBuffer charBuf = mInputCharBuffer;
                CharsetDecoder decoder = mUTF8Decoder;

                byteBuf.rewind();
                decoder.reset();
                decoder.decode(byteBuf, charBuf, true);
                decoder.flush(charBuf);

                char[] chars = charBuf.array();
                if (chars[0] >= 0x80 && chars[0] <= 0x9f) {
                    /* Sequence decoded to a C1 control character which needs
                       to be sent through process() again */
                    process((byte) chars[0], false);
                } else {
                    emit(chars);
                }

                byteBuf.clear();
                charBuf.clear();
            }
        } else {
            if ((b & 0xe0) == 0xc0) { // 0b110 -- two-byte sequence
                mUTF8ToFollow = 1;
            } else if ((b & 0xf0) == 0xe0) { // 0b1110 -- three-byte sequence
                mUTF8ToFollow = 2;
            } else if ((b & 0xf8) == 0xf0) { // 0b11110 -- four-byte sequence
                mUTF8ToFollow = 3;
            } else {
                // Not a valid UTF-8 sequence start -- replace this char
                emit(UNICODE_REPLACEMENT_CHAR);
                return true;
            }

            mUTF8ByteBuffer.put(b);
        }

        return true;
    }

    private void setAltCharSet(boolean alternateCharSet) {
        mAlternateCharSet = alternateCharSet;
        computeEffectiveCharSet();
    }

    private void computeEffectiveCharSet() {
        int charSet = mCharSet[mAlternateCharSet ? 1 : 0];
        mUseAlternateCharSet = charSet == CHAR_SET_SPECIAL_GRAPHICS;
    }

    private int nextTabStop(int cursorCol) {
        for (int i = cursorCol + 1; i < mColumns; i++) {
            if (mTabStop[i]) {
                return i;
            }
        }
        return mColumns - 1;
    }

    private int prevTabStop(int cursorCol) {
        for (int i = cursorCol - 1; i >= 0; i--) {
            if (mTabStop[i]) {
                return i;
            }
        }
        return 0;
    }

    private void doEscPercent(byte b) {
        switch (b) {
        case '@': // Esc % @ -- return to ISO 2022 mode
           setUTF8Mode(false);
           mUTF8EscapeUsed = true;
           break;
        case 'G': // Esc % G -- UTF-8 mode
           setUTF8Mode(true);
           mUTF8EscapeUsed = true;
           break;
        default: // unimplemented character set
           break;
        }
    }

    private void doEscLSBQuest(byte b) {
        int arg = getArg0(0);
        int mask = getDecFlagsMask(arg);
        int oldFlags = mDecFlags;
        switch (b) {
        case 'h': // Esc [ ? Pn h - DECSET
            mDecFlags |= mask;
            switch (arg) {
            case 1:
                mKeyListener.setCursorKeysApplicationMode(true);
                break;
            case 47:
            case 1047:
            case 1049:
                if (mAltBuffer != null) {
                    mScreen = mAltBuffer;
                }
                break;
            }
            if (arg >= 1000 && arg <= 1003) {
                mMouseTrackingMode = arg;
            }
            break;

        case 'l': // Esc [ ? Pn l - DECRST
            mDecFlags &= ~mask;
            switch (arg) {
            case 1:
                mKeyListener.setCursorKeysApplicationMode(false);
                break;
            case 47:
            case 1047:
            case 1049:
                mScreen = mMainBuffer;
                break;
            }
            if (arg >= 1000 && arg <= 1003) {
                mMouseTrackingMode = 0;
            }
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

        int newlySetFlags = (~oldFlags) & mDecFlags;
        int changedFlags = oldFlags ^ mDecFlags;

        // 132 column mode
        if ((changedFlags & K_132_COLUMN_MODE_MASK) != 0) {
            // We don't actually set/reset 132 cols, but we do want the
            // side effect of clearing the screen and homing the cursor.
            blockClear(0, 0, mColumns, mRows);
            setCursorRowCol(0, 0);
        }

        // origin mode
        if ((newlySetFlags & K_ORIGIN_MODE_MASK) != 0) {
            // Home the cursor.
            setCursorPosition(0, 0);
        }
    }

    private int getDecFlagsMask(int argument) {
        if (argument >= 1 && argument <= 32) {
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
        doSelectCharSet(0, b);
    }

    private void doEscSelectRightParen(byte b) {
        doSelectCharSet(1, b);
    }

    private void doSelectCharSet(int charSetIndex, byte b) {
        int charSet;
        switch (b) {
        case 'A': // United Kingdom character set
            charSet = CHAR_SET_UK;
            break;
        case 'B': // ASCII set
            charSet = CHAR_SET_ASCII;
            break;
        case '0': // Special Graphics
            charSet = CHAR_SET_SPECIAL_GRAPHICS;
            break;
        case '1': // Alternate character set
            charSet = CHAR_SET_ALT_STANDARD;
            break;
        case '2':
            charSet = CHAR_SET_ALT_SPECIAL_GRAPICS;
            break;
        default:
            unknownSequence(b);
            return;
        }
        mCharSet[charSetIndex] = charSet;
        computeEffectiveCharSet();
    }

    private void doEscPound(byte b) {
        switch (b) {
        case '8': // Esc # 8 - DECALN alignment test
            mScreen.blockSet(0, 0, mColumns, mRows, 'E',
                    getStyle());
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
            mSavedEffect = mEffect;
            mSavedDecFlags_DECSC_DECRC = mDecFlags & K_DECSC_DECRC_MASK;
            break;

        case '8': // DECRC restore cursor
            setCursorRowCol(mSavedCursorRow, mSavedCursorCol);
            mEffect = mSavedEffect;
            mDecFlags = (mDecFlags & ~ K_DECSC_DECRC_MASK)
                    | mSavedDecFlags_DECSC_DECRC;
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
            if (mCursorRow <= mTopMargin) {
                mScreen.blockCopy(0, mTopMargin, mColumns, mBottomMargin
                        - (mTopMargin + 1), 0, mTopMargin + 1);
                blockClear(0, mTopMargin, mColumns);
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

        case ']': // OSC
            startCollectingOSCArgs();
            continueSequence(ESC_RIGHT_SQUARE_BRACKET);
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
        // CSI
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

        case 'J': // ESC [ Pn J - ED - Erase in Display
            // ED ignores the scrolling margins.
            switch (getArg0(0)) {
            case 0: // Clear below
                blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                blockClear(0, mCursorRow + 1, mColumns,
                        mRows - (mCursorRow + 1));
                break;

            case 1: // Erase from the start of the screen to the cursor.
                blockClear(0, 0, mColumns, mCursorRow);
                blockClear(0, mCursorRow, mCursorCol + 1);
                break;

            case 2: // Clear all
                blockClear(0, 0, mColumns, mRows);
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

        case 'X': // Erase characters
            blockClear(mCursorCol, mCursorRow, getArg0(0));
            break;

        case 'Z': // Back tab
            setCursorCol(prevTabStop(mCursorCol));
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
                  // (can have up to 16 numerical arguments)
            selectGraphicRendition();
            break;

        case 'n': // Esc [ Pn n - ECMA-48 Status Report Commands
            //sendDeviceAttributes()
            switch (getArg0(0)) {
            case 5: // Device status report (DSR):
                    // Answer is ESC [ 0 n (Terminal OK).
                byte[] dsr = { (byte) 27, (byte) '[', (byte) '0', (byte) 'n' };
                mSession.write(dsr, 0, dsr.length);
                break;

            case 6: // Cursor position report (CPR):
                    // Answer is ESC [ y ; x R, where x,y is
                    // the cursor location.
                byte[] cpr = String.format(Locale.US, "\033[%d;%dR",
                                 mCursorRow + 1, mCursorCol + 1).getBytes();
                mSession.write(cpr, 0, cpr.length);
                break;

            default:
                break;
            }
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
        // SGR
        for (int i = 0; i <= mArgIndex; i++) {
            int code = mArgs[i];
            if ( code < 0) {
                if (mArgIndex > 0) {
                    continue;
                } else {
                    code = 0;
                }
            }

            // See http://en.wikipedia.org/wiki/ANSI_escape_code#graphics

            if (code == 0) { // reset
                mForeColor = mDefaultForeColor;
                mBackColor = mDefaultBackColor;
                mEffect = TextStyle.fxNormal;
            } else if (code == 1) { // bold
                mEffect |= TextStyle.fxBold;
            } else if (code == 3) { // italics, but rarely used as such; "standout" (inverse colors) with TERM=screen
                mEffect |= TextStyle.fxItalic;
            } else if (code == 4) { // underscore
                mEffect |= TextStyle.fxUnderline;
            } else if (code == 5) { // blink
                mEffect |= TextStyle.fxBlink;
            } else if (code == 7) { // inverse
                mEffect |= TextStyle.fxInverse;
            } else if (code == 8) { // invisible
                mEffect |= TextStyle.fxInvisible;
            } else if (code == 10) { // exit alt charset (TERM=linux)
                setAltCharSet(false);
            } else if (code == 11) { // enter alt charset (TERM=linux)
                setAltCharSet(true);
            } else if (code == 22) { // Normal color or intensity, neither bright, bold nor faint
                //mEffect &= ~(TextStyle.fxBold | TextStyle.fxFaint);
                mEffect &= ~TextStyle.fxBold;
            } else if (code == 23) { // not italic, but rarely used as such; clears standout with TERM=screen
                mEffect &= ~TextStyle.fxItalic;
            } else if (code == 24) { // underline: none
                mEffect &= ~TextStyle.fxUnderline;
            } else if (code == 25) { // blink: none
                mEffect &= ~TextStyle.fxBlink;
            } else if (code == 27) { // image: positive
                mEffect &= ~TextStyle.fxInverse;
            } else if (code == 28) { // invisible
                mEffect &= ~TextStyle.fxInvisible;
            } else if (code >= 30 && code <= 37) { // foreground color
                mForeColor = code - 30;
            } else if (code == 38 && i+2 <= mArgIndex && mArgs[i+1] == 5) { // foreground 256 color
                int color = mArgs[i+2];
                if (checkColor(color)) {
                    mForeColor = color;
                }
                i += 2;
            } else if (code == 39) { // set default text color
                mForeColor = mDefaultForeColor;
            } else if (code >= 40 && code <= 47) { // background color
                mBackColor = code - 40;
            } else if (code == 48 && i+2 <= mArgIndex && mArgs[i+1] == 5) { // background 256 color
                mBackColor = mArgs[i+2];
                int color = mArgs[i+2];
                if (checkColor(color)) {
                    mBackColor = color;
                }
                i += 2;
            } else if (code == 49) { // set default background color
                mBackColor = mDefaultBackColor;
            } else if (code >= 90 && code <= 97) { // bright foreground color
                mForeColor = code - 90 + 8;
            } else if (code >= 100 && code <= 107) { // bright background color
                mBackColor = code - 100 + 8;
            } else {
                if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
                    Log.w(EmulatorDebug.LOG_TAG, String.format("SGR unknown code %d", code));
                }
            }
        }
    }

    private boolean checkColor(int color) {
        boolean result = isValidColor(color);
        if (!result) {
            if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
                Log.w(EmulatorDebug.LOG_TAG,
                        String.format("Invalid color %d", color));
            }
        }
        return result;
    }

    private boolean isValidColor(int color) {
        return color >= 0 && color < TextStyle.ciColorLength;
    }

    private void doEscRightSquareBracket(byte b) {
        switch (b) {
        case 0x7:
            doOSC();
            break;
        case 0x1b: // Esc, probably start of Esc \ sequence
            continueSequence(ESC_RIGHT_SQUARE_BRACKET_ESC);
            break;
        default:
            collectOSCArgs(b);
            break;
        }
    }

    private void doEscRightSquareBracketEsc(byte b) {
        switch (b) {
        case '\\':
            doOSC();
            break;

        default:
            // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            collectOSCArgs((byte) 0x1b);
            collectOSCArgs(b);
            continueSequence(ESC_RIGHT_SQUARE_BRACKET);
            break;
        }
    }

    private void doOSC() { // Operating System Controls
        startTokenizingOSC();
        int ps = nextOSCInt(';');
        switch (ps) {
        case 0: // Change icon name and window title to T
        case 1: // Change icon name to T
        case 2: // Change window title to T
            changeTitle(ps, nextOSCString(-1));
            break;
        default:
            unknownParameter(ps);
            break;
        }
        finishSequence();
    }

    private void changeTitle(int parameter, String title) {
        if (parameter == 0 || parameter == 2) {
            mSession.setTitle(title);
        }
    }

    private void blockClear(int sx, int sy, int w) {
        blockClear(sx, sy, w, 1);
    }

    private void blockClear(int sx, int sy, int w, int h) {
        mScreen.blockSet(sx, sy, w, h, ' ', getStyle());
    }

    private int getForeColor() {
        return mForeColor;
    }

    private int getBackColor() {
        return mBackColor;
    }

    private int getEffect() {
        return mEffect;
    }

    private int getStyle() {
        return TextStyle.encode(getForeColor(), getBackColor(),  getEffect());
    }

    private void doSetMode(boolean newValue) {
        int modeBit = getArg0(0);
        switch (modeBit) {
        case 4:
            mInsertMode = newValue;
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

        mSession.write(attributes, 0, attributes.length);
    }

    private void scroll() {
        //System.out.println("Scroll(): mTopMargin " + mTopMargin + " mBottomMargin " + mBottomMargin);
        mScrollCounter ++;
        mScreen.scroll(mTopMargin, mBottomMargin, getStyle());
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
        return getArg(0, defaultValue, true);
    }

    private int getArg1(int defaultValue) {
        return getArg(1, defaultValue, true);
    }

    private int getArg(int index, int defaultValue,
            boolean treatZeroAsDefault) {
        int result = mArgs[index];
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue;
        }
        return result;
    }

    private void startCollectingOSCArgs() {
        mOSCArgLength = 0;
    }

    private void collectOSCArgs(byte b) {
        if (mOSCArgLength < MAX_OSC_STRING_LENGTH) {
            mOSCArg[mOSCArgLength++] = b;
            continueSequence();
        } else {
            unknownSequence(b);
        }
    }

    private void startTokenizingOSC() {
        mOSCArgTokenizerIndex = 0;
    }

    private String nextOSCString(int delimiter) {
        int start = mOSCArgTokenizerIndex;
        int end = start;
        while (mOSCArgTokenizerIndex < mOSCArgLength) {
            byte b = mOSCArg[mOSCArgTokenizerIndex++];
            if ((int) b == delimiter) {
                break;
            }
            end++;
        }
        if (start == end) {
            return "";
        }
        try {
            return new String(mOSCArg, start, end-start, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(mOSCArg, start, end-start);
        }
    }

    private int nextOSCInt(int delimiter) {
        int value = -1;
        while (mOSCArgTokenizerIndex < mOSCArgLength) {
            byte b = mOSCArg[mOSCArgTokenizerIndex++];
            if ((int) b == delimiter) {
                break;
            } else if (b >= '0' && b <= '9') {
                if (value < 0) {
                    value = 0;
                }
                value = value * 10 + b - '0';
            } else {
                unknownSequence(b);
            }
        }
        return value;
    }

    private void unimplementedSequence(byte b) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unimplemented", b);
        }
        finishSequence();
    }

    private void unknownSequence(byte b) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unknown", b);
        }
        finishSequence();
    }

    private void unknownParameter(int parameter) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            StringBuilder buf = new StringBuilder();
            buf.append("Unknown parameter");
            buf.append(parameter);
            logError(buf.toString());
        }
    }

    private void logError(String errorType, byte b) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
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
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            Log.e(EmulatorDebug.LOG_TAG, error);
        }
        finishSequence();
    }

    private void finishSequence() {
        mEscapeState = ESC_NONE;
    }

    private boolean autoWrapEnabled() {
        return (mDecFlags & K_WRAPAROUND_MODE_MASK) != 0;
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param c The code point of the character to display
     * @param foreColor The foreground color of the character
     * @param backColor The background color of the character
     */
    private void emit(int c, int style) {
        boolean autoWrap = autoWrapEnabled();
        int width = UnicodeTranscript.charWidth(c);

        if (autoWrap) {
            if (mCursorCol == mColumns - 1 && (mAboutToAutoWrap || width == 2)) {
                mScreen.setLineWrap(mCursorRow);
                mCursorCol = 0;
                mJustWrapped = true;
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++;
                } else {
                    scroll();
                }
            }
        }

        if (mInsertMode & width != 0) { // Move character to right one space
            int destCol = mCursorCol + width;
            if (destCol < mColumns) {
                mScreen.blockCopy(mCursorCol, mCursorRow, mColumns - destCol,
                        1, destCol, mCursorRow);
            }
        }

        if (width == 0) {
            // Combining character -- store along with character it modifies
            if (mJustWrapped) {
                mScreen.set(mColumns - mLastEmittedCharWidth, mCursorRow - 1, c, style);
            } else {
                mScreen.set(mCursorCol - mLastEmittedCharWidth, mCursorRow, c, style);
            }
        } else {
            mScreen.set(mCursorCol, mCursorRow, c, style);
            mJustWrapped = false;
        }

        if (autoWrap) {
            mAboutToAutoWrap = (mCursorCol == mColumns - 1);

            //Force line-wrap flag to trigger even for lines being typed
            if(mAboutToAutoWrap)
                mScreen.setLineWrap(mCursorRow);
        }

        mCursorCol = Math.min(mCursorCol + width, mColumns - 1);
        if (width > 0) {
            mLastEmittedCharWidth = width;
        }
    }

    private void emit(int c) {
        emit(c, getStyle());
    }

    private void emit(byte b) {
        if (mUseAlternateCharSet && b < 128) {
            emit((int) mSpecialGraphicsCharMap[b]);
        } else {
            emit((int) b);
        }
    }

    /**
     * Send a UTF-16 char or surrogate pair to the screen.
     *
     * @param c A char[2] containing either a single UTF-16 char or a surrogate pair to be sent to the screen.
     */
    private void emit(char[] c) {
        if (Character.isHighSurrogate(c[0])) {
            emit(Character.toCodePoint(c[0], c[1]));
        } else {
            emit((int) c[0]);
        }
    }

    /**
     * Send an array of UTF-16 chars to the screen.
     *
     * @param c A char[] array whose contents are to be sent to the screen.
     */
    private void emit(char[] c, int offset, int length, int style) {
        for (int i = offset; i < length; ++i) {
            if (c[i] == 0) {
                break;
            }
            if (Character.isHighSurrogate(c[i])) {
                emit(Character.toCodePoint(c[i], c[i+1]), style);
                ++i;
            } else {
                emit((int) c[i], style);
            }
        }
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

    public int getScrollCounter() {
        return mScrollCounter;
    }

    public void clearScrollCounter() {
        mScrollCounter = 0;
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
        mSavedEffect = 0;
        mSavedDecFlags_DECSC_DECRC = 0;
        mDecFlags = 0;
        if (DEFAULT_TO_AUTOWRAP_ENABLED) {
            mDecFlags |= K_WRAPAROUND_MODE_MASK;
        }
        mDecFlags |= K_SHOW_CURSOR_MASK;
        mSavedDecFlags = 0;
        mInsertMode = false;
        mTopMargin = 0;
        mBottomMargin = mRows;
        mAboutToAutoWrap = false;
        mForeColor = mDefaultForeColor;
        mBackColor = mDefaultBackColor;
        mbKeypadApplicationMode = false;
        mAlternateCharSet = false;
        mCharSet[0] = CHAR_SET_ASCII;
        mCharSet[1] = CHAR_SET_SPECIAL_GRAPHICS;
        computeEffectiveCharSet();
        // mProcessedCharCount is preserved unchanged.
        setDefaultTabStops();
        blockClear(0, 0, mColumns, mRows);

        setUTF8Mode(mDefaultUTF8Mode);
        mUTF8EscapeUsed = false;
        mUTF8ToFollow = 0;
        mUTF8ByteBuffer.clear();
        mInputCharBuffer.clear();
    }

    public void setDefaultUTF8Mode(boolean defaultToUTF8Mode) {
        mDefaultUTF8Mode = defaultToUTF8Mode;
        if (!mUTF8EscapeUsed) {
            setUTF8Mode(defaultToUTF8Mode);
        }
    }

    public void setUTF8Mode(boolean utf8Mode) {
        if (utf8Mode && !mUTF8Mode) {
            mUTF8ToFollow = 0;
            mUTF8ByteBuffer.clear();
            mInputCharBuffer.clear();
        }
        mUTF8Mode = utf8Mode;
        if (mUTF8ModeNotify != null) {
            mUTF8ModeNotify.onUpdate();
        }
    }

    public boolean getUTF8Mode() {
        return mUTF8Mode;
    }

    public void setUTF8ModeUpdateCallback(UpdateCallback utf8ModeNotify) {
        mUTF8ModeNotify = utf8ModeNotify;
    }

    public void setColorScheme(ColorScheme scheme) {
        mDefaultForeColor = TextStyle.ciForeground;
        mDefaultBackColor = TextStyle.ciBackground;
        mMainBuffer.setColorScheme(scheme);
        if (mAltBuffer != null) {
            mAltBuffer.setColorScheme(scheme);
        }
    }

    public String getSelectedText(int x1, int y1, int x2, int y2) {
        return mScreen.getSelectedText(x1, y1, x2, y2);
    }

    public void finish() {
        if (mAltBuffer != null) {
            mAltBuffer.finish();
            mAltBuffer = null;
        }
    }
}
