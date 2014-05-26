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

import jackpal.androidterm.emulatorview.compat.AndroidCompat;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;


class Bitmap4x8FontRenderer extends BaseTextRenderer {
    private final static int kCharacterWidth = 4;
    private final static int kCharacterHeight = 8;
    private Bitmap mFont;
    private int mCurrentForeColor;
    private int mCurrentBackColor;
    private float[] mColorMatrix;
    private Paint mPaint;
    private static final float BYTE_SCALE = 1.0f / 255.0f;

    public Bitmap4x8FontRenderer(Resources resources, ColorScheme scheme) {
        super(scheme);
        int fontResource = AndroidCompat.SDK <= 3 ? R.drawable.atari_small
                : R.drawable.atari_small_nodpi;
        mFont = BitmapFactory.decodeResource(resources,fontResource);
        mPaint = new Paint();
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public float getCharacterWidth() {
        return kCharacterWidth;
    }

    public int getCharacterHeight() {
        return kCharacterHeight;
    }

    public int getTopMargin() {
        return 0;
    }

    public void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, int runWidth, char[] text, int index, int count,
            boolean selectionStyle, int textStyle,
            int cursorOffset, int cursorIndex, int cursorIncr, int cursorWidth, int cursorMode) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        int effect = TextStyle.decodeEffect(textStyle);

        boolean inverse = mReverseVideo ^
                ((effect & (TextStyle.fxInverse | TextStyle.fxItalic)) != 0);
        if (inverse) {
            int temp = foreColor;
            foreColor = backColor;
            backColor = temp;
        }

        boolean bold = ((effect & TextStyle.fxBold) != 0);
        if (bold && foreColor < 8) {
            // In 16-color mode, bold also implies bright foreground colors
            foreColor += 8;
        }
        boolean blink = ((effect & TextStyle.fxBlink) != 0);
        if (blink && backColor < 8) {
            // In 16-color mode, blink also implies bright background colors
            backColor += 8;
        }

        if (selectionStyle) {
            backColor = TextStyle.ciCursorBackground;
        }

        boolean invisible = (effect & TextStyle.fxInvisible) != 0;

        if (invisible) {
            foreColor = backColor;
        }

        drawTextRunHelper(canvas, x, y, lineOffset, text, index, count, foreColor, backColor);

        // The cursor is too small to show the cursor mode.
        if (lineOffset <= cursorOffset && cursorOffset < (lineOffset + count)) {
          drawTextRunHelper(canvas, x, y, cursorOffset, text, cursorOffset-lineOffset, 1,
                  TextStyle.ciCursorForeground, TextStyle.ciCursorBackground);
        }
    }

    private void drawTextRunHelper(Canvas canvas, float x, float y, int lineOffset, char[] text,
            int index, int count, int foreColor, int backColor) {
        setColorMatrix(mPalette[foreColor], mPalette[backColor]);
        int destX = (int) x + kCharacterWidth * lineOffset;
        int destY = (int) y;
        Rect srcRect = new Rect();
        Rect destRect = new Rect();
        destRect.top = (destY - kCharacterHeight);
        destRect.bottom = destY;
        boolean drawSpaces = mPalette[backColor] != mPalette[TextStyle.ciBackground];
        for (int i = 0; i < count; i++) {
            // XXX No Unicode support in bitmap font
            char c = text[i + index];
            if ((c < 128) && ((c != 32) || drawSpaces)) {
                int cellX = c & 31;
                int cellY = (c >> 5) & 3;
                int srcX = cellX * kCharacterWidth;
                int srcY = cellY * kCharacterHeight;
                srcRect.set(srcX, srcY,
                        srcX + kCharacterWidth, srcY + kCharacterHeight);
                destRect.left = destX;
                destRect.right = destX + kCharacterWidth;
                canvas.drawBitmap(mFont, srcRect, destRect, mPaint);
            }
            destX += kCharacterWidth;
        }
    }

    private void setColorMatrix(int foreColor, int backColor) {
        if ((foreColor != mCurrentForeColor)
                || (backColor != mCurrentBackColor)
                || (mColorMatrix == null)) {
            mCurrentForeColor = foreColor;
            mCurrentBackColor = backColor;
            if (mColorMatrix == null) {
                mColorMatrix = new float[20];
                mColorMatrix[18] = 1.0f; // Just copy Alpha
            }
            for (int component = 0; component < 3; component++) {
                int rightShift = (2 - component) << 3;
                int fore = 0xff & (foreColor >> rightShift);
                int back = 0xff & (backColor >> rightShift);
                int delta = back - fore;
                mColorMatrix[component * 6] = delta * BYTE_SCALE;
                mColorMatrix[component * 5 + 4] = fore;
            }
            mPaint.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
        }
    }
}
