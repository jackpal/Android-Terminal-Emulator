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
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        addClickListener(R.id.openNewWindow, new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent =
                        new Intent("jackpal.androidterm.OPEN_NEW_WINDOW");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(intent);
            }});

        final EditText script = (EditText) findViewById(R.id.script);
        script.setText(getString(R.string.default_script));
        addClickListener(R.id.runScript, new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent =
                        new Intent("jackpal.androidterm.RUN_SCRIPT");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                String command = script.getText().toString();
                intent.putExtra("jackpal.androidterm.iInitialCommand", command);
                startActivity(intent);
            }});
    }

    private void addClickListener(int buttonId, OnClickListener onClickListener) {
        ((Button) findViewById(buttonId)).setOnClickListener(onClickListener);
    }
}
