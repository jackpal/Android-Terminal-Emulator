package jackpal.androidterm.emulatorview;

/**
 * Utility class for dealing with text style lines.
 *
 * We pack color and formatting information for a particular character into an
 * int -- see the TextStyle class for details.  The simplest way of storing
 * that information for a screen row would be to use an array of int -- but
 * given that we only use the lower three bytes of the int to store information,
 * that effectively wastes one byte per character -- nearly 8 KB per 100 lines
 * with an 80-column transcript.
 *
 * Instead, we use an array of bytes and store the bytes of each int
 * consecutively in big-endian order.
 */
final class StyleRow {
    private int mStyle;
    private int mColumns;
    /** Initially null, will be allocated when needed. */
    private byte[] mData;

    StyleRow(int style, int columns) {
        mStyle = style;
        mColumns = columns;
    }

    void set(int column, int style) {
        if (style == mStyle && mData == null) {
            return;
        }
        ensureData();
        setStyle(column, style);
    }

    int get(int column) {
        if (mData == null) {
            return mStyle;
        }
        return getStyle(column);
    }

    boolean isSolidStyle() {
        return mData == null;
    }

    int getSolidStyle() {
        if (mData != null) {
            throw new IllegalArgumentException("Not a solid style");
        }
        return mStyle;
    }

    void copy(int start, StyleRow dst, int offset, int len) {
        // fast case
        if (mData == null && dst.mData == null && start == 0 && offset == 0
                && len == mColumns) {
            dst.mStyle = mStyle;
            return;
        }
        // There are other potentially fast cases, but let's just treat them
        // all the same for simplicity.
        ensureData();
        dst.ensureData();
        System.arraycopy(mData, 3*start, dst.mData, 3*offset, 3*len);

    }

    void ensureData() {
        if (mData == null) {
            allocate();
        }
    }

    private void allocate() {
        mData = new byte[3*mColumns];
        for (int i = 0; i < mColumns; i++) {
            setStyle(i, mStyle);
        }
    }

    private int getStyle(int column) {
        int index = 3 * column;
        byte[] line = mData;
        return line[index] & 0xff | (line[index+1] & 0xff) << 8
                | (line[index+2] & 0xff) << 16;
    }

    private void setStyle(int column, int value) {
        int index = 3 * column;
        byte[] line = mData;
        line[index] = (byte) (value & 0xff);
        line[index+1] = (byte) ((value >> 8) & 0xff);
        line[index+2] = (byte) ((value >> 16) & 0xff);
    }


}
