//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.app.        Activity;
import android.content.    Context;
import android.content.    DialogInterface;
import android.content.    Intent;
import android.content.    SharedPreferences;
import android.net.        Uri;
import android.os.         Bundle;
import android.os.         Environment;
import android.preference. PreferenceManager;
import android.widget.     EditText;

import java.io.            File;
import jackpal.androidterm.R;

public class      AddShortcut
       extends    Activity
{
  private final int            OP_MAKE_SHORTCUT=    1;
  private Context              context=             this;
  private SharedPreferences    SP;
  private Intent               intent;
  private String               pkg_jackpal=         "jackpal.androidterm";

  //////////////////////////////////////////////////////////////////////
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    SP=PreferenceManager.getDefaultSharedPreferences(context);
    intent=getIntent();
    String action=intent.getAction();
    if(action!=null && action.equals("android.intent.action.CREATE_SHORTCUT")) makeShortcut(null);
    else finish();
  }
  //////////////////////////////////////////////////////////////////////
  void makeShortcut(final String path)
  {
    if(path==null)
    {
      String last=SP.getString("lastPath", null);
      File get=last==null?Environment.getExternalStorageDirectory():new File(last).getParentFile();
      startActivityForResult(
        new Intent()
        .setClass(getApplicationContext(), jackpal.androidterm.shortcuts.FSNavigator.class)
        .setData(Uri.fromFile(get))
        .putExtra("title", "SELECT SCRIPT FILE")
      , OP_MAKE_SHORTCUT
      );
    }
    else
    {
      String name=path.replaceAll(".*/", "");
      GetInput.get(
        context
        , OP_MAKE_SHORTCUT
        , "ICON"
        , new String[]
            {
              "Arguments", ""
            , "Icon label; text appearing below the icon", name
            , "Icon text; leave blank to use default icon",   ""
            , "Icon color", SP.getString("colorShortcut", "0xFFFFFFFF")
            }
        , "OK"
        , null
        , "CANCEL"
        , new GetInput.GetInputCallback()
        {
          public void getInputCallback(int operation, int which, EditText[] edits)
          {
            switch(which)
            {
              case DialogInterface.BUTTON_POSITIVE:
                switch(operation)
                {
                  case OP_MAKE_SHORTCUT: buildShortcut(path, edits); break;
                }
                break;
              case DialogInterface.BUTTON_NEUTRAL:
              case DialogInterface.BUTTON_NEGATIVE:
                finish();
                break;
            }
          }
        }
      );
    }
  }
  //////////////////////////////////////////////////////////////////////
  void buildShortcut(final String path, EditText inputs[])
  {
    int ix=0;
    int ARGS=ix++, NAME=ix++, TEXT=ix++, COLOR=ix++;
    String shortcutName= inputs[NAME].getText().toString();
    String shortcutText= inputs[TEXT].getText().toString();
    String arguments=    inputs[ARGS].getText().toString();
android.net.Uri uri=new android.net.Uri.Builder()
                                   .scheme("File")
                                   .path(path)
                                   .build();
    int    shortcutColor=Long.decode(inputs[COLOR].getText().toString()).intValue();
    Intent target=  new Intent().setClassName(pkg_jackpal, pkg_jackpal+".RemoteInterface");
           target.setAction(pkg_jackpal+".RUN_SCRIPT");
           target.setDataAndType(uri, "text/plain");
           target.putExtra(pkg_jackpal+".window_handle", shortcutName);
           target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Intent wrapper= new Intent();
           wrapper.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
           wrapper.putExtra(Intent.EXTRA_SHORTCUT_INTENT, target);
           if(shortcutName!=null && !shortcutName.equals(""))
           {
             wrapper.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
           }
           if(shortcutText!=null && !shortcutText.equals(""))
           {
             wrapper.putExtra(
               Intent.EXTRA_SHORTCUT_ICON
             , TextIcon.getTextIcon(
                 shortcutText
               , shortcutColor
               , 96
               , 96
               )
             );
           }
           else
           {
             wrapper.putExtra(
               Intent.EXTRA_SHORTCUT_ICON_RESOURCE
             , Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher)
             );
           }
    setResult(RESULT_OK, wrapper);
    finish();
  }
  //////////////////////////////////////////////////////////////////////
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Uri    uri=  null;
    String path= null;
    switch(requestCode)
    {
      case OP_MAKE_SHORTCUT:
        if(data!=null && (uri=data.getData())!=null && (path=uri.getPath())!=null)
        {
          makeShortcut(path);//No null!
        }
        else finish();
        break;
    }
  }
}
