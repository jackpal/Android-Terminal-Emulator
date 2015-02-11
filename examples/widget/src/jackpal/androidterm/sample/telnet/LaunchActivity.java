package jackpal.androidterm.sample.telnet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

/**
 * Provides a UI to launch the terminal emulator activity, connected to
 * either a local shell or a Telnet server.
 */
public class LaunchActivity extends Activity
{
    private static final String TAG = "TelnetLaunchActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launch_activity);
        final Context context = this;
        addClickListener(R.id.launchLocal, new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(context, TermActivity.class);
                intent.putExtra("type", "local");
                startActivity(intent);
            }});

        final EditText hostEdit = (EditText) findViewById(R.id.hostname);
        addClickListener(R.id.launchTelnet, new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(context, TermActivity.class);
                intent.putExtra("type", "telnet");
                String hostname = hostEdit.getText().toString();
                intent.putExtra("host", hostname);
                startActivity(intent);
            }});

        // Unpack the binary executable if not already done
        setupBinDir();
    }

    private void addClickListener(int buttonId, OnClickListener onClickListener) {
        ((Button) findViewById(buttonId)).setOnClickListener(onClickListener);
    }

    /**
     * Stuff to grab the 'execpty' binary for this architecture and unpack it
     * into bin/ under our data directory.  See TermActivity to see how we use
     * this program.
     */
    static String getDataDir(Context context) {
        /* On API 4 and later, you can just do this */
        // return context.getApplicationInfo().dataDir;

        String packageName = context.getPackageName();
        PackageManager pm = context.getPackageManager();
        String dataDir = null;
        try {
            dataDir = pm.getApplicationInfo(packageName, 0).dataDir;
        } catch (Exception e) {
            // Won't happen -- we know we're installed
        }
        return dataDir;
    }

    private void setupBinDir() {
        String dataDir = getDataDir(this);
        File binDir = new File(dataDir, "bin");
        if (!binDir.exists()) {
            try {
                binDir.mkdir();
                chmod("755", binDir.getAbsolutePath());
            } catch (Exception e) {
            }
        }

        /**
         * NB: If you actually plan on deploying an app which ships a binary
         * this way, you will want to implement versioning of the binary so
         * that you aren't writing it out every time the app is run.
         */
        File binary = new File(binDir, "execpty");
        String arch = getArch();
        try {
            InputStream src = getAssets().open("execpty-" + arch);
            FileOutputStream dst = new FileOutputStream(binary);
            copyStream(dst, src);
            chmod("755", binary.getAbsolutePath());
        } catch (Exception e) {
        }
    }

    private String getArch() {
        /* Returns the value of uname -m */
        String machine = System.getProperty("os.arch");
        Log.d(TAG, "os.arch is " + machine);

        /* Convert machine name to our arch identifier */
        if (machine.matches("armv[0-9]+(tej?)?l")) {
            return "arm";
        } else if (machine.matches("i[3456]86")) {
            return "x86";
        } else if (machine.equals("OS_ARCH")) {
            /* This is what API < 5 devices seem to return.  Presumably all
               of these are ARM devices. */
            return "arm";
        } else {
            /* Result is correct for mips, and this is probably the best thing
               to do for an unknown arch */
            return machine;
        }
    }

    private void copyStream(OutputStream dst, InputStream src) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = 0;
        while ((bytesRead = src.read(buffer)) >= 0) {
            dst.write(buffer, 0, bytesRead);
        }
        dst.close();
    }

    private void chmod(String... args) throws IOException {
        String[] cmdline = new String[args.length + 1];
        cmdline[0] = "/system/bin/chmod";
        System.arraycopy(args, 0, cmdline, 1, args.length);
        new ProcessBuilder(cmdline).start();
    }
}
