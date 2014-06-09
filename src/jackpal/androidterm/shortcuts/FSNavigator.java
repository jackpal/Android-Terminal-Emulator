//From the desk of Frank P. Westlake; public domain.
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
import java.util.                   HashMap;
import jackpal.androidterm.         R;

public class      FSNavigator
       extends    android.app.Activity
{
  private final int                      ACTION_THEME_SWAP=           0x00000100;
  private final int                      BUTTON_SIZE=                 150;
  private android.content.Context        context=                     this;
  private float                          textLg=                      24;
  private int                            theme=                       android.R.style.Theme;
  private SharedPreferences              SP;
  private File                           cd;
  private File                           extSdCardFile;
  private String                         extSdCard;
  private HashMap<Integer, LinearLayout> cachedFileView;
  private HashMap<Integer, LinearLayout> cachedDirectoryView;
  private HashMap<Integer, TextView>     cachedDividerView;
  private int                            countFileView;
  private int                            countDirectoryView;
  private int                            countDividerView;
  private LinearLayout                   contentView;
  private LinearLayout                   titleView;
  private LinearLayout                   pathEntryView;

  ////////////////////////////////////////////////////////////
  public void onCreate(android.os.Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setTitle(getString(R.string.fsnavigator_title));//"File Selector");
    SP=PreferenceManager.getDefaultSharedPreferences(context);
    theme=SP.getInt("theme", theme);
    setTheme(theme);
    getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    Intent intent= getIntent();
    extSdCardFile=Environment.getExternalStorageDirectory();
    extSdCard=getCanonicalPath(extSdCardFile);
    Uri    uri=intent.getData();
    String path=uri==null?null:uri.getPath();
    if(null == path || null==(chdir(path))) chdir(extSdCard);
    if(intent.hasExtra("title"))           setTitle(intent.getStringExtra("title"));

    titleView=           directoryEntry("..");
    pathEntryView=       fileEntry(null);
    contentView=         makeContentView();
    cachedDirectoryView= new HashMap<Integer, LinearLayout>();
    cachedFileView=      new HashMap<Integer, LinearLayout>();
    cachedDividerView=   new HashMap<Integer, TextView>();
  }
  ////////////////////////////////////////////////////////////
  public void onPause()
  {
    super.onPause();
    doPause();
  }
  ////////////////////////////////////////////////////////////
  private void doPause()
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
    makeView();
  }
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
      toast(getString(R.string.fsnavigator_no_external_storage), 1);//"External storage not available", 1);
      return(extSdCard);
    }
    return(goTo);
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
    TextView tv;
    if(countDividerView<cachedDividerView.size())
    {
      tv=cachedDividerView.get(countDividerView);
    }
    else
    {
      tv=new TextView(context);
      tv.setLayoutParams(
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.FILL_PARENT
        , 1
        , 1
        )
      );
      cachedDividerView.put(countDividerView, tv);
    }
    ++countDividerView;
    return(tv);
  }
  ////////////////////////////////////////////////////////////
  View.OnClickListener fileListener=new View.OnClickListener()
  {
    public void onClick(View view)
    {
      String path=(String)view.getTag();
      if(path!=null)
      {
        setResult(RESULT_OK, getIntent().setData(Uri.fromFile(new File(cd, path))));
        finish();
      }
    }
  };
  ////////////////////////////////////////////////////////////
  private LinearLayout fileView(boolean entryWindow)
  {
    LinearLayout  ll=new LinearLayout(context);
                  ll.setLayoutParams(
                    new LinearLayout.LayoutParams(
                      LinearLayout.LayoutParams.FILL_PARENT
                    , LinearLayout.LayoutParams.WRAP_CONTENT
                    , 1
                    )
                  );
                  ll.setOrientation(LinearLayout.HORIZONTAL);
                  ll.setGravity(android.view.Gravity.FILL);
    final TextView tv;
    if(entryWindow)
    {
      tv=new EditText(context);
      tv.setHint(getString(R.string.fsnavigator_optional_enter_path));
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
      tv.setClickable(true);
      tv.setLongClickable(true);
      tv.setOnClickListener(fileListener);
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
        , BUTTON_SIZE
        , 7
        )
      );
      hv.addView(tv);
      ll.addView(hv);
    }
    tv.setFocusable(true);
    tv.setSingleLine();
    tv.setTextSize(textLg);
    tv.setTypeface(Typeface.SERIF, Typeface.BOLD);
    tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    tv.setPadding(10, 5, 10, 5);
    tv.setId(R.id.textview);//1);

    return(ll);
  }
  ////////////////////////////////////////////////////////////
  private LinearLayout fileEntry(final String entry)
  {
    LinearLayout ll;
    if(entry==null)                           ll=fileView(entry==null);
    else
    {
      if(countFileView<cachedFileView.size()) ll=cachedFileView.get(countFileView);
      else                                    cachedFileView.put(countFileView, ll=fileView(entry==null));
      ++countFileView;
    }
    TextView     tv=(TextView)ll.findViewById(R.id.textview);
                 tv.setText(entry==null?"":entry);
                 tv.setTag(entry==null?"":entry);
    return(ll);
  }
  ////////////////////////////////////////////////////////////
  private ImageView imageViewFolder(boolean up)
  {
    ImageView b1=new ImageView(context);
              b1.setClickable(true);
              b1.setFocusable(true);
              b1.setId(R.id.imageview);
              b1.setLayoutParams(
                new LinearLayout.LayoutParams(
                  120
                , 120
                , 1
                )
              );
              b1.setImageResource(up?R.drawable.ic_folderup:R.drawable.ic_folder);
              b1.setOnClickListener(directoryListener);
              b1.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    return(b1);
  }
  ////////////////////////////////////////////////////////////
  View.OnClickListener directoryListener=new View.OnClickListener()
  {
    public void onClick(View view)
    {
      String path=(String)view.getTag();
      if(path!=null)
      {
        File file=new File(path);
        if(file.isFile())
        {
          setResult(RESULT_OK, getIntent().setData(Uri.fromFile(file)));
          finish();
        }
        else chdir(file);
        makeView();
      }
    }
  };
  ////////////////////////////////////////////////////////////
  private LinearLayout directoryView(boolean up)
  {
    ImageView             b1=imageViewFolder(up);
    TextView              tv=new TextView(context);
    if(up)                tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);//Gravity.CENTER);
    else                  tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                          tv.setClickable(true);
                          tv.setLongClickable(true);
                          tv.setFocusable(true);
                          tv.setOnClickListener(directoryListener);
                          tv.setMaxLines(1);
                          tv.setTextSize(textLg);
                          tv.setPadding(10, 5, 10, 5);
                          tv.setId(R.id.textview);
                          tv.setLayoutParams(
                            new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.FILL_PARENT
                            , BUTTON_SIZE
                            , 1
                            )
                          );
    HorizontalScrollView  hv=new HorizontalScrollView(context);
                          hv.addView(tv);
                          hv.setFillViewport(true);
                          hv.setFocusable(true);
                          hv.setOnClickListener(directoryListener);
                          hv.setLayoutParams(
                            new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.FILL_PARENT
                            , BUTTON_SIZE
                            , 7
                            )
                          );
    LinearLayout          ll=new LinearLayout(context);
                          ll.setLayoutParams(
                            new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.FILL_PARENT
                            , BUTTON_SIZE
                            , 2
                            )
                          );
                          ll.setOrientation(LinearLayout.HORIZONTAL);
                          ll.setGravity(android.view.Gravity.FILL);
                          ll.setOnClickListener(directoryListener);
                          ll.addView(b1);
                          ll.addView(hv);

    return(ll);
  }
  ////////////////////////////////////////////////////////////
  private LinearLayout directoryEntry(final String name)
  {
    boolean up=name.equals("..");
    LinearLayout ll;
    if(up)                                              {ll=directoryView(up);}
    else
    {
      if(countDirectoryView<cachedDirectoryView.size()) {ll=                   cachedDirectoryView.get(countDirectoryView);}
      else                                              {ll=directoryView(up); cachedDirectoryView.put(countDirectoryView, ll);}
      ++countDirectoryView;
    }

    TextView     tv=((TextView)ll.findViewById(R.id.textview));
                 tv.setTag(name);
                 tv.setText(up  ? "["+cd.getPath()+"]"
                                : name
                 );
    ((ImageView)ll.findViewById(R.id.imageview)).setTag(name);
    return(ll);
  }
  ////////////////////////////////////////////////////////////
  public boolean onKeyUp(int keyCode, KeyEvent event)
  {
    if((keyCode == KeyEvent.KEYCODE_BACK)) {finish(); return(true);}
    else return(super.onKeyUp(keyCode, event));
  }
  ////////////////////////////////////////////////////////////
  private LinearLayout makeContentView()
  {
    final LinearLayout  ll=new LinearLayout(context);
                        ll.setLayoutParams(
                          new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.FILL_PARENT
                          , LinearLayout.LayoutParams.WRAP_CONTENT
                          , 1
                          )
                        );
                        ll.setId(R.id.mainview);
                        ll.setOrientation(LinearLayout.VERTICAL);
                        ll.setGravity(android.view.Gravity.FILL);
    final ScrollView    sv=new ScrollView(context);
                        sv.setId(R.id.scrollview);
                        sv.setLayoutParams(
                          new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.FILL_PARENT
                          , LinearLayout.LayoutParams.WRAP_CONTENT
                          , 1
                          )
                        );
                        sv.addView(ll);
    final LinearLayout  bg=new LinearLayout(context);
                        bg.setLayoutParams(
                          new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.FILL_PARENT
                          , LinearLayout.LayoutParams.WRAP_CONTENT
                          , 1
                          )
                        );
                        bg.setOrientation(LinearLayout.VERTICAL);
                        bg.setGravity(android.view.Gravity.FILL);
                        bg.setTag(ll);
                        bg.addView(
                          titleView
                        , android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        bg.addView(sv);
                        bg.addView(
                          pathEntryView
                        , android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        );
    return(bg);
  }
  ////////////////////////////////////////////////////////////
  private void makeView()
  {
    countDirectoryView=countFileView=0;
    ScrollView   sv=(ScrollView)contentView.findViewById(R.id.scrollview);
    LinearLayout ll=(LinearLayout)sv.findViewById(R.id.mainview);
                 ll.removeAllViews();
    if(cd == null) chdir("/");
    String path=getCanonicalPath(cd);

    if(path.equals("")) {chdir(path="/");}
    if(path.equals("/"))
    {
      titleView.setVisibility(View.GONE);
    }
    else
    {
      titleView.setVisibility(View.VISIBLE);
      titleView.requestLayout();
      ((TextView)titleView.findViewById(R.id.textview)).setText("["+cd.getPath()+"]");
    }

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
    ((TextView)pathEntryView.findViewById(R.id.textview)).setText("");
    sv.scrollTo(0, 0);
//    titleView.setSelected(true);
    setContentView(contentView);
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
    menu.add(0, ACTION_THEME_SWAP,  0,  getString(R.string.fsnavigator_change_theme));//"Change theme");
    return(true);
  }
  //////////////////////////////////////////////////////////////////////
  public boolean onOptionsItemSelected(MenuItem item)
  {
    super.onOptionsItemSelected(item);
    return(doOptionsItem(item.getItemId()));
  }
  //////////////////////////////////////////////////////////////////////
  private boolean doOptionsItem(int itemId)
  {
    switch(itemId)
    {
      case ACTION_THEME_SWAP: swapTheme();  return(true);
    }
    return(false);
  }
  //////////////////////////////////////////////////////////////////////
//  private void toast(final String message){toast(message, 0);}
  private void toast(final String message, final int duration)
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
