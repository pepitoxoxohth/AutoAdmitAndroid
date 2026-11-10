package com.xoclassic.app;

import java.util.*;
import java.util.function.LongSupplier;

/** Bounded, process-only public media bytes; never thread, article or embed HTML. */
public final class MediaByteCache {
    private static final class Entry {
        final PreviewFetch.Result result;final long expires;
        Entry(PreviewFetch.Result result,long expires){this.result=result;this.expires=expires;}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private final LongSupplier clock;private final int budget,maximum;private int bytes;
    public MediaByteCache(){this(()->System.nanoTime()/1000000,16*1024*1024,4*1024*1024);}
    public MediaByteCache(LongSupplier clock,int budget,int maximum){this.clock=clock;this.budget=budget;this.maximum=maximum;}
    private PreviewFetch.Result copy(PreviewFetch.Result source){PreviewFetch.Result r=new PreviewFetch.Result();r.url=source.url;r.type=source.type;r.bytes=source.bytes.clone();return r;}
    private void prune(){long now=clock.getAsLong();Iterator<Entry> it=entries.values().iterator();while(it.hasNext()){Entry e=it.next();if(e.expires<=now){bytes-=e.result.bytes.length;it.remove();}}}
    public synchronized PreviewFetch.Result get(String url,int limit){prune();Entry e=entries.get(url);return e==null||e.result.bytes.length>limit?null:copy(e.result);}
    public synchronized void put(String url,PreviewFetch.Result result,long ttl){
        prune();Entry old=entries.remove(url);if(old!=null)bytes-=old.result.bytes.length;
        String type=result.type==null?"":result.type.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        if(!(type.startsWith("image/")||type.startsWith("video/")||type.startsWith("audio/")||type.equals("application/pdf")))return;
        if(ttl<=0||result.bytes.length==0||result.bytes.length>maximum||result.bytes.length>budget)return;
        entries.put(url,new Entry(copy(result),clock.getAsLong()+Math.min(ttl,300000)));bytes+=result.bytes.length;
        Iterator<Entry> it=entries.values().iterator();while(bytes>budget||entries.size()>64){Entry drop=it.next();bytes-=drop.result.bytes.length;it.remove();}
    }
    public synchronized void invalidate(String url){Iterator<Map.Entry<String,Entry>> it=entries.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Entry> e=it.next();if(e.getKey().equals(url)||e.getValue().result.url.equals(url)){bytes-=e.getValue().result.bytes.length;it.remove();}}}
    public synchronized void clear(){entries.clear();bytes=0;}
}
