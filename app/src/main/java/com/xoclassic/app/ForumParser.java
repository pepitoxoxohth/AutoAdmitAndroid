package com.xoclassic.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.Safelist;
import org.jsoup.safety.Cleaner;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoField;

/** Pure parsing: no networking, script execution, or browser dependency. */
public final class ForumParser {
    private static final ZoneId DATE_ZONE=ZoneId.of("America/New_York");
    private static final DateTimeFormatter POST_DATE=new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM d, uuuu h:mm a").toFormatter(Locale.US);
    private static final DateTimeFormatter INDEX_DATE=new DateTimeFormatterBuilder().appendPattern("M/d/").appendValueReduced(ChronoField.YEAR,2,2,1970).toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern ORDINAL=Pattern.compile("(?i)(?<=\\d)(st|nd|rd|th)\\b"),SPACES=Pattern.compile("\\s+");
    private static final Pattern REPLY_COUNT=Pattern.compile("^\\(\\s*(\\d+)\\s*\\)$");
    private static final Pattern POST_LINK=Pattern.compile("^\\((https?://[^\\s]+)\\)$");
    private static final Pattern FORUM_LINK=Pattern.compile("^\\(https?://(?:www\\.)?(?:xoxohth|autoadmit)\\.com/thread\\.php\\?[^\\s]+\\)$");
    public static class ThreadItem {
        public String id, title, date = "", author = "", url;
        public int count;
        public long createdAt;
    }
    public static class Post {
        public String id, parent = "", author, date = "", html, text, editUrl = "";
        public boolean editable;
    }
    public static class ThreadPage {
        public String id, title, url;
        public long createdAt;
        public List<Post> posts = new ArrayList<>();
    }
    public static class Form {
        public String target, method, author = "", parent = "", thread = "", subjectField = "";
        public String editPostId="", initialMessage="", initialSubject="";
        public boolean hasMessage, verification, threadLevel, newThread;
        public Map<String,String> values = new LinkedHashMap<>();
        public Map<String,FormLimits.Rule> limits = new LinkedHashMap<>();
    }
    public static final class AliasOption {public String id,name;}
    public static final class AliasSettings {public Form create,choose;public int maxLength=50;public String current="";public List<AliasOption> options=new ArrayList<>();}
    private static Form aliasForm(Element element,String address){Form form=readForm(element,element,address);if(!form.method.equals("POST")||!URI.create(form.target).getPath().equals("/alias.php"))throw new IllegalArgumentException("The posting-name form changed. Open the website to check it.");Element image=element.selectFirst("input[type=image]:not([disabled])");if(image!=null){String prefix=image.attr("name");if(!prefix.isEmpty())prefix+=".";form.values.put(prefix+"x","1");form.values.put(prefix+"y","1");}return form;}
    public static AliasSettings aliases(String html,String address){
        Document doc=Jsoup.parse(html,address);if(doc.selectFirst("input[type=password]")!=null)throw new IllegalArgumentException("Sign in through Account to change your posting name.");if(doc.selectFirst("#captcha, input[name*=captcha], iframe[src*=captcha]")!=null)throw new IllegalArgumentException("Open the website to complete posting-name verification.");AliasSettings result=new AliasSettings();
        for(Element form:doc.select("form")){Element name=form.selectFirst("input[name=alias_name]");if(name!=null){result.create=aliasForm(form,address);result.maxLength=FormLimits.rule(result.create,"alias_name").max;}
            Element choices=form.selectFirst("select[name=alias_id]");if(choices!=null){result.choose=aliasForm(form,address);Set<String> ids=new HashSet<>();for(Element option:choices.select("option")){String id=option.attr("value"),label=option.text();if(!id.matches("[1-9][0-9]{0,19}")||label.isEmpty()||!ids.add(id))continue;AliasOption item=new AliasOption();item.id=id;item.name=label;result.options.add(item);if(option.hasAttr("selected"))result.current=label;}}}
        if(result.create==null&&result.choose==null)throw new IllegalArgumentException("The posting-name form is unavailable. Open the website to check it.");return result;
    }
    public static URI origin(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1 ||
            !("xoxohth.com".equals(uri.getHost()) || "autoadmit.com".equals(uri.getHost())))
            throw new IllegalArgumentException("Choose an approved XO website.");
        return URI.create("https://" + uri.getHost());
    }
    public static long createdAt(String text){
        String date=SPACES.matcher(ORDINAL.matcher(text.replace('\u00a0',' ')).replaceAll("")).replaceAll(" ").trim();
        // Board dates use slashes; try their format first instead of throwing once per row.
        if(date.indexOf('/')>=0)try{return LocalDate.parse(date,INDEX_DATE).atStartOfDay(DATE_ZONE).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return LocalDateTime.parse(date,POST_DATE).atZone(DATE_ZONE).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return LocalDate.parse(date,INDEX_DATE).atStartOfDay(DATE_ZONE).toInstant().toEpochMilli();}catch(Exception ignored){}
        return 0;
    }
    public static boolean recentAuthor(long created,long now){return created>0&&created<=now&&now-created<=7L*24*60*60*1000;}
    public static String safeUrl(String base, String href) {
        URI baseUri = origin(base);
        // Java URI.resolve treats a query-only reference differently from browsers.
        String cleanBase=base.split("#",2)[0];
        URI result = href.isEmpty()?URI.create(cleanBase):href.startsWith("?")?URI.create(cleanBase.split("\\?",2)[0]+href):URI.create(base).resolve(href);
        if (!baseUri.equals(origin(result.toString()))) throw new IllegalArgumentException("Unexpected website destination.");
        return result.toString();
    }
    public static String query(String address, String key) {
        try {
            String query = URI.create(address).getRawQuery();
            if (query == null) return "";
            for (String pair : query.split("&")) {
                String[] bits = pair.split("=", 2);
                if (URLDecoder.decode(bits[0], "UTF-8").equals(key)) return bits.length > 1 ? URLDecoder.decode(bits[1], "UTF-8") : "";
            }
        } catch (Exception ignored) { }
        return "";
    }
    public static List<ThreadItem> index(String html, String address) {
        Document doc = Jsoup.parse(html, address);
        Map<String,ThreadItem> result = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            String url;
            try { url = safeUrl(address, a.attr("href")); } catch (Exception ignored) { continue; }
            String id = query(url, "thread_id");
            Element row = a.closest("tr");
            if (!URI.create(url).getPath().equals("/thread.php") || !id.matches("\\d+") || row == null || result.containsKey(id) || a.text().trim().isEmpty()) continue;
            ThreadItem item = new ThreadItem(); item.id=id; item.title=a.text(); item.url=url;
            String count=query(url,"mc");
            try { item.count=Integer.parseInt(count); } catch (Exception ignored) { }
            Element cell=a.closest("td");
            if (cell != null && cell.nextElementSibling()!=null) {
                Element dateCell=cell.nextElementSibling();item.date=dateCell.text().trim();
                Element countCell=dateCell.nextElementSibling();
                if(countCell!=null){Matcher displayed=REPLY_COUNT.matcher(countCell.text().replace('\u00a0',' ').trim());if(displayed.matches())try{item.count=Integer.parseInt(displayed.group(1));}catch(NumberFormatException ignored){}}
            }
            item.createdAt=createdAt(item.date);
            result.put(id,item);
        }
        return new ArrayList<>(result.values());
    }
    /** Only OP identity/date; do not sanitize every reply just to label a board row. */
    public static ThreadPage threadIdentity(String html,String address){
        Document doc=Jsoup.parse(html,address);ThreadPage page=new ThreadPage();page.id=query(address,"thread_id");page.url=address;
        for(Element label:doc.select("b"))if(label.text().trim().equals("Author:")){
            Element table=label.closest("table");
            if(table==null||table.select("b").stream().filter(b->b.text().trim().equals("Author:")).count()!=1)return page;
            Post post=new Post();post.author=author(label);
            for(Element b:table.select("b"))if(b.text().trim().equals("Date:")){post.date=siblingsText(b);break;}
            page.createdAt=createdAt(post.date);if(!post.author.isEmpty()&&page.createdAt>0)page.posts.add(post);
            return page;
        }
        return page;
    }
    public static ThreadPage thread(String html, String address) {
        Document doc=Jsoup.parse(html,address); ThreadPage page=new ThreadPage();
        Cleaner cleaner=new Cleaner(Safelist.basic().addTags("div","tt"));
        page.id=query(address,"thread_id"); page.url=address;
        page.title=doc.title().replaceFirst("\\s*\\|\\s*AutoAdmit\\.com\\s*$", "").trim();
        Set<String> ids=new HashSet<>();
        for (Element label : doc.select("b")) {
            if (!label.text().trim().equals("Author:")) continue;
            Element table=label.closest("table");
            if(table==null || table.select("b").stream().filter(b->b.text().trim().equals("Author:")).count()!=1) continue;
            Post p=new Post(); p.author=author(label);
            for(Element font:table.select("font")) {
                Matcher match=POST_LINK.matcher(font.text().trim());
                if(!match.matches())continue;
                try {
                    URI uri=URI.create(match.group(1));
                    String host=uri.getHost()==null?"":uri.getHost().replaceFirst("^www\\.","");
                    if(!(host.equals("xoxohth.com")||host.equals("autoadmit.com")))continue;
                    if(!query(uri.toString(),"thread_id").equals(page.id))continue;
                    if(uri.getFragment()!=null && uri.getFragment().matches("\\d+"))p.id=uri.getFragment();
                }catch(Exception ignored){}
            }
            if(p.id==null) {
                Element prev=table.previousElementSibling();
                if(prev!=null && prev.id().matches("\\d+"))p.id=prev.id();
                Element anchor=table.closest("a[name]");
                if(p.id==null && anchor!=null && anchor.attr("name").matches("\\d+"))p.id=anchor.attr("name");
            }
            if(p.id==null||!ids.add(p.id)||p.author.isEmpty())continue;
            for(Element control:table.getAllElements()){
                if(control==label)break;
                if(!control.tagName().equals("a")||control.selectFirst("img[src$='imgs/Edit.gif'],img[src$='Edit.png'],img.edit")==null)continue;
                try{String edit=safeUrl(address,control.attr("href"));if(URI.create(edit).getPath().equals("/post.php")&&query(edit,"message_id").equals(p.id)&&query(edit,"thread_id").equals(page.id)){p.editable=true;p.editUrl=edit;}}catch(Exception ignored){}
            }
            for(Element a:table.select("a[href^='#']")) {
                if(a.selectFirst("img[src$=Parent.gif],img[src$=Parent.png]")!=null && a.attr("href").matches("#\\d+"))p.parent=a.attr("href").substring(1);
            }
            for(Element b:table.select("b"))if(b.text().trim().equals("Date:"))p.date=siblingsText(b);
            StringBuilder body=new StringBuilder(); boolean reached=false;
            for(Node n=label.nextSibling();n!=null;n=n.nextSibling()) {
                if(!reached){if(n.nodeName().equals("br"))reached=true;continue;}
                body.append(n.outerHtml());
            }
            Document content=Jsoup.parseBodyFragment(body.toString(),address);
            content.outputSettings().prettyPrint(false);
            content.select("script,style,form,iframe,object,embed,.xoxo-tools").remove();
            for(Element font:content.select("font"))if(FORUM_LINK.matcher(font.text().trim()).matches())font.remove();
            for(Element a:content.select("a[href]")) {
                String href=a.absUrl("href");
                if(href.startsWith("https://")||href.startsWith("http://"))a.attr("href",href);
                else a.removeAttr("href");
            }
            boolean transformedBlocks=content.selectFirst("pre")!=null;
            for(Element image:content.select("img[src]")) {
                // Inserted paragraph/link nodes inside phrasing markup need nesting normalization.
                if(image.closest("p,a,b,i,strong,em,span,font,tt,u,s,small,h1,h2,h3,h4,h5,h6")!=null)transformedBlocks=true;
                String src=image.absUrl("src");
                if(src.startsWith("https://")||src.startsWith("http://"))image.after(new Element("p").appendElement("a").attr("href",src).text("Open image"));
                image.remove();
            }
            // Android's HTML reader does not preserve preformatted whitespace itself.
            for(Element pre:new ArrayList<>(content.select("pre"))){
                String[] lines=pre.wholeText().replace("\r\n","\n").replace('\r','\n').split("\n",-1);Element block=new Element("p"),mono=new Element("tt");block.appendChild(mono);
                for(int line=0;line<lines.length;line++){if(line>0)mono.appendElement("br");StringBuilder spaced=new StringBuilder();int column=0;for(char character:lines[line].toCharArray()){if(character=='\t'){int spaces=8-column%8;for(int n=0;n<spaces;n++)spaced.append('\u00a0');column+=spaces;}else{spaced.append(character==' '?'\u00a0':character);column++;}}mono.appendText(spaced.toString());}
                pre.replaceWith(block);
            }
            // Image/pre conversion can insert block nodes needing an HTML normalization pass.
            // Ordinary messages already have a parsed DOM; sanitize it directly.
            Document clean=cleaner.clean(transformedBlocks?Jsoup.parseBodyFragment(content.body().html(),address):content);
            clean.outputSettings().prettyPrint(false);p.html=clean.body().html();
            p.text=clean.text(); page.posts.add(p);
        }
        if(page.title.isEmpty())page.title="Thread";
        if(!page.posts.isEmpty())page.createdAt=createdAt(page.posts.get(0).date);
        return page;
    }
    private static String siblingsText(Element label) {
        StringBuilder text=new StringBuilder();
        for(Node n=label.nextSibling();n!=null&&!n.nodeName().equals("br");n=n.nextSibling())text.append(n instanceof TextNode?((TextNode)n).text():n instanceof Element?((Element)n).text():"");
        return text.toString().replace('\u00a0',' ').trim();
    }
    private static String author(Element label) {
        StringBuilder text=new StringBuilder();
        for(Node n=label.nextSibling();n!=null&&!n.nodeName().equals("br");n=n.nextSibling()) {
            if(n instanceof Element && (((Element)n).is("a[href^='mailto:']" )||((Element)n).selectFirst("a[href^='mailto:']")!=null))break;
            text.append(n instanceof TextNode?((TextNode)n).text():n instanceof Element?((Element)n).text():"");
        }
        return text.toString().replace('\u00a0',' ').replaceFirst("\\s*\\(\\s*$","").trim();
    }
    public static boolean signedIn(String html) {
        Document doc=Jsoup.parse(html);
        return doc.selectFirst("input[type=password]")==null && doc.selectFirst("form")!=null &&
            Pattern.compile("you may create a new user\\s*name|change.*(?:user\\s*name|moniker)",Pattern.CASE_INSENSITIVE).matcher(doc.text()).find();
    }
    public static Form loginForm(String html,String address) {
        Document doc=Jsoup.parse(html,address);
        if(doc.select("input[type=password]").size()!=1 || doc.selectFirst("input[name=username]")==null || doc.select("form").size()!=1)
            throw new IllegalArgumentException("The login page changed. Open the website to check it.");
        Element nativeForm=doc.selectFirst("form");
        Form form=readForm(nativeForm,doc,address);
        if(!form.method.equals("POST")||!URI.create(form.target).getPath().equals("/login.php"))throw new IllegalArgumentException("Unexpected login form.");
        form.values.put("stay","1");
        return form;
    }
    public static Form composer(String html,String address,String expectedThread,String expectedParent) {
        Document doc=Jsoup.parse(html,address);
        Element nativeForm=doc.selectFirst("form[name=postform]");
        if(nativeForm==null)throw new IllegalArgumentException(doc.selectFirst("input[type=password]")!=null?"Sign in to post. Your draft is still here.":"The posting form is unavailable. Check the website.");
        Form form=readForm(nativeForm,nativeForm,address);
        if(!form.method.equals("POST")||!URI.create(form.target).getPath().equals("/post.php"))throw new IllegalArgumentException("Unexpected posting destination.");
        form.hasMessage=nativeForm.selectFirst("textarea[name=message]")!=null;
        if(form.hasMessage)form.initialMessage=nativeForm.selectFirst("textarea[name=message]").val();
        form.editPostId=form.values.getOrDefault("message_id","");
        if(!form.editPostId.isEmpty()&&!form.editPostId.matches("\\d+"))throw new IllegalArgumentException("Invalid edit target.");
        if(form.hasMessage&&!form.values.getOrDefault("action","").equals("post"))throw new IllegalArgumentException("Unexpected posting action.");
        form.thread=form.values.getOrDefault("parent_thread_id","");
        form.parent=form.values.getOrDefault("parent_message_id","");
        form.author=form.values.getOrDefault("txtAuthor","");
        if(!expectedThread.isEmpty() && !form.thread.equals(expectedThread))throw new IllegalArgumentException("The reply form does not match this thread.");
        if(!expectedParent.isEmpty()&&!form.parent.equals(expectedParent))throw new IllegalArgumentException("The reply form does not match this post.");
        if(!form.parent.isEmpty()&&!form.parent.matches("\\d+"))throw new IllegalArgumentException("Invalid reply target.");
        for(Element input:nativeForm.select("input[name]"))if(!input.attr("type").equalsIgnoreCase("hidden")&&Pattern.compile("^subj$|subject|topic|title",Pattern.CASE_INSENSITIVE).matcher(input.attr("name")).find()){form.subjectField=input.attr("name");break;}
        if(!form.subjectField.isEmpty())for(Element input:nativeForm.select("input[name]"))if(input.attr("name").equals(form.subjectField)){form.initialSubject=input.val();break;}
        Element email=nativeForm.selectFirst("input[name=poster_email]");if(email!=null)form.values.put("poster_email",email.val());
        form.verification=doc.selectFirst("#captcha, input[name*=captcha], iframe[src*=captcha]")!=null;
        return form;
    }
    private static Form readForm(Element formElement,Element controls,String address) {
        Form form=new Form(); form.target=safeUrl(address,formElement.attr("action"));
        form.method=formElement.attr("method").equalsIgnoreCase("post")?"POST":"GET";
        for(Element input:controls.select("input[name]")) {
            if(input.hasAttr("disabled"))continue;
            String type=input.attr("type").toLowerCase(Locale.ROOT);
            if(type.equals("hidden")||type.equals("submit"))form.values.putIfAbsent(input.attr("name"),input.attr("value"));
        }
        FormLimits.read(form,controls);
        form.values.remove("btnCancel"); form.values.remove("Cancel");
        return form;
    }
    /** Visible response text for the user's device only; never persists raw response HTML or form values. */
    public static String postingResponseNote(String html){
        Document doc=Jsoup.parse(html==null?"":html);
        doc.select("script,style,noscript,template,form,input,textarea,select,button,iframe,object,embed,head,nav,header,footer,[hidden],[aria-hidden=true]").remove();
        String text=doc.body()==null?"":doc.body().text().replaceAll("\\s+"," ").trim();
        if(text.isEmpty())return "The website returned no readable explanation.";
        return "Website response: "+(text.length()>600?text.substring(0,600)+"…":text);
    }
    private static String confirmationText(ThreadPage page,Post post){
        if(!page.posts.isEmpty()&&page.posts.get(0)==post&&post.parent.isEmpty()){
            Document body=Jsoup.parseBodyFragment(post.html==null?"":post.html);
            Node first=body.body().firstChild();while(first instanceof TextNode&&((TextNode)first).isBlank())first=first.nextSibling();
            if(first instanceof Element&&((Element)first).tagName().equals("b")&&((Element)first).text().equals("Subject:")&&siblingsText((Element)first).equals(page.title)){
                Node next=first.nextSibling();while(next!=null){Node following=next.nextSibling();boolean end=next.nodeName().equals("br");next.remove();if(end)break;next=following;}first.remove();return body.text();
            }
        }
        return post.text;
    }
    public static boolean confirms(ThreadPage page,Set<String> known,String parent,String author,String message) {
        // Match the text normalization used while reading HTML, without interpreting typed markup.
        String normalized=new Element("span").text(message).text().replaceAll("\\s+"," ").trim();
        for(Post p:page.posts)if(!known.contains(p.id)&&p.author.equals(author)&&p.parent.equals(parent.equals("0")?"":parent)&&confirmationText(page,p).replaceAll("\\s+"," ").trim().equals(normalized))return true;
        return false;
    }
}
