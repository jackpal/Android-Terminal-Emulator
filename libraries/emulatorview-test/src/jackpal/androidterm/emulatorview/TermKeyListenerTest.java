package jackpal.androidterm.emulatorview;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.http.util.ByteArrayBuffer;

import junit.framework.Assert;

import android.R.bool;
import android.annotation.TargetApi;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

@TargetApi(3)
public class TermKeyListenerTest extends  AndroidTestCase {

	private final boolean noAppMode = false;
	
	public TermKeyListenerTest() {
		super();
	}
	
	public void setUp() {
	}
	
	public void testKey_a() {
		try {
			TermSession session = new TermSession();
			TermKeyListener tkl = new TermKeyListener(session);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] inBuf= new byte[10];
			ByteArrayInputStream in = new ByteArrayInputStream(inBuf);
			session.setTermOut(out);
			session.setTermIn(in);
			session.setDefaultUTF8Mode(true);
			session.initializeEmulator(80, 25);

		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
		int keyCode = event.getKeyCode();
		tkl.keyDown(keyCode, event, noAppMode);
		byte[] queue;
//		try {
//			Field f = session.getClass().getField("mWriteQueue");
//			f.setAccessible(true);
//			ByteQueue b = (ByteQueue) f.get(session);
//			Field buf = b.getClass().getField("mBuffer");
//			buf.setAccessible(true);
//			queue = (byte[]) buf.get(b);
//		} catch (NoSuchFieldException r) {
//			fail("No such field");
//		} catch (IllegalAccessException e){
//			fail("no access");
//		}
		int tries = 5;
		while (out.size() <= 0 && tries-- > 0) {
			//noop;
			try {
				Thread.sleep(1);
			} catch (Exception e) {
				fail("sleep failed");
			}
		}
		if (tries <= 0) {
			fail("no character");
		}
		assertTrue(out.size() > 0);
		byte[] res = out.toByteArray();
		assertEquals(0x61, res[0]);
		} catch (IOException e) {
			fail("IOException");
		}
	}
	
	public void testKey_X() {
		try {
			TermSession session = new TermSession();
			TermKeyListener tkl = new TermKeyListener(session);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] inBuf= new byte[10];
			ByteArrayInputStream in = new ByteArrayInputStream(inBuf);
			session.setTermOut(out);
			session.setTermIn(in);
			session.setDefaultUTF8Mode(true);
			session.initializeEmulator(80, 25);
			KeyEvent event = new KeyEvent(1,2,KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, KeyEvent.META_SHIFT_ON);
			tkl.keyDown(event.getKeyCode(), event, noAppMode);
			int tries = 5;
			while (out.size() <= 0 && tries-- > 0) {
				//noop;
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					fail("sleep failed");
				}
			}
			if (tries <= 0) {
				fail("no character");
			}
			byte[] res = out.toByteArray();
			assertEquals(0x58, res[0]);
		} catch (IOException e) {
			fail("IOException");
		}
	}
//	
	public void testKey_CTRL_C() {
		try {
			TermSession session = new TermSession();
			TermKeyListener tkl = new TermKeyListener(session);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] inBuf= new byte[10];
			ByteArrayInputStream in = new ByteArrayInputStream(inBuf);
			session.setTermOut(out);
			session.setTermIn(in);
			session.setDefaultUTF8Mode(true);
			session.initializeEmulator(80, 25);
			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0,  KeyEvent.META_CTRL_ON);
			tkl.keyDown(event.getKeyCode(), event, noAppMode);
			int tries = 5;
			while (out.size() <= 0 && tries-- > 0) {
				//noop;
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					fail("sleep failed");
				}
			}
			if (tries <= 0) {
				fail("no character");
			}
			byte[] res = out.toByteArray();
			assertEquals(0x03, res[0]);
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testKey_Alt_x() {
		try {
			TermSession session = new TermSession();
			TermKeyListener tkl = new TermKeyListener(session);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] inBuf= new byte[10];
			ByteArrayInputStream in = new ByteArrayInputStream(inBuf);
			session.setTermOut(out);
			session.setTermIn(in);
			session.setDefaultUTF8Mode(true);
			session.initializeEmulator(80, 25);

			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0,  KeyEvent.META_ALT_ON);
			tkl.keyDown(event.getKeyCode(), event, noAppMode);
			int tries = 5;
			while (out.size() <= 0 && tries-- > 0) {
				//noop;
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					fail("sleep failed");
				}
			}
			if (tries <= 0) {
				fail("no character");
			}
			assertTrue(out.size() > 0);
			byte[] res = out.toByteArray();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
			assertEquals(0x58, res[4]);
			assertEquals(0x00, res[5]);
			assertEquals(0x00, res[6]);
			assertEquals(0x00, res[7]);
			session.finish();
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testKey_Alt_e() {
		try {
			TermSession session = new TermSession();
			TermKeyListener tkl = new TermKeyListener(session);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] inBuf= new byte[10];
			ByteArrayInputStream in = new ByteArrayInputStream(inBuf);
			session.setTermOut(out);
			session.setTermIn(in);
			session.setDefaultUTF8Mode(true);
			session.initializeEmulator(80, 25);

			KeyEvent event = new KeyEvent(1,2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E, 0,  KeyEvent.META_ALT_ON);
			int keycode = event.getKeyCode();
			assertEquals(0, out.size());
			tkl.keyDown(keycode, event, noAppMode);
			int tries = 5;
			while (out.size() <= 0 && tries-- > 0) {
				//noop;
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					fail("sleep failed");
				}
			}
			if (tries <= 0) {
				fail("no character");
			}
			byte[] res = out.toByteArray();
			assertNotNull(res);
			assertEquals(0x1b, res[0]);
			assertEquals(0x00, res[1]);
			assertEquals(0x00, res[2]);
			assertEquals(0x00, res[3]);
			assertEquals(0x65, res[4]);
			assertEquals(0x00, res[5]);
			assertEquals(0x00, res[6]);
			assertEquals(0x00, res[7]);
			session.finish();
		} catch (IOException e) {
			fail("IOExceptions");
		}
	}
	
	public void testPreconditions() {
		
	}
	
	public void tearDown() {
	}
	
}
