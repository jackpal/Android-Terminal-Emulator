//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts;

import android.app.       AlertDialog;
import android.content.   Context;
import android.content.   DialogInterface;
import android.text.      InputType;
import android.widget.    EditText;
import android.widget.    LinearLayout;
import android.widget.    TextView;

//////////////////////////////////////////////////////////////////////
public class GetInput
{
  public GetInput(){}
  static public void get(
    final Context context
    // operation=0
  , final String  title
  , final String  strings[]
  , final String  sButton1
  , final String  sButton2
  , final String  sButton3
  , final GetInputCallback inputCallback
  )
  {
    get(context, 0, title, strings, sButton1, sButton2, sButton3, inputCallback);
  }
  ////////////////////////////////////////////////////////////
  static public void get(
    final Context context
  , final int     operation
  , final String  title
  , final String  strings[]
  , final String  sButton1
  , final String  sButton2
  , final String  sButton3
  , final GetInputCallback inputCallback
  )
  {
    final AlertDialog.Builder  alert=new AlertDialog.Builder(context);
    LinearLayout        ll=new LinearLayout(context);
                        ll.setOrientation(LinearLayout.VERTICAL);
    int size=strings==null?0:strings.length/2;
    final EditText edits[]=new EditText[size];
    if(strings!=null)
    {
      for(int i=0, e=0, n=strings.length; i<n; )
      {
        if(strings[i]!=null)
        {
          final TextView  view=new TextView(context);
          view.setText("\n"+strings[i]+"\n");
          ll.addView(view,  LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        i++;
        if(i<n && strings[i]!=null)
        {
          final EditText  input=new EditText(context);
          input.setText(strings[i]);
          input.setSelectAllOnFocus(true);
          input.setInputType(InputType.TYPE_CLASS_TEXT);
          ll.addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
          edits[e++]=input;
        }
        i++;
      }
      alert.setView(ll);
    }
    if(title   !=null)  alert.setTitle(title);
    if(sButton1!=null)  alert.setPositiveButton(sButton1, new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int which){inputCallback.getInputCallback(operation, which, edits);}});
    if(sButton2!=null)  alert.setNegativeButton(sButton2, new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int which){inputCallback.getInputCallback(operation, which, edits);}});
    if(sButton3!=null)  alert.setNeutralButton( sButton3, new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int which){inputCallback.getInputCallback(operation, which, edits);}});
    alert.show();
  }
  ////////////////////////////////////////////////////////////
  public interface GetInputCallback
  {
    public void getInputCallback(int operation, int which, EditText edits[]);
  }
  ////////////////////////////////////////////////////////////
}
//////////////////////////////////////////////////////////////////////
