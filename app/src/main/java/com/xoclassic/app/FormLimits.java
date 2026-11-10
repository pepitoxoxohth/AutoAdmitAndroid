package com.xoclassic.app;

import java.util.*;
import org.jsoup.nodes.Element;

/** Advertised HTML limits, with observed fallbacks only for known single-line fields. */
public final class FormLimits {
    private FormLimits(){}
    public static final class Rule {
        public final String label;public final int max,min;public final boolean required,multiline;
        public Rule(String label,int max,int min,boolean required,boolean multiline){this.label=label;this.max=max;this.min=min;this.required=required;this.multiline=multiline;}
        public String error(String value,boolean complete){
            if(value==null)return label+" is missing.";
            if(complete&&required&&(value.isEmpty()||Arrays.asList("Title","Moniker","Account name").contains(label)&&value.trim().isEmpty()))return "Enter "+label.toLowerCase(Locale.ROOT)+".";
            for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c==0)return label+" contains an unsupported null character.";if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))return label+" contains an incomplete character. Re-enter it.";}else if(Character.isLowSurrogate(c))return label+" contains an incomplete character. Re-enter it.";}
            if(!multiline&&(value.indexOf('\r')>=0||value.indexOf('\n')>=0))return label+" must be on one line.";
            int count=length(value);if(max>=0&&count>max)return label+" is too long ("+count+" / "+max+"). Shorten it before sending.";
            if(complete&&!value.isEmpty()&&min>=0&&count<min)return label+" needs at least "+min+" characters.";
            return null;
        }
        public int length(String value){return (multiline?value.replace("\r\n","\n").replace('\r','\n'):value).length();}
        public void validate(String value){String error=error(value,true);if(error!=null)throw new IllegalArgumentException(error);}
    }
    public static Rule fallback(String name){
        if(name.equals("username"))return new Rule("Account name",100,-1,true,false);
        if(name.equals("password"))return new Rule("Password",50,-1,true,false);
        if(name.equals("alias_name"))return new Rule("Moniker",50,-1,true,false);
        if(name.equals("message"))return new Rule("Message",-1,-1,false,true);
        if(name.equals("poster_email"))return new Rule("Email",64,-1,false,false);
        if(name.matches("(?i)subj|.*subject.*|.*topic.*|.*title.*"))return new Rule("Title",80,-1,false,false);
        return new Rule("Field",-1,-1,false,false);
    }
    private static int attribute(Element e,String name,int fallback){try{int n=Integer.parseInt(e.attr(name).trim());return n>=0?n:fallback;}catch(NumberFormatException ignored){return fallback;}}
    public static void read(ForumParser.Form form,Element controls){
        for(Element e:controls.select("input[name],textarea[name]")){
            String type=e.attr("type").toLowerCase(Locale.ROOT),name=e.attr("name");
            if(e.hasAttr("disabled")||e.hasAttr("readonly")||Arrays.asList("hidden","submit","button","image","reset","checkbox","radio","file").contains(type))continue;
            Rule base=fallback(name);form.limits.put(name,new Rule(base.label,attribute(e,"maxlength",base.max),attribute(e,"minlength",base.min),e.hasAttr("required")||base.required,e.tagName().equals("textarea")));
        }
    }
    public static Rule rule(ForumParser.Form form,String field){return form.limits.getOrDefault(field,fallback(field));}
    public static void validate(ForumParser.Form form,Map<String,String> values){
        for(Map.Entry<String,Rule> entry:form.limits.entrySet())if(values.containsKey(entry.getKey())||entry.getValue().required)entry.getValue().validate(values.get(entry.getKey()));
    }
    public static String postSubject(ForumParser.Form form,String title){
        // The new-thread title is carried by hidden subj on the second step.
        // message_subject is a separate optional opening-post subject, not that title.
        return form.newThread&&form.subjectField.equalsIgnoreCase("message_subject")?"":title;
    }
    public static void posting(ForumParser.Form form,String title,String message){
        if(!form.subjectField.isEmpty())rule(form,form.subjectField).validate(postSubject(form,title));
        if(form.newThread&&title.trim().isEmpty())throw new IllegalArgumentException("Enter a title.");
        if(form.hasMessage||form.newThread)rule(form,"message").validate(message);
    }
    public static void login(String username,String password){fallback("username").validate(username);fallback("password").validate(password);}
}
