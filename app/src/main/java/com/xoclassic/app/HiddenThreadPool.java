package com.xoclassic.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;
import java.util.function.LongSupplier;

/** Oldest-to-newest local hide order, independent of author blocks and read history. */
public final class HiddenThreadPool {
    public static final int LIMIT=30;
    public static final long MAX_AGE=7L*24*60*60*1000;
    private final SharedPreferences preferences;
    private final LongSupplier clock;
    public HiddenThreadPool(SharedPreferences preferences){this(preferences,System::currentTimeMillis);}
    public HiddenThreadPool(SharedPreferences preferences,LongSupplier clock){this.preferences=preferences;this.clock=clock;}
    public static final class Change {
        public final String origin,id;
        private final String evictedId,evictedTitle;
        private final long evictedSince;
        private Change(String origin,String id,String evictedId,String evictedTitle,long evictedSince){this.origin=origin;this.id=id;this.evictedId=evictedId;this.evictedTitle=evictedTitle;this.evictedSince=evictedSince;}
    }
    private String host(String origin){return ForumParser.origin(origin).toString();}
    private String titleKey(String origin,String id){return origin+":hiddenThreadTitle:"+id;}
    private String sinceKey(String origin,String id){return origin+":hiddenThreadSince:"+id;}
    private long since(String origin,String id){try{return preferences.getLong(sinceKey(origin,id),0);}catch(ClassCastException invalid){return 0;}}
    private void save(String origin,List<String> order,String titleId,String title,Map<String,Long> times){
        Set<String> keep=new LinkedHashSet<>(order),before=preferences.getStringSet(origin+":hiddenThreadIds",Collections.emptySet());
        SharedPreferences.Editor edit=preferences.edit().putStringSet(origin+":hiddenThreadIds",keep).putString(origin+":hiddenThreadOrder",new JSONArray(order).toString());
        for(String id:before)if(!keep.contains(id)){edit.remove(titleKey(origin,id));edit.remove(sinceKey(origin,id));}
        for(Map.Entry<String,Long> time:times.entrySet())if(keep.contains(time.getKey()))edit.putLong(sinceKey(origin,time.getKey()),time.getValue());
        if(titleId!=null)edit.putString(titleKey(origin,titleId),title==null?"Thread "+titleId:title);
        edit.apply();
    }
    private List<String> order(String origin){
        Set<String> stored=preferences.getStringSet(origin+":hiddenThreadIds",Collections.emptySet());
        long now=clock.getAsLong();Map<String,Long> times=new HashMap<>();Set<String> valid=new HashSet<>();
        for(String id:stored)if(id!=null&&id.matches("[0-9]{1,20}")){long hidden=since(origin,id);if(hidden>0&&hidden<=now&&now-hidden>=MAX_AGE)continue;valid.add(id);if(hidden<=0||hidden>now)times.put(id,now);}
        String record=preferences.getString(origin+":hiddenThreadOrder","");List<String> order=new ArrayList<>();Set<String> seen=new HashSet<>();
        try{if(record.length()<=8000){JSONArray array=new JSONArray(record);for(int i=0;i<array.length();i++){String id=array.optString(i);if(valid.contains(id)&&seen.add(id))order.add(id);}}}catch(Exception malformed){/* Recover from the existing membership set. */}
        // Previous releases stored an unordered set; IDs give migration a stable, repeatable initial order.
        List<String> missing=new ArrayList<>();for(String id:valid)if(!seen.contains(id))missing.add(id);missing.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));order.addAll(missing);
        if(order.size()>LIMIT)order=new ArrayList<>(order.subList(order.size()-LIMIT,order.size()));
        if(!times.isEmpty()||!record.equals(new JSONArray(order).toString())||!stored.equals(new HashSet<>(order)))save(origin,order,null,null,times);
        return order;
    }
    public Set<String> ids(String origin){return new LinkedHashSet<>(order(host(origin)));}
    public Change hide(String origin,String id,String title){
        String site=host(origin);if(id==null||!id.matches("[0-9]{1,20}"))throw new IllegalArgumentException("Invalid thread.");
        List<String> order=order(site);order.remove(id);order.add(id);String evicted="",oldTitle="";long oldSince=0;
        if(order.size()>LIMIT){evicted=order.remove(0);oldTitle=preferences.getString(titleKey(site,evicted),"Thread "+evicted);oldSince=since(site,evicted);}
        save(site,order,id,title,Collections.singletonMap(id,clock.getAsLong()));return new Change(site,id,evicted,oldTitle,oldSince);
    }
    public void remove(String origin,String id){String site=host(origin);List<String> order=order(site);order.remove(id);save(site,order,null,null,Collections.emptyMap());}
    public void undo(Change change){
        String site=host(change.origin);List<String> order=order(site);if(!order.remove(change.id))return;
        String restore=null;if(!change.evictedId.isEmpty()&&!order.contains(change.evictedId)&&order.size()<LIMIT&&clock.getAsLong()-change.evictedSince<MAX_AGE){order.add(0,change.evictedId);restore=change.evictedId;}
        save(site,order,restore,change.evictedTitle,restore==null?Collections.emptyMap():Collections.singletonMap(restore,change.evictedSince));
    }
}
