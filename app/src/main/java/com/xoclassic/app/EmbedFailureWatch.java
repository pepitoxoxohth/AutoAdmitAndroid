package com.xoclassic.app;

/** Event-driven failure detection inside hosted players; no bridge, polling or network requests. */
public final class EmbedFailureWatch {
    public static final String TITLE="__XO_PREVIEW_MEDIA_ERROR__";
    public static final String SCRIPT="""
        (function(){
          if(window.xoFailureWatch)return;window.xoFailureWatch=true;
          var failed=false,pending=false,vimeo=location.hostname==='player.vimeo.com',reddit=/^(?:www\\.)?redditmedia\\.com$|^embed\\.reddit\\.com$/.test(location.hostname),selector='.vp-error,[class*="error" i],[role="alert"]';
          function visible(e){var s=getComputedStyle(e),r=e.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}
          function report(){if(failed)return;failed=true;observer.disconnect();document.title='__XO_PREVIEW_MEDIA_ERROR__';}
          function inspect(){
            pending=false;if(failed)return;
            var videos=document.querySelectorAll('video,audio');
            for(var i=0;i<videos.length;i++)if(visible(videos[i])&&videos[i].error&&videos[i].error.code>1){report();return;}
            if(reddit&&/^not supported post[.!]?$/i.test((document.body.innerText||'').replace(/\\s+/g,' ').trim())){report();return;}
            if(!vimeo)return;
            var errors=document.querySelectorAll(selector);
            for(var i=0;i<errors.length&&i<50;i++)if(visible(errors[i])){
              var text=(errors[i].innerText||'').replace(/\\s+/g,' ').trim();
              if(/unable to play video/i.test(text)&&/supported browser|cannot be played|try again/i.test(text)){report();return;}
            }
          }
          function schedule(){if(!failed&&!pending){pending=true;requestAnimationFrame(inspect);}}
          function related(e){return e&&e.nodeType===1&&(e.matches(selector)||e.closest(selector)||e.querySelector(selector));}
          var observer=new MutationObserver(function(records){if(reddit){schedule();return;}for(var i=0;i<records.length;i++){var r=records[i];if(related(r.target.nodeType===1?r.target:r.target.parentElement)){schedule();return;}for(var j=0;j<r.addedNodes.length;j++)if(related(r.addedNodes[j])){schedule();return;}}});
          if((vimeo||reddit)&&document.body)observer.observe(document.body,reddit?{subtree:true,childList:true,characterData:true}:{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['class','style','hidden']});
          document.addEventListener('error',function(e){if(e.target instanceof HTMLMediaElement)schedule();},true);
          inspect();
        })();
        """;
}
