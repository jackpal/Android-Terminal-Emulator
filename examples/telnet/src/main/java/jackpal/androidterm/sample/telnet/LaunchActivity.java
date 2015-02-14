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
    }

    private void addClickListener(int buttonId, OnClickListener onClickListener) {
        ((Button) findViewById(buttonId)).setOnClickListener(onClickListener);
    }
}
