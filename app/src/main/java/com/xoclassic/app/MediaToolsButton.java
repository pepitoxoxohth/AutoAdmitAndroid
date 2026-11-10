package com.xoclassic.app;

import android.content.Context;
import android.graphics.*;
import android.widget.Button;

/** Drawn overlay: media status never changes the menu button's layout or touch target. */
public final class MediaToolsButton extends Button {
    private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean media;
    public MediaToolsButton(Context context){super(context);float d=getResources().getDisplayMetrics().density;dot.setShader(new RadialGradient(0,0,4.5f*d,new int[]{0xfff26770,0xffef6069,0x99ef6069,0x26ef6069,0x00ef6069},new float[]{0,.25f,.45f,.66f,1},Shader.TileMode.CLAMP));}
    public void setMediaDetected(boolean detected){media=detected;setContentDescription("Thread tools: find in thread and thread media"+(detected?". Media available":""));invalidate();}
    public boolean isMediaDetected(){return media;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!media)return;float d=getResources().getDisplayMetrics().density;canvas.save();canvas.translate(getWidth()-12.5f*d,9.5f*d);canvas.drawCircle(0,0,4.5f*d,dot);canvas.restore();}
}