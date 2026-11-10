package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Compact tab-count control; the number stays inside the tab outline. */
public final class TabCountView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int count;
    public TabCountView(Context context){super(context);setClickable(true);setFocusable(true);}
    public void setCount(int value){count=value;setContentDescription("Open thread tabs, "+count+" open");invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(AppColors.color(0xff9cbce3));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*d);canvas.drawRoundRect(cx-8*d,cy-13*d,cx+12*d,cy+8*d,3*d,3*d,paint);paint.setColor(AppColors.color(0xff34383f));paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(cx-12*d,cy-9*d,cx+8*d,cy+12*d,3*d,3*d,paint);paint.setColor(AppColors.color(0xff9cbce3));paint.setStyle(Paint.Style.STROKE);canvas.drawRoundRect(cx-12*d,cy-9*d,cx+8*d,cy+12*d,3*d,3*d,paint);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.SANS_SERIF);paint.setTextSize(12*d);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(Integer.toString(count),cx-2*d,cy+2*d-(paint.ascent()+paint.descent())/2,paint);}
}
