/*
 * Copyright (C) 2012 Steven Luo
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
 * A class describing a color scheme for a 16-color VT100 terminal.
 * <p>
 * A 16-color VT100 has two separate color maps, one for foreground colors and
 * one for background colors.  Each one contains eight colors, which are
 * traditionally found in the following order:
 * <p>
 * <code>{ black, red, green, yellow, blue, magenta, cyan, white }</code>
 * <p>
 * In addition, each of the foreground colors has a corresponding "bright"
 * version.  Traditionally, the "dim" white is actually a light gray, while
 * the "bright" black is a dark gray color.
 * <p>
 * {@link EmulatorView} supports limited changes to the default color maps
 * via the color scheme mechanism.  Passing a <code>ColorScheme</code> to
 * {@link EmulatorView#setColorScheme setColorScheme} will cause the
 * foreground color with map index <code>foreColorIndex</code> to be replaced
 * with the provided <code>foreColor</code>, and the background color with map
 * index <code>backColorIndex</code> to be replaced with the provided
 * <code>backColor</code>.  The provided colors will then become the default
 * foreground and background colors for the <code>EmulatorView</code>.
 *
 * @see EmulatorView#setColorScheme
 */

public class ColorScheme {
    private int foreColor;
    private int backColor;
    private int cursorForeColor;
    private int cursorBackColor;
    final private static int sDefaultCursorBackColor = 0xff808080;

    private void setDefaultCursorColors() {
        cursorBackColor = sDefaultCursorBackColor;
        // Use the foreColor unless the foreColor is too similar to the cursorBackColor
        int foreDistance = distance(foreColor, cursorBackColor);
        int backDistance = distance(backColor, cursorBackColor);
        if (foreDistance * 2 >= backDistance) {
            cursorForeColor = foreColor;
        } else {
            cursorForeColor = backColor;
        }
    }

    private static int distance(int a, int b) {
        return channelDistance(a, b, 0) * 3 + channelDistance(a, b, 1) * 5
                + channelDistance(a, b, 2);
    }

    private static int channelDistance(int a, int b, int channel) {
        return Math.abs(getChannel(a, channel) - getChannel(b, channel));
    }

    private static int getChannel(int color, int channel) {
        return 0xff & (color >> ((2 - channel) * 8));
    }

    /**
     * Creates a <code>ColorScheme</code> object.
     *
     * @param foreColor The foreground color as an ARGB hex value.
     * @param backColor The background color as an ARGB hex value.
     */
    public ColorScheme(int foreColor, int backColor) {
        this.foreColor = foreColor;
        this.backColor = backColor;
        setDefaultCursorColors();
    }

    /**
     * Creates a <code>ColorScheme</code> object.
     *
     * @param foreColor The foreground color as an ARGB hex value.
     * @param backColor The background color as an ARGB hex value.
     * @param cursorForeColor The cursor foreground color as an ARGB hex value.
     * @param cursorBackColor The cursor foreground color as an ARGB hex value.
     */
    public ColorScheme(int foreColor, int backColor, int cursorForeColor, int cursorBackColor) {
        this.foreColor = foreColor;
        this.backColor = backColor;
        this.cursorForeColor = cursorForeColor;
        this.cursorBackColor = cursorBackColor;
    }

    /**
     * Creates a <code>ColorScheme</code> object from an array.
     *
     * @param scheme An integer array <code>{ foreColor, backColor,
     *               optionalCursorForeColor, optionalCursorBackColor }</code>.
     */
    public ColorScheme(int[] scheme) {
        int schemeLength = scheme.length;
        if (schemeLength != 2 && schemeLength != 4) {
            throw new IllegalArgumentException();
        }
        this.foreColor = scheme[0];
        this.backColor = scheme[1];
        if (schemeLength == 2)  {
            setDefaultCursorColors();
        } else {
            this.cursorForeColor = scheme[2];
            this.cursorBackColor = scheme[3];
        }
    }

    /**
     * @return This <code>ColorScheme</code>'s foreground color as an ARGB
     *         hex value.
     */
    public int getForeColor() {
        return foreColor;
    }

    /**
     * @return This <code>ColorScheme</code>'s background color as an ARGB
     *         hex value.
     */
    public int getBackColor() {
        return backColor;
    }

    /**
     * @return This <code>ColorScheme</code>'s cursor foreground color as an ARGB
     *         hex value.
     */
    public int getCursorForeColor() {
        return cursorForeColor;
    }

    /**
     * @return This <code>ColorScheme</code>'s cursor background color as an ARGB
     *         hex value.
     */
    public int getCursorBackColor() {
        return cursorBackColor;
    }
}
