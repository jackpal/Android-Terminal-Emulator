package jackpal.androidterm.emulatorview.compat;

/**
 * The classes in this package take advantage of the fact that the VM does
 * not attempt to load a class until it's accessed, and the verifier
 * does not run until a class is loaded.  By keeping the methods which
 * are unavailable on older platforms in subclasses which are only ever
 * accessed on platforms where they are available, we can preserve
 * compatibility with older platforms without resorting to reflection.
 *
 * See http://developer.android.com/resources/articles/backward-compatibility.html
 * and http://android-developers.blogspot.com/2010/07/how-to-have-your-cupcake-and-eat-it-too.html
 * for further discussion of this technique.
 */

public class AndroidCompat {
    public final static int SDK = getSDK();

    private final static int getSDK() {
        int result;
        try {
            result = AndroidLevel4PlusCompat.getSDKInt();
        } catch (VerifyError e) {
            // We must be at an SDK level less than 4.
            try {
                result = Integer.valueOf(android.os.Build.VERSION.SDK);
            } catch (NumberFormatException e2) {
                // Couldn't parse string, assume the worst.
                result = 1;
            }
        }
        return result;
    }
}

class AndroidLevel4PlusCompat {
    static int getSDKInt() {
        return android.os.Build.VERSION.SDK_INT;
    }
}
