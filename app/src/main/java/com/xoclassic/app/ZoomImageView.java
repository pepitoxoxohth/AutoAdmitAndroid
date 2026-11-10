package com.xoclassic.app;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.ImageView;
import java.util.function.IntConsumer;

/** Fit, pinch, double-tap and pan for the modal's current image. */
public final class ZoomImageView extends ImageView {
    private final Matrix transform=new Matrix();
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private float fit=1,current=1;
    private IntConsumer swipe;
    private boolean multiTouch;
    public void setOnSwipe(IntConsumer listener){swipe=listener;}
    public ZoomImageView(Context context){
        super(context);setScaleType(ScaleType.MATRIX);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){zoom(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());return true;}});
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){if(current>fit*1.1f)fit();else zoom(2.5f,e.getX(),e.getY());return true;}@Override public boolean onFling(MotionEvent a,MotionEvent b,float vx,float vy){if(a!=null&&b!=null&&swipe!=null&&!multiTouch&&!scale.isInProgress()&&current<=fit*1.05f&&Math.abs(b.getX()-a.getX())>80*getResources().getDisplayMetrics().density&&Math.abs(vx)>Math.abs(vy)*1.5f){swipe.accept(b.getX()<a.getX()?1:-1);return true;}return false;}@Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(!scale.isInProgress()){transform.postTranslate(-dx,-dy);constrain();}return true;}});
    }
    @Override public void setImageDrawable(Drawable drawable){super.setImageDrawable(drawable);if(transform!=null)post(this::fit);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
    public void fit(){Drawable d=getDrawable();if(d==null||d.getIntrinsicWidth()<=0||getWidth()==0||getHeight()==0)return;fit=Math.min((float)getWidth()/d.getIntrinsicWidth(),(float)getHeight()/d.getIntrinsicHeight());current=fit;transform.reset();transform.postScale(fit,fit);transform.postTranslate((getWidth()-d.getIntrinsicWidth()*fit)/2,(getHeight()-d.getIntrinsicHeight()*fit)/2);setImageMatrix(transform);}
    public void zoomBy(float factor){zoom(factor,getWidth()/2f,getHeight()/2f);}
    private void zoom(float factor,float x,float y){if(getDrawable()==null)return;float next=Math.max(fit,Math.min(fit*8,current*factor));transform.postScale(next/current,next/current,x,y);current=next;constrain();}
    private void constrain(){Drawable d=getDrawable();if(d==null)return;float[] values=new float[9];transform.getValues(values);float width=d.getIntrinsicWidth()*current,height=d.getIntrinsicHeight()*current,x=values[Matrix.MTRANS_X],y=values[Matrix.MTRANS_Y];float tx=width<=getWidth()?(getWidth()-width)/2:Math.max(getWidth()-width,Math.min(0,x));float ty=height<=getHeight()?(getHeight()-height)/2:Math.max(getHeight()-height,Math.min(0,y));transform.postTranslate(tx-x,ty-y);setImageMatrix(transform);}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)multiTouch=false;if(event.getPointerCount()>1)multiTouch=true;scale.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
}
