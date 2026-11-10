package com.xoclassic.app;

import android.text.Html;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.regex.*;

/** Local visible-body text index. Build/search off the UI thread; offsets match native TextViews. */
public final class ThreadFind {
    public static final int MAX_MATCHES=10000;
    private ThreadFind(){}
    public static final class Hit {
        public final String postId;public final int start,end;
        Hit(String postId,int start,int end){this.postId=postId;this.start=start;this.end=end;}
        public boolean same(Hit other){return other!=null&&postId.equals(other.postId)&&start==other.start&&end==other.end;}
    }
    public static final class Results {
        public final List<Hit> hits;public final Map<String,List<Hit>> posts;public final boolean capped;
        private Results(List<Hit> hits,boolean capped){this.hits=Collections.unmodifiableList(hits);this.capped=capped;Map<String,List<Hit>> grouped=new HashMap<>();for(Hit hit:hits)grouped.computeIfAbsent(hit.postId,key->new ArrayList<>()).add(hit);for(Map.Entry<String,List<Hit>> group:grouped.entrySet())group.setValue(Collections.unmodifiableList(group.getValue()));posts=Collections.unmodifiableMap(grouped);}
    }
    public static Results empty(){return new Results(Collections.emptyList(),false);}
    private static final class Body {final String id,text;Body(String id,String text){this.id=id;this.text=text;}}
    public static final class Index {
        private final List<Body> bodies;
        private Index(List<Body> bodies){this.bodies=bodies;}
        public Results find(String query){
            if(query==null||query.trim().isEmpty())return empty();
            Pattern pattern=Pattern.compile(Pattern.quote(query),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);List<Hit> hits=new ArrayList<>();
            for(Body body:bodies){if(Thread.currentThread().isInterrupted())throw new CancellationException();Matcher matcher=pattern.matcher(body.text);while(matcher.find()){if(Thread.currentThread().isInterrupted())throw new CancellationException();if(hits.size()==MAX_MATCHES)return new Results(hits,true);hits.add(new Hit(body.id,matcher.start(),matcher.end()));}}
            return new Results(hits,false);
        }
    }
    public static Index build(List<ForumParser.Post> posts,int htmlFlags){List<Body> bodies=new ArrayList<>();for(ForumParser.Post post:posts){if(Thread.currentThread().isInterrupted())throw new CancellationException();bodies.add(new Body(post.id,Html.fromHtml(post.html==null?"":post.html,htmlFlags).toString()));}return new Index(bodies);}
    public static final class Highlight extends AppColors.Mark {public Highlight(boolean active){super(active?0xfff0a94d:0xffe5d59a);}}
    public static final class HighlightInk extends AppColors.Ink {public HighlightInk(){super(0xff242424);}}
}
