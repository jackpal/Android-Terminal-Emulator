package jackpal.androidterm.sample.intents;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class IntentSampleActivity extends Activity
{
    private String mHandle;
    private static final int REQUEST_WINDOW_HANDLE = 1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        addClickListener(R.id.openNewWindow, new OnClickListener() {
            public void onClick(View v) {
                // Intent for opening a new window without providing script
                Intent intent =
                        new Intent("jackpal.androidterm.OPEN_NEW_WINDOW");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(intent);
            }});

        final EditText script = (EditText) findViewById(R.id.script);
        script.setText(getString(R.string.default_script));
        addClickListener(R.id.runScript, new OnClickListener() {
            public void onClick(View v) {
                /* Intent for opening a new window and running the provided
                   script -- you must declare the permission
                   jackpal.androidterm.permission.RUN_SCRIPT in your manifest
                   to use */
                Intent intent =
                        new Intent("jackpal.androidterm.RUN_SCRIPT");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                String command = script.getText().toString();
                intent.putExtra("jackpal.androidterm.iInitialCommand", command);
                startActivity(intent);
            }});
        addClickListener(R.id.runScriptSaveWindow, new OnClickListener() {
            public void onClick(View v) {
                /* Intent for running a script in a previously opened window,
                   if it still exists
                   This will open another window if it doesn't find a match */
                Intent intent =
                        new Intent("jackpal.androidterm.RUN_SCRIPT");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                String command = script.getText().toString();
                intent.putExtra("jackpal.androidterm.iInitialCommand", command);
                if (mHandle != null) {
                    // Identify the targeted window by its handle
                    intent.putExtra("jackpal.androidterm.window_handle",
                            mHandle);
                }
                /* The handle for the targeted window -- whether newly opened
                   or reused -- is returned to us via onActivityResult()
                   You can compare it against an existing saved handle to
                   determine whether or not a new window was opened */
                startActivityForResult(intent, REQUEST_WINDOW_HANDLE);
            }});
    }

    private void addClickListener(int buttonId, OnClickListener onClickListener) {
        ((Button) findViewById(buttonId)).setOnClickListener(onClickListener);
    }

    protected void onActivityResult(int request, int result, Intent data) {
        if (result != RESULT_OK) {
            return;
        }

        if (request == REQUEST_WINDOW_HANDLE && data != null) {
            mHandle = data.getStringExtra("jackpal.androidterm.window_handle");
            ((Button) findViewById(R.id.runScriptSaveWindow)).setText(
                    R.string.run_script_existing_window);
        }
    }
}
