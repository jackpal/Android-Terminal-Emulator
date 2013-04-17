
package jackpal.androidterm.emulatorview;



import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;

public class SymTable extends Dialog
  implements AdapterView.OnItemClickListener, View.OnClickListener
{
    public static final String LOG_TAG = "SymTable";
    private Button mCancelButton;
    private LayoutInflater mInflater;
    private static String[] mNames;

	private static String[] mSequences;

    public static String[] NAMES={" { ", " } "," [ ",
	" ] "," < "," > "," | "," ~ "," ` "," \\ "," ^ "," _ "};

	public static String[] SEQUENCES={"{", "}","[",
		"]","<",">","|","~","`","\\","^","_"};

    
    public SymTable(Context context,String[] names,String[] sequences)
    {
        super(context, 0);
        mNames=names;
        mSequences=sequences;
        mInflater = LayoutInflater.from(context);
    }

    public SymTable(Context context){
        this(context,NAMES,SEQUENCES);
    }

    public void onClick(View view)
    {
        if(view.getId()==R.id.cancel)
            dismiss();
    }

    protected void onCreate(Bundle saveBundle)
    {
        super.onCreate(saveBundle);
        setContentView(R.layout.sym_table);
        GridView localGridView = (GridView)findViewById(R.id.sym_table);
        localGridView.setAdapter(new OptionsAdapter(getContext()));
        localGridView.setOnItemClickListener(this);
        mCancelButton = ((Button)findViewById(R.id.cancel));
        mCancelButton.setOnClickListener(this);
    }

    public void onItemClick(AdapterView adapter, View view, int id, long paramLong)
    {
    }

    private class OptionsAdapter extends BaseAdapter
    {
        public OptionsAdapter(Context context)
        {
        }

        public final int getCount()
        {
            return mNames.length;
        }

        public final String getItem(int index)
        {
            return String.valueOf(mNames[index]);
        }

        public final long getItemId(int index)
        {
            return index;
        }

        public View getView(int index, View view, ViewGroup viewGroup)
        {
            View localView = SymTable.this.mInflater.inflate(R.layout.sym_table_button, null);
            Button btn = (Button)localView;

            btn.setText(mNames[index]);

            btn.setTag(mSequences[index]);
            btn.setOnClickListener(SymTable.this);
            return btn;
        }
    }
}
