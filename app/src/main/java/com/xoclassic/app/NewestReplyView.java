package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class NewestReplyView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public NewestReplyView(Context context){super(context);setClickable(true);setFocusable(true);setTag("jump-newest");setContentDescription("Jump to newest reply");setTooltipText("Newest reply");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/52f;canvas.save();canvas.translate((getWidth()-52*scale)/2,(getHeight()-52*scale)/2);canvas.scale(scale,scale);
        paint.setStyle(Paint.Style.FILL);paint.setColor(AppColors.color(0xff34383f));canvas.drawCircle(26,26,25,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(Color.WHITE);canvas.drawCircle(26,26,25,paint);
        paint.setColor(AppColors.color(0xff9cbce3));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawLine(26,13,26,32,paint);Path arrow=new Path();arrow.moveTo(19,25);arrow.lineTo(26,32);arrow.lineTo(33,25);canvas.drawPath(arrow,paint);canvas.drawLine(17,39,35,39,paint);canvas.restore();
    }
}
