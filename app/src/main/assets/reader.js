/* Local extraction only. Remote HTML is parsed inertly and never inserted into this page. */
window.xoExtract = function (html, address, provider) {
  "use strict";
  const doc = new DOMParser().parseFromString(html, "text/html");
  doc.querySelectorAll("base").forEach(n => n.remove());
  const base = doc.createElement("base"); base.href = address; doc.head.appendChild(base);
  const meta = name => {const n = doc.querySelector('meta[property="'+name+'"],meta[name="'+name+'"]');return n ? n.content || "" : "";};
  const safe = value => {if(typeof value!=="string"||!value.trim())return "";try {const u=new URL(value,address);if(!/^https?:$/.test(u.protocol)||u.username||u.password||u.port)return "";u.protocol="https:";return u.href;}catch(_){return "";}};
  const imageSource = img => {
    const lazy=img.getAttribute("data-src")||img.getAttribute("data-original")||img.getAttribute("data-lazy-src");
    const srcset=img.getAttribute("data-srcset")||img.getAttribute("srcset")||"";
    const candidates=srcset.split(",").map(s=>s.trim().split(/\s+/)).filter(a=>a[0]).sort((a,b)=>(parseFloat(b[1])||1)-(parseFloat(a[1])||1));
    return safe(lazy || (candidates[0]||[])[0] || img.getAttribute("src") || "");
  };
  if(provider === "listing") {
    const objects=[];
    const visit=(v,depth)=>{if(!v||typeof v!=="object"||depth>12||objects.length>=1000)return;if(Array.isArray(v)){v.slice(0,100).forEach(x=>visit(x,depth+1));return;}objects.push(v);["@graph","mainEntity","itemOffered","about","offers"].forEach(k=>visit(v[k],depth+1));};
    doc.querySelectorAll('script[type="application/ld+json"]').forEach(s=>{try{visit(JSON.parse(s.textContent),0);}catch(_){}});
    const property=objects.find(o=>o.address&&/Residence|House|Apartment|Accommodation|RealEstateListing|Product/.test([].concat(o["@type"]||[]).join(" ")))||{};
    const listing=objects.find(o=>[].concat(o["@type"]||[]).includes("RealEstateListing"))||property;
    const text=(v,max=500)=>typeof v==="string"||typeof v==="number"?String(v).slice(0,max):"";
    const a=property.address,title=text(typeof a==="string"?a:a?[a.streetAddress,a.addressLocality,a.addressRegion,a.postalCode].filter(Boolean).join(", "):meta("og:title")||((doc.querySelector("h1")||{}).textContent||""));
    const description=text(property.description||listing.description||meta("og:description")||meta("description"),10000);
    const blocks=[],offer=[].concat(listing.offers||property.offers||[]).find(x=>x&&(x.price||x.priceSpecification))||{};
    const price=text(offer.price||(offer.priceSpecification||{}).price||meta("product:price:amount")),currency=text(offer.priceCurrency||"USD",10);
    let formatted=price;if(price){const amount=Number(price.replace(/[$,]/g,""));if(Number.isFinite(amount)&&amount>0)try{formatted=new Intl.NumberFormat("en-US",{style:"currency",currency:/^[A-Z]{3}$/.test(currency)?currency:"USD",maximumFractionDigits:0}).format(amount);}catch(_){}}
    const detail=[formatted,property.numberOfBedrooms?text(property.numberOfBedrooms)+" beds":"",property.numberOfBathroomsTotal?text(property.numberOfBathroomsTotal)+" baths":"",property.floorSize&&property.floorSize.value?text(property.floorSize.value)+" "+text(property.floorSize.unitText||property.floorSize.unitCode||""):""].filter(Boolean);
    if(detail.length)blocks.push({type:"heading",text:detail.join(" · ")});if(description)blocks.push({type:"paragraph",text:description});
    const seen=new Set();let photos=0;const photo=v=>{if(Array.isArray(v)){v.slice(0,30).forEach(photo);return;}if(v&&typeof v==="object")v=v.contentUrl||v.url;const u=typeof v==="string"?safe(v):"";if(u&&!seen.has(u)&&photos<20&&/(^|\.)(cdn-redfin\.com|zillowstatic\.com|harstatic\.com|redfin\.com|har\.com|zillow\.com)$/.test(new URL(u).hostname)){seen.add(u);photos++;blocks.push({type:"image",url:u,alt:title});}};
    photo(property.image);photo(listing.image);photo(meta("og:image"));
    const street=text(a&&typeof a==="object"?a.streetAddress:title.split(",")[0]).toLowerCase();
    doc.querySelectorAll("img[src],img[data-src]").forEach(img=>{if(street.length>=5&&(img.alt||"").toLowerCase().includes(street))photo(img.getAttribute("data-src")||img.getAttribute("src"));});
    if(!title||/captcha|access denied|verify.*human|robot|security check/i.test(title)||(!a&&!description&&!photos))return {error:"Listing data unavailable or verification required. Use Open original."};
    return {title,site:new URL(address).hostname,byline:"",blocks};
  }
  doc.querySelectorAll("img").forEach(img=>{const src=imageSource(img);if(src){img.setAttribute("src",src);img.removeAttribute("srcset");}});
  // Preserve publisher photo groups before Readability prunes sharing controls.
  doc.querySelectorAll("#js-article-text .mol-img-group").forEach(group=>{const imgs=group.querySelectorAll(".mol-img img,.image-wrap img");if(!imgs.length)return;const figure=doc.createElement("figure");imgs.forEach(img=>{const photo=img.cloneNode(true);photo.removeAttribute("class");figure.appendChild(photo);});const cap=group.querySelector(".imageCaption");if(cap){const text=doc.createElement("figcaption");text.textContent=cap.textContent;figure.appendChild(text);}group.replaceWith(figure);});
  const parsed=new Readability(doc.cloneNode(true),{maxElemsToParse:50000,serializer:n=>n}).parse();
  if(!parsed||!parsed.content) return {error:"No readable article was returned. Use Open original."};
  const blocks=[];let total=0;
  const blockTags=/^(P|DIV|SECTION|ARTICLE|MAIN|FIGURE|FIGCAPTION|H[1-6]|LI|UL|OL|BLOCKQUOTE|PRE|DD|DT|TABLE|TR|TD)$/;
  const kindFor=tag=>/^H/.test(tag)?"heading":tag==="LI"?"item":tag==="FIGCAPTION"?"caption":tag==="PRE"?"pre":"paragraph";
  const walk=(root,kind)=>{
    let text="",links=[];
    const flush=()=>{const lead=(text.match(/^\s*/)||[""])[0].length,trimmed=text.trim();if(trimmed&&blocks.length<500&&total+trimmed.length<=100000){if(!(blocks.length===0&&kind==="heading"&&trimmed===parsed.title)){blocks.push({type:kind,text:trimmed,links:links.map(l=>({start:Math.max(0,l.start-lead),end:Math.min(trimmed.length,l.end-lead),url:l.url})).filter(l=>l.end>l.start)});total+=trimmed.length;}}text="";links=[];};
    const inline=(node,anchor)=>{
      if(blocks.length>=500)return;
      if(node.nodeType===3){let value=kind==="pre"?node.textContent:node.textContent.replace(/\s+/g," ");if(text.endsWith(" ")&&value.startsWith(" "))value=value.slice(1);const start=text.length;text+=value;if(anchor&&value)links.push({start,end:text.length,url:anchor});return;}
      if(node.nodeType!==1)return;const tag=node.tagName;if(/^(SCRIPT|STYLE|NOSCRIPT|IFRAME|BUTTON|FORM|NAV|FOOTER)$/.test(tag))return;
      if(tag==="IMG"){flush();const src=imageSource(node);if(src&&blocks.length<500){blocks.push({type:"image",url:src,alt:(node.alt||"Article image").slice(0,500)});}return;}
      if(tag==="BR"){text+="\n";return;}
      if(blockTags.test(tag)){flush();walk(node,kind==="item"&&tag==="P"?"item":kindFor(tag));return;}
      const link=tag==="A"?safe(node.getAttribute("href")||""):anchor;Array.from(node.childNodes).forEach(child=>inline(child,link));
    };
    Array.from(root.childNodes).forEach(child=>inline(child,""));flush();
  };
  walk(parsed.content,"paragraph");
  if(total<200||!blocks.some(b=>b.type==="paragraph"||b.type==="item"))return {error:"No substantial article was found. The site may require sign-in or verification. Use Open original."};
  return {title:(parsed.title||meta("og:title")||doc.title).slice(0,500),byline:(parsed.byline||"").slice(0,300),site:(parsed.siteName||new URL(address).hostname).slice(0,100),blocks};
};
