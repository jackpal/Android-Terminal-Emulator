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

import android.graphics.Canvas;

/**
 * Text renderer interface
 */

interface TextRenderer {
    public static final int MODE_OFF = 0;
    public static final int MODE_ON = 1;
    public static final int MODE_LOCKED = 2;
    public static final int MODE_MASK = 3;

    public static final int MODE_SHIFT_SHIFT = 0;
    public static final int MODE_ALT_SHIFT = 2;
    public static final int MODE_CTRL_SHIFT = 4;
    public static final int MODE_FN_SHIFT = 6;

    void setReverseVideo(boolean reverseVideo);
    float getCharacterWidth();
    int getCharacterHeight();
    /** @return pixels above top row of text to avoid looking cramped. */
    int getTopMargin();
    /**
     * Draw a run of text
     * @param canvas The canvas to draw into.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param lineOffset
     * @param runWidth
     * @param text
     * @param index
     * @param count
     * @param selectionStyle True to draw the text using the "selected" style (for clipboard copy)
     * @param textStyle
     */
    void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, int runWidth, char[] text,
            int index, int count, boolean selectionStyle, int textStyle);
    // width is width in characters. (1 for normal 2 for wide.)
    void drawCursor(Canvas canvas, float x, float y, int lineOffset,
            int width, int cursorMode);
}
