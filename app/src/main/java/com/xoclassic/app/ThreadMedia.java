package com.xoclassic.app;

import java.net.URI;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Local link inventory only: no requests, provider expansion or credentials. */
public final class ThreadMedia {
    public static final class Item {
        public final String url,postId;
        public Item(String url,String postId){this.url=url;this.postId=postId;}
    }
    public static boolean supported(String address){
        try{
            String safe=PreviewFetch.webUrl(address);URI uri=URI.create(safe);String host=uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.",""),path=uri.getPath();
            if(host.equals("xoxohth.com")||host.endsWith(".xoxohth.com")||host.equals("autoadmit.com")||host.endsWith(".autoadmit.com"))return false;
            // Twitter/X image CDN often puts the image format in the query, not the filename.
            if(host.equals("pbs.twimg.com")&&path.matches("/media/[A-Za-z0-9_-]+")&&uri.getRawQuery()!=null&&java.util.regex.Pattern.compile("(?:^|&)format=(?:jpg|jpeg|png|gif|webp)(?:&|$)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(uri.getRawQuery()).find())return true;
            String direct=PreviewResolver.directKind(path,"");
            if(direct.equals("image")||direct.equals("video")||direct.equals("audio"))return true;
            if(!PreviewResolver.embed(safe).isEmpty()||!PreviewResolver.imgurAlbumViewer(safe).isEmpty())return true;
            if(host.matches("(?:m\\.)?imgur\\.com"))return path.matches("/[a-zA-Z0-9]{5,10}/?");
            if(host.equals("ibb.co"))return path.matches("/[a-zA-Z0-9]+/?");
            return host.equals("postimg.cc")&&path.matches("/(?:gallery/)?[a-zA-Z0-9]+/?");
        }catch(Exception invalid){return false;}
    }
    public static List<Item> collect(ForumParser.ThreadPage page){
        List<Item> items=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ForumParser.Post post:page.posts){
            if(Thread.currentThread().isInterrupted())return Collections.emptyList();
            for(Element link:Jsoup.parseBodyFragment(post.html==null?"":post.html,page.url).select("a[href]")){
                try{String url=PreviewFetch.webUrl(link.absUrl("href"));if(supported(url)&&seen.add(url))items.add(new Item(url,post.id));}catch(Exception ignored){}
            }
        }
        return Collections.unmodifiableList(items);
    }
    public static String permalink(ForumParser.ThreadPage page,String postId){
        if(page==null||page.id==null||!page.id.matches("\\d+")||postId==null||!postId.matches("\\d+"))throw new IllegalArgumentException("Invalid post link.");
        boolean found=false;for(ForumParser.Post post:page.posts)if(postId.equals(post.id)){found=true;break;}
        if(!found)throw new IllegalArgumentException("Post is no longer in this thread.");
        String origin=ForumParser.origin(page.url).toString();
        return origin+"/thread.php?thread_id="+page.id+"&forum_id=2#"+postId;
    }
}
