package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Accessible compose control for the compact board toolbar. */
public final class ComposeButtonView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ComposeButtonView(Context context){super(context);setClickable(true);setFocusable(true);setContentDescription("New thread");}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f;c.save();c.translate(x,y);c.scale(d,d);paint.setColor(AppColors.color(0xff9cbce3));paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);Path frame=new Path();frame.moveTo(1,-9);frame.lineTo(-8,-9);frame.quadTo(-10,-9,-10,-7);frame.lineTo(-10,8);frame.quadTo(-10,10,-8,10);frame.lineTo(7,10);frame.quadTo(9,10,9,8);frame.lineTo(9,1);c.drawPath(frame,paint);Path pen=new Path();pen.moveTo(-3,4);pen.lineTo(-2,-1);pen.lineTo(8,-11);pen.lineTo(12,-7);pen.lineTo(2,3);pen.close();c.drawPath(pen,paint);c.drawLine(6,-9,10,-5,paint);c.restore();}
}
