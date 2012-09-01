package jackpal.androidterm.emulatorview;


import android.annotation.TargetApi;
import android.test.AndroidTestCase;
import android.view.KeyEvent;

@TargetApi(3)
public class TermKeyListenerTest extends  AndroidTestCase {
	KeyStateMachine tkl_AltIsEsc;
	KeyStateMachine tkl_AltNotEsc;
	
	public TermKeyListenerTest() {
		super();
	}
	
	public void setUp() {
		tkl_AltIsEsc = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
				KeyEvent.KEYCODE_ESCAPE, true, false);
		tkl_AltNotEsc = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
				KeyEvent.KEYCODE_ESCAPE, false, false);
	}
	
	public void testKey_a() {
		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
		tkl_AltIsEsc.consumeKeyDownEvent(event);
		byte[] res = tkl_AltIsEsc.getCharSequence();
		assertNotNull(res);
		assertEquals(0x61, res[0]);
	}
	
	public void testKey_X() {
			KeyEvent event = new KeyEvent(1,2,KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, KeyEvent.META_SHIFT_ON);
			tkl_AltIsEsc.consumeKeyDownEvent(event);
			byte[] res = tkl_AltIsEsc.getCharSequence();
			assertNotNull(res);
			assertEquals(0x58, res[0]);
	}

	public void testKey_CTRL_c() {
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
			tkl_AltIsEsc.consumeKeyDownEvent(event);
			byte[] res = tkl_AltIsEsc.getCharSequence();
			assertNotNull(res);
			assertEquals(0x03, res[0]);
	}

	public void testKey_CTRL_c_no_esc() {
		KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
		tkl_AltNotEsc.consumeKeyDownEvent(event);
		byte[] res = tkl_AltNotEsc.getCharSequence();
		assertNotNull(res);
		assertEquals(0x03, res[0]);
    }
	
	public void testKey_Alt_x() {
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
			tkl_AltIsEsc.consumeKeyDownEvent(event);
			byte[] res = tkl_AltIsEsc.getCharSequence();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x78, res[1]);
	}
	
	public void testKey_Alt_x_no_esc() {
		KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
		tkl_AltNotEsc.consumeKeyDownEvent(event);
		byte[] res = tkl_AltNotEsc.getCharSequence();
		assertNull(res);
	}


	public void testKey_Alt_e() {
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E, 0,  KeyEvent.META_ALT_ON);
			tkl_AltIsEsc.consumeKeyDownEvent(event);
			byte res[] = tkl_AltIsEsc.getCharSequence();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x65, res[1]);
	}

	public void testKey_enter() {
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0,  0);
			tkl_AltIsEsc.consumeKeyDownEvent(event);
			byte[] res = tkl_AltIsEsc.getCharSequence();
			assertNotNull(res);
			assertEquals(0x0a, res[0]);
	}
	
	public void testPreconditions() {

	}
	
	public void tearDown() {
	}
	
}
