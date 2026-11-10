package com.xoclassic.app;

import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit portable allowlist. Never reads the credential vault or session stores. */
public final class SettingsBackup {
    public static final int MAX_BYTES=512*1024;
    private static final String[] SITES={"https://xoxohth.com","https://autoadmit.com"};
    public static final class Plan {
        final Map<String,Object> classic=new LinkedHashMap<>(),appearance=new LinkedHashMap<>();
        public int count(){return classic.size()+appearance.size();}
        public void apply(SharedPreferences prefs,SharedPreferences themes){
            Map<String,?> before=prefs.getAll(),oldThemes=themes.getAll();
            if(!write(themes,appearance)||!write(prefs,classic)){
                restore(themes,oldThemes);restore(prefs,before);
                throw new IllegalArgumentException("Could not save settings. Import was rolled back.");
            }
        }
    }
    public static String read(InputStream input)throws IOException {
        if(input==null)throw new IOException("Cannot open backup");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(bytes.size()+n>MAX_BYTES)throw new IOException("Backup exceeds 512 KB");bytes.write(buffer,0,n);}
        return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }
    public static String export(SharedPreferences prefs,SharedPreferences themes)throws JSONException {
        JSONObject main=new JSONObject(),colors=new JSONObject();main.put("replyPreviewMode",prefs.getString("replyPreviewMode","links")).put("hiddenThreadAuthors",json(prefs.getStringSet("hiddenThreadAuthors",Collections.emptySet()))).put("openThreadTabs",prefs.getString("openThreadTabs","{\"tabs\":[],\"active\":\"\"}"));colors.put("custom",themes.getString("custom","{}")).put("active",themes.getString("active","Default")).put("day",themes.getString("day","Light")).put("night",themes.getString("night","Default")).put("scheduled",themes.getBoolean("scheduled",false)).put("dayTime",themes.getInt("dayTime",420)).put("nightTime",themes.getInt("nightTime",1140));
        Map<String,?> values=prefs.getAll();
        for(String key:new String[]{"replyPreviewMode","hiddenThreadAuthors","openThreadTabs"})if(values.containsKey(key))main.put(key,json(values.get(key)));
        for(String site:SITES){for(String suffix:new String[]{"hiddenThreadIds","hiddenThreadOrder"}){String key=site+":"+suffix;if(values.containsKey(key))main.put(key,json(values.get(key)));}
            Set<String> ids=prefs.getStringSet(site+":hiddenThreadIds",Collections.emptySet());for(String id:ids)if(id.matches("[0-9]{1,20}"))for(String suffix:new String[]{"hiddenThreadTitle:","hiddenThreadSince:"}){String key=site+":"+suffix+id;if(values.containsKey(key))main.put(key,json(values.get(key)));}}
        for(String key:new String[]{"custom","active","day","night","scheduled","dayTime","nightTime"})if(themes.contains(key))colors.put(key,json(themes.getAll().get(key)));
        JSONObject backup=new JSONObject().put("format","autoadmit-settings").put("version",1).put("settings",main).put("appearance",colors);
        Plan checked=parse(backup.toString()); // Normalize structured values before anything leaves the app.
        JSONObject safe=new JSONObject(),safeColors=new JSONObject();for(String key:checked.classic.keySet())safe.put(key,json(checked.classic.get(key)));for(String key:checked.appearance.keySet())safeColors.put(key,json(checked.appearance.get(key)));
        String result=new JSONObject().put("format","autoadmit-settings").put("version",1).put("settings",safe).put("appearance",safeColors).toString(2);
        if(result.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Settings backup exceeds 512 KB");return result;
    }
    private static Object json(Object value){return value instanceof Set?new JSONArray((Set<?>)value):value;}
    private static String string(Object value,int max)throws JSONException {if(!(value instanceof String)||((String)value).length()>max)throw new JSONException("Invalid text field");return (String)value;}
    private static long number(Object value,long min,long max)throws JSONException {if(!(value instanceof Number)||value instanceof Double||value instanceof Float)throw new JSONException("Invalid number");long n=((Number)value).longValue();if(n<min||n>max)throw new JSONException("Number out of range");return n;}
    private static Set<String> strings(Object value,int max,int length,boolean ids)throws JSONException {if(!(value instanceof JSONArray)||((JSONArray)value).length()>max)throw new JSONException("Invalid list");Set<String> out=new LinkedHashSet<>();JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++){String s=string(list.get(i),length);if(s.isEmpty()||ids&&!s.matches("[0-9]{1,20}"))throw new JSONException("Invalid list item");out.add(s);}return out;}
    private static String tabs(String value)throws JSONException {
        checkDepth(value);JSONObject source=new JSONObject(value),safe=new JSONObject();JSONArray list=source.getJSONArray("tabs"),out=new JSONArray();if(list.length()>12)throw new JSONException("Too many tabs");Set<String> keys=new HashSet<>();
        for(int i=0;i<list.length();i++){JSONObject tab=list.getJSONObject(i);String site=string(tab.get("origin"),40),id=string(tab.get("id"),20);if(!Arrays.asList(SITES).contains(site)||!id.matches("[0-9]{1,20}")||!keys.add(site+":"+id))throw new JSONException("Invalid tab");JSONObject record=new JSONObject().put("origin",site).put("id",id).put("title",string(tab.opt("title")==null?"Thread "+id:tab.get("title"),500));String own=string(tab.opt("own")==null?"":tab.get("own"),30);if(!own.matches("[0-9]{0,30}"))throw new JSONException("Invalid post");record.put("own",own);if(tab.has("anchor")){JSONObject a=tab.getJSONObject("anchor");String tag=a.isNull("tag")?"":string(a.get("tag"),30);if(!tag.matches("[0-9]{0,30}"))throw new JSONException("Invalid anchor");record.put("anchor",new JSONObject().put("tag",tag.isEmpty()?JSONObject.NULL:tag).put("offset",number(a.get("offset"),-1000000,1000000)).put("y",number(a.get("y"),0,10000000)));}out.put(record);}
        return safe.put("tabs",out).put("active","").toString();
    }
    public static Plan parse(String text){
        try{
            if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new JSONException("Too large");checkDepth(text);JSONObject root=new JSONObject(text);if(!root.getString("format").equals("autoadmit-settings")||number(root.get("version"),1,1)!=1)throw new JSONException("Unsupported backup format");
            Plan plan=new Plan();JSONObject main=root.getJSONObject("settings"),colors=root.getJSONObject("appearance");Iterator<String> keys=main.keys();
            while(keys.hasNext()){String key=keys.next();Object value=main.get(key);
                if(key.equals("replyPreviewMode")){String mode=string(value,10);if(!Arrays.asList("links","nested","stacked").contains(mode))throw new JSONException("Invalid preview mode");plan.classic.put(key,mode);}
                else if(key.equals("hiddenThreadAuthors"))plan.classic.put(key,strings(value,2000,500,false));
                else if(key.equals("openThreadTabs"))plan.classic.put(key,tabs(string(value,30000)));
                else for(String site:SITES){String prefix=site+":";if(!key.startsWith(prefix))continue;String suffix=key.substring(prefix.length());
                    if(suffix.equals("hiddenThreadIds"))plan.classic.put(key,strings(value,30,20,true));
                    else if(suffix.equals("hiddenThreadOrder")){String orderText=string(value,8000);checkDepth(orderText);JSONArray order=new JSONArray(orderText);strings(order,30,20,true);plan.classic.put(key,order.toString());}
                    else if(suffix.matches("hiddenThreadTitle:[0-9]{1,20}"))plan.classic.put(key,string(value,500));
                    else if(suffix.matches("hiddenThreadSince:[0-9]{1,20}"))plan.classic.put(key,number(value,0,System.currentTimeMillis()));
                }
            }
            keys=colors.keys();while(keys.hasNext()){String key=keys.next();Object value=colors.get(key);
                if(Arrays.asList("active","day","night").contains(key))plan.appearance.put(key,string(value,40));
                else if(key.equals("scheduled")){if(!(value instanceof Boolean))throw new JSONException("Invalid schedule toggle");plan.appearance.put(key,value);}
                else if(key.equals("dayTime")||key.equals("nightTime"))plan.appearance.put(key,(int)number(value,0,1439));
                else if(key.equals("custom")){String customText=string(value,200000);checkDepth(customText);JSONObject source=new JSONObject(customText),safe=new JSONObject();if(source.length()>100)throw new JSONException("Too many themes");Iterator<String> names=source.keys();while(names.hasNext()){String name=names.next();if(name.trim().isEmpty()||name.length()>40||Arrays.asList("Default","Light","Midnight","Sepia","Ocean").contains(name))throw new JSONException("Invalid theme name");JSONArray palette=source.getJSONArray(name),out=new JSONArray();if(palette.length()!=AppColors.KEYS.length)throw new JSONException("Invalid palette");for(int i=0;i<palette.length();i++)out.put((int)number(palette.get(i),Integer.MIN_VALUE,0xffffffffL)|0xff000000);safe.put(name,out);}plan.appearance.put(key,safe.toString());}
            }
            if(plan.count()==0)throw new JSONException("No supported settings");return plan;
        }catch(Exception invalid){throw new IllegalArgumentException("Invalid or unsupported settings backup. No settings were changed.");}
    }
    private static void checkDepth(String text)throws JSONException {
        boolean quoted=false,escaped=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>32)throw new JSONException("Backup nesting too deep");}else if(c=='}'||c==']')depth--;}
    }
    private static boolean write(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit();put(edit,values);return edit.commit();}
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit().clear();put(edit,values);edit.commit();}
    @SuppressWarnings("unchecked") private static void put(SharedPreferences.Editor edit,Map<String,?> values){for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object v=entry.getValue();if(v instanceof String)edit.putString(key,(String)v);else if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof Integer)edit.putInt(key,(Integer)v);else if(v instanceof Long)edit.putLong(key,(Long)v);else if(v instanceof Float)edit.putFloat(key,(Float)v);else if(v instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)v));}}
}
