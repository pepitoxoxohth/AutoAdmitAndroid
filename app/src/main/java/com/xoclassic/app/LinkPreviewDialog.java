package com.xoclassic.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native previews, hosted players, and a separate website fallback for unavailable previews. */
public final class LinkPreviewDialog extends Dialog {
    private static final int BG=0xff292b30,PANEL=0xff34383f,INK=0xffe1dfdc,MUTED=0xffa8adb5,BLUE=0xff9cbce3;
    private final Activity activity;
    private final String original;
    private final Consumer<String> link;
    private Consumer<String> linkHold;
    public void setLinkHold(Consumer<String> callback){linkHold=callback;}
    private final PreviewFetch fetch;
    private final ExecutorService jobs=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<WebView> webViews=new ArrayList<>();
    private final ArrayList<File> files=new ArrayList<>();
    private final ArrayList<ImageSlot> images=new ArrayList<>();
    private LinearLayout root,stage,controls;
    private TextView title,status,counter;
    private Button fileButton;
    private ZoomImageView zoom;
    private VideoView video;
    private WebView embed;
    private PreviewResolver.Page page;
    private ArticleExtractor extractor;
    private ScrollView articleScroll;
    private volatile boolean closed;
    private boolean manualEmbed,browserMode,browserFallbackUsed;
    private String embedFitMode="contain";
    private boolean mobileEmbedPost;
    private int index,version,articleImageScale=100;
    private TextView imageScaleLabel;
    private PdfRenderer pdf;
    private Drawable currentDrawable;
    private static class ImageSlot {FrameLayout holder;ImageView image;TextView hint;String url,alt;boolean loading,loaded;int generation;Drawable drawable;WebView svg;}
    public LinkPreviewDialog(Activity activity,String url,Consumer<String> link){this(activity,url,link,new PreviewFetch());}
    public LinkPreviewDialog(Activity activity,String url,Consumer<String> link,PreviewFetch fetch){super(activity,R.style.AppTheme);this.activity=activity;this.original=url;this.link=link;this.fetch=fetch;fetch.setRetryListener(()->handler.post(()->{if(!closed&&!manualEmbed){status.setVisibility(View.VISIBLE);status.setText("The site is slow · retrying once with more time…");}}));}
    private int sweepIndex,sweepTotal;private Runnable sweepPrevious,sweepNext,sweepPost;
    public void setSweep(int index,int total,Runnable previous,Runnable next,Runnable post){sweepIndex=index;sweepTotal=total;sweepPrevious=previous;sweepNext=next;sweepPost=post;}
    public void open(){AppLock.attach(this);show();Window window=getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(AppColors.color(BG)));window.setStatusBarColor(AppColors.color(BG));window.setNavigationBarColor(AppColors.color(BG));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}AppColors.window(window);resize();load();}
    public void resize(){Window window=getWindow();if(window!=null)window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);}
    public void pauseMedia(){if(video!=null&&video.isPlaying())video.pause();for(WebView view:webViews)view.onPause();}
    public void resumeMedia(){for(WebView view:webViews)view.onResume();}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable box(){GradientDrawable b=new GradientDrawable();AppColors.fill(b,PANEL);b.setCornerRadius(dp(9));return b;}
    private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);AppColors.text(v,color);v.setTextSize(size);v.setTypeface(Typeface.SERIF);return v;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);AppColors.text(b,BLUE);b.setTextSize(14);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(7),0,dp(7),0);b.setBackground(box());b.setOnClickListener(v->action.run());return b;}
    private Button add(LinearLayout r,String label,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));Button b=button(label,action);r.addView(b,p);return b;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);root=column();root.setPadding(dp(10),dp(10),dp(10),dp(10));AppColors.background(root,BG);LinearLayout header=row();title=text("Preview",19,INK);title.setMaxLines(2);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));fileButton=button("File",()->{if(page!=null&&!page.media.isEmpty())external(page.media.get(index).url);});fileButton.setMinimumWidth(dp(48));fileButton.setVisibility(View.GONE);LinearLayout.LayoutParams fileSpace=new LinearLayout.LayoutParams(-2,dp(44));fileSpace.setMargins(0,0,dp(6),0);header.addView(fileButton,fileSpace);Button close=button("×",this::dismiss);close.setTag("preview-close");close.setContentDescription("Close preview");close.setTextSize(26);header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);String host;try{host=URI.create(original).getHost();}catch(Exception e){host="Link";}TextView site=text(host==null?"Link":host,12,MUTED);site.setPadding(0,dp(4),0,dp(4));root.addView(site);if(sweepTotal>0){LinearLayout navigation=row();navigation.setTag("sweep-navigation");Button previous=button("‹",sweepPrevious),next=button("›",sweepNext);previous.setContentDescription("Previous thread media link");next.setContentDescription("Next thread media link");previous.setEnabled(sweepTotal>1);next.setEnabled(sweepTotal>1);navigation.addView(previous,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView position=text("Link "+(sweepIndex+1)+" / "+sweepTotal,13,MUTED);position.setGravity(Gravity.CENTER);navigation.addView(position,new LinearLayout.LayoutParams(0,dp(44),1));navigation.addView(next,new LinearLayout.LayoutParams(dp(44),dp(44)));Button go=button("Go to post",sweepPost);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-2,dp(44));space.setMargins(dp(7),0,0,0);navigation.addView(go,space);root.addView(navigation);}status=text("Loading preview…",14,MUTED);status.setPadding(0,dp(5),0,dp(8));root.addView(status);stage=column();stage.setTag("preview-stage");root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));controls=row();root.addView(controls);LinearLayout footer=row();add(footer,"Retry",()->{fetch.invalidateMedia(original);if(page!=null){fetch.invalidateMedia(page.url);for(PreviewResolver.Media media:page.media)fetch.invalidateMedia(media.url);}for(ImageSlot slot:images)fetch.invalidateMedia(slot.url);dismiss();link.accept(original);});add(footer,"Browser",this::showBrowser);add(footer,"Open original",()->external(original));String imgurViewer=PreviewResolver.imgurAlbumViewer(original);if(!imgurViewer.isEmpty())add(footer,"Imgur viewer",()->{manualEmbed=true;if(page==null)page=new PreviewResolver.Page();page.title="Imgur viewer";showEmbed(imgurViewer);});root.addView(footer);setContentView(root);}
    private interface Work<T>{T get()throws Exception;}
    private interface Done<T>{void done(T value);}
    private <T> void async(Work<T> work,Done<T> done){async(work,done,this::error);}
    private <T> void async(Work<T> work,Done<T> done,Consumer<String> failure){if(closed)return;int requestVersion=version;jobs.execute(()->{if(closed)return;try{T value=work.get();handler.post(()->{if(!closed&&requestVersion==version){try{done.done(value);}catch(Exception e){failure.accept("Preview unavailable. Use Open original.");}}else if(value instanceof AutoCloseable)try{((AutoCloseable)value).close();}catch(Exception ignored){}});}catch(Exception e){handler.post(()->{if(!closed&&requestVersion==version)failure.accept(e.getMessage());});}catch(OutOfMemoryError e){handler.post(()->{if(!closed&&requestVersion==version)failure.accept("This media is too large for the preview. Use Open original.");});}});}
    private void error(String message){
        if(closed||browserMode)return;
        if(page!=null&&index>=0&&index<page.media.size())fetch.invalidateMedia(page.media.get(index).url);
        if(!browserFallbackUsed){try{publisherUrl(original);browserFallbackUsed=true;showBrowser();return;}catch(Exception invalid){}}
        status.setVisibility(View.VISIBLE);status.setText(message==null?"Preview unavailable. Use Open original.":message);
    }
    private void ready(){status.setVisibility(View.GONE);}
    private void load(){int resolvingVersion=version;Runnable deadline=()->{if(!closed&&!manualEmbed&&version==resolvingVersion)error("Preview request took too long. Opening website…");};handler.postDelayed(deadline,25000);async(()->new PreviewResolver(fetch).resolve(original),result->{handler.removeCallbacks(deadline);if(manualEmbed)return;page=result;if(result.kind.equals("article")){extractor=new ArticleExtractor();status.setText("Reading article…");int readingVersion=version;extractor.extract(activity,result.html,result.url,result.provider,(article,error)->{page.html="";if(closed||readingVersion!=version||manualEmbed)return;if(error!=null){error(error);return;}if(!manualEmbed)showArticle(article);});}else if(result.kind.equals("embed")){showEmbed(result.media.get(0).url);}else if(result.kind.equals("pdf")){loadPdf();}else{showGallery();}},message->{handler.removeCallbacks(deadline);error(message);});}
    private void clearMedia(){browserMode=false;version++;if(fileButton!=null)fileButton.setVisibility(View.GONE);if(video!=null){video.stopPlayback();video=null;}if(embed!=null){embed.stopLoading();webViews.remove(embed);embed.destroy();embed=null;}if(Build.VERSION.SDK_INT>=28&&currentDrawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)currentDrawable).stop();currentDrawable=null;
        for(ImageSlot slot:images){slot.generation++;if(Build.VERSION.SDK_INT>=28&&slot.drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)slot.drawable).stop();if(slot.image!=null)slot.image.setImageDrawable(null);slot.drawable=null;if(slot.svg!=null){webViews.remove(slot.svg);if(slot.svg.getParent() instanceof ViewGroup)((ViewGroup)slot.svg.getParent()).removeView(slot.svg);slot.svg.stopLoading();slot.svg.destroy();slot.svg=null;}}
        images.clear();articleScroll=null;imageScaleLabel=null;articleImageScale=100;stage.removeAllViews();controls.removeAllViews();zoom=null;
    }
    private void showGallery(){clearMedia();if(page.media.isEmpty()){error("No media was returned. Use Open original.");return;}fileButton.setVisibility(View.VISIBLE);int token=version;PreviewResolver.Media media=page.media.get(index);title.setText(media.title.isEmpty()?(page.media.size()>1?"Album":"Media"):media.title);status.setVisibility(View.VISIBLE);status.setText("Loading "+media.type+"…");
        if(page.media.size()>1){add(controls,"‹",()->{index=Math.max(0,index-1);showGallery();}).setEnabled(index>0);counter=text((index+1)+"/"+page.media.size(),14,INK);counter.setGravity(Gravity.CENTER);controls.addView(counter,new LinearLayout.LayoutParams(dp(54),dp(44)));add(controls,"›",()->{index=Math.min(page.media.size()-1,index+1);showGallery();}).setEnabled(index<page.media.size()-1);}
        if(media.type.equals("video")||media.type.equals("audio")){loadPlayer(media,token);return;}
        zoom=new ZoomImageView(activity);zoom.setContentDescription(media.title.isEmpty()?"Image preview":media.title);if(page.media.size()>1)zoom.setOnSwipe(direction->{int next=index+direction;if(next>=0&&next<page.media.size()){index=next;showGallery();}});stage.addView(zoom,new LinearLayout.LayoutParams(-1,-1));add(controls,"−",()->{if(zoom!=null)zoom.zoomBy(.75f);else if(embed!=null)embed.zoomOut();});add(controls,"Fit",()->{if(zoom!=null)zoom.fit();else if(embed!=null)embed.zoomBy(activity.getResources().getDisplayMetrics().density/embed.getScale());});add(controls,"+",()->{if(zoom!=null)zoom.zoomBy(1.35f);else if(embed!=null)embed.zoomIn();});
        async(()->mediaData(media,24000000),data->{if(token!=version)return;if(isSvg(data)){zoom=null;stage.removeAllViews();embed=svg(data.text());stage.addView(embed,new LinearLayout.LayoutParams(-1,-1));ready();return;}async(()->decode(data.bytes),drawable->{if(token!=version)return;currentDrawable=drawable;zoom.setImageDrawable(drawable);start(drawable);ready();});});
    }
    private PreviewFetch.Result mediaData(PreviewResolver.Media media,int limit)throws Exception{if(page!=null&&page.bytes!=null&&page.media.size()==1&&media.url.equals(page.media.get(0).url)){PreviewFetch.Result data=new PreviewFetch.Result();data.url=media.url;data.type=page.mime;data.bytes=page.bytes;page.bytes=null;return data;}return fetch.get(media.url,limit);}
    private Drawable decode(byte[] data)throws Exception{return decode(data,2048);}
    private Drawable decode(byte[] data,int max)throws Exception{
        if(Build.VERSION.SDK_INT>=28)try{return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)),(decoder,info,source)->{int width=info.getSize().getWidth(),height=info.getSize().getHeight();float scale=Math.min(1f,(float)max/Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});}catch(IOException|RuntimeException unsupported){/* Some valid JPEGs fail ImageDecoder; try Android's legacy bitmap decoder. */}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<=0)throw new Exception("This image format cannot be displayed. Use Open original.");options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>max)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)throw new Exception("This image cannot be displayed here. Use Open original.");return new BitmapDrawable(activity.getResources(),bitmap);
    }
    private void start(Drawable drawable){if(Build.VERSION.SDK_INT>=28&&drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)drawable).start();}
    private static boolean isSvg(PreviewFetch.Result data){String beginning=new String(data.bytes,0,Math.min(data.bytes.length,512),java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);return data.type.toLowerCase(Locale.ROOT).contains("svg")||beginning.contains("<svg");}
    private WebView svg(String source){
        if(source.length()>2000000)throw new IllegalArgumentException("This SVG is too large. Use Open original.");
        org.jsoup.nodes.Document document=Jsoup.parse(source,"",Parser.xmlParser());document.select("script,foreignObject").remove();
        for(Element element:document.getAllElements())for(org.jsoup.nodes.Attribute attr:new ArrayList<>(element.attributes().asList()))if(attr.getKey().toLowerCase(Locale.ROOT).startsWith("on"))element.removeAttr(attr.getKey());
        Element graphic=document.selectFirst("svg");if(graphic==null)throw new IllegalArgumentException("No SVG image was returned. Use Open original.");
        if(!graphic.hasAttr("viewBox")){String width=graphic.attr("width").replaceFirst("px$",""),height=graphic.attr("height").replaceFirst("px$","");if(width.matches("[0-9]+(?:\\.[0-9]+)?")&&height.matches("[0-9]+(?:\\.[0-9]+)?"))graphic.attr("viewBox","0 0 "+width+" "+height);}
        graphic.attr("width","100%");graphic.attr("height","100%");
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,minimum-scale=0.25,maximum-scale=8,user-scalable=yes'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><title>SVG preview</title><style>html,body{margin:0;width:100%;height:100%;background:"+AppColors.hex(AppColors.color(BG))+"}svg{display:block;width:100%;height:100%}</style></head><body>"+graphic.outerHtml()+"</body></html>";
        byte[] content=html.getBytes(java.nio.charset.StandardCharsets.UTF_8);String address="https://preview.invalid/vector";WebView view=web(false);
        view.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){if(r.isForMainFrame()&&r.getMethod().equals("GET")&&r.getUrl().toString().equals(address))return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(content));return blocked();}
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError failure){if(r.isForMainFrame()&&embed==w)error("This SVG could not be displayed. Use Retry or Open original.");}
        });
        view.loadUrl(address);return view;
    }
    private File cached(byte[] bytes,String suffix)throws Exception{if(closed)throw new IOException("Preview closed.");File file=File.createTempFile("link-preview-",suffix,activity.getCacheDir());synchronized(files){files.add(file);}try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}if(closed){file.delete();throw new IOException("Preview closed.");}return file;}
    private void loadPlayer(PreviewResolver.Media media,int token){async(()->cached(mediaData(media,64000000).bytes,media.type.equals("audio")?".audio":".video"),file->{if(token!=version)return;video=new VideoView(activity);VideoZoomFrame videoFrame=new VideoZoomFrame(activity);videoFrame.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));if(media.type.equals("video")){add(controls,"−",()->videoFrame.zoomBy(.75f));add(controls,"Fit",videoFrame::fit);add(controls,"+",()->videoFrame.zoomBy(1.35f));}stage.addView(videoFrame,new LinearLayout.LayoutParams(-1,0,1));MediaController player=new MediaController(activity);player.setAnchorView(video);video.setMediaController(player);video.setOnPreparedListener(mp->{if(closed||token!=version)return;ready();video.seekTo(1);player.show(0);});video.setOnErrorListener((mp,what,extra)->{if(closed||token!=version)return true;error("This media cannot be played here. Use Open original.");return true;});video.setVideoPath(file.getAbsolutePath());LinearLayout play=row();add(play,"Play / Pause",()->{if(video==null)return;if(video.isPlaying())video.pause();else video.start();});stage.addView(play);});}
    private WebView web(boolean javascript){WebView view=new WebView(activity);WebSettings s=view.getSettings();s.setJavaScriptEnabled(javascript);s.setDomStorageEnabled(javascript);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setSafeBrowsingEnabled(true);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);AppColors.background(view,BG);view.setDownloadListener((a,b,c,d,e)->error("Use Open original to download this file."));view.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){request.deny();}@Override public void onReceivedTitle(WebView view,String title){if(!closed&&!browserMode&&embed==view&&EmbedFailureWatch.TITLE.equals(title))error("The embedded video could not play. Opening its website.");}});CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);webViews.add(view);return view;}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private boolean sameEmbedSite(String first,String second){return PreviewResolver.sameEmbedSite(first,second);}
    private boolean fittingEmbed;
    private void fitPost(WebView view,String mode){
        if(closed||embed!=view)return;embedFitMode=mode;if(fittingEmbed)return;fittingEmbed=true;
        // Let WebView finish its own rotation/viewport scale adjustment first.
        view.postDelayed(()->{
            if(closed||embed!=view){fittingEmbed=false;return;}
            view.evaluateJavascript("document.documentElement.clientWidth",raw->{
                if(closed||embed!=view){fittingEmbed=false;return;}
                try{float cssWidth=Float.parseFloat(raw);if(cssWidth>0&&view.getScale()>0)view.zoomBy((view.getWidth()/cssWidth)/view.getScale());}catch(Exception ignored){}
                view.postDelayed(()->{
                    fittingEmbed=false;if(closed||embed!=view)return;
                    float naturalWidth=view.getWidth()/activity.getResources().getDisplayMetrics().density;
                    view.scrollTo(0,0);view.evaluateJavascript("window.scrollTo(0,0);window.xoFitPost&&window.xoFitPost("+JSONObject.quote(embedFitMode)+","+naturalWidth+","+mobileEmbedPost+")",null);
                },150);
            });
        },400);
    }
    /** Explicit publisher browsing; WebView sessions never receive the forum's native cookie jar. */
    private void showBrowser(){
        manualEmbed=true;if(page==null)page=new PreviewResolver.Page();if(extractor!=null){extractor.close();extractor=null;}clearMedia();browserMode=true;browserFallbackUsed=true;
        title.setText("Website");status.setVisibility(View.VISIBLE);status.setText("Loading website…");
        WebView browser=web(true);embed=browser;
        add(controls,"‹",()->{if(browser.canGoBack())browser.goBack();});
        add(controls,"Stop",browser::stopLoading);
        add(controls,"Reader",()->readBrowser(browser));add(controls,"−",browser::zoomOut);add(controls,"+",browser::zoomIn);
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){try{publisherUrl(request.getUrl().toString());return false;}catch(Exception e){return true;}}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){try{String safe=publisherUrl(request.getUrl().toString());for(java.net.InetAddress ip:java.net.InetAddress.getAllByName(URI.create(safe).getHost()))if(!PreviewFetch.publicAddress(ip))return blocked();return null;}catch(Exception e){return blocked();}}
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){if(closed||embed!=view)return;status.setVisibility(View.VISIBLE);try{status.setText("Loading · "+URI.create(publisherUrl(url)).getHost());}catch(Exception e){view.stopLoading();status.setText("This address cannot be opened here.");}}
            @Override public void onPageFinished(WebView view,String url){if(closed||embed!=view)return;CookieManager.getInstance().flush();status.setVisibility(View.VISIBLE);try{status.setText(URI.create(publisherUrl(url)).getHost()+" · Website mode");}catch(Exception ignored){}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()&&!closed&&embed==view){status.setVisibility(View.VISIBLE);status.setText("Website could not load. Try Open original.");}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&!closed&&embed==view){status.setVisibility(View.VISIBLE);status.setText("Website returned HTTP "+response.getStatusCode()+". You can use Open original.");}}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(embed!=view)return true;webViews.remove(view);stage.removeView(view);view.destroy();embed=null;status.setVisibility(View.VISIBLE);status.setText("Website stopped. Tap Browser to try again.");return true;}
        });
        stage.addView(browser,new LinearLayout.LayoutParams(-1,-1));
        try{browser.loadUrl(publisherUrl(original));}catch(Exception e){browser.stopLoading();status.setText("This address cannot be opened here.");}
    }
    static String publisherUrl(String address){String safe=PreviewFetch.webUrl(address);String host=URI.create(safe).getHost();if(host.equals("autoadmit.com")||host.endsWith(".autoadmit.com")||host.equals("xoxohth.com")||host.endsWith(".xoxohth.com"))throw new IllegalArgumentException("Forum links use the native app.");String fragment=URI.create(address.trim()).getRawFragment();return fragment==null?safe:safe+"#"+fragment;}
    private void readBrowser(WebView browser){
        if(closed||embed!=browser)return;String address;try{address=publisherUrl(browser.getUrl());}catch(Exception e){return;}
        status.setVisibility(View.VISIBLE);status.setText("Reading loaded page…");
        browser.evaluateJavascript("(function(){var h=document.documentElement?document.documentElement.outerHTML:'';return h.length<=2000000?h:null;})()",raw->{
            if(closed||embed!=browser)return;
            try{Object value=new JSONTokener(raw).nextValue();if(!(value instanceof String)||((String)value).isEmpty())throw new Exception();
                if(extractor!=null)extractor.close();extractor=new ArticleExtractor();extractor.extract(activity,(String)value,address,"",(article,failure)->{if(closed||embed!=browser)return;if(failure!=null){status.setText("No readable article found. Continue in Website mode.");return;}showArticle(article);});
            }catch(Exception e){status.setText("This page could not be read. Continue in Website mode.");}
        });
    }
    private void showEmbed(String address){
        clearMedia();title.setText(page.title.isEmpty()?"Embedded preview":page.title);status.setVisibility(View.VISIBLE);status.setText("Loading embedded player…");boolean[] loaded={false},failed={false};embed=web(true);
        mobileEmbedPost=EmbedFit.isXPost(address);WebView shown=embed;
        {embedFitMode="contain";add(controls,"−",shown::zoomOut);add(controls,"Fit",()->fitPost(shown,"contain"));add(controls,"Width",()->fitPost(shown,"width"));add(controls,"+",shown::zoomIn);shown.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)shown.post(()->fitPost(shown,embedFitMode));});}
        embed.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(closed||embed!=view)return true;if(!request.isForMainFrame())return false;String target=request.getUrl().toString();try{PreviewFetch.webUrl(target);if(request.hasGesture()){external(target);return true;}if(sameEmbedSite(address,target))return false;}catch(Exception ignored){}error("This player needs its website. Use Open original.");return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){try{PreviewFetch.webUrl(request.getUrl().toString());String h=request.getUrl().getHost();if("autoadmit.com".equals(h)||h.endsWith(".autoadmit.com")||"xoxohth.com".equals(h)||h.endsWith(".xoxohth.com"))return blocked();for(java.net.InetAddress ip:java.net.InetAddress.getAllByName(h))if(!PreviewFetch.publicAddress(ip))return blocked();return null;}catch(Exception e){return blocked();}}
            @Override public void onPageFinished(WebView view,String url){if(!closed&&embed==view&&!failed[0]){view.evaluateJavascript(EmbedFailureWatch.SCRIPT,null);view.evaluateJavascript(EmbedFit.SCRIPT,ignored->fitPost(view,embedFitMode));checkEmbedContent(view,loaded,failed,0);}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError failure){if(!closed&&embed==view&&request.isForMainFrame()){failed[0]=true;loaded[0]=true;error("The provider could not load its embed. Use Open original.");}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(!closed&&embed==view&&request.isForMainFrame()){failed[0]=true;loaded[0]=true;error("The provider blocked its preview (HTTP "+response.getStatusCode()+"). Use Open original.");}}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(embed!=view)return true;webViews.remove(view);stage.removeView(view);view.destroy();embed=null;error("The embedded player stopped. Use Retry or Open original.");return true;}
        });
        stage.addView(embed,new LinearLayout.LayoutParams(-1,-1));if(URI.create(address).getHost().endsWith("youtube-nocookie.com"))embed.loadUrl(address,Collections.singletonMap("Referer","https://"+activity.getPackageName()));else embed.loadUrl(address);
        handler.postDelayed(()->{if(!closed&&embed==shown&&!loaded[0]){loaded[0]=true;error("The provider is taking too long. Use Retry or Open original.");}},30000);
    }
    private void checkEmbedContent(WebView view,boolean[] loaded,boolean[] failed,int attempt){
        if(closed||failed[0]||embed!=view)return;
        view.evaluateJavascript("(function(){var b=document.body;if(!b)return false;var t=(b.innerText||'').trim(),h=b.querySelector('h1,h2');if(/^not supported post[.!]?$/i.test(t))return -1;if(t.length<2000&&h&&/^(page not found|content unavailable|video unavailable|this page isn.t available)[.!]?$/i.test(h.innerText.trim()))return -1;if(t.length)return true;return Array.from(b.querySelectorAll('img,video,audio,iframe,canvas,object,embed')).some(function(e){var r=e.getBoundingClientRect();return r.width>32&&r.height>32&&!e.hidden;});})()",value->{
            if(closed||failed[0]||embed!=view)return;
            if("-1".equals(value)){loaded[0]=true;error("The provider returned an unavailable preview. Use Open original.");}
            else if("true".equals(value)){loaded[0]=true;ready();}
            else if(attempt<10)handler.postDelayed(()->checkEmbedContent(view,loaded,failed,attempt+1),2000);
            else{loaded[0]=true;error("The provider returned an empty preview. Try Retry or Open original.");}
        });
    }
    private void loadPdf(){status.setText("Loading PDF…");async(()->{PreviewResolver.Media media=page.media.get(0);PreviewFetch.Result data=mediaData(media,20000000);if(data.bytes.length<5||!new String(data.bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new Exception("The server did not return a PDF. Use Open original.");return new PdfRenderer(ParcelFileDescriptor.open(cached(data.bytes,".pdf"),ParcelFileDescriptor.MODE_READ_ONLY));},renderer->{pdf=renderer;index=0;showPdf();});}
    private void showPdf(){clearMedia();int token=version;title.setText("PDF");zoom=new ZoomImageView(activity);stage.addView(zoom,new LinearLayout.LayoutParams(-1,-1));add(controls,"‹",()->{index=Math.max(0,index-1);showPdf();});counter=text((index+1)+"/"+pdf.getPageCount(),14,INK);counter.setGravity(Gravity.CENTER);controls.addView(counter,new LinearLayout.LayoutParams(dp(54),dp(44)));add(controls,"›",()->{index=Math.min(pdf.getPageCount()-1,index+1);showPdf();});add(controls,"−",()->zoom.zoomBy(.75f));add(controls,"Fit",()->zoom.fit());add(controls,"+",()->zoom.zoomBy(1.35f));int selected=index;async(()->{synchronized(pdf){try(PdfRenderer.Page p=pdf.openPage(selected)){float scale=Math.min(1600f/p.getWidth(),2200f/p.getHeight());Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return new BitmapDrawable(activity.getResources(),bitmap);}}},image->{if(token==version){currentDrawable=image;zoom.setImageDrawable(image);ready();}});}
    private void showArticle(JSONObject data){clearMedia();title.setText(page.provider.equals("listing")?"Property listing":"Article");articleScroll=new ScrollView(activity);LinearLayout body=column();body.setPadding(dp(4),dp(8),dp(4),dp(12));body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol&&articleImageScale!=100)resizeArticleImages(0);});articleScroll.addView(body);stage.addView(articleScroll,new LinearLayout.LayoutParams(-1,-1));TextView headline=text(data.optString("title","Article"),24,INK);headline.setTypeface(Typeface.SERIF,Typeface.BOLD);headline.setPadding(0,dp(4),0,dp(14));body.addView(headline);String byline=data.optString("byline");if(!byline.isEmpty()){TextView who=text(byline,13,MUTED);who.setPadding(0,0,0,dp(14));body.addView(who);}JSONArray blocks=data.optJSONArray("blocks");if(blocks==null){error("No article text was returned. Use Open original.");return;}
        for(int n=0;n<blocks.length();n++){JSONObject block=blocks.optJSONObject(n);if(block==null)continue;String type=block.optString("type");if(type.equals("image")){String url;try{url=PreviewFetch.webUrl(block.optString("url"));}catch(Exception e){continue;}ImageSlot slot=new ImageSlot();slot.url=url;slot.alt=block.optString("alt","Article image");slot.holder=new FrameLayout(activity);slot.image=new ImageView(activity);slot.image.setScaleType(ImageView.ScaleType.FIT_CENTER);slot.image.setContentDescription(slot.alt);slot.holder.addView(slot.image,new FrameLayout.LayoutParams(-1,-1));slot.hint=text("Image · loading as you scroll",13,MUTED);slot.hint.setGravity(Gravity.CENTER);slot.holder.addView(slot.hint,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(210));p.setMargins(0,dp(8),0,dp(8));body.addView(slot.holder,p);slot.holder.setOnClickListener(v->link.accept(slot.url));images.add(slot);continue;}
            String plain=block.optString("text","");if(plain.isEmpty())continue;TextView paragraph=text(type.equals("item")?"• "+plain:plain,type.equals("heading")?21:type.equals("caption")?13:18,type.equals("caption")?MUTED:INK);if(type.equals("heading"))paragraph.setTypeface(Typeface.SERIF,Typeface.BOLD);if(type.equals("pre"))paragraph.setTypeface(Typeface.MONOSPACE);paragraph.setTextIsSelectable(true);paragraph.setLineSpacing(dp(3),1);paragraph.setPadding(0,dp(type.equals("heading")?13:3),0,dp(type.equals("caption")?12:14));
            SpannableStringBuilder styled=new SpannableStringBuilder(paragraph.getText());JSONArray links=block.optJSONArray("links");if(links!=null)for(int x=0;x<links.length();x++){JSONObject l=links.optJSONObject(x);if(l==null)continue;int start=l.optInt("start")+(type.equals("item")?2:0),end=l.optInt("end")+(type.equals("item")?2:0);String url=l.optString("url");if(start>=0&&end>start&&end<=styled.length())styled.setSpan(new URLSpan(url){@Override public void onClick(View view){link.accept(url);}},start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}paragraph.setText(styled);AppColors.link(paragraph,BLUE);paragraph.setMovementMethod(linkHold==null?LinkMovementMethod.getInstance():new LinkHoldMovementMethod(linkHold));body.addView(paragraph);
        }
        if(!images.isEmpty()){add(controls,"−",()->resizeArticleImages(-10));imageScaleLabel=text("Images 100%",13,INK);imageScaleLabel.setGravity(Gravity.CENTER);controls.addView(imageScaleLabel,new LinearLayout.LayoutParams(dp(98),dp(44)));add(controls,"Fit",()->{articleImageScale=100;resizeArticleImages(0);});add(controls,"+",()->resizeArticleImages(10));}ready();articleScroll.setOnScrollChangeListener((v,x,y,ox,oy)->loadVisibleImages());articleScroll.post(this::loadVisibleImages);
    }
    private void resizeArticleImages(int delta){
        if(articleScroll==null)return;articleImageScale=Math.max(20,Math.min(100,articleImageScale+delta));
        if(imageScaleLabel!=null)imageScaleLabel.setText("Images "+articleImageScale+"%");
        ViewGroup body=(ViewGroup)articleScroll.getChildAt(0);int y=articleScroll.getScrollY();View anchor=null;int offset=0;
        for(int n=0;n<body.getChildCount();n++){View child=body.getChildAt(n);if(child.getBottom()>y){anchor=child;offset=y-child.getTop();break;}}
        int available=Math.max(1,body.getWidth()-body.getPaddingLeft()-body.getPaddingRight());
        for(ImageSlot slot:images){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)slot.holder.getLayoutParams();p.gravity=Gravity.CENTER_HORIZONTAL;p.width=articleImageScale==100?-1:Math.max(dp(32),available*articleImageScale/100);int width=articleImageScale==100?available:p.width;p.height=slot.drawable!=null&&slot.drawable.getIntrinsicWidth()>0?Math.max(dp(32),Math.min(dp(600),(int)((float)width*slot.drawable.getIntrinsicHeight()/slot.drawable.getIntrinsicWidth()))):dp(210)*articleImageScale/100;slot.holder.setLayoutParams(p);}
        View target=anchor;int relative=offset;ScrollView targetScroll=articleScroll;targetScroll.post(()->{if(!closed&&articleScroll==targetScroll&&target!=null)targetScroll.scrollTo(0,target.getTop()+relative);});
    }
    private void loadVisibleImages(){if(closed||articleScroll==null)return;int top=articleScroll.getScrollY(),height=articleScroll.getHeight();for(ImageSlot slot:images){int start=slot.holder.getTop(),end=slot.holder.getBottom();boolean near=end>=top-height/2&&start<=top+height*2;if(!near&&slot.loaded){slot.generation++;slot.loaded=false;slot.image.setImageDrawable(null);if(Build.VERSION.SDK_INT>=28&&slot.drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)slot.drawable).stop();slot.drawable=null;if(slot.svg!=null){webViews.remove(slot.svg);slot.holder.removeView(slot.svg);slot.svg.destroy();slot.svg=null;}slot.hint.setVisibility(View.VISIBLE);slot.hint.setText("Image · scroll to load");}else if(near&&!slot.loaded&&!slot.loading){slot.loading=true;int generation=slot.generation;Consumer<String> failed=message->{slot.loading=false;if(generation!=slot.generation)return;slot.loaded=true;slot.hint.setVisibility(View.VISIBLE);slot.hint.setText("Image unavailable · tap to preview or use Open original");};async(()->fetch.get(slot.url,24000000),data->{if(generation!=slot.generation){slot.loading=false;return;}if(isSvg(data)){slot.svg=svg(data.text());slot.svg.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_UP)link.accept(slot.url);return true;});slot.holder.addView(slot.svg,new FrameLayout.LayoutParams(-1,-1));slot.loaded=true;slot.loading=false;slot.hint.setVisibility(View.GONE);return;}async(()->decode(data.bytes,1024),drawable->{slot.loading=false;if(generation!=slot.generation)return;slot.drawable=drawable;slot.image.setImageDrawable(drawable);slot.loaded=true;start(drawable);int width=slot.holder.getWidth();if(width>0&&drawable.getIntrinsicWidth()>0){int h=Math.max(dp(articleImageScale==100?100:32),Math.min(dp(600),(int)((float)width*drawable.getIntrinsicHeight()/drawable.getIntrinsicWidth())));slot.holder.getLayoutParams().height=h;slot.holder.requestLayout();}slot.hint.setVisibility(View.GONE);},failed);},failed);}}
    }
    private void external(String address){try{URI u=URI.create(address);if(!"https".equalsIgnoreCase(u.getScheme())&&!"http".equalsIgnoreCase(u.getScheme()))return;activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}catch(Exception e){error("No browser is available to open this link.");}}
    @Override public void dismiss(){if(!closed){closed=true;version++;fetch.cancel();handler.removeCallbacksAndMessages(null);if(extractor!=null)extractor.close();if(video!=null)video.stopPlayback();for(WebView view:new ArrayList<>(webViews)){view.stopLoading();view.destroy();}webViews.clear();for(ImageSlot slot:images)if(Build.VERSION.SDK_INT>=28&&slot.drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)slot.drawable).stop();if(Build.VERSION.SDK_INT>=28&&currentDrawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)currentDrawable).stop();jobs.shutdown();final PdfRenderer renderer=pdf;new Thread(()->{if(renderer!=null)synchronized(renderer){renderer.close();}synchronized(files){for(File file:files)file.delete();files.clear();}},"preview-cleanup").start();}super.dismiss();}
}
