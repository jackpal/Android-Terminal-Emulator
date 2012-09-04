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

    /**
     * Creates a <code>ColorScheme</code> object.
     *
     * @param foreColor The foreground color as an ARGB hex value.
     * @param backColor The background color as an ARGB hex value.
     */
    public ColorScheme(int foreColor, int backColor) {
        this.foreColor = foreColor;
        this.backColor = backColor;
    }

    /**
     * Creates a <code>ColorScheme</code> object from an array.
     *
     * @param scheme An integer array <code>{ foreColorIndex, foreColor,
     *               backColorIndex, backColor }</code>.
     */
    public ColorScheme(int[] scheme) {
        if (scheme.length != 2) {
            throw new IllegalArgumentException();
        }

        this.foreColor = scheme[0];
        this.backColor = scheme[1];
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
}
