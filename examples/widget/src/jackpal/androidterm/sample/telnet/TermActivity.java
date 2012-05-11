package jackpal.androidterm.sample.telnet;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

/**
 * This sample activity demonstrates the use of EmulatorView.
 *
 * This activity also demonstrates how to set up a simple TermSession connected
 * to a local program.  The Telnet connection demonstrates a more complex case;
 * see the TelnetSession class for more details.
 */
public class TermActivity extends Activity
{
    private EditText mEntry;
    private EmulatorView mEmulatorView;
    private TermSession mSession;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.term_activity);

        /* Text entry box at the bottom of the activity.  Note that you can
           also send input (whether from a hardware device or soft keyboard)
           directly to the EmulatorView. */
        mEntry = (EditText) findViewById(R.id.term_entry);
        mEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                // Ignore enter-key-up events
                if (ev != null && ev.getAction() == KeyEvent.ACTION_UP) {
                    return false;
                }
                // Don't try to send something if we're not connected yet
                TermSession session = mSession;
                if (mSession == null) {
                    return true;
                }

                Editable e = (Editable) v.getText();
                // Write to the terminal session
                session.write(e.toString());
                session.write('\r');
                TextKeyListener.clear(e);
                return true;
            }
        });

        /* Sends the content of the text entry box to the terminal, without
           sending a carriage return afterwards */
        Button sendButton = (Button) findViewById(R.id.term_entry_send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Don't try to send something if we're not connected yet
                TermSession session = mSession;
                if (mSession == null) {
                    return;
                }
                Editable e = (Editable) mEntry.getText();
                session.write(e.toString());
                TextKeyListener.clear(e);
            }
        });

        /**
         * EmulatorView setup.
         */
        EmulatorView view = (EmulatorView) findViewById(R.id.emulatorView);
        mEmulatorView = view;

        /* Let the EmulatorView know the screen's density. */
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        view.setDensity(metrics);

        /* Create a TermSession. */
        Intent myIntent = getIntent();
        String sessionType = myIntent.getStringExtra("type");
        TermSession session;

        if (sessionType != null && sessionType.equals("telnet")) {
            /* Telnet connection: we need to do the network connect on a
               separate thread, so kick that off and wait for it to finish. */
            connectToTelnet(myIntent.getStringExtra("host"));
            return;
        } else {
            // Create a local shell session.
            session = createLocalTermSession();
            mSession = session;
        }

        /* Attach the TermSession to the EmulatorView. */
        view.attachSession(session);

        /* That's all you have to do!  The EmulatorView will call the attached
           TermSession's initializeEmulator() automatically, once it can
           calculate the appropriate screen size for the terminal emulator. */
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* You should call this to let EmulatorView know that it's visible
           on screen. */
        mEmulatorView.onResume();

        mEntry.requestFocus();
    }

    @Override
    protected void onPause() {
        /* You should call this to let EmulatorView know that it's no longer
           visible on screen. */
        mEmulatorView.onPause();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /**
         * Finish the TermSession when we're destroyed.  This will free
         * resources, stop I/O threads, and close the I/O streams attached
         * to the session.
         *
         * For the local session, closing the streams will kill the shell; for
         * the Telnet session, it closes the network connection.
         */
        if (mSession != null) {
            mSession.finish();
        }

        super.onDestroy();
    }

    /**
     * Create a TermSession connected to a local shell.
     */
    private TermSession createLocalTermSession() {
        /* Instantiate the TermSession ... */
        TermSession session = new TermSession();

        /* ... create a process ... */
        String execPath = LaunchActivity.getDataDir(this) + "/bin/execpty";
        ProcessBuilder execBuild =
                new ProcessBuilder(execPath, "/system/bin/sh", "-");
        execBuild.redirectErrorStream(true);
        Process exec = null;
        try {
            exec = execBuild.start();
        } catch (Exception e) {
            // handle exception here
        }

        /* ... and connect the process's I/O streams to the TermSession. */
        session.setTermIn(exec.getInputStream());
        session.setTermOut(exec.getOutputStream());

        /* You're done! */
        return session;

        /**
         * NB: You can invoke a program without using execpty or a native code
         * method, but the results may not be what you expect, because the
         * process will be connected to a pipe, not a tty device.  tty devices
         * provide services such as flow control and input/output translation
         * which many programs expect.
         *
         * If you do connect a program directly to a TermSession without using
         * a tty, you should probably at a minimum translate '\r' (sent by the
         * Enter key) to '\n' (which most programs expect as their newline
         * input) in write(), and translate '\n' (sent by most programs to
         * indicate a newline) to '\r\n' (which the terminal emulator needs to
         * actually start a new line without overdrawing text or "staircase
         * effect") in processInput(), before sending it to the terminal
         * emulator.
         *
         * For an example of how to obtain and use a tty device in native code,
         * see assets-src/execpty.c.
         */
    }

    /**
     * Connect to the Telnet server.
     */
    public void connectToTelnet(String server) {
        String[] telnetServer = server.split(":", 2);
        final String hostname = telnetServer[0];
        int port = 23;
        if (telnetServer.length == 2) {
            port = Integer.parseInt(telnetServer[1]);
        }
        final int portNum = port;

        /* On Android API >= 11 (Honeycomb and later), network operations
           must be done off the main thread, so kick off a new thread to
           perform the connect. */
        new Thread() {
            public void run() {
                // Connect to the server
                try {
                    Socket socket = new Socket(hostname, portNum);
                    mSocket = socket;
                } catch (IOException e) {
                    Log.e("Telnet", e.toString());
                    return;
                }

                // Notify the main thread of the connection
                mHandler.sendEmptyMessage(MSG_CONNECTED);
            }
        }.start();
    }

    /**
     * Handler which will receive the message from the Telnet connect thread
     * that the connection has been established.
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_CONNECTED) {
                createTelnetSession();
            }
        }
    };
    Socket mSocket;
    private static final int MSG_CONNECTED = 1;

    /* Create the TermSession which will handle the Telnet protocol and
       terminal emulation. */
    private void createTelnetSession() {
        Socket socket = mSocket;

        // Get the socket's input and output streams
        InputStream termIn;
        OutputStream termOut;
        try {
            termIn = socket.getInputStream();
            termOut = socket.getOutputStream();
        } catch (IOException e) {
            // Handle exception here
            return;
        }

        /* Create the TermSession and attach it to the view.  See the
           TelnetSession class for details. */
        TermSession session = new TelnetSession(termIn, termOut);
        mEmulatorView.attachSession(session);
        mSession = session;
    }
}
