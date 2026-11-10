package com.xoclassic.app;

import android.app.Activity;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bundled Readability in an offline DOM. Remote document scripts never execute. */
public final class ArticleExtractor {
    public interface Callback {void done(JSONObject article,String error);}
    private WebView engine;
    private boolean completed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    public void extract(Activity activity,String html,String url,String provider,Callback callback){
        engine=new WebView(activity);WebSettings settings=engine.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        Runnable timeout=()->finish(callback,null,"Article extraction timed out. Use Open original.");handler.postDelayed(timeout,15000);
        engine.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String path=request.getUrl().getPath();if("appassets.androidplatform.net".equals(request.getUrl().getHost())&&Arrays.asList("/assets/reader.html","/assets/Readability.js","/assets/reader.js").contains(path))try{return new WebResourceResponse(path.endsWith(".html")?"text/html":"application/javascript","UTF-8",activity.getAssets().open(path.substring("/assets/".length())));}catch(Exception ignored){}
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view,String address){if(completed)return;view.evaluateJavascript("JSON.stringify(window.xoExtract("+JSONObject.quote(html)+","+JSONObject.quote(url)+","+JSONObject.quote(provider)+"))",raw->{if(completed)return;try{Object decoded=new JSONTokener(raw).nextValue();JSONObject article=new JSONObject((String)decoded);finish(callback,article,article.optString("error",null));}catch(Exception e){finish(callback,null,"Article extraction failed. Use Open original.");}});}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){finish(callback,null,"The article reader stopped. Use Retry or Open original.");return true;}
        });
        engine.loadUrl("https://appassets.androidplatform.net/assets/reader.html");
    }
    private void finish(Callback callback,JSONObject article,String error){if(completed)return;completed=true;handler.removeCallbacksAndMessages(null);if(engine!=null){engine.stopLoading();engine.destroy();engine=null;}callback.done(article,error);}
    public void close(){completed=true;handler.removeCallbacksAndMessages(null);if(engine!=null){engine.stopLoading();engine.destroy();engine=null;}}
}
