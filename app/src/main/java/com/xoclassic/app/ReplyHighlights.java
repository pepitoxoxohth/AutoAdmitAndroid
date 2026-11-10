package com.xoclassic.app;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.*;

/** UI-thread viewport exposure; no network work, and unseen/recycled posts keep their tint. */
public final class ReplyHighlights {
    private static final int BASE=0xff34383f,TINT=0xff4c3d47;
    private static final class Entry {View card;GradientDrawable surface;long last,dwell,fade;}
    private final ScrollView scroll;private final LinearLayout content;private final SharedPreferences prefs;private final String pendingKey;
    private final Map<String,Entry> pending=new HashMap<>();private final Rect visible=new Rect();
    private boolean active=true,closed,queued;
    private Runnable unreadChanged=()->{};
    private final Runnable tick=this::update;
    private final ViewTreeObserver.OnPreDrawListener layout=()->{request();return true;};
    public ReplyHighlights(ScrollView scroll,LinearLayout content,SharedPreferences prefs,String key,ForumParser.ThreadPage page){
        this.scroll=scroll;this.content=content;this.prefs=prefs;pendingKey=key+":pending";
        Set<String> ids=new HashSet<>(),old=new HashSet<>(prefs.getStringSet(key+":observed",Collections.emptySet())),unread=new HashSet<>(prefs.getStringSet(pendingKey,Collections.emptySet()));
        for(ForumParser.Post post:page.posts)ids.add(post.id);
        unread.retainAll(ids);
        if(prefs.contains(key+":observed"))for(int n=1;n<page.posts.size();n++){ForumParser.Post post=page.posts.get(n);if(!post.editable&&!old.contains(post.id))unread.add(post.id);}
        for(String id:unread)pending.put(id,new Entry());
        prefs.edit().putStringSet(key+":observed",ids).putStringSet(pendingKey,new HashSet<>(unread)).apply();
        content.getViewTreeObserver().addOnPreDrawListener(layout);
    }
    public void bind(String id,View card,GradientDrawable surface){Entry entry=pending.get(id);if(entry==null)return;entry.card=card;entry.surface=surface;entry.last=0;surface.setColor(color(entry.fade/1500f));request();}
    public Set<String> unreadIds(){return new HashSet<>(pending.keySet());}
    public int unreadCount(){return pending.size();}
    public void setUnreadChanged(Runnable listener){unreadChanged=listener;}
    public void setActive(boolean value){active=value;for(Entry entry:pending.values()){entry.last=0;if(entry.fade==0)entry.dwell=0;}if(value)request();else{content.removeCallbacks(tick);queued=false;}}
    public void request(){if(!closed&&active&&!queued&&!pending.isEmpty()){queued=true;content.postOnAnimation(tick);}}
    private boolean exposed(Entry entry){
        View card=entry.card;if(card==null||!card.isShown()||card.getHeight()==0||!card.hasWindowFocus()||card.getParent()!=content||card instanceof ViewGroup&&((ViewGroup)card).getChildCount()==0)return false;
        return card.getLocalVisibleRect(visible)&&visible.height()>=Math.min(card.getHeight(),scroll.getHeight())*.5f&&visible.width()>=card.getWidth()*.5f;
    }
    public void retheme(){for(Entry e:pending.values())if(e.surface!=null)e.surface.setColor(color(e.fade/1500f));}
    private static int color(float progress){float f=Math.max(0,Math.min(1,progress));int a=AppColors.color(TINT),b=AppColors.color(0xff000001);return android.graphics.Color.rgb(Math.round(android.graphics.Color.red(a)*(1-f)+android.graphics.Color.red(b)*f),Math.round(android.graphics.Color.green(a)*(1-f)+android.graphics.Color.green(b)*f),Math.round(android.graphics.Color.blue(a)*(1-f)+android.graphics.Color.blue(b)*f));}    private void update(){
        queued=false;if(closed||!active)return;long now=SystemClock.uptimeMillis();boolean again=false,changed=false;
        Iterator<Map.Entry<String,Entry>> entries=pending.entrySet().iterator();
        while(entries.hasNext()){
            Entry entry=entries.next().getValue();if(!exposed(entry)){entry.last=0;if(entry.fade==0)entry.dwell=0;continue;}
            again=true;long elapsed=entry.last==0?0:Math.min(100,Math.max(0,now-entry.last));entry.last=now;
            if(entry.dwell<500){entry.dwell+=elapsed;continue;}
            entry.fade=ValueAnimator.areAnimatorsEnabled()?Math.min(1500,entry.fade+elapsed):1500;
            if(entry.surface!=null)entry.surface.setColor(color(entry.fade/1500f));
            if(entry.fade>=1500){entries.remove();changed=true;}
        }
        if(changed){prefs.edit().putStringSet(pendingKey,new HashSet<>(pending.keySet())).apply();unreadChanged.run();}
        if(again)request();
    }
    public void close(){if(closed)return;closed=true;content.removeCallbacks(tick);if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(layout);pending.clear();}
}
