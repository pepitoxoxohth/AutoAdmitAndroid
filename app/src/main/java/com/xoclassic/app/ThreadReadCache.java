package com.xoclassic.app;

import java.util.*;
import java.util.function.LongSupplier;

/** Short-lived, memory-only responses shared by author lookup and board navigation. */
public final class ThreadReadCache {
    public static final class Entry {
        public final String html,url;public final int count;public final long time;
        Entry(String html,String url,int count,long time){this.html=html;this.url=url;this.count=count;this.time=time;}
        int size(){return html.length()*2;}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private final LongSupplier clock;private final long ttl;private final int budget;private int bytes;
    public ThreadReadCache(){this(()->System.nanoTime()/1000000,30000,12*1024*1024);}
    public ThreadReadCache(LongSupplier clock,long ttl,int budget){this.clock=clock;this.ttl=ttl;this.budget=budget;}
    public synchronized Entry get(String id,int count){Entry e=entries.get(id);if(e!=null&&(clock.getAsLong()-e.time>=ttl||e.count!=count)){entries.remove(id);bytes-=e.size();e=null;}return e;}
    public synchronized void put(String id,String html,String url,int count){Entry old=entries.remove(id);if(old!=null)bytes-=old.size();Entry e=new Entry(html,url,count,clock.getAsLong());if(e.size()>budget)return;entries.put(id,e);bytes+=e.size();Iterator<Entry> it=entries.values().iterator();while(bytes>budget||entries.size()>32){Entry drop=it.next();bytes-=drop.size();it.remove();}}
    public synchronized void clear(){entries.clear();bytes=0;}
    public synchronized void remove(String id){Entry e=entries.remove(id);if(e!=null)bytes-=e.size();}
}
