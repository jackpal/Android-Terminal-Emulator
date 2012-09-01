package jackpal.androidterm.emulatorview;


import android.annotation.TargetApi;
import android.test.AndroidTestCase;
import android.view.KeyEvent;

@TargetApi(3)
public class TermKeyListenerTest extends  AndroidTestCase {
	
	public TermKeyListenerTest() {
		super();
	}
	
	public void setUp() {
	}
	
	public void testKey_a() {
		KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
				KeyEvent.KEYCODE_ESCAPE, true, false);

		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
		tkl.consumeKeyDownEvent(event);
		byte[] res = tkl.getCharSequence();
		assertNotNull(res);
		assertEquals(0x61, res[0]);
	}
	
	public void testKey_X() {
			KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
					KeyEvent.KEYCODE_ESCAPE, true, false);
			KeyEvent event = new KeyEvent(1,2,KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, KeyEvent.META_SHIFT_ON);
			tkl.consumeKeyDownEvent(event);
			byte[] res = tkl.getCharSequence();
			assertNotNull(res);
			assertEquals(0x58, res[0]);
	}

	public void testKey_CTRL_c() {
			KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
					KeyEvent.KEYCODE_ESCAPE, true, false);
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
			tkl.consumeKeyDownEvent(event);
			byte[] res = tkl.getCharSequence();
			assertNotNull(res);
			assertEquals(0x03, res[0]);
	}

	public void testKey_CTRL_c_no_esc() {
		KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
				KeyEvent.KEYCODE_ESCAPE, false, false);
		KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
		tkl.consumeKeyDownEvent(event);
		byte[] res = tkl.getCharSequence();
		assertNotNull(res);
		assertEquals(0x03, res[0]);
    }
	
	public void testKey_Alt_x() {
			KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
					KeyEvent.KEYCODE_ESCAPE, true, false);
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
			tkl.consumeKeyDownEvent(event);
			byte[] res = tkl.getCharSequence();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x78, res[1]);
	}
	
	public void testKey_Alt_x_no_esc() {
		KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
				KeyEvent.KEYCODE_ESCAPE, false, false);
		KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
		tkl.consumeKeyDownEvent(event);
		byte[] res = tkl.getCharSequence();
		assertNull(res);
	}


	public void testKey_Alt_e() {
			KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
					KeyEvent.KEYCODE_ESCAPE, true, false);
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E, 0,  KeyEvent.META_ALT_ON);
			tkl.consumeKeyDownEvent(event);
			byte res[] = tkl.getCharSequence();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x65, res[1]);
	}

	public void testKey_enter() {
			KeyStateMachine tkl = new KeyStateMachine(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
					KeyEvent.KEYCODE_ESCAPE, true, false);
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0,  0);
			tkl.consumeKeyDownEvent(event);
			byte[] res = tkl.getCharSequence();
			assertNotNull(res);
			assertEquals(0x0a, res[0]);
	}
	
	public void testPreconditions() {

	}
	
	public void tearDown() {
	}
	
}
