package com.xoclassic.app;

import android.view.*;
import android.widget.*;
import java.util.*;

/** UI-thread-only viewport rendering. Empty holders retain their last measured height. */
public final class LazyThreadRenderer {
    private static final class Row {
        final LinearLayout holder;final Runnable bind;boolean bound;
        Row(LinearLayout holder,Runnable bind){this.holder=holder;this.bind=bind;}
    }
    private final ScrollView scroll;private final LinearLayout content;
    private final List<Row> rows=new ArrayList<>();private final Map<String,Row> byId=new HashMap<>();
    private boolean closed,queued,adjusting;
    private int navigation;private Row pinned;
    private final Runnable tick=()->update();
    public LazyThreadRenderer(ScrollView scroll,LinearLayout content){this.scroll=scroll;this.content=content;}
    public void add(String id,LinearLayout holder,int estimate,Runnable bind){holder.setMinimumHeight(0);holder.getLayoutParams().height=estimate;Row row=new Row(holder,bind);rows.add(row);byId.put(id,row);}
    public void request(){if(!closed&&!queued&&!adjusting){queued=true;content.postOnAnimation(tick);}}
    public void close(){closed=true;content.removeCallbacks(tick);rows.clear();byId.clear();}
    public void realize(String id){Row row=byId.get(id);if(row!=null&&!row.bound){bind(row);}}
    // A second zero-distance call cancels any existing ScrollView animation within its scroll gap.
    private void stopAnimation(){scroll.smoothScrollBy(0,0);scroll.smoothScrollBy(0,0);}
    public boolean position(String id,int offset){
        Row row=byId.get(id);if(row==null||closed)return false;stopAnimation();int token=++navigation;pinned=row;if(!row.bound)bind(row);
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(this);if(!closed&&navigation==token){scroll.scrollTo(0,Math.max(0,row.holder.getTop()+offset));pinned=null;content.postOnAnimation(()->request());}return true;}});
        content.requestLayout();content.invalidate();return true;
    }
    public void bottom(){
        if(closed||rows.isEmpty())return;stopAnimation();int token=++navigation;Row last=rows.get(rows.size()-1);pinned=last;if(!last.bound)bind(last);
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(this);if(!closed&&navigation==token){scroll.scrollTo(0,Math.max(0,content.getHeight()-scroll.getHeight()));pinned=null;content.postOnAnimation(()->request());}return true;}});
        content.requestLayout();content.invalidate();
    }
    private void bind(Row row){row.holder.getLayoutParams().height=LinearLayout.LayoutParams.WRAP_CONTENT;row.bind.run();row.bound=true;row.holder.requestLayout();}
    private void update(){
        queued=false;if(closed||adjusting||scroll.getHeight()==0)return;
        int top=scroll.getScrollY(),height=scroll.getHeight(),nearTop=top-height,nearBottom=top+height*3;
        View anchor=null;for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child.getBottom()>top){anchor=child;break;}}
        int offset=anchor==null?0:top-anchor.getTop();boolean bottom=top>0&&content.getHeight()-top-scroll.getHeight()<=2;
        int built=0;boolean changed=false,more=false;
        // Visible posts get priority over the buffer in either direction.
        for(int pass=0;pass<2;pass++)for(Row row:rows){int start=row.holder.getTop(),end=row.holder.getBottom();boolean visible=end>=top&&start<=top+height,near=end>=nearTop&&start<=nearBottom;if(row.bound||!near||visible!=(pass==0))continue;if(built<2){bind(row);built++;changed=true;}else more=true;}
        for(Row row:rows)if(row.bound&&row!=pinned&&!row.holder.hasFocus()&&(row.holder.getBottom()<top-height*3||row.holder.getTop()>top+height*5)){
            int measured=row.holder.getHeight();if(measured<=0)continue;row.holder.removeAllViews();row.holder.setPadding(0,0,0,0);row.holder.getLayoutParams().height=measured;row.holder.requestLayout();row.bound=false;
        }
        if(changed){
            adjusting=true;View saved=anchor;int savedTop=top,savedOffset=offset,revision=navigation;boolean again=more;
            content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(this);adjusting=false;if(closed)return true;
                // Do not fight a gesture or an explicit jump that happened while layout was pending.
                if(navigation==revision&&Math.abs(scroll.getScrollY()-savedTop)<=2){if(bottom)scroll.scrollTo(0,Math.max(0,content.getHeight()-scroll.getHeight()));else if(saved!=null&&saved.getParent()==content)scroll.scrollTo(0,Math.max(0,saved.getTop()+savedOffset));}
                if(again)request();return true;}});
        }else if(more)request();
    }
}
