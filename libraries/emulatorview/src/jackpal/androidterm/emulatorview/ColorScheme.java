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
 * A color scheme for a 16-color VT100 terminal.
 */

public class ColorScheme {
    private int foreColorIndex;
    private int foreColor;
    private int backColorIndex;
    private int backColor;

    public ColorScheme(int foreColorIndex, int foreColor, int backColorIndex, int backColor) {
        this.foreColorIndex = foreColorIndex;
        this.foreColor = foreColor;
        this.backColorIndex = backColorIndex;
        this.backColor = backColor;
    }

    public ColorScheme(int[] scheme) {
        if (scheme.length != 4) {
            throw new IllegalArgumentException();
        }

        this.foreColorIndex = scheme[0];
        this.foreColor = scheme[1];
        this.backColorIndex = scheme[2];
        this.backColor = scheme[3];
    }

    public int getForeColor() {
        return foreColor;
    }

    public int getBackColor() {
        return backColor;
    }

    public int getForeColorIndex() {
        return foreColorIndex;
    }

    public int getBackColorIndex() {
        return backColorIndex;
    }
}
