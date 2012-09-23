package jackpal.androidterm.emulatorview;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;


/**
 * An ASCII key listener. Supports control characters and escape. Keeps track of
 * the current state of the alt, shift, and control keys.
 */

class TermKeyListener {
    /**
     * This class is responsible for the handling of key events. It consumes
     * key events and when the key events generate characters, then one or more are made
     * available for consumption.
     */

    private final ModifierKey mControlKey;
    private final ModifierKey mFnKey;
    private final ModifierKey mCapsKey;
    private final ModifierKey mAltKey;
    private final int mBackBehavior;
    private final boolean mAllowToggle;
    private final boolean mAppMode;
    private byte[] mCharSequence;
    private boolean mAltSendsEscape;
    private Integer mDeadChar;

    /**
     * Are any of the alt or meta keys pressed?
     */
    private final static int META_ALT_OR_META_MASK =
            KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK;

    TermKeyListener(int controlKey, int fnKey, int backBehavior, boolean altSendsEscape, boolean appMode) {
        mAppMode = appMode;
        mControlKey = new ModifierKey(controlKey);
        mFnKey = new ModifierKey(fnKey);
        mAltKey = new ModifierKey(KeyEvent.KEYCODE_ALT_LEFT);
        mCapsKey = new ModifierKey(KeyEvent.KEYCODE_CAPS_LOCK);
        mBackBehavior = backBehavior;
        mAllowToggle = false;
        mAltSendsEscape = altSendsEscape;
        this.resetKeys();
    }

    public void handleControlKey(boolean down) {
        mControlKey.handleModifierKey(down);
    }

    public void handleFnKey(boolean down) {
        mFnKey.handleModifierKey(down);
    }
    /**
     * Resets the KeyStateMachine into its default state
     *
     */
    public void resetKeys() {
        mControlKey.reset();
        mFnKey.reset();
        mCapsKey.reset();
        mAltKey.reset();
        mCharSequence = null;
    }

    /**
     * Returns the effective state of the metakeys as a bitvector.
     *
     * This method does not change the state of the KeyStateMachine. It strictly
     * combines the stored modifier key state with the MetaState bitvector
     * passed in.
     */

    public int getEffectiveMetaState(int metaState) {
        boolean effectiveCaps = mAllowToggle && mCapsKey.isActive();
        boolean effectiveAlt = mAllowToggle && mAltKey.isActive();
        boolean effectiveCtrl = mAllowToggle && mControlKey.isActive();
        boolean effectiveFn = mAllowToggle && mFnKey.isActive();
        // this construct ors the META states together depending on the booleans
        // for google foo the ? is called the ternary operator.
        // I prefer it because it forces me to supply an alternative to the
        // consequence
        // of the condition.
        return metaState | (effectiveCaps ? KeyEvent.META_SHIFT_MASK : 0)
                | (effectiveAlt ? KeyEvent.META_ALT_MASK : 0)
                | (effectiveCtrl ? KeyEvent.META_CTRL_MASK : 0)
                | (effectiveFn ? KeyEvent.META_FUNCTION_ON : 0);
    }

    /**
     * returns true when the KeyEvent e is the conclusion of a key sequence and
     * a character sequence can be generated. The character sequence is then
     * stored in mCharSequence. When a new KeyEvent sequenced has commenced then
     * mCharSequence is truncated.
     *
     * @return boolean.
     */

//    private static final ByteBuffer BYTE_TO_INT = ByteBuffer.allocate(8);
    private static final byte ESC = 0x1b;


    private static int packKeyCode(int keyCode) {
        return keyCode + C.KEYCODE_OFFSET;
    }

    private static int unpackCharCode(int charCode) {
        return charCode - C.KEYCODE_OFFSET;
    }

    private static boolean isPackedCharCode(int charCode) {
        return (charCode >= C.KEYCODE_OFFSET);
    }

    private static String handleSpecialKeyCode(int packedCharCode, boolean appMode) {
        //the keycode is packed by mapControlChar(). Since it doesn't map to a single char, but to a
        //String it needs to be handled here.
        //Depending on the app mode.
        String code = null;
        int specialKeyCode = unpackCharCode(packedCharCode) ;
        if (specialKeyCode >= 0 && specialKeyCode < C.specialKeyCharSeq.length) {
            if (appMode) {
                code = C.appSpecialKeyCharSeq[specialKeyCode];
            }
            if (code == null) {
                code = C.specialKeyCharSeq[specialKeyCode];
            }
        }
        return code;
    }

    public int mapControlChars(int c) {
        int effectiveChar;
        if (mControlKey.isActive()) {
            effectiveChar = mapControlChar(c);
        } else if (mFnKey.isActive()) {
            effectiveChar = mapFnChar(c);
        } else {
            effectiveChar = c;
        }
        return effectiveChar;
    }

