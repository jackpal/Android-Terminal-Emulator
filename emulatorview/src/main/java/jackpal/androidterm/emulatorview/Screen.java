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
     * Store a Unicode code point into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param codePoint Unicode code point to store
     * @param style the text style
     */
    void set(int x, int y, int codePoint, int style);

    /**
     * Store byte b into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param b ASCII character to store
     * @param style the text style
     */
    void set(int x, int y, byte b, int style);

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style the style for the newly exposed line.
     */
    void scroll(int topMargin, int bottomMargin, int style);

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
     * @param style the text style
     */
    void blockSet(int sx, int sy, int w, int h, int val, int style);

    /**
     * Get the contents of the transcript buffer as a text string.
     *
     * @return the contents of the transcript buffer.
     */
    String getTranscriptText();

    /**
     * Get the contents of the transcript buffer as a text string with color
     * information.
     *
     * @param colors A GrowableIntArray which will hold the colors.
     * @return the contents of the transcript buffer.
     */
    String getTranscriptText(GrowableIntArray colors);

    /**
     * Get the selected text inside transcript buffer as a text string.
     * @param x1 Selection start
     * @param y1 Selection start
     * @param x2 Selection end
     * @param y2 Selection end
     * @return the contents of the transcript buffer.
     */
    String getSelectedText(int x1, int y1, int x2, int y2);

    /**
     * Get the selected text inside transcript buffer as a text string with
     * color information.
     *
     * @param colors A StringBuilder which will hold the colors.
     * @param x1 Selection start
     * @param y1 Selection start
     * @param x2 Selection end
     * @param y2 Selection end
     * @return the contents of the transcript buffer.
     */
    String getSelectedText(GrowableIntArray colors, int x1, int y1, int x2, int y2);

    /**
     * Get the number of "active" (in-use) screen rows, including any rows in a
     * scrollback buffer.
     */
    int getActiveRows();

    /**
     * Try to resize the screen without losing its contents.
     *
     * @param columns
     * @param rows
     * @param cursor An int[2] containing the current cursor position
     *               { col, row }.  If the resize succeeds, the array will be
     *               updated to reflect the new location.
     * @return Whether the resize succeeded. If the operation fails, save the
     *         contents of the screen and then use the standard resize.
     */
    boolean fastResize(int columns, int rows, int[] cursor);

    /**
     * Resize the screen
     * @param columns
     * @param rows
     * @param style
     */
    void resize(int columns, int rows, int style);
}
