//From the desk of Frank P. Westlake.
package jackpal.androidterm.shortcuts;

import android.content.             Intent;
import android.content.             SharedPreferences;
import android.graphics.            Typeface;
import android.net.                 Uri;
import android.os.                  Environment;
import android.preference.          PreferenceManager;
import android.view.                Gravity;
import android.view.                KeyEvent;
import android.view.                Menu;
import android.view.                MenuItem;
import android.view.                View;
import android.widget.              EditText;
import android.widget.              HorizontalScrollView;
import android.widget.              ImageView;
import android.widget.              LinearLayout;
import android.widget.              ScrollView;
import android.widget.              TextView;
import android.widget.              Toast;
import java.io.                     File;
import java.io.                     IOException;
import jackpal.androidterm.         R;

public class      FSNavigator
       extends    android.app.Activity
{
  public final   String                       strings[]=
  {
    "File Selector"       // Window title.
  , "Or enter path here." // Input box hint.
  };
  public final int                     ACTION_THEME_SWAP=           0x00000100;
  final   int                          WINDOW_TITLE=                0;
  final   int                          INPUT_BOX=                   1;
  final   int                          BUTTON_SIZE=                 150;
  final   int                          VIEW_ID_LL=                  0;
  final   int                          VIEW_ID_TV=                  1;
  final   int                          VIEW_ID_HIGH=                VIEW_ID_TV;
  final   int                          COLOR_LIGHT=                 0xFFAAAAAA;
  final   int                          COLOR_DARK=                  0xFF000000;
  String                               colorScheme=                 COLOR_LIGHT+" "+COLOR_DARK;
  int                                  color_text=                  COLOR_DARK;
  int                                  color_back=                  COLOR_LIGHT;
  private android.content.Context      context=                     this;
  private File                         cd=                          null;
  private float                        textLg=                      24;
  //private File                         zipDir=                      null;
  private boolean                      allowFileEntry=              false;
  private boolean                      allowPathEntry=              true;
  public SharedPreferences             SP=                          null;
  private int                          theme=                       android.R.style.Theme;
  boolean setColors=false;//true;
  private File                         extSdCardFile;
  private String                       extSdCard;

  ////////////////////////////////////////////////////////////
  private void swapTheme()
  {
    switch(theme)
    {
      case android.R.style.Theme:       theme=android.R.style.Theme_Light; break;
      case android.R.style.Theme_Light: theme=android.R.style.Theme;       break;
      default: return;
    }
    SP.edit().putInt("theme", theme).commit();
    startActivityForResult(getIntent().addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT), -1);
    finish();
  }
  ////////////////////////////////////////////////////////////
  private String ifAvailable(String goTo)
  {
    if(goTo.startsWith(extSdCard))
    {
      String s=Environment.getExternalStorageState();
      if(s.equals(Environment.MEDIA_MOUNTED)
      || s.equals(Environment.MEDIA_MOUNTED_READ_ONLY)
      )
      {
        return(goTo);
      }
      toast("External storage not available", 1);
      return(extSdCard);
    }
    return(goTo);
  }
  ////////////////////////////////////////////////////////////
  public void onCreate(android.os.Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setTitle(strings[WINDOW_TITLE]);
    SP=PreferenceManager.getDefaultSharedPreferences(context);
    theme=SP.getInt("theme", theme);
    setTheme(theme);
    getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    Intent intent= getIntent();
    extSdCardFile=Environment.getExternalStorageDirectory();
    extSdCard=getCanonicalPath(extSdCardFile);
    if(null==(chdir(intent.getData().getPath()))) chdir(extSdCard);
    if(intent.hasExtra("title"))           setTitle(intent.getStringExtra("title"));
    if(intent.hasExtra("allowFileEntry"))  allowFileEntry=intent.getBooleanExtra("allowFileEntry", allowFileEntry);
    if(intent.hasExtra("allowPathEntry"))  allowPathEntry=intent.getBooleanExtra("allowFileEntry", allowPathEntry);
    if(intent.hasExtra("colorScheme"))     colorScheme=intent.getStringExtra("colorScheme");
    setColorScheme();
    //makeView();
  }
  ////////////////////////////////////////////////////////////
  public void onPause()
  {
    super.onPause();
    doPause();
  }
  ////////////////////////////////////////////////////////////
  public void doPause()
  {
    SP.edit().putString("lastDirectory", getCanonicalPath(cd)).commit();
  }
  ////////////////////////////////////////////////////////////
  public void onResume()
  {
    super.onResume();
    doResume();
  }
  ////////////////////////////////////////////////////////////
  private void doResume()
  {
    //String lastDirectory=SP.getString("lastDirectory", null);
    //if(lastDirectory!=null) chdir(lastDirectory);
    makeView();
  }
  ////////////////////////////////////////////////////////////
  //public void onDestroy()
  //{
  //  super.onDestroy();
  //}
  ////////////////////////////////////////////////////////////
  void setColorScheme()
  {
    colorScheme=SP.getString("colorScheme", colorScheme);
    String ss[]=colorScheme.split("\\s+");
    color_back=Integer.decode(ss[0]);
    color_text=Integer.decode(ss[1]);
  }
  ////////////////////////////////////////////////////////////
  private File chdir(File file)
  {
    String path=ifAvailable(getCanonicalPath(file));
    System.setProperty("user.dir", path);
    return(cd=new File(path));
  }
  private File chdir(String path)
  {
    return(chdir(new File(path)));
  }
  ////////////////////////////////////////////////////////////
  private TextView entryDividerH()
  {
    LinearLayout.LayoutParams btn_params=new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.FILL_PARENT
    , 1
    , 1
    );
    TextView b1=new TextView(context);
    if(setColors) b1.setBackgroundColor(color_text);
                  b1.setLayoutParams(btn_params);
    return(b1);
  }
  ////////////////////////////////////////////////////////////
  View.OnClickListener fileListener=new View.OnClickListener()
  {
    public void onClick(View view)
    {
      setResult(RESULT_OK, getIntent().setData(Uri.fromFile(new File(cd, (String)view.getTag(R.id.tag_filename)))));
      finish();
    }
  };
  ////////////////////////////////////////////////////////////
  public View fileEntry(final String entry)
  {
    boolean newFile=entry==null && (allowFileEntry || allowPathEntry);
// LAYOUT
    LinearLayout ll=new LinearLayout(context);
    //ll.setBackgroundColor(colorBackground);
    ll.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , LinearLayout.LayoutParams.WRAP_CONTENT
      , 1
      )
    );
    ll.setOrientation(LinearLayout.HORIZONTAL);
    ll.setGravity(android.view.Gravity.FILL);
    if(setColors) ll.setBackgroundColor(color_back);
    ll.setId(0);
