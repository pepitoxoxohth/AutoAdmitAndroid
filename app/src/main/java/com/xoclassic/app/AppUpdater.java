package com.xoclassic.app;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit public-release requests only; never shares the forum cookie jar. */
public final class AppUpdater {
    public static final int MAX_APK=64*1024*1024;
    public static final class Release {public String version,notes,url,digest;public long size;}
    public static final class ReleasePage {public final List<Release> releases=new ArrayList<>();public boolean more;public int skipped;}
    public static ReleasePage parsePage(String repo,String json)throws Exception{
        repo=repository(repo);JSONArray source=new JSONArray(json);ReleasePage page=new ReleasePage();page.more=source.length()==20;Set<String> seen=new HashSet<>();
        for(int i=0;i<source.length();i++){try{Release release=parse(repo,source.getJSONObject(i).toString());if(seen.add(release.version))page.releases.add(release);}catch(Exception unsupported){page.skipped++;}}
        return page;
    }
    public static String repository(String value){
        String s=value.trim();if(s.startsWith("https://github.com/"))s=s.substring(19);if(s.endsWith("/"))s=s.substring(0,s.length()-1);if(s.endsWith(".git"))s=s.substring(0,s.length()-4);
        if(!s.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_.-]{1,100}")||s.endsWith("/.")||s.endsWith("/.."))throw new IllegalArgumentException("Enter a public GitHub repository as owner/repository or its HTTPS URL.");return s;
    }
    public static int compare(String a,String b){
        String[] x=a.replaceFirst("^[vV]","").split("\\."),y=b.replaceFirst("^[vV]","").split("\\.");if(x.length>4||y.length>4)throw new IllegalArgumentException("Use numeric release tags such as v1.0.0.");
        if(!a.matches("[vV]?[0-9]{1,9}(?:\\.[0-9]{1,9}){0,3}")||!b.matches("[vV]?[0-9]{1,9}(?:\\.[0-9]{1,9}){0,3}"))throw new IllegalArgumentException("Use numeric release tags such as v1.0.0.");
        for(int i=0;i<Math.max(x.length,y.length);i++){String p=i<x.length?x[i]:"0",q=i<y.length?y[i]:"0";if(!p.matches("[0-9]{1,9}")||!q.matches("[0-9]{1,9}"))throw new IllegalArgumentException("Use numeric release tags such as v1.0.0.");int c=Long.compare(Long.parseLong(p),Long.parseLong(q));if(c!=0)return c;}return 0;
    }
    public static Release parse(String repo,String json)throws Exception{
        repo=repository(repo);JSONObject root=new JSONObject(json);if(root.optBoolean("draft")||root.optBoolean("prerelease"))throw new IOException("No stable release is available.");Release r=new Release();r.version=root.getString("tag_name");compare(r.version,"0");r.notes=root.optString("body","");if(r.notes.length()>12000)r.notes=r.notes.substring(0,12000);
        JSONArray assets=root.optJSONArray("assets");List<JSONObject> candidates=new ArrayList<>();JSONObject preferred=null;
        if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(!a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk"))continue;candidates.add(a);if(a.optString("name").equals("AutoAdmit.apk")){if(preferred!=null)throw new IOException("Release contains ambiguous APK assets.");preferred=a;}}
        JSONObject asset=preferred!=null?preferred:candidates.size()==1?candidates.get(0):null;if(asset==null)throw new IOException("Attach one signed APK, or an asset named AutoAdmit.apk, to the stable release.");
        r.url=asset.getString("browser_download_url");URI u=URI.create(r.url);if(!downloadAddress(u)||!"github.com".equals(u.getHost())||!u.getPath().startsWith("/"+repo+"/releases/download/"))throw new IOException("The APK link does not belong to this repository.");
        r.size=asset.optLong("size",-1);if(r.size<=0||r.size>MAX_APK)throw new IOException("The update has an unsupported file size.");r.digest=asset.optString("digest","");if(!r.digest.isEmpty()&&!r.digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("Unsupported release checksum.");return r;
    }
    public static boolean downloadAddress(URI uri){String h=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&uri.getPort()==-1&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","releases.githubusercontent.com").contains(h);}
    public static Release check(String repo,String installed)throws Exception{
        Release r=parse(repo,metadata(repo,"/releases/latest"));return compare(r.version,installed)>0?r:null;
    }
    public static ReleasePage releases(String repo,int page)throws Exception{
        if(page<1||page>1000)throw new IllegalArgumentException("Release page is out of range.");return parsePage(repo,metadata(repo,"/releases?per_page=20&page="+page));
    }
    private static String metadata(String repo,String suffix)throws Exception{
        repo=repository(repo);HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/"+repo+suffix).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","AutoAdmit-Update-Check");
        try{int status=c.getResponseCode();if(status==404)throw new IOException("Repository or stable release not found. Create a public repository and publish a release with an APK first.");if(status==403||status==429)throw new IOException("GitHub is limiting update checks. Try again later.");if(status!=200)throw new IOException("GitHub update check failed (HTTP "+status+").");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2*1024*1024)throw new IOException("Release metadata is too large.");bytes.write(b,0,n);}}return bytes.toString("UTF-8");}finally{c.disconnect();}
    }
    public static File file(Context context){return new File(context.getCacheDir(),"autoadmit-update.apk");}
    private static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private static Set<String> signers(PackageInfo p)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28&&p.signingInfo!=null?p.signingInfo.getApkContentsSigners():p.signatures;Set<String> result=new HashSet<>();if(signatures!=null)for(Signature s:signatures)result.add(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;
    }
    public static void verify(Context context,File apk)throws Exception{
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageManager manager=context.getPackageManager();PackageInfo current=manager.getPackageInfo(context.getPackageName(),flags),next=manager.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(next==null||!context.getPackageName().equals(next.packageName))throw new IOException("The downloaded file is not an AutoAdmit update.");if(version(next)<=version(current))throw new IOException("The APK is not newer than the installed app.");Set<String> known=signers(current);if(known.isEmpty()||!known.equals(signers(next)))throw new IOException("Update refused: the APK has a different signing identity.");
    }
    public static File download(Context context,Release release)throws Exception{
        File part=new File(context.getCacheDir(),"autoadmit-update.part"),destination=file(context);String address=release.url;boolean saved=false;long deadline=System.nanoTime()+180000000000L;
        try{for(int hop=0;hop<6;hop++){URI uri=URI.create(address);if(!downloadAddress(uri))throw new IOException("Unapproved update download destination.");HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","AutoAdmit-Update-Download");try{int status=c.getResponseCode();if(status>=300&&status<400){String target=c.getHeaderField("Location");if(target==null)throw new IOException("Invalid update redirect.");address=uri.resolve(target).toString();continue;}if(status!=200)throw new IOException("Update download failed (HTTP "+status+").");long total=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("Update download timed out. Try again later.");total+=n;if(total>MAX_APK||total>release.size)throw new IOException("Update exceeds its advertised size.");digest.update(b,0,n);out.write(b,0,n);}}if(total!=release.size)throw new IOException("Update download was incomplete.");StringBuilder hex=new StringBuilder();for(byte v:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",v&255));if(!release.digest.isEmpty()&&!release.digest.substring(7).equalsIgnoreCase(hex.toString()))throw new IOException("Update checksum did not match.");verify(context,part);if(!part.renameTo(destination))throw new IOException("Could not save the downloaded update.");saved=true;return destination;}finally{c.disconnect();}}throw new IOException("Too many update redirects.");}finally{if(!saved)part.delete();}
    }
}
