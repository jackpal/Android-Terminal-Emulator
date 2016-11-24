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

import java.util.Arrays;
import android.graphics.Canvas;

/**
 * A TranscriptScreen is a screen that remembers data that's been scrolled. The
 * old data is stored in a ring buffer to minimize the amount of copying that
 * needs to be done. The transcript does its own drawing, to avoid having to
 * expose its internal data structures.
 */
class TranscriptScreen implements Screen {
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
     * The number of rows in the screen.
     */
    private int mScreenRows;

    private UnicodeTranscript mData;

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
            ColorScheme scheme) {
        init(columns, totalRows, screenRows, TextStyle.kNormalTextStyle);
    }

    private void init(int columns, int totalRows, int screenRows, int style) {
        mColumns = columns;
        mTotalRows = totalRows;
        mScreenRows = screenRows;

        mData = new UnicodeTranscript(columns, totalRows, screenRows, style);
        mData.blockSet(0, 0, mColumns, mScreenRows, ' ', style);
    }

    public void setColorScheme(ColorScheme scheme) {
        mData.setDefaultStyle(TextStyle.kNormalTextStyle);
    }

    public void finish() {
        /*
         * The Android InputMethodService will sometimes hold a reference to
         * us for a while after the activity closes, which is expensive because
         * it means holding on to the now-useless mData array.  Explicitly
         * get rid of our references to this data to help keep the amount of
         * memory being leaked down.
         */
        mData = null;
    }

    public void setLineWrap(int row) {
        mData.setLineWrap(row);
    }

    /**
     * Store a Unicode code point into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param codePoint Unicode codepoint to store
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    public void set(int x, int y, int codePoint, int style) {
        mData.setChar(x, y, codePoint, style);
    }

    public void set(int x, int y, byte b, int style) {
        mData.setChar(x, y, b, style);
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
        mData.scroll(topMargin, bottomMargin, style);
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
        mData.blockCopy(sx, sy, w, h, dx, dy);
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
            int style) {
        mData.blockSet(sx, sy, w, h, val, style);
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
     * @param selx1 the text selection start X coordinate
     * @param selx2 the text selection end X coordinate, if equals to selx1 don't draw selection
     * @param imeText current IME text, to be rendered at cursor
     * @param cursorMode the cursor mode. See TextRenderer.
     */
    public final void drawText(int row, Canvas canvas, float x, float y,
            TextRenderer renderer, int cx, int selx1, int selx2, String imeText, int cursorMode) {
        char[] line;
        StyleRow color;
        int cursorWidth = 1;
        try {
            line = mData.getLine(row);
            color = mData.getLineColor(row);
        } catch (IllegalArgumentException e) {
            // Out-of-bounds rows are blank.
            return;
        } catch (NullPointerException e) {
            // Attempt to draw on a finished transcript
            // XXX Figure out why this happens on Honeycomb
            return;
        }
        int defaultStyle = mData.getDefaultStyle();

        if (line == null) {
            // Line is blank.
            if (selx1 != selx2) {
                // We need to draw a selection
                char[] blank = new char[selx2-selx1];
                Arrays.fill(blank, ' ');
                renderer.drawTextRun(canvas, x, y, selx1, selx2-selx1,
                                blank, 0, 1, true, defaultStyle,
                                cx, 0, 1, 1, cursorMode);
            }
            if (cx != -1) {
                char[] blank = new char[1];
                Arrays.fill(blank, ' ');
                // We need to draw the cursor
                renderer.drawTextRun(canvas, x, y, cx, 1,
                        blank, 0, 1, true, defaultStyle,
                        cx, 0, 1, 1, cursorMode);
            }

            return;
        }

        int columns = mColumns;
        int lineLen = line.length;
        int lastStyle = 0;
        boolean lastSelectionStyle = false;
        int runWidth = 0;
        int lastRunStart = -1;
        int lastRunStartIndex = -1;
        boolean forceFlushRun = false;
        int column = 0;
        int nextColumn = 0;
        int displayCharWidth = 0;
        int index = 0;
        int cursorIndex = 0;
        int cursorIncr = 0;
        while (column < columns && index < lineLen && line[index] != '\0') {
            int incr = 1;
            int width;
            if (Character.isHighSurrogate(line[index])) {
                width = UnicodeTranscript.charWidth(line, index);
                incr++;
            } else {
                width = UnicodeTranscript.charWidth(line[index]);
            }
            if (width > 0) {
                // We've moved on to the next column
                column = nextColumn;
                displayCharWidth = width;
            }
            int style = color.get(column);
            boolean selectionStyle = false;
            if ((column >= selx1 || (displayCharWidth == 2 && column == selx1 - 1)) &&
                    column <= selx2) {
                // Draw selection:
                selectionStyle = true;
            }
            if (style != lastStyle
                    || selectionStyle != lastSelectionStyle
                    || (width > 0 && forceFlushRun)) {
                if (lastRunStart >= 0) {
                    renderer.drawTextRun(canvas, x, y, lastRunStart, runWidth,
                            line,
                            lastRunStartIndex, index - lastRunStartIndex,
                            lastSelectionStyle, lastStyle,
                            cx, cursorIndex, cursorIncr, cursorWidth, cursorMode);
                }
                lastStyle = style;
                lastSelectionStyle = selectionStyle;
                runWidth = 0;
                lastRunStart = column;
                lastRunStartIndex = index;
                forceFlushRun = false;
            }
            if (cx == column) {
                if (width > 0) {
                    cursorIndex = index;
                    cursorIncr = incr;
                    cursorWidth = width;
                } else {
                    // Combining char attaching to the char under the cursor
                    cursorIncr += incr;
                }
            }
            runWidth += width;
            nextColumn += width;
            index += incr;
            if (width > 1) {
                /* We cannot draw two or more East Asian wide characters in the
                   same run, because we need to make each wide character take
                   up two columns, which may not match the font's idea of the
                   character width */
                forceFlushRun = true;
            }
        }
        if (lastRunStart >= 0) {
            renderer.drawTextRun(canvas, x, y, lastRunStart, runWidth,
                    line,
                    lastRunStartIndex, index - lastRunStartIndex,
                    lastSelectionStyle, lastStyle,
                    cx, cursorIndex, cursorIncr, cursorWidth, cursorMode);
        }

        if (cx >= 0 && imeText.length() > 0) {
            int imeLength = Math.min(columns, imeText.length());
            int imeOffset = imeText.length() - imeLength;
            int imePosition = Math.min(cx, columns - imeLength);
            renderer.drawTextRun(canvas, x, y, imePosition, imeLength, imeText.toCharArray(),
                    imeOffset, imeLength, true, TextStyle.encode(0x0f, 0x00, TextStyle.fxNormal),
                    -1, 0, 0, 0, 0);
        }
     }

    /**
     * Get the count of active rows.
     *
     * @return the count of active rows.
     */
    public int getActiveRows() {
        return mData.getActiveRows();
    }

    /**
     * Get the count of active transcript rows.
     *
     * @return the count of active transcript rows.
     */
    public int getActiveTranscriptRows() {
        return mData.getActiveTranscriptRows();
    }

    public String getTranscriptText() {
        return internalGetTranscriptText(null, 0, -mData.getActiveTranscriptRows(), mColumns, mScreenRows);
    }

    public String getTranscriptText(GrowableIntArray colors) {
        return internalGetTranscriptText(colors, 0, -mData.getActiveTranscriptRows(), mColumns, mScreenRows);
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2) {
        return internalGetTranscriptText(null, selX1, selY1, selX2, selY2);
    }

    public String getSelectedText(GrowableIntArray colors, int selX1, int selY1, int selX2, int selY2) {
        return internalGetTranscriptText(colors, selX1, selY1, selX2, selY2);
    }

    private String internalGetTranscriptText(GrowableIntArray colors, int selX1, int selY1, int selX2, int selY2) {
        StringBuilder builder = new StringBuilder();
        UnicodeTranscript data = mData;
        int columns = mColumns;
        char[] line;
        StyleRow rowColorBuffer = null;
        if (selY1 < -data.getActiveTranscriptRows()) {
            selY1 = -data.getActiveTranscriptRows();
        }
        if (selY2 >= mScreenRows) {
            selY2 = mScreenRows - 1;
        }
        for (int row = selY1; row <= selY2; row++) {
            int x1 = 0;
            int x2;
            if ( row == selY1 ) {
                x1 = selX1;
            }
            if ( row == selY2 ) {
                x2 = selX2 + 1;
                if (x2 > columns) {
                    x2 = columns;
                }
            } else {
                x2 = columns;
            }
            line = data.getLine(row, x1, x2);
            if (colors != null) {
                rowColorBuffer = data.getLineColor(row, x1, x2);
            }
            if (line == null) {
                if (!data.getLineWrap(row) && row < selY2 && row < mScreenRows - 1) {
                    builder.append('\n');
                    if (colors != null) {
                        colors.append(0);
                    }
                }
                continue;
            }
            int defaultColor = mData.getDefaultStyle();
            int lastPrintingChar = -1;
            int lineLen = line.length;
            int i;
            int column = 0;
            for (i = 0; i < lineLen; ++i) {
                char c = line[i];
                if (c == 0) {
                    break;
                }
                
                int style = defaultColor;
                try {
                    if (rowColorBuffer != null) {
                        style = rowColorBuffer.get(column);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    // XXX This probably shouldn't happen ...
                    style = defaultColor;
                }

                if (c != ' ' || style != defaultColor) {
                    lastPrintingChar = i;
                }
                if (!Character.isLowSurrogate(c)) {
                    column += UnicodeTranscript.charWidth(line, i);
                }
            }
            if (data.getLineWrap(row) && lastPrintingChar > -1 && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space
                lastPrintingChar = i - 1;
            }
            builder.append(line, 0, lastPrintingChar + 1);
            if (colors != null) {
                if (rowColorBuffer != null) {
                    column = 0;
                    for (int j = 0; j <= lastPrintingChar; ++j) {
                        colors.append(rowColorBuffer.get(column));
                        column += UnicodeTranscript.charWidth(line, j);
                        if (Character.isHighSurrogate(line[j])) {
                            ++j;
                        }
                    }
                } else {
                    for (int j = 0; j <= lastPrintingChar; ++j) {
                        colors.append(defaultColor);
                        char c = line[j];
                        if (Character.isHighSurrogate(c)) {
                            ++j;
                        }
                    }
                }
            }
            if (!data.getLineWrap(row) && row < selY2 && row < mScreenRows - 1) {
                builder.append('\n');
                if (colors != null) {
                    colors.append((char) 0);
                }
            }
        }
        return builder.toString();
    }

    public boolean fastResize(int columns, int rows, int[] cursor) {
        if (mData == null) {
            // XXX Trying to resize a finished TranscriptScreen?
            return true;
        }
        if (mData.resize(columns, rows, cursor)) {
            mColumns = columns;
            mScreenRows = rows;
            return true;
        } else {
            return false;
        }
    }

    public void resize(int columns, int rows, int style) {
        // Ensure backing store will be large enough to hold the whole screen 
        if (rows > mTotalRows) {
            mTotalRows = rows;
        }
        init(columns, mTotalRows, rows, style);
    }

    /**
     *
     * Return the UnicodeTranscript line at this row index.
     * @param row The row index to be queried
     * @return The line of text at this row index
     */
    char[] getScriptLine(int row)
    {
        try
        {
            return mData.getLine(row);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
        catch (NullPointerException e)
        {
            return null;
        }
    }

    /**
     * Get the line wrap status of the row provided.
     * @param row The row to check for line-wrap status
     * @return The line wrap status of the row provided
     */
    boolean getScriptLineWrap(int row)
    {
        return mData.getLineWrap(row);
    }

    /**
     * Get whether the line at this index is "basic" (contains only BMP
     * characters of width 1).
     */
    boolean isBasicLine(int row) {
        if (mData != null) {
            return mData.isBasicLine(row);
        } else {
            return true;
        }
    }
}
