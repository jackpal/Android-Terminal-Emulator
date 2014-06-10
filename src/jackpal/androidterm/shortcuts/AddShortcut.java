//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.content.    Context;
import android.content.    DialogInterface;
import android.content.    Intent;
import android.content.    SharedPreferences;
import android.graphics.   Typeface;
import android.net.        Uri;
import android.os.         Bundle;
import android.os.         Environment;
import android.preference. PreferenceManager;
import android.view.       Gravity;
import android.view.       View;
import android.view.       View.OnFocusChangeListener;
import android.widget.     Button;
import android.widget.     ImageView;
import android.widget.     LinearLayout;
import android.widget.     ScrollView;
import android.widget.     TextView;
import android.widget.     EditText;
import jackpal.androidterm.R;
import jackpal.androidterm.compat.AlertDialogCompat;

import java.io.            File;

public class      AddShortcut
       extends    android.app.Activity
{
  private final int                    OP_MAKE_SHORTCUT=            1;
  private final Context                context=                     this;
  private       SharedPreferences      SP;
  private final String                 pkg_jackpal=                 "jackpal.androidterm";
  private       int                    ix=                          0;
  private final int                    PATH=                        ix++
  ,                                    ARGS=                        ix++
  ,                                    NAME=                        ix++;
  private final EditText               et[]=                        new EditText[5];
  private       String                 path;
  private       String                 name="";
  private       String                 iconText[]=                  {"", null};

  //////////////////////////////////////////////////////////////////////
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    SP=PreferenceManager.getDefaultSharedPreferences(context);
    String action=getIntent().getAction();
    if(action!=null && action.equals("android.intent.action.CREATE_SHORTCUT")) makeShortcut();
    else finish();
  }
  //////////////////////////////////////////////////////////////////////
  void makeShortcut()
  {
    if(path==null) path="";
    final AlertDialogCompat.Builder alert =
        AlertDialogCompat.newInstanceBuilder(context, AlertDialogCompat.THEME_HOLO_DARK);
    LinearLayout   lv=new LinearLayout(context);
                   lv.setOrientation(LinearLayout.VERTICAL);
    for(int i=0, n=et.length; i<n; i++) {et[i]=new EditText(context); et[i].setSingleLine(true);}
    if(!path.equals("")) et[0].setText(path);
    et[PATH].setHint(getString(R.string.addshortcut_command_hint));//"command");
    et[NAME].setText(name);
    et[ARGS].setHint(getString(R.string.addshortcut_example_hint));//"--example=\"a\"");
    et[ARGS].setOnFocusChangeListener(
      new OnFocusChangeListener()
      {
        public void onFocusChange(View view, boolean focus)
        {
          if(!focus)
          {
            String s;
            if(
              et[NAME].getText().toString().equals("")
            && !(s=et[ARGS].getText().toString()).equals("")
            )
              et[NAME].setText(s.split("\\s")[0]);
          }
        }
      }
    );

    Button  btn_path=new Button(context);
            btn_path.setText(getString(R.string.addshortcut_button_find_command));//"Find command");
            btn_path.setOnClickListener(
              new View.OnClickListener()
              {
                public void onClick(View p1)
                {
                  String lastPath=SP.getString("lastPath", null);
                  File get= (lastPath==null)
                            ?Environment.getExternalStorageDirectory()
                            :new File(lastPath).getParentFile();
                    Intent pickerIntent=new Intent();
                    if(SP.getBoolean("useInternalScriptFinder", false))
                    {
                      pickerIntent.setClass(getApplicationContext(), jackpal.androidterm.shortcuts.FSNavigator.class)
                      .setData(Uri.fromFile(get))
                      .putExtra("title", getString(R.string.addshortcut_navigator_title));//"SELECT SHORTCUT TARGET")
                    }
                    else
                    {
                      pickerIntent
                      .putExtra("CONTENT_TYPE", "*/*")
                      .setAction(Intent.ACTION_PICK);
                    }
                    startActivityForResult(pickerIntent, OP_MAKE_SHORTCUT);
                }
              }
            );
    lv.addView(
      layoutTextViewH(
        getString(R.string.addshortcut_command_window_instructions)//"Command window requires full path, no arguments. For other commands use Arguments window (ex: cd /sdcard)."
      , null
      , false
      )
    );
    lv.addView(layoutViewViewH(btn_path,          et[PATH]));
    lv.addView(layoutTextViewH(getString(R.string.addshortcut_arguments_label), et[ARGS]));
    lv.addView(layoutTextViewH(getString(R.string.addshortcut_shortcut_label),  et[NAME]));

    final ImageView img=new ImageView(context);
                    img.setImageResource(jackpal.androidterm.R.drawable.ic_launcher);
                    img.setMaxHeight(100);
                    img.setTag(0xFFFFFFFF);
                    img.setMaxWidth(100);
                    img.setAdjustViewBounds(true);
                    img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    final Button    btn_color=new Button(context);
                    btn_color.setText(getString(R.string.addshortcut_button_text_icon));//"Text icon");
                    btn_color.setOnClickListener(
                      new View.OnClickListener()
                      {
                        public void onClick(View p1)
                        {
                          new ColorValue(context, img, iconText);
                        }
                      }
                    );
    lv.addView(
      layoutTextViewH(
          getString(R.string.addshortcut_text_icon_instructions)//"Optionally create a text icon:"
      , null
      , false
      )
    );
    lv.addView(layoutViewViewH(btn_color, img));
    final ScrollView sv=new ScrollView(context);
                     sv.setFillViewport(true);
                     sv.addView(lv);

    alert.setView(sv);
    alert.setTitle(getString(R.string.addshortcut_title));//"Term Shortcut");
    alert.setPositiveButton(
      android.R.string.yes
    , new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          buildShortcut(
            path
          , et[ARGS].getText().toString()
          , et[NAME].getText().toString()
          , iconText[1]
          , (Integer)img.getTag()
          );
        }
      }
    );
    alert.setNegativeButton(
      android.R.string.cancel
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
  //////////////////////////////////////////////////////////////////////
  LinearLayout layoutTextViewH(String text, View vw)
  {
    return(layoutTextViewH(text, vw, true));
  }
  LinearLayout layoutTextViewH(String text, View vw, boolean attributes)
  {
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      TextView                  tv=new TextView(context);
                                tv.setText(text);
      if(attributes)            tv.setTypeface(Typeface.DEFAULT_BOLD);
      if(attributes)            tv.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                                tv.setPadding(10, tv.getPaddingTop(), 10, tv.getPaddingBottom());
      LinearLayout              lh=new LinearLayout(context);
                                lh.setOrientation(LinearLayout.HORIZONTAL);
                                lh.addView(tv, lp);
      if(vw!=null)              lh.addView(vw, lp);
      return(lh);
  }
  //////////////////////////////////////////////////////////////////////
  LinearLayout layoutViewViewH(View vw1, View vw2)
  {
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      LinearLayout  lh=new LinearLayout(context);
                    lh.setOrientation(LinearLayout.HORIZONTAL);
                    lh.addView(vw1, lp);
      if(vw2!=null) lh.addView(vw2, lp);
      return(lh);
  }
  //////////////////////////////////////////////////////////////////////
  void buildShortcut(
      String path
    , String arguments
    , String shortcutName
    , String shortcutText
    , int    shortcutColor
    )
    {
      Uri.Builder urib=new Uri.Builder().scheme("file");
      // Explicitly setting these unused fields to null avoids a NPE when writing to Parcel for Android SDK level 3.
      urib.authority(null).query(null).fragment(null);

      if(path!=null      && !path.equals(""))      urib.path(path);
      if(arguments!=null && !arguments.equals("")) urib.fragment(arguments!=null?arguments:"");
      android.net.Uri uri=urib.build();
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
               , Intent.ShortcutIconResource.fromContext(context, jackpal.androidterm.R.drawable.ic_launcher)
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
    path= null;
    switch(requestCode)
    {
      case OP_MAKE_SHORTCUT:
        if(data!=null && (uri=data.getData())!=null && (path=uri.getPath())!=null)
        {
          SP.edit().putString("lastPath", path).commit();
          et[PATH].setText(path);
          name=path.replaceAll(".*/", "");
          if(et[NAME].getText().toString().equals("")) et[NAME].setText(name);
          if(iconText[0]!=null && iconText[0].equals("")) iconText[0]=name;
        }
        else finish();
        break;
    }
  }
  //////////////////////////////////////////////////////////////////////
}
