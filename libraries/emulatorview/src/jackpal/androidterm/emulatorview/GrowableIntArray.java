package jackpal.androidterm.emulatorview;

class GrowableIntArray {
    GrowableIntArray(int initalCapacity) {
        mData = new int[initalCapacity];
        mLength = 0;
    }

    void append(int i) {
        if (mLength + 1 > mData.length) {
            int newLength = Math.max((mData.length * 3) >> 1, 16);
            int[] temp = new int[newLength];
            System.arraycopy(mData, 0, temp, 0, mLength);
            mData = temp;
        }
        mData[mLength++] = i;
    }

    int length() {
        return mLength;
    }

    int at(int index) {
        return mData[index];
    }

    int[] mData;
    int mLength;
}