    public int mapControlChar(int charCode) {
        // Search is the control key.
        return
        (charCode >= 'a' && charCode <= 'z') ?
            (char) (charCode - 'a' + '\001') :
        (charCode >= 'A' && charCode <= 'Z') ?
            (char) (charCode - 'A' + '\001') :
        (charCode == ' ' || charCode == '2') ?
            0 :
        (charCode == '[' || charCode == '3') ?
            27 : // ^[ (Esc)
        (charCode == '\\' || charCode == '4') ?
            28 :
        (charCode == ']' || charCode == '5') ?
            29 :
        (charCode == '^' || charCode == '6') ?
            30 : // control-^
        (charCode == '_' || charCode == '7') ?
            31 :
        (charCode == '8') ?
            127 : // DEL
        (charCode == '9') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F11:
        (charCode == '0') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F12:
            charCode;
    }

    private int mapFnChar(int charCode) {
        return (charCode == 'w' || charCode == 'W') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_UP:
         (charCode == 'a' || charCode == 'A') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_LEFT:
         (charCode == 's' || charCode == 'S') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_DOWN:
         (charCode == 'd' || charCode == 'D') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_DPAD_RIGHT:
         (charCode == 'p' || charCode == 'P') ?
            C.KEYCODE_OFFSET + C.KEYCODE_PAGE_UP:
         (charCode == 'n' || charCode == 'N') ?
            C.KEYCODE_OFFSET + C.KEYCODE_PAGE_DOWN:
         (charCode == 't' || charCode == 'T') ?
            C.KEYCODE_OFFSET + KeyEvent.KEYCODE_TAB:
         (charCode == 'l' || charCode == 'L') ?
            '|':
         (charCode == 'u' || charCode == 'U') ?
            '_':
         (charCode == 'e' || charCode == 'E') ?
            27: // ^[ (Esc)
         (charCode == '.') ?
            28: // ^\
         (charCode > '0' && charCode <= '9') ?
            // F1-F9
            (char)(charCode + C.KEYCODE_OFFSET + C.KEYCODE_F1 - 1):
         (charCode == '0') ?
            C.KEYCODE_OFFSET + C.KEYCODE_F10:
         (charCode == 'i' || charCode == 'I') ?
            C.KEYCODE_OFFSET + C.KEYCODE_INSERT:
         (charCode == 'x' || charCode == 'X') ?
            C.KEYCODE_OFFSET + C.KEYCODE_FORWARD_DEL:
         (charCode == 'h' || charCode == 'H') ?
            C.KEYCODE_OFFSET + C.KEYCODE_MOVE_HOME:
         (charCode == 'f' || charCode == 'F') ?
            C.KEYCODE_OFFSET + C.KEYCODE_MOVE_END:
            charCode;
    }

    private boolean ctrlActive(int metaState) {
        return mControlKey.isActive() || ((metaState & KeyEvent.META_CTRL_ON) != 0);
    }

    private boolean fnActive(int metaState) {
        return mFnKey.isActive() || ((metaState & KeyEvent.META_FUNCTION_ON) != 0);
    }

    private static final int NO_CHAR = KeyCharacterMap.COMBINING_ACCENT;

    private int handleDeadKey(KeyEvent e, int metaState, int masks) {
        int charcode;
        int charCode = e.getUnicodeChar(metaState & (~masks));
        if ((charCode & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            mDeadChar = charCode;
            charcode = NO_CHAR;
        } else if (mDeadChar != null) {
            int c = KeyEvent.getDeadChar(mDeadChar, charCode);
            charcode = (c != 0) ? NO_CHAR : c;
            mDeadChar = null;
        } else if (charCode == 0) {
            charcode = NO_CHAR;
        } else {
            charcode = (ctrlActive(metaState) ?
                    mapControlChar(charCode) :
                fnActive(metaState) ? mapFnChar(charCode) :
                    charCode);
            assert(charcode != NO_CHAR);
        }
        return charcode;
    }

    private boolean handleCharEvent(KeyEvent e) {
        int metaState = getEffectiveMetaState(e.getMetaState());
        // The CTRL Key must be masked when using e.getUnicodeChar().
        // FIXME Please document why CTRL must be masked.

        // We want to mask the Alt key because of the mAltSendsEscape flag,
        // but only when Alt is pressed.

        // Some keyboards/IMEs (e.g. Hacker Keyboard IME) will set "Meta"
        // key down mask bits instead of "Alt" key down mask bits.
        boolean prefixEscFlag =
                (mAltSendsEscape && ((metaState & META_ALT_OR_META_MASK) != 0));
        int unicodeMask = KeyEvent.META_CTRL_MASK
                | (prefixEscFlag ? META_ALT_OR_META_MASK : 0);
        byte[] directMapping = lookupDirectMap(
                packKeyCode(e.getKeyCode()), mAppMode, prefixEscFlag);
        if (directMapping != null) {
            // don't handle the key event any further when there is a direct map
            // entry.
            mCharSequence = directMapping;
        } else if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            mCharSequence = new byte[] { (byte) mBackBehavior };
        } else {
            int charCode = handleDeadKey(e, metaState, unicodeMask);
            mCharSequence = lookupDirectMap(charCode, mAppMode, prefixEscFlag);
        }
        return true;
    }

