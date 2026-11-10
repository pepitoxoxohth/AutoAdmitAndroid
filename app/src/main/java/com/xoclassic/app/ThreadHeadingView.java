package com.xoclassic.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Fit the actual title width, keeping full wrapping rather than truncating long titles. */
public final class ThreadHeadingView extends TextView {
    private final int maximum,minimum;
    public ThreadHeadingView(Context context){
        this(context,23,18);
    }
    public ThreadHeadingView(Context context,int maximum,int minimum){
        super(context);
        this.maximum=maximum;this.minimum=minimum;
        setTag("thread-heading");
        setTypeface(Typeface.SERIF,Typeface.BOLD);
        setSingleLine(false);
        setMaxLines(Integer.MAX_VALUE);
        setHorizontallyScrolling(false);
        setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        setTextSize(maximum);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=View.MeasureSpec.getSize(widthSpec)-getCompoundPaddingLeft()-getCompoundPaddingRight();
        if(width>0&&View.MeasureSpec.getMode(widthSpec)!=View.MeasureSpec.UNSPECIFIED){
            TextPaint paint=new TextPaint(getPaint());
            int size=maximum;
            for(;size>minimum;size--){
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size,getResources().getDisplayMetrics()));
                StaticLayout layout=StaticLayout.Builder.obtain(getText(),0,getText().length(),paint,width)
                    .setBreakStrategy(getBreakStrategy()).setHyphenationFrequency(getHyphenationFrequency())
                    .setIncludePad(getIncludeFontPadding()).setMaxLines(4).build();
                if(layout.getLineCount()<=3)break;
            }
            float pixels=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size,getResources().getDisplayMetrics());
            if(Math.abs(getTextSize()-pixels)>.1f)super.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
