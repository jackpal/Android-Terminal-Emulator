//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.app.        Activity;
import android.app.        AlertDialog;
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
import java.io.            File;

public class      AddShortcut
       extends    Activity
{
  private final int                    OP_MAKE_SHORTCUT=            1;
  private Intent                       intent;
  private Context                      context=                     this;
  private SharedPreferences            SP;
  private String                       pkg_jackpal=                 "jackpal.androidterm";
//  private int                          build_version=               android.os.Build.VERSION.SDK_INT;
  private int ix=0;
  private int PATH=ix++, ARGS=ix++, NAME=ix++;//, TEXT=ix++, COLOR=ix++;
  private final EditText et[]=new EditText[5];
  private String                       path;
  private String                       name="";
//  private String                       lastPath="";
  private String                       iconText[]=                   {"", null};

  //////////////////////////////////////////////////////////////////////
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    SP=PreferenceManager.getDefaultSharedPreferences(context);
    intent=getIntent();
    String action=intent.getAction();
    if(action!=null && action.equals("android.intent.action.CREATE_SHORTCUT")) makeShortcut();
    else finish();
  }
  //////////////////////////////////////////////////////////////////////
//  public void onResume()
//  {
//    super.onResume();
//    if(path==null)
//    {
//      intent=getIntent();
//      String action=intent.getAction();
//      if(action!=null && action.equals("android.intent.action.CREATE_SHORTCUT")) makeShortcut();
//      else finish();
//    }
//  }
  //////////////////////////////////////////////////////////////////////
  void makeShortcut()
  {
    if(path==null) path="";
    final AlertDialog.Builder  alert=new AlertDialog.Builder(context);
    LinearLayout   lv=new LinearLayout(context);
                   lv.setOrientation(LinearLayout.VERTICAL);
    for(int i=0, n=et.length; i<n; i++) {et[i]=new EditText(context); et[i].setSingleLine(true);}
    if(!path.equals("")) et[0].setText(path);
    et[PATH].setHint("command");
    et[NAME].setText(name);
    et[ARGS].setHint("--example=\"a\"");
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

    Button         btn_path=new Button(context);
    btn_path.setText("Find command");
    btn_path.setOnClickListener(
      new View.OnClickListener()
      {
        public void onClick(View p1)
        {
          String lastPath=SP.getString("lastPath", null);
          File get= (lastPath==null)
                    ?Environment.getExternalStorageDirectory()
                    :new File(lastPath).getParentFile();
          startActivityForResult(
            new Intent()
            .setClass(getApplicationContext(), jackpal.androidterm.shortcuts.FSNavigator.class)
            .setData(Uri.fromFile(get))
            .putExtra("title", "SELECT SHORTCUT TARGET")
          , OP_MAKE_SHORTCUT
          );
        }
      }
    );
    lv.addView(
      layoutTextViewH(
        "Command window requires full path, no arguments. For other commands use Arguments window (ex: cd /sdcard)."
      , null
      , false
      )
    );
    lv.addView(layoutViewViewH(btn_path,          et[PATH]));
    lv.addView(layoutTextViewH("Arguments:",      et[ARGS]));
    lv.addView(layoutTextViewH("Shortcut label:", et[NAME]));

    final ImageView img=new ImageView(context);
                    img.setImageResource(jackpal.androidterm.R.drawable.ic_launcher);
                    img.setMaxHeight(100);
                    img.setTag(0xFFFFFFFF);
                    img.setMaxWidth(100);
                    img.setAdjustViewBounds(true);
                    img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    final Button    btn_color=new Button(context);
                    btn_color.setText("Text icon");
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
        "Optionally create a text icon:"
      , null
      , false
      )
    );
    lv.addView(layoutViewViewH(btn_color, img));
    final ScrollView sv=new ScrollView(context);
                     sv.setFillViewport(true);
                     sv.addView(lv);

    alert.setView(sv);
    alert.setTitle("Term Shortcut");
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
      android.net.Uri.Builder urib=new android.net.Uri.Builder().scheme("File");
      if(path!=null      && !path.equals(""))      urib.path(path);
      if(arguments!=null && !arguments.equals("")) urib.fragment(arguments!=null?arguments:"");
      android.net.Uri uri=urib.build();
      Intent target=  new Intent().setClassName(pkg_jackpal, pkg_jackpal+".RemoteInterface");
             target.setAction(pkg_jackpal+".RUN_SCRIPT");
             target.setDataAndType(uri, "text/plain");
//               target.putExtra(pkg_jackpal+".iInitialCommand", path);
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
