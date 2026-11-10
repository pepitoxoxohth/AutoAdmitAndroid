package com.xoclassic.app;

import android.view.*;
import java.util.function.Consumer;

/** Horizontal gestures consume the tap; vertical gestures remain ordinary scrolling. */
final class SwipeReadTouchListener implements View.OnTouchListener {
    private final Runnable dismiss;
    private final Consumer<Boolean> active;
    private float x,y,dx;
    private boolean horizontal,vertical;
    SwipeReadTouchListener(Runnable dismiss,Consumer<Boolean> active){this.dismiss=dismiss;this.active=active;}
    @Override public boolean onTouch(View view,MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){x=event.getRawX();y=event.getRawY();dx=0;horizontal=vertical=false;view.getParent().requestDisallowInterceptTouchEvent(true);active.accept(true);return false;}
        if(event.getPointerCount()>1){reset(view);vertical=true;return true;}
        if(action==MotionEvent.ACTION_MOVE){
            dx=event.getRawX()-x;float dy=event.getRawY()-y;
            int slop=ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            if(!horizontal&&!vertical){if(Math.abs(dy)>slop&&Math.abs(dy)>=Math.abs(dx)){vertical=true;view.cancelLongPress();view.getParent().requestDisallowInterceptTouchEvent(false);active.accept(false);}else if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.5f){horizontal=true;view.cancelLongPress();active.accept(true);view.getParent().requestDisallowInterceptTouchEvent(true);}}
            if(horizontal){view.setTranslationX(Math.min(0,dx));return true;}
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(!horizontal){view.getParent().requestDisallowInterceptTouchEvent(false);active.accept(false);if(vertical){view.setPressed(false);return true;}return false;}
            boolean handled=true;
            // Raw coordinates avoid feedback from the card's own translation.
            float distance=-view.getTranslationX();
            boolean commit=horizontal&&action==MotionEvent.ACTION_UP&&distance>=96*view.getResources().getDisplayMetrics().density;
            reset(view);if(commit)dismiss.run();return handled;
        }
        return horizontal;
    }
    private void reset(View view){horizontal=false;view.setTranslationX(0);view.setPressed(false);if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(false);active.accept(false);}
}
