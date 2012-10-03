package jackpal.androidterm.emulatorview;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import android.annotation.TargetApi;
import android.test.AndroidTestCase;
import android.view.KeyEvent;

class MockTermSession extends TermSession {

    private byte[] charseq = null;

    @Override
    public void write(byte[] data, int offset, int count) {
        if (charseq==null) {
            charseq = data;
        } else {
            byte[] tmp = new byte[data.length + charseq.length];
            for (int i = 0; i < charseq.length; i++) {
                tmp[i] = charseq[i];
            }
            for (int i = 0; i < data.length; i++) {
                tmp[i+charseq.length] = data[i];
            }
            charseq = tmp;
        }
    }

    public void clearQueue() {
        charseq = null;
    }

    public byte[] getCharSequence() {
        return charseq;
    }

    @Override
    public void write(String data) {
        try {
            byte[] bytes = data.getBytes("UTF-8");
            write(bytes, 0, bytes.length);
        } catch (UnsupportedEncodingException e) {
        }
    }

    @Override
    public void write(int codePoint) {
        CharBuffer charBuf = CharBuffer.allocate(2);
        ByteBuffer byteBuf = ByteBuffer.allocate(4);
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();

        charBuf.clear();
        byteBuf.clear();
        Character.toChars(codePoint, charBuf.array(), 0);
        encoder.reset();
        encoder.encode(charBuf, byteBuf, true);
        encoder.flush(byteBuf);
        write(byteBuf.array(), 0, byteBuf.position()-1);
    }

}

@TargetApi(3)
public class TermKeyListenerTest extends  AndroidTestCase {
    //TermKeyListener tkl_AltIsEsc;
    TermKeyListener tkl_AltNotEsc;
    //MockTermSession mckTermSessionA = new MockTermSession();
    MockTermSession mckTermSessionB = new MockTermSession();
    public TermKeyListenerTest() {
        super();
    }

    public void setUp() {
        //tkl_AltIsEsc = new TermKeyListener(mckTermSessionA);
        tkl_AltNotEsc = new TermKeyListener(mckTermSessionB);
        mckTermSessionB.clearQueue();
    }

