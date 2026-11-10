package com.xoclassic.app;

import android.view.*;
import android.widget.*;
import java.util.*;

/** Build near-viewport board rows in small batches; retain their measured layout afterward. */
public final class DeferredBoardRenderer {
    private static final class Row {
        final LinearLayout holder;final Runnable bind;boolean bound;
        Row(LinearLayout holder,Runnable bind){this.holder=holder;this.bind=bind;}
    }
    private final ScrollView scroll;private final LinearLayout content;
    private final List<Row> rows=new ArrayList<>();private final Map<String,Row> byTag=new HashMap<>();
    private boolean closed,queued,adjusting;private int navigation;
    private final Runnable tick=this::update;
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)request();};
    public DeferredBoardRenderer(ScrollView scroll,LinearLayout content){this.scroll=scroll;this.content=content;scroll.addOnLayoutChangeListener(layout);}
    public void add(LinearLayout holder,int estimate,Runnable bind){holder.getLayoutParams().height=estimate;Row row=new Row(holder,bind);rows.add(row);byTag.put((String)holder.getTag(),row);}
    public void prime(int count){for(Row row:rows)if(count>0&&row.holder.getVisibility()==View.VISIBLE&&!row.bound){bind(row);count--;}}
    public void request(){if(!closed&&!queued&&!adjusting){queued=true;content.postOnAnimation(tick);}}
    public void close(){closed=true;navigation++;content.removeCallbacks(tick);scroll.removeOnLayoutChangeListener(layout);rows.clear();byTag.clear();}
    private void bind(Row row){row.holder.getLayoutParams().height=LinearLayout.LayoutParams.WRAP_CONTENT;row.bind.run();row.bound=true;row.holder.requestLayout();}
    public boolean position(String tag,int offset){
        Row row=byTag.get(tag);if(closed||row==null||row.holder.getVisibility()!=View.VISIBLE)return false;
        scroll.smoothScrollBy(0,0);scroll.smoothScrollBy(0,0);int token=++navigation;if(!row.bound)bind(row);
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(this);if(!closed&&navigation==token){scroll.scrollTo(0,Math.max(0,row.holder.getTop()+offset));request();}return true;}});content.requestLayout();return true;
    }
    private void update(){
        queued=false;if(closed||adjusting||scroll.getHeight()==0)return;
        int top=scroll.getScrollY(),height=scroll.getHeight();View anchor=null;
        for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child.getVisibility()==View.VISIBLE&&child.getBottom()>top){anchor=child;break;}}
        int offset=anchor==null?0:top-anchor.getTop(),built=0;boolean more=false;
        boolean bottom=top>0&&content.getHeight()-top-height<=2;
        for(int pass=0;pass<2;pass++)for(Row row:rows){
            if(row.bound||row.holder.getVisibility()!=View.VISIBLE)continue;
            int start=row.holder.getTop(),end=row.holder.getBottom();boolean visible=end>=top&&start<=top+height,near=end>=top-height&&start<=top+height*3;
            if(!near||visible!=(pass==0))continue;if(built<2){bind(row);built++;}else more=true;
        }
        if(built>0){
            adjusting=true;View saved=anchor;int savedOffset=offset,revision=navigation;boolean again=more;
            content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(this);adjusting=false;if(closed)return true;
                if(navigation==revision&&Math.abs(scroll.getScrollY()-top)<=2){if(bottom)scroll.scrollTo(0,Math.max(0,content.getHeight()-scroll.getHeight()));else if(saved!=null&&saved.getParent()==content)scroll.scrollTo(0,Math.max(0,saved.getTop()+savedOffset));}
                if(again)request();return true;}});
        }else if(more)request();
    }
}
