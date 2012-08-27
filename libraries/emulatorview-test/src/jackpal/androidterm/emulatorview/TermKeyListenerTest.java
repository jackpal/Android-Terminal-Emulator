package jackpal.androidterm.emulatorview;

import java.io.IOException;

import junit.framework.Assert;

import android.R.bool;
import android.annotation.TargetApi;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

@TargetApi(3)
public class TermKeyListenerTest extends  AndroidTestCase {

	private TermKeyListener tkl = new TermKeyListener();
	
	public TermKeyListenerTest() {
		super();
	}
	
	public void setUp() {
		
	}
	
	public void testKey_a() {
		try {
		final long time = 0;
		final int deviceId = KeyCharacterMap.FULL;
		final int flags = 0;
		boolean noAppMode = false;
		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
		int keyCode = event.getKeyCode();
		byte[] res = tkl.keyDown(keyCode, event, noAppMode);
		assertEquals(res.length,4);
		assertEquals(0x61, res[0]);
		assertEquals(0x00, res[1]);
		assertEquals(0x00, res[2]);
		assertEquals(0x00, res[3]);
		} catch (IOException e) {
			fail("IOException");
		}
	}
	
	public void testKey_X() {
		try {
			KeyEvent event = new KeyEvent(1,2,KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, KeyEvent.META_SHIFT_ON);
			boolean appMode = false;
			byte[] res = tkl.keyDown(event.getKeyCode(), event, appMode);
			assertEquals(0x58, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
		} catch (IOException e) {
			fail("IOException");
		}
	}
	
	public void testKey_CTRL_C() {
		try {
			KeyEvent e = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
			boolean appMode = false;
			byte[] res = tkl.keyDown(e.getKeyCode(), e, appMode);
			assertEquals(0x03, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testKey_Alt_X() {
		try {
			KeyEvent e = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
			boolean appMode = false;
			byte[] res = tkl.keyDown(e.getKeyCode(), e, appMode);
			assertNotNull(res);
			assertEquals(8, res.length);
			assertEquals(0x1b, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
			assertEquals(0x58, res[4]);
			assertEquals(0x00, res[5]);
			assertEquals(0x00, res[6]);
			assertEquals(0x00, res[7]);
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testKey_Alt_e() {
		try {
			KeyEvent e = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E, 0,  KeyEvent.META_ALT_ON);
			boolean appMode = false;
			int keycode = e.getKeyCode();
			byte[] res = tkl.keyDown(keycode, e, appMode);
			assertNotNull(res);
			assertEquals(8, res.length);
			assertEquals(0x1b, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
			assertEquals(0x65, res[4]);
			assertEquals(0x00, res[5]);
			assertEquals(0x00, res[6]);
			assertEquals(0x00, res[7]);			
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testPreconditions() {
		
	}
	
	public void tearDown() {
		
	}
	
}
