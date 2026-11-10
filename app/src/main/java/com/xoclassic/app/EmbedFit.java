package com.xoclassic.app;

import java.net.URI;

/** Shared hosted-media fitting, with mobile-width layout for the official X post renderer. */
public final class EmbedFit {
    public static boolean isXPost(String address){try{URI uri=URI.create(address);return "https".equalsIgnoreCase(uri.getScheme())&&"platform.twitter.com".equalsIgnoreCase(uri.getHost())&&"/embed/Tweet.html".equals(uri.getPath());}catch(Exception e){return false;}}
    public static final String SCRIPT="""
        (function(){
          if(window.xoFitPost)return;
          var node=null,mode='contain',width=375,mobilePost=false,pending=false,resize,originalHeight='',lastHeight='',baseWidth=0,fluid=false,originalStyle=null;
          var properties=['position','width','height','maxWidth','margin','transformOrigin','transform','left','top','boxSizing'];
          function schedule(){if(!pending){pending=true;requestAnimationFrame(function(){pending=false;fit();});}}
          function set(e,k,v){if(e.style[k]!==v)e.style[k]=v;}
          function substantial(e){return e.offsetWidth>32&&e.offsetHeight>32&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden';}
          function target(){
            var semantic=document.querySelector('article,main,[role="main"]');if(semantic&&substantial(semantic))return semantic;
            var children=Array.from(document.body.children).filter(function(e){return !/^(SCRIPT|STYLE|LINK|META)$/.test(e.tagName)&&substantial(e);});
            return children.length===1?children[0]:null;
          }
          function fit(){
            if(!document.body)return false;var root=target();if(!root)return false;
            var vw=document.documentElement.clientWidth||innerWidth,vh=innerHeight;if(vw<=4||vh<=4)return false;
            if(root!==node){
              if(resize)resize.disconnect();if(node&&originalStyle)properties.forEach(function(k){set(node,k,originalStyle[k]);});
              node=root;originalStyle={};properties.forEach(function(k){originalStyle[k]=node.style[k];});originalHeight=node.style.height;lastHeight=originalHeight;
              baseWidth=node.offsetWidth;fluid=Math.abs(baseWidth-vw)<3;
              if(resize){resize.observe(node);Array.from(node.children).slice(0,30).forEach(function(e){resize.observe(e);});}
            }
            if(node.style.height!==lastHeight){originalHeight=node.style.height;originalStyle.height=originalHeight;}
            for(var ancestor=node.parentElement;ancestor&&ancestor!==document.body;ancestor=ancestor.parentElement){set(ancestor,'overflow','visible');set(ancestor,'contain','none');set(ancestor,'height',vh+'px');set(ancestor,'width',vw+'px');set(ancestor,'maxWidth','none');set(ancestor,'borderRadius','0px');}
            set(document.body,'height',vh+'px');set(node,'height',originalHeight);set(node,'transform','none');
            set(node,'position','absolute');set(node,'boxSizing','border-box');set(node,'width',(mobilePost?width:fluid?vw:baseWidth)+'px');
            set(node,'maxWidth','none');set(node,'margin','0px');set(node,'transformOrigin','0px 0px');
            set(node,'left','0px');set(node,'top','0px');
            var origin=node.getBoundingClientRect(),ox=origin.left+scrollX,oy=origin.top+scrollY;
            var w=Math.max(node.offsetWidth,node.scrollWidth),h=Math.max(node.offsetHeight,node.scrollHeight);if(w<=0||h<=0)return false;
            var scale=mode==='width'?(vw-4)/w:Math.min((vw-4)/w,(vh-4)/h),height=Math.max(vh,Math.ceil(h*scale)+4);
            lastHeight=h+'px';set(node,'height',lastHeight);
            set(document.documentElement,'margin','0px');set(document.documentElement,'height','100%');
            set(document.documentElement,'overflowX','hidden');set(document.documentElement,'overflowY',mode==='width'?'auto':'hidden');
            set(document.body,'margin','0px');set(document.body,'height',height+'px');
            set(document.body,'overflowX','hidden');set(document.body,'overflowY',mode==='width'?'auto':'hidden');
            set(node,'left',((vw-w*scale)/2-ox)+'px');set(node,'top',(2-oy)+'px');set(node,'transform','scale('+scale+')');
            if(resize)Array.from(node.querySelectorAll('img,video,iframe')).slice(0,30).forEach(function(e){resize.observe(e);});
            return true;
          }
          resize=window.ResizeObserver?new ResizeObserver(schedule):null;
          new MutationObserver(schedule).observe(document.body,{childList:true,subtree:true,characterData:true});
          document.addEventListener('load',schedule,true);document.addEventListener('loadedmetadata',schedule,true);addEventListener('resize',schedule);
          window.xoFitPost=function(value,naturalWidth,isMobilePost){mode=value==='width'?'width':'contain';width=Math.max(250,Math.min(550,naturalWidth||375));mobilePost=!!isMobilePost;schedule();};
        })();
        """;
}
