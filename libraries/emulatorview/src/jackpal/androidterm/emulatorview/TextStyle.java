package jackpal.androidterm.emulatorview;

public final class TextStyle {
    // Effect bitmasks:
    final static int fxNormal = 0;
    final static int fxBold = 1; // Originally Bright
    final static int fxFaint = 2;
    final static int fxItalic = 4;
    final static int fxUnderline = 8;
    final static int fxBlink = 16;
    final static int fxInverse = 32;
    final static int fxInvisible = 64;

    // Special color indices
    final static int ciForeground = 256; // VT100 text foreground color
    final static int ciBackground = 257; // VT100 text background color
    final static int ciCursor = 258;     // VT100 text cursor color

    final static int ciColorLength = ciCursor + 1;

    final static int kNormalTextStyle = encode(ciForeground, ciBackground, fxNormal);

    static int encode(int foreColor, int backColor, int effect) {
        return ((effect & 0x7f) << 18) | ((foreColor & 0x1ff) << 9) | (backColor & 0x1ff);
    }

    static int decodeForeColor(int encodedColor) {
        return (encodedColor >> 9) & 0x1ff;
    }

    static int decodeBackColor(int encodedColor) {
        return encodedColor & 0x1ff;
    }

    static int decodeEffect(int encodedColor) {
        return (encodedColor >> 18) & 0x7f;
    }
}
