package jackpal.androidterm.shortcuts;

import android.app.        AlertDialog;
import android.content.    Context;
import android.content.    DialogInterface;
import android.graphics.   Typeface;
import android.view.       Gravity;
import android.widget.     CheckBox;
import android.widget.     CompoundButton;
import android.widget.     ImageView;
import android.widget.     LinearLayout;
import android.widget.     ScrollView;
import android.widget.     SeekBar;
import android.widget.     TextView;
import android.widget.     EditText;

//////////////////////////////////////////////////////////////////////
public class      ColorValue
       //implements View.OnKeyListener
       //,          CompoundButton.OnCheckedChangeListener
       implements CompoundButton.OnCheckedChangeListener
{
  Context     context;
  SeekBar[]   sb=         {null, null, null, null, null};
  EditText[]  value=      {null, null, null};
  int[]       start=      {0,    0, 0};
  int[]       current=    {0,    0, 0};
  int[]       color=      {0xFF, 0, 0, 0};
//  int         which=      0;
  boolean     started=    false;
  AlertDialog alert;
  AlertDialog.Builder     builder;
  TextView    lt=         null;
  boolean     barLock=    false;
  CheckBox[]  lk=         {null, null, null, null};
  String      Title=      "MAKE TEXT ICON";
  boolean[]   locks=      {false, false, false, false};
  final int   FP=         LinearLayout.LayoutParams.FILL_PARENT;
  final int   WC=         LinearLayout.LayoutParams.WRAP_CONTENT;
  final ImageView imgview;
  final String result[];
  private String imgtext="";
//  private int build_version=               android.os.Build.VERSION.SDK_INT;

  ////////////////////////////////////////////////////////////
  public ColorValue(Context context, final ImageView imgview, final String result[])
  {
    this.context=context;
    this.imgtext=result[0];
    this.imgview=imgview;
    this.result=result;
    colorValue();
  }
  public void colorValue()
  {
//         if(build_version>=14) builder=new AlertDialog.Builder(context, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
//    else if(build_version>=11) builder=new AlertDialog.Builder(context, AlertDialog.THEME_TRADITIONAL);
//    else                       builder=new AlertDialog.Builder(context);
    final TextView hexWindow[]=new TextView[4];
    builder=new AlertDialog.Builder(context);
    LinearLayout  lv=new LinearLayout(context);
                  lv.setOrientation(LinearLayout.VERTICAL);
    String  lab[]={"α ", "R ", "G ", "B "};
    int     clr[]={0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
    for(int i=0, n=(Integer)imgview.getTag(); i<4; i++)  color[i]=(n>>(24-i*8))&0xFF;
    lt=new TextView(context);
    lt.setText("LOCK");
    lt.setPadding(lt.getPaddingLeft(), lt.getPaddingTop(), 5, lt.getPaddingBottom());
    lt.setGravity(Gravity.RIGHT);
    for(int i=0, n=value.length; i<n; i++) {value[i]=new EditText(context);}
    value[0].setText(imgtext);
    value[0].setSingleLine(true);
    value[0].setGravity(Gravity.CENTER);
    value[0].setTextColor((Integer)imgview.getTag());
    value[0].setBackgroundColor((0xFF<<24)|0x007799);
    LinearLayout  vh=new LinearLayout(context);
                  vh.setOrientation(LinearLayout.HORIZONTAL);
                  vh.setGravity(Gravity.CENTER_HORIZONTAL);
                  vh.addView(value[0]);
                  value[0].setHint("Enter icon text");
    lv.addView(vh);
    lv.addView(lt);
    for(int i=0, n=sb.length-1;    i<n; i++)
    {
      LinearLayout    lh=new LinearLayout(context);
                      lh.setGravity(Gravity.CENTER_VERTICAL);
      final TextView  tv=new TextView(context);
                      tv.setTypeface(Typeface.MONOSPACE);
                      tv.setText(lab[i]);
                      tv.setTextColor(clr[i]);
      sb[i]=new SeekBar(context);
      sb[i].setMax(0xFF);
      sb[i].setProgress(color[i]);
      sb[i].setSecondaryProgress(color[i]);
      sb[i].setTag(i);
      sb[i].setBackgroundColor(0xFF<<24|(color[i]<<(24-i*8)));
      sb[i].setLayoutParams(new LinearLayout.LayoutParams(WC, WC, 1));
      sb[i].setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener()
        {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
          {
            doProgressChanged(seekBar, progress, fromUser);
          }
          private void doProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
          {
            if(fromUser && started)
            {
              int  me=(Integer)seekBar.getTag();
              int  k=(color[0]<<24)|(color[1]<<16)|(color[2]<<8)|color[3];
              value[0].setTextColor(k);
              int  start, end;
              if(barLock && locks[me])  {start=0;  end=3;}
              else  start=end=(Integer)seekBar.getTag();
              for(int  i=start; i<=end; i++)
              {
                if(i==me || (barLock && locks[i]))
                {
                  color[i]=progress;
toHexWindow(hexWindow[i], color[i]);
                  sb[i].setBackgroundColor(0xFF<<24|(progress<<(24-i*8)));
                  sb[i].setProgress(progress);
                }
              }
            }
          }
          public void onStartTrackingTouch(SeekBar seekBar)
          {
            doProgressChanged(seekBar, seekBar.getProgress(), true);
          }
          public void  onStopTrackingTouch(SeekBar seekBar)
          {
            doProgressChanged(seekBar, seekBar.getProgress(), true);
          }
        }
      );
      lk[i]=new CheckBox(context);
      lk[i].setLayoutParams(new LinearLayout.LayoutParams(WC, WC, 0));
      lk[i].setOnCheckedChangeListener(this);
      lk[i].setTag(i);
      lh.addView(tv);
      lh.addView(sb[i]);
      lh.addView(lk[i]);
      lv.addView(lh, FP, WC);
    }
{//Evaluating hex windows.
  LinearLayout    lh=new LinearLayout(context);
  lh.setGravity(Gravity.CENTER);
  for(int i=0; i<4; i++)
  {
    hexWindow[i]=new TextView(context);
    toHexWindow(hexWindow[i], color[i]);
    lh.addView(hexWindow[i]);
  }
  lv.addView(lh);
}//Evaluating hex windows.
    ScrollView    sv=new ScrollView(context);
                  sv.addView(lv);
    builder.setView(sv);
    DialogInterface.OnClickListener ocl=new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface dialog, int which)
      {
        buttonHit(which, (color[0]<<24)|(color[1]<<16)|(color[2]<<8)|color[3]);
      }
    };
    builder.setTitle(Title);
    builder.setPositiveButton(android.R.string.yes,    ocl);
    builder.setNegativeButton(android.R.string.cancel, ocl);
    alert=builder.show();
    started=true;
  }
  //////////////////////////////////////////////////////////////////////
  public void toHexWindow(TextView tv, int k)
  {
    String  HEX="0123456789ABCDEF";
    String  s="";
    int   n=8;
    k&=(1L<<8)-1L;
    for(n-=4; n>=0; n-=4) s+=HEX.charAt((k>>n)&0xF);
    tv.setText(s);
  }
  ////////////////////////////////////////////////////////////
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
  {
    int  view=(Integer)buttonView.getTag();
    locks[view]=isChecked;
    barLock=false;
    for(int i=0; i<locks.length; i++)  if(locks[i])  barLock=true;
  }
  ////////////////////////////////////////////////////////////
  private void buttonHit(int hit, int color)
  {
    switch(hit)
    {
      case AlertDialog.BUTTON_NEGATIVE:  //  CANCEL
        return;
      case AlertDialog.BUTTON_POSITIVE:  //  OK == set
        imgtext=value[0].getText().toString();
        result[1]=imgtext;
        imgview.setTag(color);
        if(!imgtext.equals(""))
        {
          imgview.setImageBitmap(
            TextIcon.getTextIcon(
              imgtext
            , color
            , 96
            , 96
            )
          );
        }
        return;
    }
  }
  ////////////////////////////////////////////////////////////
}
