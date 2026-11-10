package com.xoclassic.app;

import android.text.*;
import java.util.*;

/** Activity-local, immutable base text. Each view still gets its own clickable spans. */
public final class RenderedPostCache {
    private final LinkedHashMap<String,SpannedString> entries=new LinkedHashMap<>(16,.75f,true);
    private final int flags,budget;private int characters;
    public RenderedPostCache(int flags){this(flags,500000);}
    public RenderedPostCache(int flags,int budget){this.flags=flags;this.budget=budget;}
    public SpannedString get(String html){
        SpannedString text=entries.get(html);if(text!=null)return text;
        text=new SpannedString(Html.fromHtml(html,flags));int size=html.length()+text.length();
        if(size>budget)return text;
        entries.put(html,text);characters+=size;
        Iterator<Map.Entry<String,SpannedString>> iterator=entries.entrySet().iterator();
        while(characters>budget||entries.size()>128){Map.Entry<String,SpannedString> oldest=iterator.next();characters-=oldest.getKey().length()+oldest.getValue().length();iterator.remove();}
        return text;
    }
    public void clear(){entries.clear();characters=0;}
}