    public void testKey_a()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_A,
                  0,
                  new byte[]{0x61});
    }

    public void testKey_X()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_X,
                  KeyEvent.META_SHIFT_ON,
                  new byte[]{0x58});
    }

    public void testKey_CTRL_c()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_C,

                  KeyEvent.META_CTRL_ON ,
                  new byte[]{0x03});
    }

    public void testKey_ALT_x_no_esc()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_X,

                  KeyEvent.META_ALT_ON,
                  new byte[]{0x00,0x00,0x00,0x00});
    }

    public void testKey_ALT_x_esc()
            throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.setAltSendsEsc(true);
        int keycode = KeyEvent.KEYCODE_ALT_LEFT;
        KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, keycode, 0,
                0);
        tkl_AltNotEsc.keyDown(keycode, event, false, true);
        keyHelperToggle(KeyEvent.KEYCODE_X,

                      KeyEvent.META_ALT_ON,
                      new byte[]{0x1b,0x78}, true);
        }

    public void testKey_ALT_c_no_esc()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_C,

                  KeyEvent.META_ALT_ON,
                  new byte[]{-61,-89,0x00,0x00});
    }


    public void testKey_enter()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_ENTER,
                  0,
                  new byte[]{0x0d});
    }


    public void testKey_del()
        throws UnsupportedEncodingException, IOException {
        KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN,
                                      KeyEvent.KEYCODE_DEL,0 ,0);
        tkl_AltNotEsc.keyDown(event.getKeyCode(), event, true, false);
        byte[] res = mckTermSessionB.getCharSequence();
        byte[] exp = "\177".getBytes("UTF-8");
        assertNotNull(res);
        assertEquals(exp.length, res.length);
        for (int i = 0; i<exp.length; i++) {
            assertEquals(exp[i],res[i]);
        }
    }

    public void testKey_CTRL_C()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_C,
                  KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                  new byte[]{0x03});
    }

    public void testKey_CTRL_2()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_2,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x00});
    }

    public void testKey_CTRL_SPC()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_SPACE,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x00});
    }

    public void testKey_CTRL_3()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_3,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1b});
    }

    public void testKey_CTRL_LEFT_BRACKET()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_LEFT_BRACKET,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1b});
    }

    public void testKey_CTRL_BACKSLASH()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_BACKSLASH,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1c});
    }

    public void testKey_CTRL_4()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_4,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1c});
    }

    public void testKey_CTRL_RIGHT_BRACKET()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_RIGHT_BRACKET,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1d});
    }

    public void testKey_CTRL_5()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_5,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1d});
    }

    public void testKey_CTRL_CARET()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_6,
                  KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                  new byte[]{0x1e});
    }

    public void testKey_CTRL_6()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_6,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1e});
    }

    public void testKey_CTRL_UNDERSCORE()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_MINUS,
                  KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                  new byte[]{0x1f});
    }

    public void testKey_CTRL_7()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_7,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x1f});
    }

    public void testKey_CTRL_8()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_8,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{0x7f});
    }

    public void testKey_CTRL_9()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_9,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{27,91,50,51,126});
    }

    public void testKey_CTRL_0()
        throws UnsupportedEncodingException, IOException {
        keyHelper(KeyEvent.KEYCODE_0,
                  KeyEvent.META_CTRL_ON,
                  new byte[]{27,91,50,52,126});
    }

    public void testKey_FN_w()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_W,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,79,65}, true);
    }

    public void testKey_FN_a()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_A,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,79,68}, true);
    }

    public void testKey_FN_s()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_S,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,79,66}, true);
    }

    public void testKey_FN_d()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_D,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,79,67}, true);
    }

    public void testKey_FN_p()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_P,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,53,126}, true);
    }

    public void testKey_FN_n()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_N,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,54,126}, true);
    }

    public void testKey_FN_t()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_T,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{9}, true);
    }

    public void testKey_FN_l()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_L,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{124,0,0,0}, true);
    }

    public void testKey_FN_u()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_U,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{95,0,0,0}, true);
    }

    public void testKey_FN_e()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_E,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,0,0,0}, true);
    }

    public void testKey_FN_i()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_I,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,50,126}, true);
    }

    public void testKey_FN_x()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_X,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,51,126}, true);
    }

    public void testKey_FN_h()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_H,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,49,126}, true);
    }

    public void testKey_FN_f()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_F,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,52,126}, true);
    }

    public void testKey_FN_PERIOD()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_PERIOD,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{28,0,0,0}, true);
    }

    public void testKey_FN_9()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_9,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{-62,-69,0,0}, true);
    }

    public void testKey_FN_0()
        throws UnsupportedEncodingException, IOException {
        tkl_AltNotEsc.handleFnKey(true);
        keyHelperToggle(KeyEvent.KEYCODE_0,
                  KeyEvent.META_FUNCTION_ON,
                  new byte[]{27,91,50,49,126}, true);
    }

    private void keyHelper(int keycode, int metastate,
            byte[] expectedOutput)
                       throws UnsupportedEncodingException, IOException{
        keyHelperToggle(keycode, metastate, expectedOutput, false);
    }

    private void keyHelperToggle(int keycode, int metastate,
               byte[] expectedOutPut, boolean toggle)
        throws UnsupportedEncodingException, IOException {
        KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, keycode, 0,
                                      metastate);
        tkl_AltNotEsc.keyDown(event.getKeyCode(), event, true, toggle);
        byte[] res = mckTermSessionB.getCharSequence();
        assertNotNull(res);
        assertTrue(expectedOutPut.length <= res.length);
        for (int i=0; i<expectedOutPut.length; i++) {
            assertEquals(expectedOutPut[i], res[i]);
        }
    }

    public void testPreconditions() {

    }

    public void tearDown() {
    }

}
