package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Photo-frame glyph; its count stays inside, with a full 44dp touch target. */
public final class MediaCountView extends View {
    private final Paint paint=new Paint(3);private int count=-1;
    public MediaCountView(Context context){super(context);setFocusable(true);setCount(-1);}
    public void setCount(int value){count=value;setEnabled(value>0);setAlpha(value==0?.4f:1f);setContentDescription(value<0?"Finding thread media":value+" media links. Open media sweep.");invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;canvas.save();canvas.translate(getWidth()/2f-22*d,getHeight()/2f-22*d);canvas.scale(d,d);
        paint.setColor(AppColors.color(0xff9cbce3));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);canvas.drawRoundRect(7,10,37,35,3,3,paint);
        Path mountain=new Path();mountain.moveTo(8,31);mountain.lineTo(15,23);mountain.lineTo(21,29);mountain.lineTo(25,25);mountain.lineTo(35,34);canvas.drawPath(mountain,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(14,17,2,paint);paint.setColor(AppColors.color(0xff292b30));canvas.drawRect(19,12,35,24,paint);
        paint.setColor(AppColors.color(0xff9cbce3));paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(count>99?10:12);
        canvas.drawText(count<0?"…":count>99?"99+":String.valueOf(count),27,22,paint);canvas.restore();
    }
}
