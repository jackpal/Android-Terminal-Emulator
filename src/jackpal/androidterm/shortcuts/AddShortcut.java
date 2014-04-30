//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.app.        Activity;
import android.app.AlertDialog;
import android.content.    Context;
import android.content.    DialogInterface;
import android.content.    Intent;
import android.content.    SharedPreferences;
import android.net.        Uri;
import android.os.         Bundle;
import android.os.         Environment;
import android.preference. PreferenceManager;
import android.widget.     EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
      final AlertDialog.Builder  alert=new AlertDialog.Builder(context);
      LinearLayout   lv=new LinearLayout(context);
                     lv.setOrientation(LinearLayout.VERTICAL);
      final EditText et[]=new EditText[4];
      for(int i=0, n=et.length; i<n; i++) {et[i]=new EditText(context); et[i].setSingleLine(true);}
      et[0].setHint("--example=\"a\"");
      et[1].setText(name);
      et[2].setHint(name);
      et[3].setHint("#FF00FF00");

      lv.addView(layoutHorizontal("Arguments:", et[0]));
      lv.addView(layoutHorizontal("Label:",     et[1]));
      lv.addView(layoutHorizontal("For a text icon fill in text and color value; leave empty to use the application's icon:", null));

      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      LinearLayout   lh=new LinearLayout(context);
                     lh.setOrientation(LinearLayout.HORIZONTAL);
                     lh.addView(et[2], lp);
                     lh.addView(et[3], lp);
      lv.addView(lh);
      ScrollView     sv=new ScrollView(context);
                     sv.setFillViewport(true);
                     sv.addView(lv);

      alert.setView(sv);
      alert.setTitle("ICON DATA");
      alert.setPositiveButton(
        android.R.string.yes
      , new DialogInterface.OnClickListener()
        {
          public void onClick(DialogInterface dialog, int which)
          {
             buildShortcut(path, et);
          }
        }
      );
      alert.setNegativeButton(
        android.R.string.no
      , new DialogInterface.OnClickListener()
        {
          public void onClick(DialogInterface dialog, int which)
          {
            finish();
          }
        }
      );
      alert.show();
    }
  }
  //////////////////////////////////////////////////////////////////////
  LinearLayout layoutHorizontal(String text, EditText et)
  {
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      TextView     tv=new TextView(context);
                   tv.setText(text);
      LinearLayout lh=new LinearLayout(context);
                   lh.setOrientation(LinearLayout.HORIZONTAL);
                   lh.addView(tv, lp);
      if(et!=null) lh.addView(et, lp);
      return(lh);
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
                                       .fragment(arguments)
                                       .build();
    int    shortcutColor=0xFFFFFFFF;
    String s=inputs[COLOR].getText().toString();
    if(s!=null && !s.equals("")) shortcutColor=Long.decode(s).intValue();
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
