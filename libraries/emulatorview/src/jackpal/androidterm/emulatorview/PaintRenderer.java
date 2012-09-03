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
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.FloatMath;


class PaintRenderer extends BaseTextRenderer {
    public PaintRenderer(int fontSize, ColorScheme scheme) {
        super(scheme);
        mTextPaint = new Paint();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) FloatMath.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) FloatMath.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
    }

    public void drawTextRun(Canvas canvas, float x, float y, int lineOffset,
            int runWidth, char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor, int effect) {
        if (cursor) {
            mTextPaint.setColor(mCursorPaint);
        } else {
            mTextPaint.setColor(mBackPaint[backColor]);
        }
        float left = x + lineOffset * mCharWidth;
        canvas.drawRect(left, y + mCharAscent - mCharDescent,
                left + runWidth * mCharWidth, y,
                mTextPaint);
        boolean invisible = (effect & TextStyle.fxInvisible) != 0;
        if (!invisible) {
            boolean bold = (effect & (TextStyle.fxBold | TextStyle.fxBlink)) != 0;
            boolean underline = (effect & TextStyle.fxUnderline) != 0;
            if (bold) {
                mTextPaint.setFakeBoldText(true);
            }
            if (underline) {
                mTextPaint.setUnderlineText(true);
            }
            mTextPaint.setColor(mForePaint[foreColor]);
            canvas.drawText(text, index, count, left, y - mCharDescent, mTextPaint);
            if (bold) {
                mTextPaint.setFakeBoldText(false);
            }
            if (underline) {
                mTextPaint.setUnderlineText(false);
            }
        }
    }

    public int getCharacterHeight() {
        return mCharHeight;
    }

    public float getCharacterWidth() {
        return mCharWidth;
    }

    public int getTopMargin() {
        return mCharDescent;
    }

    private Paint mTextPaint;
    private float mCharWidth;
    private int mCharHeight;
    private int mCharAscent;
    private int mCharDescent;
    private static final char[] EXAMPLE_CHAR = {'X'};
    }