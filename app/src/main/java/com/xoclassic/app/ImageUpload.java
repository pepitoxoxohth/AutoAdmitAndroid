package com.xoclassic.app;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicit user-selected image upload. No forum cookies, global credentials or automatic retries. */
public final class ImageUpload {
    public static final int LIMIT=20*1024*1024;
    public static final class Failure extends Exception {public Failure(String message){super(message);}}
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    private volatile InputStream input;
    public synchronized void cancel(){
        if(cancelled)return;cancelled=true;HttpURLConnection c=connection;InputStream in=input;
        if(c==null&&in==null)return;
        Thread cleanup=new Thread(()->{if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){}if(in!=null)try{in.close();}catch(Exception ignored){}},"cancel-image-upload");cleanup.setDaemon(true);cleanup.start();
    }
    public static String name(String host){return "ImgBB";}
    public static boolean credentialValid(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{8,200}");}
    public static String imageType(byte[] h,int n){
        if(n>=3&&(h[0]&255)==255&&(h[1]&255)==216&&(h[2]&255)==255)return "image/jpeg";
        if(n>=8&&Arrays.equals(Arrays.copyOf(h,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if(n>=6){String s=new String(h,0,6,StandardCharsets.US_ASCII);if(s.equals("GIF87a")||s.equals("GIF89a"))return "image/gif";}
        if(n>=12&&new String(h,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(h,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        return "";
    }
    public static String resultUrl(String host,String response,int status)throws Failure {
        if(status==401||status==403)throw new Failure("Upload refused. Check the upload credential in Account → Image uploads.");
        if(status==429)throw new Failure("The image host is limiting uploads. Wait before trying again.");
        if(status!=200)throw new Failure("Upload was not confirmed (HTTP "+status+"). Check the image host before trying again.");
        try{
            JSONObject json=new JSONObject(response);if(!json.optBoolean("success"))throw new Exception();
            String url=json.getJSONObject("data").getString("url");String safe=PreviewFetch.webUrl(url),domain=URI.create(safe).getHost();
            if(!(domain.equals("i.ibb.co")))throw new Exception();
            return safe;
        }catch(Exception invalid){throw new Failure("The host did not return a valid uploaded-image link. Check the image host before trying again.");}
    }
    private void check()throws Failure {if(cancelled)throw new Failure("Upload cancelled.");}
    public String upload(Context context,Uri uri,String host,String credential)throws Exception {
        if(!"imgbb".equals(host))throw new IllegalArgumentException("Choose ImgBB.");
        if(!credentialValid(credential))throw new IllegalArgumentException("Set the upload credential in Account → Image uploads first.");
        if(uri==null||!"content".equals(uri.getScheme()))throw new IllegalArgumentException("Choose an image using the file picker.");
        check();try(InputStream selected=context.getContentResolver().openInputStream(uri)){return upload(context.getCacheDir(),selected,host,credential);}
        catch(IllegalArgumentException|Failure known){throw known;}catch(Exception unreadable){throw new Failure("The selected image could not be read. Choose it again.");}
    }
    public String upload(File cacheDirectory,InputStream selectedImage,String host,String credential)throws Exception {
        if(!"imgbb".equals(host))throw new IllegalArgumentException("Choose ImgBB.");
        if(!credentialValid(credential))throw new IllegalArgumentException("Set the upload credential in Account → Image uploads first.");
        File temporary=File.createTempFile("selected-upload-",".image",cacheDirectory);ScheduledExecutorService timeout=null;
        try{
            long length=0;String mime;
            try(InputStream selected=selectedImage){
                if(selected==null)throw new Failure("The selected image could not be opened.");input=selected;check();
                BufferedInputStream buffered=new BufferedInputStream(selected);buffered.mark(16);byte[] header=new byte[12];int n=0,got;
                while(n<header.length&&(got=buffered.read(header,n,header.length-n))>0)n+=got;
                mime=imageType(header,n);if(mime.isEmpty())throw new IllegalArgumentException("Choose a JPEG, PNG, GIF or WebP image. Videos are not supported.");buffered.reset();
                try(OutputStream file=new FileOutputStream(temporary)){byte[] bytes=new byte[32768];int count;while((count=buffered.read(bytes))!=-1){check();length+=count;if(length>LIMIT)throw new IllegalArgumentException("Choose an image smaller than 20 MB.");file.write(bytes,0,count);}}
            }finally{input=null;}
            check();String boundary="AutoAdmit"+UUID.randomUUID().toString().replace("-","");String ext=mime.substring(mime.indexOf('/')+1);if(ext.equals("jpeg"))ext="jpg";
            StringBuilder head=new StringBuilder();if(host.equals("imgbb"))head.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"key\"\r\n\r\n").append(credential).append("\r\n");
            head.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"image\"; filename=\"upload.").append(ext).append("\"\r\nContent-Type: ").append(mime).append("\r\n\r\n");
            byte[] prefix=head.toString().getBytes(StandardCharsets.UTF_8),suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII);
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.imgbb.com/1/upload").openConnection();connection=c;check();
            c.setRequestMethod("POST");c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setDoOutput(true);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","AutoAdmitImageUpload/1.0");
            c.setFixedLengthStreamingMode(prefix.length+length+suffix.length);timeout=Executors.newSingleThreadScheduledExecutor();timeout.schedule(c::disconnect,90,TimeUnit.SECONDS);
            try(OutputStream out=c.getOutputStream();InputStream file=new FileInputStream(temporary)){out.write(prefix);byte[] bytes=new byte[32768];int count;while((count=file.read(bytes))!=-1){check();out.write(bytes,0,count);}out.write(suffix);}
            check();int status=c.getResponseCode();if(status!=200)return resultUrl(host,"",status);
            ByteArrayOutputStream reply=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1){check();if(reply.size()+count>1000000)throw new Failure("The upload response was too large. Check the image host before trying again.");reply.write(bytes,0,count);}}
            check();return resultUrl(host,reply.toString("UTF-8"),status);
        }catch(IllegalArgumentException|Failure known){throw known;}
        catch(Exception failed){check();throw new Failure("Upload was not confirmed. Check the image host before trying again. Your message has not been posted.");}
        finally{input=null;if(timeout!=null)timeout.shutdownNow();HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;temporary.delete();}
    }
}
