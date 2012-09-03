package jackpal.androidterm.emulatorview;

public final class TextStyle {
    // Effect bitmasks:
    final static int fxNormal = 0;
    final static int fxBold = 1;
    final static int fxItalic = 2;
    final static int fxFaint = 4;
    final static int fxUnderline = 8;
    final static int fxBlink = 16;
    final static int fxInverse = 32;
    final static int fxInvisible = 64;

    static int encode(int foreColor, int backColor, int effect) {
        return ((effect & 0xff) << 16) | ((foreColor & 0xff) << 8) | (backColor & 0xff);
    }

    static int decodeForeColor(int encodedColor) {
        return (encodedColor >> 8) & 0xff;
    }

    static int decodeBackColor(int encodedColor) {
        return encodedColor & 0xff;
    }

    static int decodeEffect(int encodedColor) {
        return (encodedColor >> 16) & 0xff;
    }
}