// FILENAME
    final TextView tv;
    if(newFile)
    {
      tv=new EditText(context);
           if(allowPathEntry) tv.setHint(strings[INPUT_BOX]);
      else if(allowFileEntry) tv.setHint("Enter new file name here.");
//      tv.setSingleLine();
      if(setColors) tv.setTextColor(color_back);
      if(setColors) tv.setBackgroundColor(color_text);
      tv.setLayoutParams(
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.FILL_PARENT
        , LinearLayout.LayoutParams.FILL_PARENT
        , 2
        )
      );
      tv.setOnKeyListener(
        new EditText.OnKeyListener()
        {
          public boolean onKey(View v, int keyCode, KeyEvent event)
          {
            if(keyCode==KeyEvent.KEYCODE_ENTER)
            {
              String path=tv.getText().toString();
              File file=new File(getCanonicalPath(path));
              chdir(file.getParentFile()==null?file:file.getParentFile());
              if(file.isFile())
              {
                setResult(RESULT_OK, getIntent().setData(Uri.fromFile(file)));
                finish();
              }
              else
              {
                chdir(file);
                makeView();
              }
              return(true);
            }
            return(false);
          }
        }
      );
      ll.addView(tv);
    }
    else
    {
      tv=new TextView(context);
      tv.setText(entry);
      tv.setClickable(true);
      tv.setLongClickable(true);
      tv.setOnClickListener(fileListener);
      if(setColors) tv.setTextColor(color_text);
      tv.setLayoutParams(
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.FILL_PARENT
        , LinearLayout.LayoutParams.FILL_PARENT
        , 1
        )
      );
      HorizontalScrollView hv=new HorizontalScrollView(context);
      hv.setFillViewport(true);
      hv.setLayoutParams(
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.FILL_PARENT
        , BUTTON_SIZE//LinearLayout.LayoutParams.WRAP_CONTENT
        , 7
        )
      );
      hv.addView(tv);
      ll.addView(hv);
    }
    tv.setSingleLine();