    public static byte[] lookupDirectMap(int charCode, boolean appMode,
            boolean prefixEsc) {
        ArrayList<Byte> escSeq = new ArrayList<Byte>();
        if (isPackedCharCode(charCode)) {
            String data = handleSpecialKeyCode(charCode, appMode);
            if (data != null) {
                try {
                    byte[] x = data.getBytes("UTF-8");
                    if (prefixEsc) { escSeq.add(ESC); }
                    for (int i=0; i<x.length; i++) {
                        escSeq.add(x[i]);
                    }
                } catch (UnsupportedEncodingException ex) {
                    //ignore
                }
            } else {
                escSeq = null;
            }
        } else if (charCode != NO_CHAR) {
            if (prefixEsc) { escSeq.add(ESC); }
            escSeq.add((byte) charCode);
        } else {
            escSeq = null;
        }
        byte[] data = null;
        if (escSeq != null) {
            data = new byte[escSeq.size()];
            int i = 0;
            for (byte e: escSeq) {
                data[i++] = e;
            }
        }
        return data;
    }

    /**
     * extracts the current character sequence from the key listener.
     * Automatically resets the character sequence to null.
     * @return the current character sequence
     */
    public byte[] extractCharSequence() {
        byte[] result = mCharSequence;
        mCharSequence = null;
        return result;
    }

    public boolean keyDown(KeyEvent e) {
        final int keycode = e.getKeyCode();
        return mCapsKey.handleModifierKey(keycode, true)
                || mAltKey.handleModifierKey(keycode, true)
                || mFnKey.handleModifierKey(keycode, true)
                || mControlKey.handleModifierKey(keycode, true)
                || handleCharEvent(e);
    }

    public boolean keyUp(KeyEvent e) {
        final int keycode = e.getKeyCode();
        return mCapsKey.handleModifierKey(keycode, false)
                || mAltKey.handleModifierKey(keycode, false)
                || mFnKey.handleModifierKey(keycode, false)
                || mControlKey.handleModifierKey(keycode, false);
    }
}

/**
 * The state engine for a modifier key. Can be pressed, released, locked, and so
 * on.
 *
 */
class ModifierKey {

    private int mState;

    private static final int UNPRESSED = 0;

    private static final int PRESSED = 1;

    private static final int RELEASED = 2;

    private static final int USED = 3;

    private static final int LOCKED = 4;

    private final int mKeyCode;

    /**
     * Construct a modifier key. UNPRESSED by default.
     *
     */
    public ModifierKey(int keyCode) {
        mState = UNPRESSED;
        mKeyCode = keyCode;
    }

    public boolean handleModifierKey(int incomingKeyCode, boolean down) {
        if (incomingKeyCode == mKeyCode) {
            if (down) {
                this.onPress();
            } else {
                this.onRelease();
            }
            return true;
        } else {
            return false;
        }
    }

    public void handleModifierKey(boolean down) {
        this.handleModifierKey(mKeyCode, down);
    }

    public void onPress() {
        switch (mState) {
        case PRESSED:
            // This is a repeat before use
            break;
        case RELEASED:
            mState = LOCKED;
            break;
        case USED:
            // This is a repeat after use
            break;
        case LOCKED:
            mState = UNPRESSED;
            break;
        default:
            mState = PRESSED;
            break;
        }
    }

    public void onRelease() {
        switch (mState) {
        case USED:
            mState = UNPRESSED;
            break;
        case PRESSED:
            mState = RELEASED;
            break;
        default:
            // Leave state alone
            break;
        }
    }

    public void reset() {
        mState = UNPRESSED;
    }

    public void adjustAfterKeypress() {
        switch (mState) {
        case PRESSED:
            mState = USED;
            break;
        case RELEASED:
            mState = UNPRESSED;
            break;
        default:
            // Leave state alone
            break;
        }
    }

    public boolean isActive() {
        return mState != UNPRESSED;
    }
}
