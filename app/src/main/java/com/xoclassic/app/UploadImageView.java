package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Standard upload tray and arrow in a full-size accessible touch target. */
public final class UploadImageView extends View {
    private final Paint paint=new Paint(3);
    public UploadImageView(Context context){super(context);setFocusable(true);setContentDescription("Upload image");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;canvas.save();canvas.translate(getWidth()/2f-22*d,getHeight()/2f-22*d);canvas.scale(d,d);
        paint.setColor(AppColors.color(0xff9cbce3));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path shape=new Path();shape.moveTo(10,27);shape.lineTo(10,33);shape.quadTo(10,35,12,35);shape.lineTo(32,35);shape.quadTo(34,35,34,33);shape.lineTo(34,27);shape.moveTo(22,28);shape.lineTo(22,10);shape.moveTo(15,17);shape.lineTo(22,10);shape.lineTo(29,17);canvas.drawPath(shape,paint);canvas.restore();
    }
}
