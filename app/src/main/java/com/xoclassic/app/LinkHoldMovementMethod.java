package com.xoclassic.app;

import android.os.*;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.*;
import android.widget.TextView;
import java.util.function.Consumer;

/** Link holds have their own menu; ordinary taps and non-link text selection stay native. */
public final class LinkHoldMovementMethod extends LinkMovementMethod {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Consumer<String> held;
    private Runnable pending;
    private boolean consumed;
    private float downX,downY;
    public LinkHoldMovementMethod(Consumer<String> held){this.held=held;}
    private void cancel(){if(pending!=null)handler.removeCallbacks(pending);pending=null;}
    private URLSpan hit(TextView view,Spannable text,MotionEvent e){
        Layout layout=view.getLayout();if(layout==null)return null;float x=e.getX()-view.getTotalPaddingLeft()+view.getScrollX(),y=e.getY()-view.getTotalPaddingTop()+view.getScrollY();
        if(y<0||y>=layout.getHeight())return null;int line=layout.getLineForVertical((int)y);if(x<layout.getLineLeft(line)||x>layout.getLineRight(line))return null;
        int offset=layout.getOffsetForHorizontal(line,x);URLSpan[] links=text.getSpans(offset,offset,URLSpan.class);return links.length==0?null:links[0];
    }
    @Override public boolean onTouchEvent(TextView view,Spannable text,MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){cancel();consumed=false;downX=event.getX();downY=event.getY();URLSpan span=hit(view,text,event);if(span!=null){view.cancelLongPress();pending=()->{pending=null;if(!view.isAttachedToWindow())return;consumed=true;view.cancelLongPress();Selection.removeSelection(text);held.accept(span.getURL());};handler.postDelayed(pending,ViewConfiguration.getLongPressTimeout());}}
        if(action==MotionEvent.ACTION_MOVE&&(Math.abs(event.getX()-downX)>ViewConfiguration.get(view.getContext()).getScaledTouchSlop()||Math.abs(event.getY()-downY)>ViewConfiguration.get(view.getContext()).getScaledTouchSlop()))cancel();
        if(event.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL)cancel();
        if(action==MotionEvent.ACTION_UP){cancel();if(consumed){consumed=false;Selection.removeSelection(text);return true;}}
        if(consumed)return true;
        return super.onTouchEvent(view,text,event);
    }
}
