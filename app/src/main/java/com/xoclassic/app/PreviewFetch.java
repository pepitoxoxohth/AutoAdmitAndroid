package com.xoclassic.app;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Public, click-only traffic. Never uses the forum cookie jar or credentials. */
public class PreviewFetch {
    public static class Result {
        public String url,type;public byte[] bytes;private long mediaTtl;
        public String text(){Charset charset=StandardCharsets.UTF_8;try{if(type.toLowerCase(Locale.ROOT).contains("charset="))charset=Charset.forName(type.split("(?i)charset=",2)[1].split("[; ]",2)[0].replace("\"",""));}catch(Exception ignored){}return new String(bytes,charset);}
    }
    private volatile boolean cancelled;
    private static final MediaByteCache mediaCache=new MediaByteCache();
    public void invalidateMedia(String address){try{mediaCache.invalidate(webUrl(address));}catch(Exception ignored){}}
    private Runnable retryListener=()->{};
    public void setRetryListener(Runnable listener){retryListener=listener;}
    private final Set<HttpURLConnection> active=Collections.synchronizedSet(new HashSet<>());
    public void cancel(){cancelled=true;synchronized(active){for(HttpURLConnection c:active)c.disconnect();active.clear();}}
    public static String webUrl(String address){
        URI uri=URI.create(address.trim());String scheme=uri.getScheme(),host=uri.getHost();
        if(host==null||uri.getUserInfo()!=null||uri.getPort()!=-1||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)))throw new IllegalArgumentException("This address cannot be previewed.");
        host=host.toLowerCase(Locale.ROOT);if(!host.contains(".")||host.matches("[\\d.]+")||host.startsWith("[")||host.endsWith(".local")||host.endsWith(".internal")||host.endsWith(".localhost")||host.equals("localhost"))throw new IllegalArgumentException("This address cannot be previewed.");
        return "https://"+host+(uri.getRawPath()==null||uri.getRawPath().isEmpty()?"/":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
    }
    public static boolean publicAddress(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] b=address.getAddress();if(b.length==16)return (b[0]&0xfe)!=0xfc;
        int a=b[0]&255,c=b[1]&255;return a!=0&&a!=127&&a<224&&!(a==100&&c>=64&&c<=127)&&!(a==169&&c==254)&&!(a==198&&(c==18||c==19));
    }
    public Result get(String address,int limit)throws Exception {
        if(cancelled)throw new IOException("Preview closed.");String key=webUrl(address);
        Result cached=mediaCache.get(key,limit);if(cached!=null){if(cancelled)throw new IOException("Preview closed.");return cached;}
        Result result;
        try{result=getOnce(address,limit,20000,30000);}catch(SocketTimeoutException timeout){
            if(cancelled)throw new IOException("Preview closed.");retryListener.run();
            result=getOnce(address,limit,25000,45000);
        }
        if(cancelled)throw new IOException("Preview closed.");
        mediaCache.put(key,result,result.mediaTtl);return result;
    }
    protected Result getOnce(String address,int limit,int connectTimeout,int readTimeout)throws Exception {
        String url=webUrl(address);
        for(int redirects=0;redirects<6;redirects++){
            if(cancelled)throw new IOException("Preview closed.");URI uri=URI.create(url);for(InetAddress ip:InetAddress.getAllByName(uri.getHost()))if(!publicAddress(ip))throw new IOException("This address cannot be previewed.");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();active.add(c);
            try {
                c.setConnectTimeout(connectTimeout);c.setReadTimeout(readTimeout);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 AutoAdmitPreview/1.0");// Imgur negotiates HTML for direct JPEGs if text/html is advertised.
                boolean image=uri.getHost().equalsIgnoreCase("i.imgur.com")||uri.getHost().equalsIgnoreCase("pbs.twimg.com")||(uri.getHost().equalsIgnoreCase("i.prcdn.co")&&uri.getPath().equals("/img"))||uri.getPath().toLowerCase(Locale.ROOT).matches(".*\\.(?:png|jpe?g|gif|webp|avif|bmp|svg)$");
                c.setRequestProperty("Accept",image?"image/avif,image/webp,image/*,*/*;q=0.8":"text/html,application/json,image/*,application/pdf,*/*;q=0.8");c.setRequestProperty("Accept-Encoding","gzip");
                // No Cookie, Authorization, Referer, or process-global cookie handler is installed by this app.
                int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("The site returned an invalid redirect.");url=webUrl(location.startsWith("?")?url.split("\\?",2)[0]+location:uri.resolve(location).toString());continue;}
                if(code!=200)throw new IOException(code==401||code==403?"This site requires sign-in or browser verification. Use Open original.":"The site returned HTTP "+code+". Use Open original.");
                if(c.getContentLengthLong()>limit)throw new IOException("This preview is too large. Use Open original.");
                InputStream source=c.getInputStream();if("gzip".equalsIgnoreCase(c.getContentEncoding()))source=new GZIPInputStream(source);ByteArrayOutputStream data=new ByteArrayOutputStream();
                try(InputStream in=source){byte[] buffer=new byte[16384];int count;while((count=in.read(buffer))!=-1){if(cancelled)throw new IOException("Preview closed.");if(data.size()+count>limit)throw new IOException("This preview is too large. Use Open original.");data.write(buffer,0,count);}}
                Result result=new Result();result.url=url;result.type=c.getContentType()==null?"":c.getContentType();result.bytes=data.toByteArray();result.mediaTtl=redirects==0?mediaFreshness(c,result.type,System.currentTimeMillis()):0;return result;
            }finally{active.remove(c);c.disconnect();}
        }
        throw new IOException("Too many redirects. Use Open original.");
    }
    private static long mediaFreshness(HttpURLConnection c,String mime,long now){
        String type=mime.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        if(!(type.startsWith("image/")||type.startsWith("video/")||type.startsWith("audio/")||type.equals("application/pdf")))return 0;
        String control=combinedHeader(c,"Cache-Control"),vary=combinedHeader(c,"Vary");
        if(c.getHeaderField("Set-Cookie")!=null||c.getHeaderField("Set-Cookie2")!=null)return 0;
        if(vary!=null)for(String header:vary.split(","))if(!header.trim().equalsIgnoreCase("Accept")&&!header.trim().equalsIgnoreCase("Accept-Encoding"))return 0;
        long lifetime=-1;
        if(control!=null)for(String part:control.toLowerCase(Locale.ROOT).split(",")){
            String[] directive=part.trim().split("=",2);String name=directive[0].trim();if(name.equals("no-store")||name.equals("no-cache")||name.equals("private"))return 0;
            if(name.equals("max-age"))try{long value=Long.parseLong(directive[1].replace("\"","").trim());if(value<=0)return 0;lifetime=lifetime<0?value:Math.min(lifetime,value);}catch(Exception invalid){return 0;}
        }
        long date=c.getHeaderFieldDate("Date",now),age;
        try{age=Math.max(0,Long.parseLong(Optional.ofNullable(c.getHeaderField("Age")).orElse("0")));}catch(Exception invalid){return 0;}
        long apparent=Math.max(0,(now-date)/1000),remaining;
        if(lifetime>=0)remaining=lifetime-Math.max(age,apparent);
        else{long expires=c.getHeaderFieldDate("Expires",0);if(expires<=0)return 0;remaining=(expires-date)/1000-Math.max(age,apparent);}
        return remaining<=0?0:Math.min(remaining,300)*1000;
    }
    private static String combinedHeader(HttpURLConnection c,String name){
        List<String> values=new ArrayList<>();for(Map.Entry<String,List<String>> entry:c.getHeaderFields().entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase(name)&&entry.getValue()!=null)values.addAll(entry.getValue());
        return values.isEmpty()?c.getHeaderField(name):String.join(",",values);
    }
}