//    tv.setMaxLines(1);
    tv.setTextSize(textLg);
//    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    tv.setTypeface(Typeface.SERIF, Typeface.BOLD);
    tv.setTag(R.id.tag_filename, entry);
    tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    tv.setPadding(10, 5, 10, 5);
    tv.setId(1);

    return(ll);
  }
  ////////////////////////////////////////////////////////////
  private ImageView entryFolder(String name)
  {
    LinearLayout.LayoutParams btn_params=new LinearLayout.LayoutParams(
      120//BUTTON_SIZE//LinearLayout.LayoutParams.WRAP_CONTENT
    , 120//BUTTON_SIZE//LinearLayout.LayoutParams.FILL_PARENT
    , 1
    );
    ImageView b1=new ImageView(context);
    b1.setClickable(true);
    // b1.setLongClickable(true);
    b1.setLayoutParams(btn_params);
    b1.setImageResource(name.equals("..")?R.drawable.ic_folderup:R.drawable.ic_folder);
    b1.setOnClickListener(directoryListener);
    b1.setTag(R.id.tag_filename, name);
    b1.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
//      b1.setMaxHeight(10);
//      b1.setMaxWidth(10);
    return(b1);
  }
  ////////////////////////////////////////////////////////////
  View.OnClickListener directoryListener=new View.OnClickListener()
  {
    public void onClick(View view)
    {
      File file=new File((String)view.getTag(R.id.tag_filename));
      if(file.isFile())
      {
        setResult(RESULT_OK, getIntent().setData(Uri.fromFile(file)));
        finish();
      }
      else chdir(file);
      //else chdir((String)view.getTag(R.id.tag_filename));
      makeView();
    }
  };
  ////////////////////////////////////////////////////////////
  public View directoryEntry(final String name)
  {
// LAYOUT
    LinearLayout ll=new LinearLayout(context);
    //ll.setBackgroundColor(colorBackground);
    ll.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , BUTTON_SIZE//LinearLayout.LayoutParams.WRAP_CONTENT
      , 2
      )
    );
    ll.setOrientation(LinearLayout.HORIZONTAL);
    ll.setGravity(android.view.Gravity.FILL);
    if(setColors) ll.setBackgroundColor(color_back);
    ll.setId(0);
    //ll.setTag(R.id.tag_filename, name);
    ll.setOnClickListener(directoryListener);
