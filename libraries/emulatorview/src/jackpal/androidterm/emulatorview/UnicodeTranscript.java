/*
 * Copyright (C) 2011 Steven Luo
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

import android.util.Log;

import jackpal.androidterm.emulatorview.compat.AndroidCharacterCompat;
import jackpal.androidterm.emulatorview.compat.AndroidCompat;

/**
 * A backing store for a TranscriptScreen.
 *
 * The text is stored as a circular buffer of rows.  There are two types of
 * row:
 * - "basic", which is a char[] array used to store lines which consist
 *   entirely of regular-width characters (no combining characters, zero-width
 *   characters, East Asian double-width characters, etc.) in the BMP; and
 * - "full", which is a char[] array with extra trappings which can be used to
 *   store a line containing any valid Unicode sequence.  An array of short[]
 *   is used to store the "offset" at which each column starts; for example,
 *   if column 20 starts at index 23 in the array, then mOffset[20] = 3.
 *
 * Style information is stored in a separate circular buffer of StyleRows.
 *
 * Rows are allocated on demand, when a character is first stored into them.
 * A "basic" row is allocated unless the store which triggers the allocation
 * requires a "full" row.  "Basic" rows are converted to "full" rows when
 * needed.  There is no conversion in the other direction -- a "full" row
 * stays that way even if it contains only regular-width BMP characters.
 */
class UnicodeTranscript {
    private static final String TAG = "UnicodeTranscript";

    private Object[] mLines;
    private StyleRow[] mColor;
    private boolean[] mLineWrap;
    private int mTotalRows;
    private int mScreenRows;
    private int mColumns;
    private int mActiveTranscriptRows = 0;
    private int mDefaultStyle = 0;

    private int mScreenFirstRow = 0;

    private char[] tmpLine;
    private StyleRow tmpColor;

    public UnicodeTranscript(int columns, int totalRows, int screenRows, int defaultStyle) {
        mColumns = columns;
        mTotalRows = totalRows;
        mScreenRows = screenRows;
        mLines = new Object[totalRows];
        mColor = new StyleRow[totalRows];
        mLineWrap = new boolean[totalRows];
        tmpColor = new StyleRow(defaultStyle, mColumns);

        mDefaultStyle = defaultStyle;
    }

    public void setDefaultStyle(int defaultStyle) {
        mDefaultStyle = defaultStyle;
    }

