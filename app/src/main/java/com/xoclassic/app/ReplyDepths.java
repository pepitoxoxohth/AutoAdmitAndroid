package com.xoclassic.app;

import java.util.*;

/** Memoizes terminating reply chains; malformed cycles retain the original bounded walk. */
public final class ReplyDepths {
    public static Map<String,Integer> calculate(Map<String,ForumParser.Post> posts){
        Map<String,Integer> result=new HashMap<>(),terminating=new HashMap<>();
        for(ForumParser.Post start:posts.values()){
            List<ForumParser.Post> path=new ArrayList<>();Set<String> seen=new HashSet<>();
            ForumParser.Post cursor=start;int depth=0;boolean cycle=false;
            while(true){
                Integer known=terminating.get(cursor.id);if(known!=null){depth=known+1;break;}
                if(!seen.add(cursor.id)){cycle=true;break;}
                path.add(cursor);if(cursor.parent.isEmpty()){depth=0;break;}
                ForumParser.Post parent=posts.get(cursor.parent);if(parent==null){depth=1;break;}cursor=parent;
            }
            if(cycle){int count=0;seen.clear();seen.add(start.id);cursor=start;while(!cursor.parent.isEmpty()&&seen.add(cursor.parent)){count++;cursor=posts.get(cursor.parent);if(cursor==null)break;}result.put(start.id,count);}
            else{for(int i=path.size()-1;i>=0;i--){String id=path.get(i).id;terminating.put(id,depth);result.put(id,depth++);}if(path.isEmpty())result.put(start.id,terminating.get(start.id));}
        }
        return result;
    }
}
