//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.graphics.  Bitmap;
import android.graphics.  Bitmap.Config;
import android.graphics.  Canvas;
import android.graphics.  Paint;
import android.graphics.  Paint.Align;
import android.graphics.  Rect;
import android.util.      FloatMath;
import java.lang.         Float;

public class TextIcon
{
  ////////////////////////////////////////////////////////////
  public static Bitmap getTextIcon(String text, int color, int width, int height)
  {
    text=text.trim();
    Rect  R=new Rect();
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
          p.setShadowLayer(2, 10, 10, 0xFF000000);
          p.setColor(color);
          p.setSubpixelText(true);
          p.setTextSize(256);
          p.setTextAlign(Align.CENTER);
          p.getTextBounds(text, 0, text.length(), R);

    float H=Float.valueOf(Math.abs(R.top-R.bottom));
    float W=Float.valueOf(Math.abs(R.right-R.left));

    float  S=W<H?H:W;

    Bitmap  b=Bitmap.createBitmap((int)FloatMath.ceil(S), (int)FloatMath.ceil(S), Config.ARGB_8888);
            b.setDensity(Bitmap.DENSITY_NONE);
    Canvas  c=new Canvas(b);
            c.drawText(text, (float)(b.getWidth()/2.0), (float)(b.getHeight()/2.0+H/2.0), p);
    return(Bitmap.createScaledBitmap(b, width, height, true));
  }
  ////////////////////////////////////////////////////////////
}
