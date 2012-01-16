/*
 * Copyright (C) 2011 Steven Luo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewFlipper;

import jackpal.androidterm.model.UpdateCallback;

public class TermViewFlipper extends ViewFlipper implements Iterable<View> {
    private Context context;
    private Toast mToast;
    private LinkedList<UpdateCallback> callbacks;

    class ViewFlipperIterator implements Iterator<View> {
        int pos = 0;

        public boolean hasNext() {
            return (pos < getChildCount());
        }

        public View next() {
            return getChildAt(pos++);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public TermViewFlipper(Context context) {
        super(context);
        this.context = context;
        callbacks = new LinkedList<UpdateCallback>();
    }

    public TermViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        callbacks = new LinkedList<UpdateCallback>();
    }

    public Iterator<View> iterator() {
        return new ViewFlipperIterator();
    }

    public void addCallback(UpdateCallback callback) {
        callbacks.add(callback);
    }

    public void removeCallback(UpdateCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyChange() {
        for (UpdateCallback callback : callbacks) {
            callback.onUpdate();
        }
    }

    public void pauseCurrentView() {
        EmulatorView view = (EmulatorView) getCurrentView();
        if (view == null) {
            return;
        }
        view.onPause();
    }

    public void resumeCurrentView() {
        EmulatorView view = (EmulatorView) getCurrentView();
        if (view == null) {
            return;
        }
        view.onResume();
    }

    private void showTitle() {
        if (getChildCount() == 0) {
            return;
        }
        String title = context.getString(R.string.window_title, getDisplayedChild()+1);
        if (mToast == null) {
            mToast = Toast.makeText(context, title, Toast.LENGTH_SHORT);
            mToast.setGravity(Gravity.CENTER, 0, 0);
        } else {
            mToast.setText(title);
        }
        mToast.show();
    }

    @Override
    public void showPrevious() {
        pauseCurrentView();
        super.showPrevious();
        showTitle();
        resumeCurrentView();
        notifyChange();
    }

    @Override
    public void showNext() {
        pauseCurrentView();
        super.showNext();
        showTitle();
        resumeCurrentView();
        notifyChange();
    }

    @Override
    public void setDisplayedChild(int position) {
        pauseCurrentView();
        super.setDisplayedChild(position);
        showTitle();
        resumeCurrentView();
        notifyChange();
    }
}
