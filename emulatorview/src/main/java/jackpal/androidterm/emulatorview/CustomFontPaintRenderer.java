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

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.FloatMath;



class CustomFontPaintRenderer extends PaintRenderer {

    public CustomFontPaintRenderer(int fontSize, ColorScheme scheme, Typeface face) {
        super(scheme);
        //Typeface face = typeface;

        mTextPaint = new Paint();
        mTextPaint.setTypeface(face);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) FloatMath.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) FloatMath.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);

    }
}