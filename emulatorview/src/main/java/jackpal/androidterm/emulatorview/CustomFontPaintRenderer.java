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
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import jackpal.androidterm.emulatorview.compat.TypefaceCompat;


class CustomFontPaintRenderer extends PaintRenderer {

    protected String readFile(File input) throws IOException{
        try(BufferedReader reader=new BufferedReader(new FileReader(input))) {
            Log.i("CustomFontPaintRenderer","read font name");
            return reader.readLine();
        }catch (IOException ex) {
            throw new IOException(ex);
        }
    }

    public CustomFontPaintRenderer(int fontSize, ColorScheme scheme) {
        super(scheme);
        File path=new File(Environment.getExternalStorageDirectory(),"jackpal.androidterm.emulatorview.typeface.txt");


        Typeface face = Typeface.MONOSPACE;

        if(path.exists() && path.isFile()) {
            try {
                String filename = readFile(path);
                File fontFilePath = new File(filename);
                if (fontFilePath.exists() && fontFilePath.exists()) {
                    face = TypefaceCompat.createFromFile(fontFilePath);
                }
            }catch (IOException ex) {

                Log.e(CustomFontPaintRenderer.class.getName(),"error loading font",ex);
            }
        }

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