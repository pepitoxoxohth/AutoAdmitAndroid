package com.xoclassic.app;

import org.json.*;
import java.util.*;

/** Small encrypted Android instance state, not a persistent draft or settings backup. */
public final class ComposerState {
    public static final String KEY="composer-instance-v1";
    public String origin,message,subject,parent,threadId="",threadTitle="";
    public int cursorStart,cursorEnd,subjectStart,subjectEnd,scrollY;
    public boolean subjectFocused;public String returnThread="",returnTag="";public int returnOffset,returnY;
    public ForumParser.Form form;
    public String encode(SecureVault vault)throws Exception {
        JSONObject f=new JSONObject().put("target",form.target).put("method",form.method).put("author",form.author).put("parent",form.parent).put("thread",form.thread).put("subjectField",form.subjectField).put("editPostId",form.editPostId).put("initialMessage",form.initialMessage).put("initialSubject",form.initialSubject).put("hasMessage",form.hasMessage).put("verification",form.verification).put("threadLevel",form.threadLevel).put("newThread",form.newThread).put("values",new JSONObject(form.values));
        JSONObject limits=new JSONObject();for(Map.Entry<String,FormLimits.Rule> e:form.limits.entrySet()){FormLimits.Rule r=e.getValue();limits.put(e.getKey(),new JSONObject().put("label",r.label).put("max",r.max).put("min",r.min).put("required",r.required).put("multiline",r.multiline));}f.put("limits",limits);
        String json=new JSONObject().put("origin",origin).put("message",message).put("subject",subject).put("parent",parent).put("threadId",threadId).put("threadTitle",threadTitle).put("cursorStart",cursorStart).put("cursorEnd",cursorEnd).put("subjectStart",subjectStart).put("subjectEnd",subjectEnd).put("subjectFocused",subjectFocused).put("scrollY",scrollY).put("returnThread",returnThread).put("returnTag",returnTag).put("returnOffset",returnOffset).put("returnY",returnY).put("form",f).toString();
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>400000)throw new IllegalArgumentException("Composer state too large");return vault.seal(KEY,json);
    }
    public static ComposerState decode(SecureVault vault,String ciphertext)throws Exception {
        if(ciphertext==null||ciphertext.length()>600000)return null;JSONObject j=new JSONObject(vault.open(KEY,ciphertext)),f=j.getJSONObject("form");ComposerState s=new ComposerState();s.origin=ForumParser.origin(j.getString("origin")).toString();s.message=j.getString("message");s.subject=j.getString("subject");s.parent=j.getString("parent");s.threadId=j.getString("threadId");s.threadTitle=j.getString("threadTitle");s.cursorStart=j.optInt("cursorStart");s.cursorEnd=j.optInt("cursorEnd");s.subjectStart=j.optInt("subjectStart");s.subjectEnd=j.optInt("subjectEnd");s.subjectFocused=j.optBoolean("subjectFocused");s.scrollY=j.optInt("scrollY");s.returnThread=j.optString("returnThread","");s.returnTag=j.optString("returnTag","");s.returnOffset=j.optInt("returnOffset");s.returnY=j.optInt("returnY");
        s.form=new ForumParser.Form();s.form.target=ForumParser.safeUrl(s.origin,f.getString("target"));s.form.method=f.getString("method");s.form.author=f.getString("author");s.form.parent=f.getString("parent");s.form.thread=f.getString("thread");s.form.subjectField=f.getString("subjectField");s.form.editPostId=f.getString("editPostId");s.form.initialMessage=f.getString("initialMessage");s.form.initialSubject=f.getString("initialSubject");s.form.hasMessage=f.getBoolean("hasMessage");s.form.verification=f.getBoolean("verification");s.form.threadLevel=f.getBoolean("threadLevel");s.form.newThread=f.getBoolean("newThread");JSONObject values=f.getJSONObject("values");Iterator<String> keys=values.keys();while(keys.hasNext()){String k=keys.next();s.form.values.put(k,values.getString(k));}JSONObject limits=f.getJSONObject("limits");keys=limits.keys();while(keys.hasNext()){String k=keys.next();JSONObject r=limits.getJSONObject(k);s.form.limits.put(k,new FormLimits.Rule(r.getString("label"),r.getInt("max"),r.getInt("min"),r.getBoolean("required"),r.getBoolean("multiline")));}return s;
    }
}
