package com.xoclassic.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;
import java.util.function.BooleanSupplier;

/** A downward drag must begin at the top and cross 120dp before release. */
public final class PullToRefreshScrollView extends ScrollView {
    private final float density,threshold;
    private final int slop;
    private final Paint cue=new Paint(Paint.ANTI_ALIAS_FLAG);
    private BooleanSupplier enabled;
    private Runnable refresh;
    private Runnable bottomScroll;
    private boolean eligible,pulling;
    private float downX,downY,distance;

    public PullToRefreshScrollView(Context context){
        super(context);density=getResources().getDisplayMetrics().density;threshold=120*density;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();setOverScrollMode(OVER_SCROLL_NEVER);
        cue.setTextAlign(Paint.Align.CENTER);cue.setTypeface(Typeface.SERIF);cue.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
    }
    public void setPullRefresh(BooleanSupplier enabled,Runnable refresh){this.enabled=enabled;this.refresh=refresh;}
    public void setBottomScroll(Runnable action){bottomScroll=action;}
    @Override public boolean fullScroll(int direction){if(direction==View.FOCUS_DOWN&&bottomScroll!=null){bottomScroll.run();return true;}return super.fullScroll(direction);}
    public boolean isPulling(){return pulling;}
    private boolean allowed(){return enabled!=null&&enabled.getAsBoolean();}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){reset(false);downX=event.getX();downY=event.getY();eligible=getScrollY()==0&&allowed();}
        if(event.getPointerCount()>1){eligible=false;if(pulling){reset(true);return true;}}
        if(action==MotionEvent.ACTION_MOVE&&eligible&&!pulling){
            float dy=event.getY()-downY,dx=Math.abs(event.getX()-downX);
            if(dx>slop&&dx>Math.abs(dy))eligible=false;
            if(dy>slop&&dy>dx*1.25f&&getScrollY()==0&&allowed()){
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();
                pulling=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
        }
        if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&!pulling)eligible=false;
        return pulling||super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(pulling){
            if(event.getPointerCount()>1){reset(true);return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE){distance=Math.max(0,event.getY()-downY);if(getChildCount()>0)getChildAt(0).setTranslationY(Math.min(96*density,distance*0.45f));invalidate();return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_UP){distance=Math.max(0,event.getY()-downY);boolean trigger=eligible&&distance>=threshold&&allowed();reset(true);if(trigger&&refresh!=null)post(refresh);return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){reset(true);return true;}
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        if(pulling&&distance>slop){float offset=Math.min(96*density,distance*0.45f);cue.setColor(distance>=threshold?AppColors.color(0xff9cbce3):AppColors.color(0xffa8adb5));cue.setAlpha(Math.min(255,(int)(offset/(24*density)*255)));canvas.drawText(distance>=threshold?"↻ Release to refresh":"↓ Pull to refresh",getWidth()/2f,getScrollY()+offset/2f+cue.getTextSize()/3f,cue);}
    }
    private void reset(boolean animate){eligible=false;pulling=false;distance=0;if(getChildCount()>0){View child=getChildAt(0);child.animate().cancel();if(animate)child.animate().translationY(0).setDuration(150).start();else child.setTranslationY(0);}invalidate();}
    @Override protected void onDetachedFromWindow(){reset(false);super.onDetachedFromWindow();}
}
