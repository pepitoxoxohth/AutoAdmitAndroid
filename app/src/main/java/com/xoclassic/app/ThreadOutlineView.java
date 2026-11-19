package com.xoclassic.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

public final class ThreadOutlineView extends ListView {
    private final List<ForumParser.Post> posts;
    private final Map<String,ForumParser.Post> byId=new LinkedHashMap<>();
    private final Map<String,List<ForumParser.Post>> children=new HashMap<>();
    private final Set<String> collapsed=new HashSet<>();
    private final List<ForumParser.Post> rows=new ArrayList<>();
    private final Map<String,Integer> depths=new HashMap<>();
    private final Consumer<String> open;
    private final SharedPreferences preferences;private final String visitedKey,pendingKey;
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return rows.size();}
        public Object getItem(int position){return rows.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            ForumParser.Post post=rows.get(position);int depth=depths.getOrDefault(post.id,0);boolean branch=!children.getOrDefault(post.id,Collections.emptyList()).isEmpty();
            LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8+Math.min(depth,3)*12),dp(7),dp(10),dp(7));row.setTag("outline-row:"+post.id);
            Button expand=new Button(getContext());expand.setText(branch?(collapsed.contains(post.id)?"›":"⌄"):"·");expand.setTextSize(20);expand.setPadding(0,0,0,0);expand.setMinWidth(0);expand.setMinimumWidth(0);AppColors.text(expand,0xff9cbce3);AppColors.background(expand,android.graphics.Color.TRANSPARENT);expand.setEnabled(branch);expand.setContentDescription((collapsed.contains(post.id)?"Expand":"Collapse")+" replies");expand.setTag("outline-expand:"+post.id);expand.setOnClickListener(v->{int first=getFirstVisiblePosition(),offset=getChildCount()>0?getChildAt(0).getTop():0;if(!collapsed.add(post.id))collapsed.remove(post.id);rebuild();setSelectionFromTop(first,offset);});row.addView(expand,new LinearLayout.LayoutParams(dp(44),dp(48)));
            LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(4),dp(4),0,dp(4));TextView snippet=new TextView(getContext());String text=post.text==null?"":post.text.replaceAll("\\s+"," ").trim();snippet.setText(text.isEmpty()?"[Blank reply]":text);snippet.setTextSize(16);snippet.setMaxLines(2);snippet.setEllipsize(android.text.TextUtils.TruncateAt.END);boolean unread=preferences.getStringSet(pendingKey,Collections.emptySet()).contains(post.id),visited=preferences.getStringSet(visitedKey,Collections.emptySet()).contains(post.id);if(unread)snippet.setText("★ "+snippet.getText());AppColors.text(snippet,unread?0xff000004:visited?0xffd1b8d4:0xff9cbce3);snippet.setTag("outline-title:"+post.id);labels.addView(snippet);
            TextView meta=new TextView(getContext());meta.setText((post.author==null?"":post.author)+(position==0&&post==posts.get(0)?" · Original post":"")+(branch?" · "+children.get(post.id).size()+" direct replies":"")+(depth>3?" · level "+depth:""));meta.setTextSize(12);meta.setPadding(0,dp(5),0,0);AppColors.text(meta,0xffa8adb5);labels.addView(meta);labels.setMinimumHeight(dp(48));labels.setFocusable(true);labels.setClickable(true);labels.setContentDescription(snippet.getText()+". "+meta.getText()+". Open post");labels.setOnClickListener(v->{Set<String> visitedIds=new HashSet<>(preferences.getStringSet(visitedKey,Collections.emptySet()));visitedIds.add(post.id);Set<String> unreadIds=new HashSet<>(preferences.getStringSet(pendingKey,Collections.emptySet()));unreadIds.remove(post.id);preferences.edit().putStringSet(visitedKey,visitedIds).putStringSet(pendingKey,unreadIds).apply();open.accept(post.id);});row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));return row;
        }
    };
    public ThreadOutlineView(Context context,ForumParser.ThreadPage page,Consumer<String> open){this(context,page,context.getSharedPreferences("classic",0),open);}
    public ThreadOutlineView(Context context,ForumParser.ThreadPage page,SharedPreferences preferences,Consumer<String> open){super(context);this.preferences=preferences;String prefix=ForumParser.origin(page.url).toString()+":";visitedKey=prefix+"outlineVisited:"+page.id;pendingKey=prefix+"replyHighlight:"+page.id+":pending";this.posts=page.posts;this.open=open;setTag("thread-outline-list");setContentDescription("Thread outline. Tap reply text to open the post; use arrows to expand or collapse branches.");
        for(ForumParser.Post post:posts)if(post.id!=null)byId.putIfAbsent(post.id,post);
        for(ForumParser.Post post:byId.values()){String parent=post.parent;if(parent==null||parent.equals(post.id)||!byId.containsKey(parent))parent="";children.computeIfAbsent(parent,k->new ArrayList<>()).add(post);}
        setDividerHeight(dp(1));setAdapter(adapter);rebuild();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void rebuild(){rows.clear();depths.clear();Set<String> seen=new HashSet<>();Deque<ForumParser.Post> stack=new ArrayDeque<>();List<ForumParser.Post> roots=children.getOrDefault("",Collections.emptyList());for(int i=roots.size()-1;i>=0;i--){stack.push(roots.get(i));depths.put(roots.get(i).id,0);}drain(stack,seen);for(ForumParser.Post post:byId.values())if(!seen.contains(post.id)){boolean hidden=false;Set<String> chain=new HashSet<>();ForumParser.Post ancestor=post;while(ancestor!=null&&chain.add(ancestor.id)){if(collapsed.contains(ancestor.parent)){hidden=true;break;}ancestor=byId.get(ancestor.parent);}if(!hidden){depths.put(post.id,0);stack.push(post);drain(stack,seen);}}adapter.notifyDataSetChanged();}
    private void drain(Deque<ForumParser.Post> stack,Set<String> seen){while(!stack.isEmpty()){ForumParser.Post post=stack.pop();if(!seen.add(post.id))continue;rows.add(post);if(collapsed.contains(post.id))continue;List<ForumParser.Post> replies=children.getOrDefault(post.id,Collections.emptyList());for(int i=replies.size()-1;i>=0;i--){ForumParser.Post child=replies.get(i);if(!seen.contains(child.id)){depths.put(child.id,depths.getOrDefault(post.id,0)+1);stack.push(child);}}}}
}
