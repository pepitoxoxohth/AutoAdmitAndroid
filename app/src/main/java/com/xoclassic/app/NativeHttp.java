package com.xoclassic.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

/** Per-origin, private cookie jar. No process-global cookie handler or WebView. */
public final class NativeHttp {
    public static class Response {
        public int code;
        public String url,body,location;
    }
    private final String origin;
    private final SecureVault vault;
    private final boolean persistent;
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final String storageKey;
    private boolean loaded;
    private final Object cookieLock=new Object();
    private static final class Backoff {volatile long until;}
    private final Backoff backoff;
    public boolean backgroundPaused(){return System.nanoTime()/1000000<backoff.until;}
    private String lastSaved="";
    private final Map<String,Long> deadlines=new HashMap<>();
    private String cookieKey(HttpCookie cookie){return cookie.getName()+"|"+cookie.getDomain()+"|"+cookie.getPath();}
    public NativeHttp(String base,SecureVault vault) {
        this(base,vault,true,new Backoff());
    }
    private NativeHttp(String base,SecureVault vault,boolean persistent,Backoff backoff) {
        origin=ForumParser.origin(base).toString();this.vault=vault;this.persistent=persistent;this.backoff=backoff;storageKey=origin+":cookies";loaded=!persistent;
    }
    /** A login attempt must not inherit the currently signed-in account's cookies. */
    public NativeHttp freshSession(){return new NativeHttp(origin,vault,false,backoff);}
    public void adoptSession(NativeHttp source) throws Exception {
        if(source==this||!origin.equals(source.origin)||!persistent||source.persistent)throw new IllegalArgumentException("Unexpected login session.");
        String snapshot;synchronized(source.cookieLock){snapshot=source.cookieSnapshot();}
        synchronized(cookieLock){vault.put(storageKey,snapshot);cookies.getCookieStore().removeAll();deadlines.clear();loaded=false;lastSaved=snapshot;load();}
    }
    public void clear() { synchronized(cookieLock){cookies.getCookieStore().removeAll();loaded=true;lastSaved="";deadlines.clear();if(persistent)vault.remove(storageKey);} }
    private void load() throws Exception {
        if(loaded)return;
        String stored=vault.get(storageKey);
        if(stored!=null){
            JSONArray array=new JSONArray(stored);
            for(int i=0;i<array.length();i++){
                JSONObject record=array.getJSONObject(i);long expires=record.optLong("expires",0);
                if(expires>0&&expires<=System.currentTimeMillis())continue;
                HttpCookie cookie=new HttpCookie(record.getString("name"),record.getString("value"));
                cookie.setDomain(record.optString("domain",URI.create(origin).getHost()));cookie.setPath(record.optString("path","/"));
                cookie.setSecure(record.optBoolean("secure",true));cookie.setHttpOnly(record.optBoolean("httpOnly",true));
                cookie.setVersion(0);
                if(expires>0)cookie.setMaxAge(Math.max(1,(expires-System.currentTimeMillis())/1000));
                cookies.getCookieStore().add(URI.create(origin),cookie);
                deadlines.put(cookieKey(cookie),expires);
            }
        }
        loaded=true;
    }
    private String cookieSnapshot() throws Exception {
        JSONArray array=new JSONArray();
        for(HttpCookie cookie:cookies.getCookieStore().getCookies()){
            if(cookie.hasExpired())continue;
            JSONObject record=new JSONObject();record.put("name",cookie.getName());record.put("value",cookie.getValue());
            record.put("domain",cookie.getDomain());record.put("path",cookie.getPath());record.put("secure",cookie.getSecure());record.put("httpOnly",cookie.isHttpOnly());
            long age=cookie.getMaxAge();long expires=deadlines.computeIfAbsent(cookieKey(cookie),k->age<0?0:System.currentTimeMillis()+age*1000L);
            record.put("expires",expires);array.put(record);
        }
        return array.toString();
    }
    private void save() throws Exception {
        if(!persistent)return;
        String serialized=cookieSnapshot();if(!serialized.equals(lastSaved)){vault.put(storageKey,serialized);lastSaved=serialized;}
    }
    public static String encode(Map<String,String> fields) throws Exception {
        StringBuilder output=new StringBuilder();
        for(Map.Entry<String,String> field:fields.entrySet()){
            if(output.length()>0)output.append('&');
            output.append(URLEncoder.encode(field.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(field.getValue(),"UTF-8"));
        }
        return output.toString();
    }
    public Response get(String path) throws Exception { return request("GET",ForumParser.safeUrl(origin,path),null,true); }
    public Response getBackground(String path,ReadCancellation cancellation)throws Exception{return request("GET",ForumParser.safeUrl(origin,path),null,true,cancellation);}
    public Response probe(String path) throws Exception { return request("GET",ForumParser.safeUrl(origin,path),null,false); }
    public Response submit(ForumParser.Form form,Map<String,String> fields) throws Exception {
        FormLimits.validate(form,fields);
        String address=ForumParser.safeUrl(origin,form.target),body=encode(fields);
        if(form.method.equals("GET"))return request("GET",address+(address.contains("?")?"&":"?")+body,null,true);
        return request("POST",address,body,true);
    }
    private Response request(String method,String address,String payload,boolean redirects) throws Exception {
        return request(method,address,payload,redirects,null);
    }
    private Response request(String method,String address,String payload,boolean redirects,ReadCancellation cancellation) throws Exception {
        synchronized(cookieLock){load();}
        for(int hop=0;hop<6;hop++) {
            if(cancellation!=null)cancellation.check();
            if((cancellation!=null||ForegroundExecutor.isBackgroundThread())&&backgroundPaused())throw new IOException("The site is limiting requests. Background reads are paused.");
            String safe=ForumParser.safeUrl(origin,address);
            HttpURLConnection connection=(HttpURLConnection)new URL(safe).openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);connection.setRequestProperty("Accept","text/html");connection.setRequestProperty("Accept-Encoding","gzip");
            connection.setRequestProperty("User-Agent","XOClassic/0.1 Android");
            connection.setRequestProperty("Referer",origin+"/main.php?forum_id=2");
            synchronized(cookieLock){for(Map.Entry<String,List<String>> header:cookies.get(URI.create(safe),Collections.emptyMap()).entrySet())
                connection.setRequestProperty(header.getKey(),String.join("; ",header.getValue()));}
            try {
                if(cancellation!=null)cancellation.attach(connection);
                if(method.equals("POST")){
                    connection.setRequestProperty("Origin",origin);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    connection.setDoOutput(true);byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
                    try(OutputStream stream=connection.getOutputStream()){stream.write(bytes);}
                }
                if(cancellation!=null)cancellation.check();int code=connection.getResponseCode();
                if(code==429)pauseBackground(connection.getHeaderField("Retry-After"));
                synchronized(cookieLock){
                // Normalize defaults with the same parser/policy as the real jar. Invalid cookies
                // are ignored by CookieManager; unrelated paths must keep their original expiry.
                Map<String,List<String>> headers=connection.getHeaderFields();
                if(headers.keySet().stream().anyMatch(name->name!=null&&(name.equalsIgnoreCase("Set-Cookie")||name.equalsIgnoreCase("Set-Cookie2")))){
                    CookieManager replaced=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
                    replaced.put(URI.create(safe),headers);
                    for(HttpCookie fresh:replaced.getCookieStore().getCookies())deadlines.remove(cookieKey(fresh));
                }
                cookies.put(URI.create(safe),connection.getHeaderFields());save();}
                String location=connection.getHeaderField("Location");
                Response response=new Response();response.code=code;response.url=safe;response.location=location;response.body="";
                if(code>=300&&code<400&&location!=null){
                    // Validate before any follow-up. Never replay a credential or message POST.
                    address=ForumParser.safeUrl(safe,location);
                    if(!redirects)return response;
                    if(method.equals("POST")&&(code==307||code==308))throw new IOException("Posting redirected unexpectedly. Check the website before retrying.");
                    method="GET";payload=null;continue;
                }
                if(code!=200)throw new IOException(code==429?"The site is limiting requests. Wait and refresh.":"The website could not be reached (HTTP "+code+").");
                InputStream stream=connection.getInputStream();if("gzip".equalsIgnoreCase(connection.getContentEncoding()))stream=new java.util.zip.GZIPInputStream(stream);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int count;
                try {while((count=stream.read(chunk))!=-1){if(cancellation!=null)cancellation.check();if(bytes.size()+count>8*1024*1024)throw new IOException("This page is too large to load in the app.");bytes.write(chunk,0,count);}}finally{stream.close();}
                Charset charset=StandardCharsets.UTF_8;
                String contentType=connection.getContentType();
                if(contentType!=null&&contentType.toLowerCase(Locale.ROOT).contains("charset=")){
                    try{charset=Charset.forName(contentType.split("(?i)charset=",2)[1].split("[; ]",2)[0].replace("\"",""));}catch(Exception ignored){}
                }
                if(cancellation!=null)cancellation.check();response.body=new String(bytes.toByteArray(),charset);return response;
            } finally { if(cancellation!=null)cancellation.detach(connection);connection.disconnect(); }
        }
        throw new IOException("Too many website redirects.");
    }
    private void pauseBackground(String retry){
        long delay=60000;
        if(retry!=null)try{delay=Math.max(delay,Math.multiplyExact(Long.parseLong(retry.trim()),1000));}
        catch(Exception ignored){try{delay=Math.max(delay,java.time.ZonedDateTime.parse(retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis());}catch(Exception invalid){}}
        synchronized(backoff){backoff.until=Math.max(backoff.until,System.nanoTime()/1000000+Math.min(delay,Long.MAX_VALUE/4));}
    }
}