// FILENAME
    TextView tv=new TextView(context);
    if(name.equals(".."))
    {
      tv.setText("["+cd.getPath()+"]");
      tv.setGravity(Gravity.CENTER);
    }
    else
    {
      tv.setText(name);
      tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    }
    tv.setClickable(true);
    tv.setLongClickable(true);
    tv.setTag(R.id.tag_filename, name);
    tv.setOnClickListener(directoryListener);
    tv.setMaxLines(1);
    if(setColors) tv.setTextColor(color_text);
    tv.setTextSize(textLg);
    tv.setPadding(10, 5, 10, 5);
    tv.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , BUTTON_SIZE//LinearLayout.LayoutParams.FILL_PARENT
      , 1
      )
    );
    HorizontalScrollView hv=new HorizontalScrollView(context);
    hv.addView(tv);
    hv.setFillViewport(true);
    hv.setOnClickListener(directoryListener);
    hv.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , BUTTON_SIZE//LinearLayout.LayoutParams.WRAP_CONTENT
      , 7
      )
    );
    ImageView b1=entryFolder(name);//U+2681, U+2687, U+2689
    ll.addView(b1);
    ll.addView(hv);

    return(ll);
  }
  ////////////////////////////////////////////////////////////
  public boolean onKeyUp(int keyCode, KeyEvent event)
  {
    if((keyCode == KeyEvent.KEYCODE_BACK)) {finish(); return(true);}
    else return(super.onKeyUp(keyCode, event));
  }
  ////////////////////////////////////////////////////////////
  public void makeView()
  {
    if(cd == null) chdir("/");
    String path=getCanonicalPath(cd);
    //chdir(path);
    //setTitle(path);
    final LinearLayout bg=new LinearLayout(context);
    if(setColors) bg.setBackgroundColor(color_back);
    bg.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , LinearLayout.LayoutParams.WRAP_CONTENT
      , 1
      )
    );
    bg.setOrientation(LinearLayout.VERTICAL);
    bg.setGravity(android.view.Gravity.FILL);
    // ll.addView(makeActionBarView());


    final LinearLayout ll=new LinearLayout(context);
    if(setColors) ll.setBackgroundColor(color_back);
    ll.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , LinearLayout.LayoutParams.WRAP_CONTENT
      , 1
      )
    );
    ll.setOrientation(LinearLayout.VERTICAL);
    ll.setGravity(android.view.Gravity.FILL);
    // ll.addView(makeActionBarView());
    final ScrollView sv=new ScrollView(context);
    if(setColors) sv.setBackgroundColor(color_back);
    //sv.setFillViewport(true);
    sv.setLayoutParams(
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.FILL_PARENT
      , LinearLayout.LayoutParams.WRAP_CONTENT
      , 1
      )
    );
    //sv.setBackgroundColor(colorBackground);
    if(path.equals("")) {chdir(path="/");}
    if(!path.equals("/"))
    {
      bg.addView(directoryEntry(".."), android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      //bg.addView(entryDividerH(),      android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      //bg.addView(entryDividerH());
    }
    sv.addView(ll);
    bg.addView(sv);

    String zd[]=cd.list(new java.io.FilenameFilter(){public boolean accept(File file, String name){return(  new File(file, name).isDirectory() );}});
    if(zd!=null)
    {
      java.util.Arrays.sort(zd, 0, zd.length, stringSortComparator);
      for(int i=0, n=zd.length; i<n; i++)
      {
        if(zd[i].equals("."))  continue;
        ll.addView(directoryEntry(zd[i]));
        ll.addView(entryDividerH());
      }
    }
    String zf[]=cd.list(new java.io.FilenameFilter(){public boolean accept(File file, String name){return(!(new File(file, name).isDirectory()));}});
    if(zf!=null)
    {
      java.util.Arrays.sort(zf, 0, zf.length, stringSortComparator);
      for(int i=0, n=zf.length; i<n; i++)
      {
        ll.addView(fileEntry(zf[i]));
        ll.addView(entryDividerH());
      }
    }
    bg.addView(fileEntry(null), android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    //ll.addView(entryDividerH());
    setContentView(bg);
  }
  //////////////////////////////////////////////////////////////////////
  java.util.Comparator<String> stringSortComparator=new java.util.Comparator<String>()
  {
    public int compare(String a, String b) {return(a.toLowerCase().compareTo(b.toLowerCase()));}
  };
  //////////////////////////////////////////////////////////////////////
  String getCanonicalPath(String path)
  {
    return(getCanonicalPath(new File(path)));
  }
  String getCanonicalPath(File file)
  {
    try{return(file.getCanonicalPath());}catch(IOException e){return(file.getPath());}
  }
  //////////////////////////////////////////////////////////////////////
  public boolean onCreateOptionsMenu(Menu menu)
//  public boolean onPrepareOptionsMenu(Menu menu)
  {
    super.onCreateOptionsMenu(menu);
//    super.onPrepareOptionsMenu(menu);    menu.clear();
    menu.add(0, ACTION_THEME_SWAP,  0,  "Change theme");
    return(true);
  }
  //////////////////////////////////////////////////////////////////////
  public boolean onOptionsItemSelected(MenuItem item)
  {
    super.onOptionsItemSelected(item);
    return(doOptionsItem(item.getItemId()));
  }
  //////////////////////////////////////////////////////////////////////
  public boolean doOptionsItem(int itemId)
  {
    switch(itemId)
    {
      case ACTION_THEME_SWAP: swapTheme();  return(true);
    }
    return(false);
  }
  //////////////////////////////////////////////////////////////////////
  public void toast(final String message){toast(message, 0);}
  public void toast(final String message, final int duration)
  {
    runOnUiThread(
      new Runnable()
      {
        public void run()
        {
          Toast.makeText(context, message, duration == 0 ?Toast.LENGTH_SHORT: Toast.LENGTH_LONG).show();
        }
      }
    );
  }
  //////////////////////////////////////////////////////////////////////
}
