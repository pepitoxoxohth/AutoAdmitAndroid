package com.xoclassic.app;

import java.math.BigInteger;

public final class NewestReply {
    public static final String KEY="showNewestReply";
    public static String select(ForumParser.ThreadPage page){
        ForumParser.Post newest=null;long latest=0;
        for(int i=1;i<page.posts.size();i++){
            ForumParser.Post post=page.posts.get(i);if(post.id==null||!post.id.matches("[0-9]+"))continue;
            long date=post.date==null?0:ForumParser.createdAt(post.date);
            if(newest==null||(date>0&&latest>0?date>latest||date==latest&&new BigInteger(post.id).compareTo(new BigInteger(newest.id))>0:new BigInteger(post.id).compareTo(new BigInteger(newest.id))>0)){newest=post;latest=date;}
        }
        return newest==null?"":newest.id;
    }
}
