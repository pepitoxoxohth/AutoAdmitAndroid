package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Thread-local thumb target; its count is painted inside the reply bubble. */
public final class UnreadJumpView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int count;
    public UnreadJumpView(Context context){super(context);setClickable(true);setFocusable(true);setTag("jump-unread");}
    public void setCount(int value){count=Math.max(0,value);setVisibility(count==0?GONE:VISIBLE);setContentDescription("Jump to closest unread reply. "+count+" unread replies remaining");invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float unit=Math.min(getWidth(),getHeight())/52f;
        canvas.save();canvas.translate((getWidth()-52*unit)/2,(getHeight()-52*unit)/2);canvas.scale(unit,unit);
        paint.setColor(AppColors.color(0xff34383f));paint.setStyle(Paint.Style.FILL);canvas.drawCircle(26,26,25,paint);
        paint.setColor(AppColors.color(0xff9cbce3));paint.setStrokeWidth(1.8f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
        Path bubble=new Path();bubble.moveTo(13,10);bubble.lineTo(39,10);bubble.quadTo(43,10,43,15);bubble.lineTo(43,31);bubble.quadTo(43,36,38,36);bubble.lineTo(22,36);bubble.lineTo(15,42);bubble.lineTo(16,36);bubble.lineTo(13,36);bubble.quadTo(9,36,9,31);bubble.lineTo(9,15);bubble.quadTo(9,10,13,10);canvas.drawPath(bubble,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);String label=count>999?"999+":String.valueOf(count);paint.setTextSize(label.length()>3?12:17);Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(label,26,23-(metrics.ascent+metrics.descent)/2,paint);canvas.restore();
    }
}