    public int getDefaultStyle() {
        return mDefaultStyle;
    }

    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }

    public int getActiveRows() {
        return mActiveTranscriptRows + mScreenRows;
    }

    /**
     * Convert a row value from the public external coordinate system to our
     * internal private coordinate system.
     * External coordinate system:
     * -mActiveTranscriptRows to mScreenRows-1, with the screen being
     * 0..mScreenRows-1
     * Internal coordinate system: the mScreenRows lines starting at
     * mScreenFirstRow comprise the screen, while the mActiveTranscriptRows
     * lines ending at mScreenRows-1 form the transcript (as a circular
     * buffer).
     *
     * @param extRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private
     *         coordinate system.
     */
    private int externalToInternalRow(int extRow) {
        if (extRow < -mActiveTranscriptRows || extRow > mScreenRows) {
            String errorMessage = "externalToInternalRow "+ extRow +
                " " + mScreenRows + " " + mActiveTranscriptRows;
            Log.e(TAG, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if (extRow >= 0) {
            return (mScreenFirstRow + extRow) % mTotalRows;
        } else {
            if (-extRow > mScreenFirstRow) {
                return mTotalRows + mScreenFirstRow + extRow;
            } else {
                return mScreenFirstRow + extRow;
            }
        }
    }

    public void setLineWrap(int row) {
        mLineWrap[externalToInternalRow(row)] = true;
    }

    public boolean getLineWrap(int row) {
        return mLineWrap[externalToInternalRow(row)];
    }

    /**
     * Resize the screen which this transcript backs.  Currently, this
     * only works if the number of columns does not change.
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows The number of rows the screen should have.
     * @param cursor An int[2] containing the current cursor location; if the
     *        resize succeeds, this will be updated with the new cursor
     *        location.  If null, don't do cursor-position-dependent tasks such
     *        as trimming blank lines during the resize.
     * @return Whether or not the resize succeeded.  If the resize failed,
     *         the caller may "resize" the screen by copying out all the data
     *         and placing it into a new transcript of the correct size.
     */
    public boolean resize(int newColumns, int newRows, int[] cursor) {
        if (newColumns != mColumns || newRows > mTotalRows) {
            return false;
        }

        int screenRows = mScreenRows;
        int activeTranscriptRows = mActiveTranscriptRows;
        int shift = screenRows - newRows;
        if (shift < -activeTranscriptRows) {
            // We want to add blank lines at the bottom instead of at the top
            Object[] lines = mLines;
            Object[] color = mColor;
            boolean[] lineWrap = mLineWrap;
            int screenFirstRow = mScreenFirstRow;
            int totalRows = mTotalRows;
            for (int i = 0; i < activeTranscriptRows - shift; ++i) {
                int index = (screenFirstRow + screenRows + i) % totalRows;
                lines[index] = null;
                color[index] = null;
                lineWrap[index] = false;
            }
            shift = -activeTranscriptRows;
        } else if (shift > 0 && cursor != null && cursor[1] != screenRows - 1) {
            /* When shrinking the screen, we want to hide blank lines at the
               bottom in preference to lines at the top of the screen */
            Object[] lines = mLines;
            for (int i = screenRows - 1; i > cursor[1]; --i) {
                int index = externalToInternalRow(i);
                if (lines[index] == null) {
                    // Line is blank
                    --shift;
                    if (shift == 0) {
                        break;
                    } else {
                        continue;
                    }
                }

                char[] line;
                if (lines[index] instanceof char[]) {
                    line = (char[]) lines[index];
                } else {
                    line = ((FullUnicodeLine) lines[index]).getLine();
                }

                int len = line.length;
                int j;
                for (j = 0; j < len; ++j) {
                    if (line[j] == 0) {
                        // We've reached the end of the line
                        j = len;
                        break;
                    } else if (line[j] != ' ') {
                        // Line is not blank
                        break;
                    }
                }

                if (j == len) {
                    // Line is blank
                    --shift;
                    if (shift == 0) {
                        break;
                    } else {
                        continue;
                    }
                } else {
                    // Line not blank -- we keep it and everything above
                    break;
                }
            }
        }

        if (shift > 0 || (shift < 0 && mScreenFirstRow >= -shift)) {
            // All we're doing is moving the top of the screen.
            mScreenFirstRow = (mScreenFirstRow + shift) % mTotalRows;
        } else if (shift < 0) {
            // The new top of the screen wraps around the top of the array.
            mScreenFirstRow = mTotalRows + mScreenFirstRow + shift;
        }

        if (mActiveTranscriptRows + shift < 0) {
            mActiveTranscriptRows = 0;
        } else {
            mActiveTranscriptRows += shift;
        }
        if (cursor != null) {
            cursor[1] -= shift;
        }
        mScreenRows = newRows;

        return true;
    }

    /**
     * Block copy lines and associated metadata from one location to another
     * in the circular buffer, taking wraparound into account.
     *
     * @param src The first line to be copied.
     * @param len The number of lines to be copied.
     * @param shift The offset of the destination from the source.
     */
    private void blockCopyLines(int src, int len, int shift) {
        int totalRows = mTotalRows;

        int dst;
        if (src + shift >= 0) {
            dst = (src + shift) % totalRows;
        } else {
            dst = totalRows + src + shift;
        }

        if (src + len <= totalRows && dst + len <= totalRows) {
            // Fast path -- no wraparound
            System.arraycopy(mLines, src, mLines, dst, len);
            System.arraycopy(mColor, src, mColor, dst, len);
            System.arraycopy(mLineWrap, src, mLineWrap, dst, len);
            return;
        }

        if (shift < 0) {
            // Do the copy from top to bottom
            for (int i = 0; i < len; ++i) {
                mLines[(dst + i) % totalRows] = mLines[(src + i) % totalRows];
                mColor[(dst + i) % totalRows] = mColor[(src + i) % totalRows];
                mLineWrap[(dst + i) % totalRows] = mLineWrap[(src + i) % totalRows];
            }
        } else {
            // Do the copy from bottom to top
            for (int i = len - 1; i >= 0; --i) {
                mLines[(dst + i) % totalRows] = mLines[(src + i) % totalRows];
                mColor[(dst + i) % totalRows] = mColor[(src + i) % totalRows];
                mLineWrap[(dst + i) % totalRows] = mLineWrap[(src + i) % totalRows];
            }
        }
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style the style for the newly exposed line.
     */
    public void scroll(int topMargin, int bottomMargin, int style) {
        // Separate out reasons so that stack crawls help us
        // figure out which condition was violated.
        if (topMargin > bottomMargin - 1) {
            throw new IllegalArgumentException();
        }

        if (topMargin < 0)  {
            throw new IllegalArgumentException();
        }

        if (bottomMargin > mScreenRows) {
            throw new IllegalArgumentException();
        }

        int screenRows = mScreenRows;
        int totalRows = mTotalRows;

        if (topMargin == 0 && bottomMargin == screenRows) {
            // Fast path -- scroll the entire screen
            mScreenFirstRow = (mScreenFirstRow + 1) % totalRows;
            if (mActiveTranscriptRows < totalRows - screenRows) {
                ++mActiveTranscriptRows;
            }

            // Blank the bottom margin
            int blankRow = externalToInternalRow(bottomMargin - 1);
            mLines[blankRow] = null;
            mColor[blankRow] = new StyleRow(style, mColumns);
            mLineWrap[blankRow] = false;

            return;
        }

        int screenFirstRow = mScreenFirstRow;
        int topMarginInt = externalToInternalRow(topMargin);
        int bottomMarginInt = externalToInternalRow(bottomMargin);

        /* Save the scrolled line, move the lines above it on the screen down
           one line, move the lines on screen below the bottom margin down
           one line, then insert the scrolled line into the transcript */
        Object[] lines = mLines;
        StyleRow[] color = mColor;
        boolean[] lineWrap = mLineWrap;
        Object scrollLine = lines[topMarginInt];
        StyleRow scrollColor = color[topMarginInt];
        boolean scrollLineWrap = lineWrap[topMarginInt];
        blockCopyLines(screenFirstRow, topMargin, 1);
        blockCopyLines(bottomMarginInt, screenRows - bottomMargin, 1);
        lines[screenFirstRow] = scrollLine;
        color[screenFirstRow] = scrollColor;
        lineWrap[screenFirstRow] = scrollLineWrap;

        // Update the screen location
        mScreenFirstRow = (screenFirstRow + 1) % totalRows;
        if (mActiveTranscriptRows < totalRows - screenRows) {
            ++mActiveTranscriptRows;
        }

        // Blank the bottom margin
        int blankRow = externalToInternalRow(bottomMargin - 1);
        lines[blankRow] = null;
        color[blankRow] = new StyleRow(style, mColumns);
        lineWrap[blankRow] = false;

        return;
    }

    /**
     * Block copy characters from one position in the screen to another. The two
     * positions can overlap. All characters of the source and destination must
     * be within the bounds of the screen, or else an InvalidParameterException
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
        Object[] lines = mLines;
        StyleRow[] color = mColor;
        if (sy > dy) {
            // Move in increasing order
            for (int y = 0; y < h; y++) {
                int srcRow = externalToInternalRow(sy + y);
                int dstRow = externalToInternalRow(dy + y);
                if (lines[srcRow] instanceof char[] && lines[dstRow] instanceof char[]) {
                    System.arraycopy(lines[srcRow], sx, lines[dstRow], dx, w);
                } else {
                    // XXX There has to be a faster way to do this ...
                    int extDstRow = dy + y;
                    char[] tmp = getLine(sy + y, sx, sx + w, true);
                    if (tmp == null) {
                        // Source line was blank
                        blockSet(dx, extDstRow, w, 1, ' ', mDefaultStyle);
                        continue;
                    }
                    char cHigh = 0;
                    int x = 0;
                    int columns = mColumns;
                    for (int i = 0; i < tmp.length; ++i) {
                        if (tmp[i] == 0 || dx + x >= columns) {
                            break;
                        }
                        if (Character.isHighSurrogate(tmp[i])) {
                            cHigh = tmp[i];
                            continue;
                        } else if (Character.isLowSurrogate(tmp[i])) {
                            int codePoint = Character.toCodePoint(cHigh, tmp[i]);
                            setChar(dx + x, extDstRow, codePoint);
                            x += charWidth(codePoint);
                        } else {
                            setChar(dx + x, extDstRow, tmp[i]);
                            x += charWidth(tmp[i]);
                        }
                    }
                }
                color[srcRow].copy(sx, color[dstRow], dx, w);
            }
        } else {
            // Move in decreasing order
            for (int y = 0; y < h; y++) {
                int y2 = h - (y + 1);
                int srcRow = externalToInternalRow(sy + y2);
                int dstRow = externalToInternalRow(dy + y2);
                if (lines[srcRow] instanceof char[] && lines[dstRow] instanceof char[]) {
                    System.arraycopy(lines[srcRow], sx, lines[dstRow], dx, w);
                } else {
                    int extDstRow = dy + y2;
                    char[] tmp = getLine(sy + y2, sx, sx + w, true);
                    if (tmp == null) {
                        // Source line was blank
                        blockSet(dx, extDstRow, w, 1, ' ', mDefaultStyle);
                        continue;
                    }
                    char cHigh = 0;
                    int x = 0;
                    int columns = mColumns;
                    for (int i = 0; i < tmp.length; ++i) {
                        if (tmp[i] == 0 || dx + x >= columns) {
                            break;
                        }
                        if (Character.isHighSurrogate(tmp[i])) {
                            cHigh = tmp[i];
                            continue;
                        } else if (Character.isLowSurrogate(tmp[i])) {
                            int codePoint = Character.toCodePoint(cHigh, tmp[i]);
                            setChar(dx + x, extDstRow, codePoint);
                            x += charWidth(codePoint);
                        } else {
                            setChar(dx + x, extDstRow, tmp[i]);
                            x += charWidth(tmp[i]);
                        }
                    }
                }
                color[srcRow].copy(sx, color[dstRow], dx, w);
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
    public void blockSet(int sx, int sy, int w, int h, int val, int style) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
            Log.e(TAG, "illegal arguments! " + sx + " " + sy + " " + w + " " + h + " " + val + " " + mColumns + " " + mScreenRows);
            throw new IllegalArgumentException();
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                setChar(sx + x, sy + y, val, style);
            }
        }
    }

    /**
     * Minimum API version for which we're willing to let Android try
     * rendering conjoining Hangul jamo as composed syllable blocks.
     *
     * This appears to work on Android 4.1.2, 4.3, and 4.4 (real devices only;
     * the emulator's broken for some reason), but not on 4.0.4 -- hence the
     * choice of API 16 as the minimum.
     */
    static final int HANGUL_CONJOINING_MIN_SDK = 16;

    /**
     * Gives the display width of the code point in a monospace font.
     *
     * Nonspacing combining marks, format characters, and control characters
     * have display width zero.  East Asian fullwidth and wide characters
     * have display width two.  All other characters have display width one.
     *
     * Known issues:
     * - Proper support for East Asian wide characters requires API >= 8.
     * - Assigning all East Asian "ambiguous" characters a width of 1 may not
     *   be correct if Android renders those characters as wide in East Asian
     *   context (as the Unicode standard permits).
     * - Isolated Hangul conjoining medial vowels and final consonants are
     *   treated as combining characters (they should only be combining when
     *   part of a Korean syllable block).
     *
     * @param codePoint A Unicode code point.
     * @return The display width of the Unicode code point.
     */
    public static int charWidth(int codePoint) {
        // Early out for ASCII printable characters
        if (codePoint > 31 && codePoint < 127) {
            return 1;
        }

        /* HACK: We're using ASCII ESC to save the location of the cursor
           across screen resizes, so we need to pretend that it has width 1 */
        if (codePoint == 27) {
            return 1;
        }

        switch (Character.getType(codePoint)) {
        case Character.CONTROL:
        case Character.FORMAT:
        case Character.NON_SPACING_MARK:
        case Character.ENCLOSING_MARK:
            return 0;
        }

        if ((codePoint >= 0x1160 && codePoint <= 0x11FF) ||
            (codePoint >= 0xD7B0 && codePoint <= 0xD7FF)) {
            if (AndroidCompat.SDK >= HANGUL_CONJOINING_MIN_SDK) {
                /* Treat Hangul jamo medial vowels and final consonants as
                 * combining characters with width 0 to make jamo composition
                 * work correctly.
                 *
                 * XXX: This is wrong for medials/finals outside a Korean
                 * syllable block, but there's no easy solution to that
                 * problem, and we may as well at least get the common case
                 * right. */
                return 0;
            } else {
                /* Older versions of Android didn't compose Hangul jamo, but
                 * instead rendered them as individual East Asian wide
                 * characters (despite Unicode defining medial vowels and final
                 * consonants as East Asian neutral/narrow).  Treat them as
                 * width 2 characters to match the rendering. */
                return 2;
            }
        }
        if (Character.charCount(codePoint) == 1) {
            // Android's getEastAsianWidth() only works for BMP characters
            switch (AndroidCharacterCompat.getEastAsianWidth((char) codePoint)) {
            case AndroidCharacterCompat.EAST_ASIAN_WIDTH_FULL_WIDTH:
            case AndroidCharacterCompat.EAST_ASIAN_WIDTH_WIDE:
                return 2;
            }
        } else {
            // Outside the BMP, only the ideographic planes contain wide chars
            switch ((codePoint >> 16) & 0xf) {
            case 2: // Supplementary Ideographic Plane
            case 3: // Tertiary Ideographic Plane
                return 2;
            }
        }

        return 1;
    }

    public static int charWidth(char cHigh, char cLow) {
        return charWidth(Character.toCodePoint(cHigh, cLow));
    }

    /**
     * Gives the display width of a code point in a char array
     * in a monospace font.
     *
     * @param chars The array containing the code point in question.
     * @param index The index into the array at which the code point starts.
     * @return The display width of the Unicode code point.
     */
    public static int charWidth(char[] chars, int index) {
        char c = chars[index];
        if (Character.isHighSurrogate(c)) {
            return charWidth(c, chars[index+1]);
        } else {
            return charWidth(c);
        }
    }

    /**
     * Get the contents of a line (or part of a line) of the transcript.
     *
     * The char[] array returned may be part of the internal representation
     * of the line -- make a copy first if you want to modify it.  The returned
     * array may be longer than the requested portion of the transcript; in
     * this case, the last character requested will be followed by a NUL, and
     * the contents of the rest of the array could potentially be garbage.
     *
     * @param row The row number to get (-mActiveTranscriptRows..mScreenRows-1)
     * @param x1 The first screen position that's wanted
     * @param x2 One after the last screen position that's wanted
     * @return A char[] array containing the requested contents
     */
    public char[] getLine(int row, int x1, int x2) {
        return getLine(row, x1, x2, false);
    }

    /**
     * Get the whole contents of a line of the transcript.
     */
    public char[] getLine(int row) {
        return getLine(row, 0, mColumns, true);
    }

    private char[] getLine(int row, int x1, int x2, boolean strictBounds) {
        if (row < -mActiveTranscriptRows || row > mScreenRows-1) {
            throw new IllegalArgumentException();
        }

        int columns = mColumns;
        row = externalToInternalRow(row);
        if (mLines[row] == null) {
            // Line is blank
            return null;
        }
        if (mLines[row] instanceof char[]) {
            // Line contains only regular-width BMP characters
            if (x1 == 0 && x2 == columns) {
                // Want the whole row? Easy.
                return (char[]) mLines[row];
            } else {
                if (tmpLine == null || tmpLine.length < columns + 1) {
                    tmpLine = new char[columns+1];
                }
                int length = x2 - x1;
                System.arraycopy(mLines[row], x1, tmpLine, 0, length);
                tmpLine[length] = 0;
                return tmpLine;
            }
        }

        // Figure out how long the array needs to be
        FullUnicodeLine line = (FullUnicodeLine) mLines[row];
        char[] rawLine = line.getLine();

        if (x1 == 0 && x2 == columns) {
            /* We can return the raw line after ensuring it's NUL-terminated at
             * the appropriate place */
            int spaceUsed = line.getSpaceUsed();
            if (spaceUsed < rawLine.length) {
                rawLine[spaceUsed] = 0;
            }
            return rawLine;
        }

        x1 = line.findStartOfColumn(x1);
        if (x2 < columns) {
            int endCol = x2;
            x2 = line.findStartOfColumn(endCol);
            if (!strictBounds && endCol > 0 && endCol < columns - 1) {
                /* If the end column is the middle of an East Asian wide
                 * character, include that character in the bounds */
                if (x2 == line.findStartOfColumn(endCol - 1)) {
                    x2 = line.findStartOfColumn(endCol + 1);
                }
            }
        } else {
            x2 = line.getSpaceUsed();
        }
        int length = x2 - x1;

        if (tmpLine == null || tmpLine.length < length + 1) {
            tmpLine = new char[length+1];
        }
        System.arraycopy(rawLine, x1, tmpLine, 0, length);
        tmpLine[length] = 0;
        return tmpLine;
    }

    /**
     * Get color/formatting information for a particular line.
     * The returned object may be a pointer to a temporary buffer, only good
     * until the next call to getLineColor.
     */
    public StyleRow getLineColor(int row, int x1, int x2) {
        return getLineColor(row, x1, x2, false);
    }

    public StyleRow getLineColor(int row) {
        return getLineColor(row, 0, mColumns, true);
    }

    private StyleRow getLineColor(int row, int x1, int x2, boolean strictBounds) {
        if (row < -mActiveTranscriptRows || row > mScreenRows-1) {
            throw new IllegalArgumentException();
        }

        row = externalToInternalRow(row);
        StyleRow color = mColor[row];
        StyleRow tmp = tmpColor;
        if (color != null) {
            int columns = mColumns;
            if (!strictBounds && mLines[row] != null &&
                    mLines[row] instanceof FullUnicodeLine) {
                FullUnicodeLine line = (FullUnicodeLine) mLines[row];
                /* If either the start or the end column is in the middle of
                 * an East Asian wide character, include the appropriate column
                 * of style information */
                if (x1 > 0 && line.findStartOfColumn(x1-1) == line.findStartOfColumn(x1)) {
                    --x1;
                }
                if (x2 < columns - 1 && line.findStartOfColumn(x2+1) == line.findStartOfColumn(x2)) {
                    ++x2;
                }
            }
            if (x1 == 0 && x2 == columns) {
                return color;
            }
            color.copy(x1, tmp, 0, x2-x1);
            return tmp;
        } else {
            return null;
        }
    }

    boolean isBasicLine(int row) {
        if (row < -mActiveTranscriptRows || row > mScreenRows-1) {
            throw new IllegalArgumentException();
        }

        return (mLines[externalToInternalRow(row)] instanceof char[]);
    }

    public boolean getChar(int row, int column) {
        return getChar(row, column, 0);
    }

    public boolean getChar(int row, int column, int charIndex) {
        return getChar(row, column, charIndex, new char[1], 0);
    }

    /**
     * Get a character at a specific position in the transcript.
     *
     * @param row The row of the character to get.
     * @param column The column of the character to get.
     * @param charIndex The index of the character in the column to get
     *  (0 for the first character, 1 for the next, etc.)
     * @param out The char[] array into which the character will be placed.
     * @param offset The offset in the array at which the character will be placed.
     * @return Whether or not there are characters following this one in the column.
     */
    public boolean getChar(int row, int column, int charIndex, char[] out, int offset) {
        if (row < -mActiveTranscriptRows || row > mScreenRows-1) {
            throw new IllegalArgumentException();
        }
        row = externalToInternalRow(row);

        if (mLines[row] instanceof char[]) {
            // Fast path: all regular-width BMP chars in the row
            char[] line = (char[]) mLines[row];
            out[offset] = line[column];
            return false;
        }

        FullUnicodeLine line = (FullUnicodeLine) mLines[row];
        return line.getChar(column, charIndex, out, offset);
    }

    private boolean isBasicChar(int codePoint) {
        return !(charWidth(codePoint) != 1 || Character.charCount(codePoint) != 1);
    }

    private char[] allocateBasicLine(int row, int columns) {
        char[] line = new char[columns];

        // Fill the line with blanks
        for (int i = 0; i < columns; ++i) {
            line[i] = ' ';
        }

        mLines[row] = line;
        if (mColor[row] == null) {
            mColor[row] = new StyleRow(0, columns);
        }
        return line;
    }

    private FullUnicodeLine allocateFullLine(int row, int columns) {
        FullUnicodeLine line = new FullUnicodeLine(columns);

        mLines[row] = line;
        if (mColor[row] == null) {
            mColor[row] = new StyleRow(0, columns);
        }
        return line;
    }

    public boolean setChar(int column, int row, int codePoint, int style) {
        if (!setChar(column, row, codePoint)) {
            return false;
        }

        row = externalToInternalRow(row);
        mColor[row].set(column, style);

        return true;
    }

    public boolean setChar(int column, int row, int codePoint) {
        if (row >= mScreenRows || column >= mColumns) {
            Log.e(TAG, "illegal arguments! " + row + " " + column + " " + mScreenRows + " " + mColumns);
            throw new IllegalArgumentException();
        }
        row = externalToInternalRow(row);

        /*
         * Whether data contains non-BMP or characters with charWidth != 1
         * 0 - false; 1 - true; -1 - undetermined
         */
        int basicMode = -1;

        // Allocate a row on demand
        if (mLines[row] == null) {
            if (isBasicChar(codePoint)) {
                allocateBasicLine(row, mColumns);
                basicMode = 1;
            } else {
                allocateFullLine(row, mColumns);
                basicMode = 0;
            }
        }

        if (mLines[row] instanceof char[]) {
            char[] line = (char[]) mLines[row];

            if (basicMode == -1) {
                if (isBasicChar(codePoint)) {
                    basicMode = 1;
                } else {
                    basicMode = 0;
                }
            }

            if (basicMode == 1) {
                // Fast path -- just put the char in the array
                line[column] = (char) codePoint;
                return true;
            }

            // Need to switch to the full-featured mode
            mLines[row] = new FullUnicodeLine(line);
        }

        FullUnicodeLine line = (FullUnicodeLine) mLines[row];
        line.setChar(column, codePoint);
        return true;
    }
}

/*
 * A representation of a line that's capable of handling non-BMP characters,
 * East Asian wide characters, and combining characters.
 *
 * The text of the line is stored in an array of char[], allowing easy
 * conversion to a String and/or reuse by other string-handling functions.
 * An array of short[] is used to keep track of the difference between a column
 * and the starting index corresponding to its contents in the char[] array (so
 * if column 42 starts at index 45 in the char[] array, the offset stored is 3).
 * Column 0 always starts at index 0 in the char[] array, so we use that
 * element of the array to keep track of how much of the char[] array we're
 * using at the moment.
 */
class FullUnicodeLine {
    private static final float SPARE_CAPACITY_FACTOR = 1.5f;

    private char[] mText;
    private short[] mOffset;
    private int mColumns;

    public FullUnicodeLine(int columns) {
        commonConstructor(columns);
        char[] text = mText;
        // Fill in the line with blanks
        for (int i = 0; i < columns; ++i) {
            text[i] = ' ';
        }
        // Store the space used
        mOffset[0] = (short) columns;
    }

    public FullUnicodeLine(char[] basicLine) {
        commonConstructor(basicLine.length);
        System.arraycopy(basicLine, 0, mText, 0, mColumns);
        // Store the space used
        mOffset[0] = (short) basicLine.length;
    }

    private void commonConstructor(int columns) {
        mColumns = columns;
        mOffset = new short[columns];
        mText = new char[(int)(SPARE_CAPACITY_FACTOR*columns)];
    }

    public int getSpaceUsed() {
        return mOffset[0];
    }

    public char[] getLine() {
        return mText;
    }

    public int findStartOfColumn(int column) {
        if (column == 0) {
            return 0;
        } else {
            return column + mOffset[column];
        }
    }

    public boolean getChar(int column, int charIndex, char[] out, int offset) {
        int pos = findStartOfColumn(column);
        int length;
        if (column + 1 < mColumns) {
            length = findStartOfColumn(column + 1) - pos;
        } else {
            length = getSpaceUsed() - pos;
        }
        if (charIndex >= length) {
            throw new IllegalArgumentException();
        }
        out[offset] = mText[pos + charIndex];
        return (charIndex + 1 < length);
    }

    public void setChar(int column, int codePoint) {
        int columns = mColumns;
        if (column < 0 || column >= columns) {
            throw new IllegalArgumentException();
        }

        char[] text = mText;
        short[] offset = mOffset;
        int spaceUsed = offset[0];

        int pos = findStartOfColumn(column);

        int charWidth = UnicodeTranscript.charWidth(codePoint);
        int oldCharWidth = UnicodeTranscript.charWidth(text, pos);

        if (charWidth == 2 && column == columns - 1) {
            // A width 2 character doesn't fit in the last column.
            codePoint = ' ';
            charWidth = 1;
        }

        boolean wasExtraColForWideChar = false;
        if (oldCharWidth == 2 && column > 0) {
            /* If the previous screen column starts at the same offset in the
             * array as this one, this column must be the second column used
             * by an East Asian wide character */
            wasExtraColForWideChar = (findStartOfColumn(column - 1) == pos);
        }

        // Get the number of elements in the mText array this column uses now
        int oldLen;
        if (wasExtraColForWideChar && column + 1 < columns) {
            oldLen = findStartOfColumn(column + 1) - pos;
        } else if (column + oldCharWidth < columns) {
            oldLen = findStartOfColumn(column+oldCharWidth) - pos;
        } else {
            oldLen = spaceUsed - pos;
        }

        // Find how much space this column will need
        int newLen = Character.charCount(codePoint);
        if (charWidth == 0) {
            /* Combining characters are added to the contents of the column
               instead of overwriting them, so that they modify the existing
               contents */
            newLen += oldLen;
        }
        int shift = newLen - oldLen;

        // Shift the rest of the line right to make room if necessary
        if (shift > 0) {
            if (spaceUsed + shift > text.length) {
                // We need to grow the array
                char[] newText = new char[text.length + columns];
                System.arraycopy(text, 0, newText, 0, pos);
                System.arraycopy(text, pos + oldLen, newText, pos + newLen, spaceUsed - pos - oldLen);
                mText = text = newText;
            } else {
                System.arraycopy(text, pos + oldLen, text, pos + newLen, spaceUsed - pos - oldLen);
            }
        }

        // Store the character
        if (charWidth > 0) {
            Character.toChars(codePoint, text, pos);
        } else {
            /* Store a combining character at the end of the existing contents,
               so that it modifies them */
            Character.toChars(codePoint, text, pos + oldLen);
        }

        // Shift the rest of the line left to eliminate gaps if necessary
        if (shift < 0) {
            System.arraycopy(text, pos + oldLen, text, pos + newLen, spaceUsed - pos - oldLen);
        }

        // Update space used
        if (shift != 0) {
            spaceUsed += shift;
            offset[0] = (short) spaceUsed;
        }

        /*
         * Handle cases where we need to pad with spaces to preserve column
         * alignment
         *
         * width 2 -> width 1: pad with a space before or after the new
         * character, depending on which of the two previously-occupied columns
         * we wrote into
         *
         * inserting width 2 character into the second column of an existing
         * width 2 character: pad with a space before the new character
         */
        if (oldCharWidth == 2 && charWidth == 1 || wasExtraColForWideChar && charWidth == 2) {
            int nextPos = pos + newLen;
            char[] newText = text;
            if (spaceUsed + 1 > text.length) {
                // Array needs growing
                newText = new char[text.length + columns];
                System.arraycopy(text, 0, newText, 0, wasExtraColForWideChar ? pos : nextPos);
            }

            if (wasExtraColForWideChar) {
                // Padding goes before the new character
                System.arraycopy(text, pos, newText, pos + 1, spaceUsed - pos);
                newText[pos] = ' ';
            } else {
                // Padding goes after the new character
                System.arraycopy(text, nextPos, newText, nextPos + 1, spaceUsed - nextPos);
                newText[nextPos] = ' ';
            }

            if (newText != text) {
                // Update mText to point to the newly grown array
                mText = text = newText;
            }

            // Update space used
            spaceUsed = ++offset[0];

            // Correct the offset for the just-modified column to reflect
            // width change
            if (wasExtraColForWideChar) {
                ++offset[column];
                ++pos;
            } else {
                if (column == 0) {
                    offset[1] = (short) (newLen - 1);
                } else if (column + 1 < columns) {
                    offset[column + 1] = (short) (offset[column] + newLen - 1);
                }
                ++column;
            }

            ++shift;
        }
        
        /*
         * Handle cases where we need to clobber the contents of the next
         * column in order to preserve column alignment
         *
         * width 1 -> width 2: should clobber the contents of the next
         * column (if next column contains wide char, need to pad with a space)
         *
         * inserting width 2 character into the second column of an existing
         * width 2 character: same
         */
        if (oldCharWidth == 1 && charWidth == 2 || wasExtraColForWideChar && charWidth == 2) {
            if (column == columns - 2) {
                // Correct offset for the next column to reflect width change
                offset[column + 1] = (short) (offset[column] - 1);

                // Truncate the line after this character.
                offset[0] = (short) (pos + newLen);
                shift = 0;
            } else {
                // Overwrite the contents of the next column.
                int nextPos = pos + newLen;
                int nextWidth = UnicodeTranscript.charWidth(text, nextPos);
                int nextLen;
                if (column + nextWidth + 1 < columns) {
                    nextLen = findStartOfColumn(column + nextWidth + 1) + shift - nextPos;
                } else {
                    nextLen = spaceUsed - nextPos;
                }

                if (nextWidth == 2) {
                    text[nextPos] = ' ';
                    // Shift the array to match
                    if (nextLen > 1) {
                        System.arraycopy(text, nextPos + nextLen, text, nextPos + 1, spaceUsed - nextPos - nextLen);
                        shift -= nextLen - 1;
                        offset[0] -= nextLen - 1;
                    }
                } else {
                    // Shift the array leftwards
                    System.arraycopy(text, nextPos + nextLen, text, nextPos, spaceUsed - nextPos - nextLen);
                    shift -= nextLen;

                    // Truncate the line
                    offset[0] -= nextLen;
                }

                // Correct the offset for the next column to reflect width change
                if (column == 0) {
                    offset[1] = -1;
                } else {
                    offset[column + 1] = (short) (offset[column] - 1);
                }
                ++column;
            }
        }

        // Update offset table
        if (shift != 0) {
            for (int i = column + 1; i < columns; ++i) {
                offset[i] += shift;
            }
        }
    }
}

