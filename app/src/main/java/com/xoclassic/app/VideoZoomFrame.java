package com.xoclassic.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Zoom the video surface while keeping playback controls outside its transform. */
public final class VideoZoomFrame extends FrameLayout {
    private float scale=1,panX,panY,downX,downY,lastX,lastY;
    private final int slop;
    public VideoZoomFrame(Context context){super(context);slop=ViewConfiguration.get(context).getScaledTouchSlop();}
    public void zoomBy(float factor){scale=Math.max(.5f,Math.min(4f,scale*factor));apply();}
    public void fit(){scale=1;panX=panY=0;apply();}
    private void apply(){
        if(getChildCount()==0)return;View media=getChildAt(0);
        float maxX=Math.max(0,(media.getWidth()*scale-getWidth())/2),maxY=Math.max(0,(media.getHeight()*scale-getHeight())/2);
        panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));
        media.setScaleX(scale);media.setScaleY(scale);media.setTranslationX(panX);media.setTranslationY(panY);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);apply();}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=lastX=event.getX();downY=lastY=event.getY();}
        return scale>1&&event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(event.getX()-downX,event.getY()-downY)>slop;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(scale<=1)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){panX+=event.getX()-lastX;panY+=event.getY()-lastY;apply();}
        lastX=event.getX();lastY=event.getY();return true;
    }
}
