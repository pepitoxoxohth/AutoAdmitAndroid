package com.xoclassic.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Classifies links and extracts provider data without running remote scripts. */
public final class PreviewResolver {
    public static class Media {public String url,type,title="";public Media(String u,String t){url=u;type=t;}}
    public static class Page {public String kind,url,title="",html="",provider="",mime="";public byte[] bytes;public final List<Media> media=new ArrayList<>();}
    private final PreviewFetch fetch;
    public PreviewResolver(PreviewFetch fetch){this.fetch=fetch;}
    private static String host(URI u){return u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.","");}
    private static String query(URI u,String key){return ForumParser.query(u.toString(),key);}
    public static String directKind(String path,String mime){String p=path.toLowerCase(Locale.ROOT),m=mime.toLowerCase(Locale.ROOT);if(m.startsWith("image/")||p.matches(".*\\.(?:png|jpe?g|gif|webp|avif|bmp|svg)$"))return "image";if(m.startsWith("video/")||p.matches(".*\\.(?:mp4|webm|gifv)$"))return "video";if(m.startsWith("audio/")||p.matches(".*\\.(?:mp3|m4a|aac|ogg|oga|wav|flac)$"))return "audio";if(m.startsWith("application/pdf")||p.endsWith(".pdf"))return "pdf";return "";}
    public static String embed(String address){
        URI u=URI.create(PreviewFetch.webUrl(address));String h=host(u),p=u.getPath();Matcher m;
        String id=h.equals("youtu.be")?p.substring(1).split("/")[0]:h.matches("(?:m\\.)?youtube\\.com|youtube-nocookie\\.com")?(p.equals("/watch")?query(u,"v"):match(p,"^/(?:shorts|embed|live)/([\\w-]+)",1)):"";
        if(id.matches("[\\w-]{11}")){String t=query(u,"t");if(t.isEmpty())t=query(u,"start");long seconds=0;Matcher time=Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s?)?").matcher(t);if(!t.isEmpty()&&time.matches())for(int n=1;n<=3;n++)if(time.group(n)!=null)seconds+=Long.parseLong(time.group(n))*(n==1?3600:n==2?60:1);return "https://www.youtube-nocookie.com/embed/"+id+(seconds>0?"?start="+seconds:"");}
        if(h.matches("x\\.com|twitter\\.com|mobile\\.twitter\\.com")&&!(id=match(p,"^/(?:[\\w]+/status|i/web/status)/(\\d{1,25})(?:/|$)",1)).isEmpty())return "https://platform.twitter.com/embed/Tweet.html?id="+id+"&dnt=true&theme=dark";
        if(h.matches("(?:m\\.)?instagram\\.com")&&!(id=match(p,"^/(?:[\\w.]+/)?(?:p|reel|reels|tv)/([\\w-]{5,40})(?:/|$)",1)).isEmpty())return "https://www.instagram.com/p/"+id+"/embed/captioned/";
        if(h.matches("(?:m\\.|web\\.)?facebook\\.com")&&!(id=match(p,"^/reel/(\\d{5,25})/?$",1)).isEmpty())return "https://www.facebook.com/plugins/video.php?href="+encode("https://www.facebook.com/reel/"+id)+"&show_text=false&autoplay=false";
        if(h.matches("(?:m\\.)?tiktok\\.com")&&!(id=match(p,"^/@[^/]+/video/(\\d{10,25})(?:/|$)",1)).isEmpty())return "https://www.tiktok.com/player/v1/"+id+"?autoplay=0";
        if(h.equals("streamable.com")&&!(id=match(p,"^/(?:e/|o/)?([\\w]{5,12})/?$",1)).isEmpty())return "https://streamable.com/e/"+id+"?autoplay=0";
        if(h.matches("vimeo\\.com|player\\.vimeo\\.com")&&!(id=match(p,"^/(?:video/|channels/[^/]+/|groups/[^/]+/videos/)?(\\d{5,12})(?:/([\\w]+))?/?$",1)).isEmpty()){String hash=query(u,"h");if(hash.isEmpty())hash=match(p,"/(\\d{5,12})/([\\w]+)",2);return "https://player.vimeo.com/video/"+id+"?autoplay=0"+(hash.matches("[\\w]+")?"&h="+hash:"");}
        if(h.equals("redd.it")&&p.matches("/[\\w]+/?"))return "https://www.redditmedia.com/r/all/comments/"+p.replace("/","")+"/?ref_source=embed&ref=share&embed=true";
        if(h.matches("(?:(?:old|new|m)\\.)?reddit\\.com")&&p.matches("/(?:r/[^/]+/)?comments/[\\w]+(?:/.*)?"))return "https://www.redditmedia.com"+(p.startsWith("/comments/")?"/r/all":"")+p.replace("/comment/","/")+"?ref_source=embed&ref=share&embed=true";
        if(h.matches("vocaroo\\.com|voca\\.ro")&&!(id=match(p,"^/(?:embed/|i/)?([\\w]{10,20})/?$",1)).isEmpty())return "https://vocaroo.com/embed/"+id+"?autoplay=0";
        if(h.equals("soundcloud.com")&&p.matches("/[^/]+/[^/]+.*"))return "https://w.soundcloud.com/player/?url="+encode("https://soundcloud.com"+p)+"&auto_play=false";
        return "";
    }
    private static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
    private static String match(String value,String expression,int group){Matcher m=Pattern.compile(expression,Pattern.CASE_INSENSITIVE).matcher(value);return m.find()&&m.group(group)!=null?m.group(group):"";}
    public static String imgurAlbumViewer(String address){try{URI u=URI.create(PreviewFetch.webUrl(address));if(!host(u).matches("(?:m\\.)?imgur\\.com"))return "";String id=match(u.getPath(),"^/(?:a|gallery)/(?:[\\w-]+-)?([a-zA-Z0-9]{5,10})/?$",1);return id.isEmpty()?"":"https://imgur.com/a/"+id+"/embed?pub=true";}catch(Exception invalid){return "";}}
    public static String normalizeArticleUrl(String address){
        String safe=PreviewFetch.webUrl(address);URI u=URI.create(safe);String h=host(u),p=u.getRawPath();
        if(h.equals("dailymail.co.uk"))return "https://www.dailymail.com"+p+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        if(h.equals("threadreaderapp.com")){String id=match(u.getPath(),"^/(?:scrolly/(\\d{5,25})/?|thread/(\\d{5,25})\\.html/?)$",1);if(id.isEmpty())id=match(u.getPath(),"^/thread/(\\d{5,25})\\.html/?$",1);if(!id.isEmpty())return "https://threadreaderapp.com/thread/"+id+".html";}
        if((h.equals("redfin.com")&&u.getPath().matches(".*/home/\\d+/?$"))||(h.equals("har.com")&&u.getPath().matches("^/homedetail/(?:[^/]+/)?\\d+/?$"))||(h.equals("zillow.com")&&u.getPath().matches("^/homedetails/(?:[^/]+/)?\\d+_zpid/?$")))return "https://www."+h+p;
        return safe;
    }
    public static boolean sameEmbedSite(String first,String second){
        try{String a=URI.create(PreviewFetch.webUrl(first)).getHost(),b=URI.create(PreviewFetch.webUrl(second)).getHost();
            if((a.equals("redditmedia.com")||a.endsWith(".redditmedia.com")||a.equals("embed.reddit.com")))return b.equals("redditmedia.com")||b.endsWith(".redditmedia.com")||b.equals("embed.reddit.com");
            for(String root:new String[]{"imgur.com","youtube.com","youtube-nocookie.com","twitter.com","x.com","instagram.com","facebook.com","tiktok.com","vocaroo.com","soundcloud.com","streamable.com","vimeo.com"})if(a.equals(root)||a.endsWith("."+root)){
                if(root.startsWith("youtube"))return b.equals("youtube.com")||b.endsWith(".youtube.com")||b.equals("youtube-nocookie.com")||b.endsWith(".youtube-nocookie.com");
                if(root.equals("twitter.com")||root.equals("x.com"))return b.equals("x.com")||b.endsWith(".x.com")||b.equals("twitter.com")||b.endsWith(".twitter.com");
                return b.equals(root)||b.endsWith("."+root);
            }return a.equals(b);
        }catch(Exception invalid){return false;}
    }
    public Page resolve(String address)throws Exception {
        String url=normalizeArticleUrl(address);URI u=URI.create(url);String h=host(u),path=u.getPath();Page result=new Page();result.url=url;
        String embedded=embed(url);if(!embedded.isEmpty()){if(URI.create(embedded).getHost().equals("www.redditmedia.com")){try{Document reddit=Jsoup.parse(fetch.get(embedded,8000000).text(),url);List<Media> images=redditImages(reddit,url);if(!images.isEmpty()){result.kind="gallery";result.media.addAll(images);return result;}String video=redditVideoEmbed(reddit,url);if(!video.isEmpty()){result.kind="embed";result.title="Reddit video";result.media.add(new Media(video,"embed"));return result;}}catch(Exception unavailable){/* Keep the provider player and Open original as fallbacks. */}}result.kind="embed";result.media.add(new Media(embedded,"embed"));return result;}
        if(h.equals("reddit.com")&&path.matches("/r/[^/]+/s/[\\w]+/?")){PreviewFetch.Result share=fetch.get(url,8000000);String target=embed(share.url);if(target.isEmpty())throw new Exception("Reddit did not return a previewable post. Use Open original.");return resolve(share.url);}
        if(h.matches("(?:m\\.)?imgur\\.com")){
            String album=match(path,"^/(?:a|gallery)/(?:[\\w-]+-)?([a-zA-Z0-9]{5,10})/?$",1);
            if(!album.isEmpty()){String viewer="https://imgur.com/a/"+album+"/embed?pub=true";try{result.kind="gallery";result.media.addAll(imgur(fetch.get(viewer,4000000).text()));}catch(Exception unavailable){result.kind="embed";result.title="Imgur viewer";result.media.add(new Media(viewer,"embed"));}return result;}
            String image=match(path,"^/(?:[\\w-]+-)?([a-zA-Z0-9]{5,10})(?:\\.(png|jpe?g|gif|webp|avif|bmp|gifv|mp4|webm))?/?$",1);String ext=match(path,"\\.(png|jpe?g|gif|webp|avif|bmp|gifv|mp4|webm)/?$",1);if(!image.isEmpty()){if(ext.isEmpty())ext="jpg";url="https://i.imgur.com/"+image+"."+(ext.equalsIgnoreCase("gifv")?"mp4":ext);u=URI.create(url);}
        }
        if(h.equals("i.imgur.com")&&path.toLowerCase(Locale.ROOT).endsWith(".gifv")){url=url.replaceFirst("(?i)\\.gifv(?=\\?|$)",".mp4");u=URI.create(url);}
        if(h.equals("postimg.cc")||h.equals("ibb.co")){PreviewFetch.Result data=fetch.get(url,4000000);result.kind="gallery";result.media.addAll(hostedImages(data.text(),data.url,h));return result;}
        String kind=directKind(u.getPath(),"");if(!kind.isEmpty()){result.kind=kind;result.media.add(new Media(url,kind));return result;}
        PreviewFetch.Result data=fetch.get(url,8000000);kind=directKind(URI.create(data.url).getPath(),data.type);
        if(!kind.isEmpty()){result.kind=kind;result.mime=data.type;result.bytes=data.bytes;result.media.add(new Media(data.url,kind));return result;}
        if(!data.type.toLowerCase(Locale.ROOT).contains("html"))throw new Exception("This link cannot be previewed. Use Open original.");
        result.kind="article";result.url=data.url;result.html=data.text();result.provider=h.matches("(?:.*\\.)?(?:redfin|zillow|har)\\.com")?"listing":"article";return result;
    }
    public static List<Media> imgur(String html)throws Exception {
        Matcher m=Pattern.compile("\\bvar\\s+images\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*,\\s*albumHash\\s*=").matcher(html);if(!m.find())throw new Exception("Imgur did not return album data. Use Open original.");JSONArray items=new JSONObject(m.group(1)).optJSONArray("images");if(items==null)throw new Exception("Album data is unavailable.");List<Media> output=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<items.length()&&output.size()<500;n++){JSONObject item=items.optJSONObject(n);if(item==null)continue;String hash=item.optString("hash"),ext=item.optString("ext").toLowerCase(Locale.ROOT);if(!hash.matches("[a-zA-Z0-9]{5,10}")||!ext.matches("\\.(?:jpe?g|png|gif|webp|avif|bmp|mp4|webm)"))continue;String url="https://i.imgur.com/"+hash+ext;if(!seen.add(url))continue;Media media=new Media(url,directKind(url,""));media.title=item.optString("title","");output.add(media);}
        if(output.isEmpty())throw new Exception("No original album images were found. Use Open original.");return output;
    }
    /** Match the requested post before trusting any media; ignore icons, trackers and other posts. */
    public static List<Media> redditImages(String html,String address){return redditImages(Jsoup.parse(html,address),address);}
    private static List<Media> redditImages(Document doc,String address){
        List<Media> output=new ArrayList<>();URI uri=URI.create(address);String h=host(uri);
        String id=h.equals("redd.it")?match(uri.getPath(),"^/([a-zA-Z0-9]+)/?$",1):match(uri.getPath(),"/comments/([a-zA-Z0-9]+)(?:/|$)",1);
        if(id.isEmpty())return output;
        for(Element node:doc.select("shreddit-screenview-data[data]"))try{
            JSONObject post=new JSONObject(node.attr("data")).optJSONObject("post");
            if(post==null||!post.optString("id").equals("t3_"+id)||!post.optString("type").equals("image"))continue;
            String image=PreviewFetch.webUrl(post.optString("url"));URI media=URI.create(image);
            if(!media.getHost().equals("i.redd.it")||!directKind(media.getPath(),"").equals("image"))continue;
            Media item=new Media(image,"image");Element heading=doc.selectFirst("h1,h2,[slot=title]");item.title=heading==null?"Reddit image":heading.text();output.add(item);return output;
        }catch(Exception ignored){}
        return output;
    }
    public static String redditVideoEmbed(String html,String address){return redditVideoEmbed(Jsoup.parse(html,address),address);}
    private static String redditVideoEmbed(Document doc,String address){
        URI uri=URI.create(address);String h=host(uri);if(!h.matches("redd\\.it|(?:(?:old|new|m)\\.)?reddit\\.com"))return "";
        String id=h.equals("redd.it")?match(uri.getPath(),"^/([a-zA-Z0-9]+)/?$",1):match(uri.getPath(),"/comments/([a-zA-Z0-9]+)(?:/|$)",1);if(id.isEmpty())return "";
        for(Element node:doc.select("shreddit-screenview-data[data]"))try{
            JSONObject post=new JSONObject(node.attr("data")).optJSONObject("post");
            if(post==null||!post.optString("id").equals("t3_"+id)||!post.optString("type").equals("link"))continue;
            String url=PreviewFetch.webUrl(post.optString("url")),provider=host(URI.create(url));
            if(!provider.matches("(?:m\\.)?youtube\\.com|youtu\\.be|youtube-nocookie\\.com|vimeo\\.com|player\\.vimeo\\.com|streamable\\.com"))continue;
            String player=embed(url);if(!player.isEmpty())return player;
        }catch(Exception invalid){}
        return "";
    }
    public static List<Media> hostedImages(String html,String url,String provider)throws Exception {
        Document doc=Jsoup.parse(html,url);List<Media> output=new ArrayList<>();Set<String> seen=new HashSet<>();boolean album=provider.equals("postimg.cc")&&URI.create(url).getPath().contains("/gallery/");
        for(Element node:doc.select(album?"[data-pswp-src]":"meta[property=og:image]")){String address=node.attr(album?"data-pswp-src":"content");try{address=PreviewFetch.webUrl(URI.create(url).resolve(address).toString());String h=URI.create(address).getHost();if(!(provider.equals("postimg.cc")?h.equals("i.postimg.cc"):h.equals("i.ibb.co"))||!directKind(URI.create(address).getPath(),"").equals("image")||!seen.add(address))continue;Media item=new Media(address,"image");item.title=node.selectFirst("img")==null?"":node.selectFirst("img").attr("alt");output.add(item);if(output.size()>=500)break;}catch(Exception ignored){}}
        if(output.isEmpty())throw new Exception("The image host did not return its original images. Use Open original.");return output;
    }
}
