package com.xoclassic.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.jsoup.Jsoup;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded read lanes; session mutations remain exclusive on the activity executor. */
public final class ForumClient {
    public static class PostingException extends Exception {
        public final boolean uncertain;
        public PostingException(String message,boolean uncertain){super(message);this.uncertain=uncertain;}
    }
    public static class Submission {
        public ForumParser.ThreadPage thread;
        public ForumParser.Form next;
    }
    public final String origin;
    private final SecureVault vault;
    private final NativeHttp http;
    private final ThreadReadCache reads=new ThreadReadCache();
    private final ConcurrentHashMap<String,FutureTask<ThreadReadCache.Entry>> pendingReads=new ConcurrentHashMap<>();
    public boolean backgroundPaused(){return http.backgroundPaused();}
    private final SharedPreferences state;
    private JSONObject account;
    private JSONArray savedLogins;
    private boolean accountLoaded,autoBlocked;
    private long lastAutoAttempt,lastCheck;
    public ForumClient(Context context,String origin,SecureVault vault) {
        this.origin=ForumParser.origin(origin).toString();this.vault=vault;http=new NativeHttp(this.origin,vault);
        state=context.getSharedPreferences("session-status",Context.MODE_PRIVATE);
    }
    private JSONObject account() throws Exception {
        if(!accountLoaded){String value=vault.get(origin+":account");account=value==null?null:new JSONObject(value);accountLoaded=true;}
        return account;
    }
    public static final class SavedAccount {
        public final String username,nickname;public final boolean automatic;
        SavedAccount(String username,String nickname,boolean automatic){this.username=username;this.nickname=nickname;this.automatic=automatic;}
        public String displayName(){return nickname.isEmpty()?username:nickname;}
    }
    private JSONArray savedLoginRecords() throws Exception {
        if(savedLogins==null){String value=vault.get(origin+":accounts");savedLogins=value==null?new JSONArray():new JSONArray(value);JSONObject legacy=account();if(legacy!=null&&!legacy.optString("username","").isEmpty()&&!legacy.optString("password","").isEmpty()){boolean found=false;for(int i=0;i<savedLogins.length();i++)if(savedLogins.getJSONObject(i).optString("username").equalsIgnoreCase(legacy.optString("username")))found=true;if(!found){savedLogins.put(new JSONObject(legacy.toString()));vault.put(origin+":accounts",savedLogins.toString());}}}return savedLogins;
    }
    public List<SavedAccount> savedAccounts() throws Exception {
        List<SavedAccount> result=new ArrayList<>();JSONArray records=savedLoginRecords();for(int i=0;i<records.length();i++){JSONObject row=records.getJSONObject(i);String name=row.optString("username","");if(!name.isEmpty()&&!row.optString("password","").isEmpty())result.add(new SavedAccount(name,row.optString("nickname","").trim(),row.optBoolean("auto",true)));}result.sort(Comparator.comparing(SavedAccount::displayName,String.CASE_INSENSITIVE_ORDER).thenComparing(a->a.username,String.CASE_INSENSITIVE_ORDER));return result;
    }
    public void setSavedAccountNickname(String username,String value) throws Exception {
        String nickname=value==null?"":value.trim();if(nickname.length()>40||nickname.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a single-line nickname of up to 40 characters.");
        JSONArray rows=new JSONArray(savedLoginRecords().toString());JSONObject target=null;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optString("username").equalsIgnoreCase(username))target=row;else if(!nickname.isEmpty()&&row.optString("nickname","").equalsIgnoreCase(nickname))throw new IllegalArgumentException("That nickname is already used for this website. Choose another.");}
        if(target==null)throw new IllegalArgumentException("The saved account is no longer available.");
        if(nickname.isEmpty())target.remove("nickname");else target.put("nickname",nickname);
        vault.put(origin+":accounts",rows.toString());savedLogins=rows;
    }
    private JSONObject savedAccount(String username) throws Exception {JSONArray rows=savedLoginRecords();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optString("username").equalsIgnoreCase(username))return row;}throw new IllegalArgumentException("The saved account is no longer available.");}
    public void loginSavedAccount(String username,boolean remember,boolean automatic) throws Exception {JSONObject saved=savedAccount(username);login(saved.getString("username"),saved.getString("password"),remember,automatic);}
    private void clearActiveAccount(){reads.clear();account=null;accountLoaded=true;autoBlocked=false;vault.remove(origin+":account");http.clear();}
    public void removeSavedAccount(String username) throws Exception {
        JSONArray rows=savedLoginRecords(),remaining=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!row.optString("username").equalsIgnoreCase(username))remaining.put(row);}vault.put(origin+":accounts",remaining.toString());savedLogins=remaining;if(savedUsername().equalsIgnoreCase(username))clearActiveAccount();
    }
    public boolean hasSavedLogin() throws Exception {JSONObject saved=account();return saved!=null&&!saved.optString("username","").isEmpty()&&!saved.optString("password","").isEmpty();}
    public void loginSaved(boolean remember,boolean automatic) throws Exception {JSONObject saved=account();if(!hasSavedLogin())throw new IllegalArgumentException("Enter your account and password.");login(saved.getString("username"),saved.getString("password"),remember,automatic);}
    public String savedUsername() throws Exception {JSONObject a=account();return a==null?"":a.optString("username","");}
    public boolean autoEnabled() throws Exception {JSONObject a=account();return a!=null&&a.optBoolean("auto",true);}
    public boolean pendingPost(){return state.getBoolean(origin+":pending",false);}
    public void acknowledgePending(){state.edit().remove(origin+":pending").commit();}
    public void forget() throws Exception {String current=savedUsername();if(current.isEmpty())clearActiveAccount();else removeSavedAccount(current);}
    public boolean signedIn() throws Exception {
        return signedIn(http);
    }
    private boolean signedIn(NativeHttp transport) throws Exception {
        NativeHttp.Response response=transport.probe("/alias.php");lastCheck=System.currentTimeMillis();
        if(response.code>=300&&response.code<400)return false;
        if(ForumParser.signedIn(response.body))return true;
        if(Jsoup.parse(response.body).selectFirst("input[type=password]")!=null)return false;
        throw new Exception("Could not confirm the login state. Check the website.");
    }
    public boolean maintainSession() throws Exception {
        if(signedIn())return true;
        JSONObject saved=account();
        if(saved==null||!saved.optBoolean("auto",true)||autoBlocked||System.currentTimeMillis()-lastAutoAttempt<60000)return false;
        lastAutoAttempt=System.currentTimeMillis();
        try{performLogin(saved.getString("username"),saved.getString("password"));return true;}
        catch(java.io.IOException unavailable){throw new Exception("Could not reach the website. Automatic sign-in will retry later.");}
        catch(Exception error){autoBlocked=true;throw new Exception("Automatic sign-in stopped. Open Account to sign in again.");}
    }
    public void login(String username,String password,boolean remember,boolean automatic) throws Exception {
        FormLimits.login(username.trim(),password);
        if(username.trim().isEmpty()||password.isEmpty())throw new Exception("Enter your account and password.");
        JSONArray existing=savedLoginRecords();boolean previouslyBlocked=autoBlocked;autoBlocked=true;
        try{performLogin(username.trim(),password);}catch(Exception failure){autoBlocked=previouslyBlocked;throw failure;}
        JSONObject next=new JSONObject().put("username",username.trim()).put("password",password).put("auto",automatic);JSONArray updated=new JSONArray();for(int i=0;i<existing.length();i++){JSONObject row=existing.getJSONObject(i);if(!row.optString("username").equalsIgnoreCase(username.trim()))updated.put(row);else if(!row.optString("nickname","").isEmpty())next.put("nickname",row.optString("nickname"));}if(remember)updated.put(next);vault.put(origin+":accounts",updated.toString());savedLogins=updated;
        if(remember){vault.put(origin+":account",next.toString());account=next;}else{vault.remove(origin+":account");account=null;}accountLoaded=true;autoBlocked=false;
    }
    private void performLogin(String username,String password) throws Exception {
        FormLimits.login(username,password);
        reads.clear();
        // Keep the active account intact while adding/switching profiles. Only adopt a confirmed new session.
        NativeHttp attempt=http.freshSession();
        NativeHttp.Response loginPage=attempt.get("/login.php?forum_id=2&pft=1");
        if(Jsoup.parse(loginPage.body).selectFirst("#captcha, input[name*=captcha],iframe[src*=captcha]")!=null)throw new Exception("The website requires verification. Complete it on the website first.");
        ForumParser.Form form=ForumParser.loginForm(loginPage.body,loginPage.url);
        Map<String,String> values=new LinkedHashMap<>(form.values);values.put("username",username);values.put("password",password);values.put("stay","1");values.put("ref_page","main.php?forum_id=2");
        FormLimits.validate(form,values);attempt.submit(form,values);
        if(!signedIn(attempt))throw new Exception("Sign-in was not confirmed. Check your account and password.");
        http.adoptSession(attempt);
    }
    private ForumParser.AliasSettings aliasSettings() throws Exception {NativeHttp.Response response=http.get("/alias.php");return ForumParser.aliases(response.body,response.url);}
    public ForumParser.AliasSettings aliases() throws Exception {return aliases("");}
    public ForumParser.AliasSettings aliases(String knownThread) throws Exception {
        ForumParser.AliasSettings settings=aliasSettings();
        // The site often omits option[selected]. Read the current posting identity instead of guessing the first option.
        if(settings.current.isEmpty())try{String thread=aliasVerificationThread(knownThread);NativeHttp.Response response=http.get("/post.php?parent_thread_id="+thread+"&forum_id=2");org.jsoup.nodes.Element author=Jsoup.parse(response.body).selectFirst("input[name=txtAuthor]");if(author!=null)settings.current=author.val().trim();}catch(Exception unavailable){/* History and create controls remain available if current-name lookup fails. */}
        return settings;
    }
    private String aliasVerificationThread(String knownThread) throws Exception {
        if(knownThread!=null&&knownThread.matches("[0-9]+"))return knownThread;
        List<ForumParser.ThreadItem> board=index("",1);if(board.isEmpty())throw new IllegalArgumentException("No thread is available to verify the posting-name change. Open the website.");return board.get(0).id;
    }
    public ForumParser.AliasSettings changeAlias(boolean create,String value,String verificationThread) throws Exception {
        ForumParser.AliasSettings fresh=aliasSettings();ForumParser.Form form=create?fresh.create:fresh.choose;if(form==null)throw new IllegalArgumentException("The selected posting-name action is unavailable.");String expected="",input=value.trim();
        if(create){if(input.isEmpty()||input.length()>fresh.maxLength)throw new IllegalArgumentException("Enter a posting name of up to "+fresh.maxLength+" characters.");expected=input;}else{for(ForumParser.AliasOption option:fresh.options)if(option.id.equals(input))expected=option.name;if(expected.isEmpty())throw new IllegalArgumentException("Choose a previous posting name from the list.");}
        if(create)FormLimits.rule(form,"alias_name").validate(input);
        String thread=aliasVerificationThread(verificationThread);
        Map<String,String> fields=new LinkedHashMap<>(form.values);fields.put(create?"alias_name":"alias_id",input);FormLimits.validate(form,fields);NativeHttp.Response response;
        reads.clear();try{response=http.submit(form,fields);}catch(Exception uncertain){throw new IllegalArgumentException("Could not confirm the posting-name change. Check the website before trying again.");}
        try{ForumParser.AliasSettings updated;try{updated=ForumParser.aliases(response.body,response.url);}catch(IllegalArgumentException changed){updated=aliasSettings();}
        NativeHttp.Response composer=http.get("/post.php?parent_thread_id="+thread+"&forum_id=2");org.jsoup.nodes.Element author=Jsoup.parse(composer.body).selectFirst("input[name=txtAuthor]");if(author==null||!author.val().trim().equals(expected))throw new IllegalArgumentException("Could not confirm the posting-name change. Check the website before trying again.");
        updated.current=author.val();reads.clear();return updated;}catch(Exception uncertain){reads.clear();throw new IllegalArgumentException("Could not confirm the posting-name change. Check the website before trying again.");}
    }
    public List<ForumParser.ThreadItem> index(String search,int page) throws Exception {
        return index(search,page,"All");
    }
    public List<ForumParser.ThreadItem> index(String search,int page,String mode) throws Exception {
        String path="/main.php?forum_id=2&p="+Math.max(1,page);
        if(mode.equals("By you"))path+="&show=my";
        else if(mode.equals("Poasted in"))path+="&show=posted";
        else if(!mode.equals("All"))throw new IllegalArgumentException("Unknown board filter.");
        if(!search.trim().isEmpty())path+="&q="+URLEncoder.encode(search.trim(),"UTF-8");
        NativeHttp.Response response=http.get(path);
        List<ForumParser.ThreadItem> items=ForumParser.index(response.body,response.url);
        if(items.isEmpty()&&Jsoup.parse(response.body).selectFirst("input[type=password]")!=null)throw new Exception("Sign in through Account to view the board.");
        return items;
    }
    public ForumParser.ThreadPage thread(String id) throws Exception {
        reads.remove(id); // Refresh and posting/edit confirmation must always use fresh data.
        if(!id.matches("\\d+"))throw new Exception("Invalid thread.");
        NativeHttp.Response response=http.get("/thread.php?thread_id="+id+"&forum_id=2");
        ForumParser.ThreadPage page=ForumParser.thread(response.body,response.url);
        requireThreadIdentity(page,id);
        if(page.posts.isEmpty())throw new Exception("No readable posts were found. Sign in or open this thread on the website.");
        return page;
    }
    public ForumParser.ThreadPage author(String id,int count)throws Exception{
        return author(id,count,null);
    }
    public ForumParser.ThreadPage author(String id,int count,ReadCancellation cancellation)throws Exception{
        if(cancellation!=null)cancellation.check();
        if(!id.matches("\\d+"))throw new Exception("Invalid thread.");
        ThreadReadCache.Entry entry=sharedRead(id,count,cancellation,true);
        if(cancellation!=null)cancellation.check();
        ForumParser.ThreadPage page=checkedIdentity(entry.html,entry.url);requireThreadIdentity(page,id);return page;
    }
    private ThreadReadCache.Entry sharedRead(String id,int count,ReadCancellation cancellation,boolean store)throws Exception{
        ThreadReadCache.Entry cached=reads.get(id,count);if(cached!=null)return cached;
        String key=id+":"+count;
        FutureTask<ThreadReadCache.Entry> candidate=new FutureTask<>(()->{
            ThreadReadCache.Entry available=reads.get(id,count);if(available!=null)return available;
            String path="/thread.php?thread_id="+id+"&forum_id=2";
            NativeHttp.Response response=cancellation==null?http.get(path):http.getBackground(path,cancellation);
            if(cancellation!=null)cancellation.check();if(store)reads.put(id,response.body,response.url,count);
            return new ThreadReadCache.Entry(response.body,response.url,count,System.nanoTime()/1000000);
        });
        FutureTask<ThreadReadCache.Entry> active=pendingReads.putIfAbsent(key,candidate);boolean owner=active==null;if(owner)active=candidate;
        try{if(owner)active.run();return active.get();}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new Exception("Thread request failed.",cause);}
        finally{if(owner)pendingReads.remove(key,active);}
    }
    private ForumParser.ThreadPage checkedIdentity(String html,String url)throws Exception{
        ForumParser.ThreadPage page=ForumParser.threadIdentity(html,url);if(page.posts.isEmpty())throw new Exception("Original poster unavailable.");return page;
    }
    private static void requireThreadIdentity(ForumParser.ThreadPage page,String expected)throws Exception{
        if(!expected.equals(page.id))throw new Exception("The website returned a different thread. Nothing was opened or sent. Try refreshing the board.");
    }
    public ForumParser.ThreadPage openThread(String id,int count)throws Exception{
        if(!id.matches("\\d+"))throw new Exception("Invalid thread.");
        ThreadReadCache.Entry entry=sharedRead(id,count,null,false);
        ForumParser.ThreadPage page=ForumParser.thread(entry.html,entry.url);
        requireThreadIdentity(page,id);
        if(page.posts.isEmpty())throw new Exception("No readable posts were found. Sign in or open this thread on the website.");
        return page;
    }
    public ForumParser.Form prepare(String thread,String parent,boolean newThread) throws Exception {
        if(!maintainSession())throw new Exception("Sign in through Account before posting. Your draft is still here.");
        if(pendingPost())throw new Exception("A previous post was not confirmed. Check that thread and acknowledge it before posting again.");
        String path=newThread?"/post.php?thread=yes&forum_id=2&hid=0":parent.isEmpty()?"/post.php?parent_thread_id="+thread+"&forum_id=2":"/post.php?parent_message_id="+parent+"&forum_id=2";
        NativeHttp.Response response=http.get(path);
        ForumParser.Form form=ForumParser.composer(response.body,response.url,newThread?"":thread,parent);
        if(!form.editPostId.isEmpty())throw new Exception("The site returned an edit form instead of a reply. Nothing was sent.");
        form.threadLevel=!newThread&&parent.isEmpty();
        form.newThread=newThread;
        if(!newThread&&!form.hasMessage)throw new Exception("The reply form is missing its message box. Check the website; no new thread was created.");
        if(!newThread&&parent.isEmpty()&&!form.parent.isEmpty()&&!form.parent.equals("0")){
            ForumParser.ThreadPage target=thread(thread);
            if(target.posts.isEmpty()||!form.parent.equals(target.posts.get(0).id))throw new Exception("The reply form does not match this thread's root post.");
        }
        if(form.author.isEmpty()&&form.hasMessage)throw new Exception("Open the website to choose your posting name, then refresh Account.");
        if(form.verification)throw new Exception("This posting form requires website verification. Open it on the website.");
        return form;
    }
    public Submission createThread(ForumParser.Form form,String subject,String message)throws Exception {
        FormLimits.posting(form,subject,message);
        if(!form.newThread||!form.editPostId.isEmpty())throw new IllegalArgumentException("Invalid new-thread form. Nothing was sent.");
        if(subject.trim().isEmpty())throw new IllegalArgumentException("Enter a title.");
        ForumParser.Form finalForm=form;
        if(!form.hasMessage){
            Submission title=post(form,subject,"","");
            finalForm=title.next;
            if(finalForm==null||!finalForm.newThread||!finalForm.hasMessage||!finalForm.editPostId.isEmpty()||!finalForm.thread.matches("|0")||!finalForm.parent.matches("|0"))throw new Exception("The website did not return a new-thread message form. Your draft is still here. Nothing was posted.");
        }
        return post(finalForm,subject,message,"");
    }
    public Submission post(ForumParser.Form form,String subject,String message,String threadId) throws Exception {
        FormLimits.posting(form,subject,message);
        if(!form.editPostId.isEmpty())throw new IllegalArgumentException("Use Save changes to edit an existing post.");
        if(pendingPost())throw new PostingException("Check your previous post before trying again.",true);
        if(form.verification)throw new Exception("Complete website verification before posting.");
        Set<String> known=new HashSet<>();
        if(!threadId.isEmpty())for(ForumParser.Post p:thread(threadId).posts)known.add(p.id);
        Map<String,String> values=new LinkedHashMap<>(form.values);
        if(form.hasMessage){values.put("message",message);values.put("poster_email","");}
        if(!form.subjectField.isEmpty())values.put(form.subjectField,FormLimits.postSubject(form,subject));
        FormLimits.validate(form,values);
        if(form.hasMessage&&!state.edit().putBoolean(origin+":pending",true).commit())throw new Exception("Could not save posting state. Nothing was sent.");
        boolean sent=false;
        try {
            sent=true;NativeHttp.Response response=http.submit(form,values);
            if(Jsoup.parse(response.body).selectFirst("form[name=postform]")!=null){
                if(!form.hasMessage){
                    Submission submission=new Submission();submission.next=ForumParser.composer(response.body,response.url,"","");submission.next.newThread=form.newThread;submission.next.threadLevel=form.threadLevel;return submission;
                }
                // Inline composers on thread pages still require normal confirmation and,
                // if necessary, one fresh read. An unconfirmed thread must keep its guard.
                if(!"/thread.php".equals(URI.create(response.url).getPath())){
                    acknowledgePending();throw new PostingException("The site returned the posting form. Your message was not confirmed; your draft is still here.",false);
                }
            }
            if(!form.hasMessage)throw new PostingException("The title step did not return a composer. Check the website before continuing.",false);
            String resultId=threadId.isEmpty()?ForumParser.query(response.url,"thread_id"):threadId;
            if(!resultId.matches("\\d+"))throw new PostingException("Posting was not confirmed. Check the board before trying again.\n\n"+ForumParser.postingResponseNote(response.body),true);
            ForumParser.ThreadPage page=ForumParser.thread(response.body,response.url);
            String expectedParent=form.threadLevel?"":form.parent;
            if(!page.id.equals(resultId)||!ForumParser.confirms(page,known,expectedParent,form.author,message))page=thread(resultId);
            if(!page.id.equals(resultId)||!ForumParser.confirms(page,known,expectedParent,form.author,message))throw new PostingException("Posting was not confirmed. Check the thread before trying again.",true);
            acknowledgePending();Submission submission=new Submission();submission.thread=page;return submission;
        } catch(PostingException error){throw error;}
        catch(Exception error){if(sent&&form.hasMessage)throw new PostingException("Posting was not confirmed. Check the thread before trying again.",true);throw error;}
    }
    private ForumParser.Form readEditForm(ForumParser.ThreadPage page,String id)throws Exception {
        ForumParser.Post target=null;for(ForumParser.Post post:page.posts)if(post.id.equals(id)){target=post;break;}
        if(target==null||!target.editable||target.editUrl.isEmpty())throw new Exception("The site no longer allows editing this post.");
        NativeHttp.Response response=http.get(target.editUrl);
        ForumParser.Form form=ForumParser.composer(response.body,response.url,page.id,"");
        if(!form.hasMessage||!form.editPostId.equals(id))throw new Exception("The edit form does not match this post. Nothing was saved.");
        if(form.verification)throw new Exception("This edit form requires website verification.");
        return form;
    }
    public ForumParser.Form prepareEdit(String threadId,String postId)throws Exception {
        if(!threadId.matches("\\d+")||!postId.matches("\\d+"))throw new IllegalArgumentException("Invalid edit target.");
        if(!maintainSession())throw new Exception("Sign in through Account before editing.");
        if(pendingPost())throw new Exception("A previous change was not confirmed. Check it before editing again.");
        return readEditForm(thread(threadId),postId);
    }
    private static String lineEndings(String text){return text.replace("\r\n","\n").replace('\r','\n');}
    public Submission saveEdit(ForumParser.Form original,String subject,String message,String threadId)throws Exception {
        FormLimits.posting(original,subject,message);
        if(original.editPostId.isEmpty()||!original.thread.equals(threadId))throw new IllegalArgumentException("Invalid edit target.");
        ForumParser.Form fresh=prepareEdit(threadId,original.editPostId);
        if(!lineEndings(fresh.initialMessage).equals(lineEndings(original.initialMessage))||!fresh.initialSubject.equals(original.initialSubject))throw new Exception("This post changed since you opened the editor. Reopen it before saving. Your draft is still here.");
        FormLimits.posting(fresh,subject,message);
        Map<String,String> values=new LinkedHashMap<>(fresh.values);values.put("message",message);if(!fresh.subjectField.isEmpty())values.put(fresh.subjectField,subject);
        FormLimits.validate(fresh,values);
        if(!state.edit().putBoolean(origin+":pending",true).commit())throw new Exception("Could not save editing state. Nothing was sent.");
        try {
            http.submit(fresh,values);
            ForumParser.ThreadPage updated=thread(threadId);ForumParser.Form confirmed=readEditForm(updated,fresh.editPostId);
            if(!lineEndings(confirmed.initialMessage).equals(lineEndings(message))||!confirmed.initialSubject.equals(subject))throw new PostingException("Saving was not confirmed. Check your post before trying again. Your draft is still here.",true);
            acknowledgePending();Submission submission=new Submission();submission.thread=updated;return submission;
        }catch(PostingException error){throw error;}
        catch(Exception error){throw new PostingException("Saving was not confirmed. Check your post before trying again. Your draft is still here.",true);}
    }
}
