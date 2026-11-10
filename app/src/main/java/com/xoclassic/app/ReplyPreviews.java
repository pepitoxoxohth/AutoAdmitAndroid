package com.xoclassic.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.URLSpan;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Loaded-thread-only parent previews. No network calls, ownership guesses or recursive view trees. */
final class ReplyPreviews {
    static final String KEY="replyPreviewMode";
    private final Activity activity;private final SharedPreferences prefs;private final RenderedPostCache cache;
    private final Consumer<String> jump,link,held,copy;private final BooleanSupplier allowed;
    private final Map<String,List<String>> expanded=new HashMap<>();
    private final Map<String,ForumParser.Post> posts=new HashMap<>();
    private String threadKey="";private ForumParser.ThreadPage page;private StackPreview stack;
    ReplyPreviews(Activity activity,SharedPreferences prefs,RenderedPostCache cache,Consumer<String> jump,Consumer<String> link,Consumer<String> held,Consumer<String> copy,BooleanSupplier allowed){this.activity=activity;this.prefs=prefs;this.cache=cache;this.jump=jump;this.link=link;this.held=held;this.copy=copy;this.allowed=allowed;}
    static String mode(SharedPreferences prefs){String mode=prefs.getString(KEY,"links");return mode.equals("nested")||mode.equals("stacked")?mode:"links";}
    boolean uses(SharedPreferences prefs){return this.prefs==prefs;}
    void page(ForumParser.ThreadPage page,String origin){
        String key=origin+":"+page.id;if(!key.equals(threadKey))leave();threadKey=key;this.page=page;posts.clear();for(ForumParser.Post p:page.posts)posts.put(p.id,p);
        expanded.keySet().retainAll(posts.keySet());
        for(Map.Entry<String,List<String>> entry:expanded.entrySet())prune(entry.getKey(),entry.getValue());
        if(stack!=null){prune(stack.owner,stack.path);if(stack.path.isEmpty())stack.dismiss();else stack.render(true);}
    }
    void leave(){if(stack!=null)stack.dismiss();expanded.clear();posts.clear();page=null;threadKey="";}
    private void prune(String owner,List<String> path){ForumParser.Post p=posts.get(owner);Set<String> seen=new HashSet<>();seen.add(owner);int good=0;for(String id:path){if(p==null||!id.equals(p.parent)||!seen.add(id)||(p=posts.get(id))==null)break;good++;}if(good<path.size())path.subList(good,path.size()).clear();}
    int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);return v;}
    private TextView text(String value,int size,int role){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTypeface(Typeface.SERIF);AppColors.text(v,role);return v;}
    private GradientDrawable surface(int role){GradientDrawable d=new GradientDrawable();AppColors.fill(d,role);d.setCornerRadius(dp(10));AppColors.stroke(d,dp(1),0xff5d7898);return d;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.SERIF);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setMinHeight(dp(44));GradientDrawable bg=new GradientDrawable();AppColors.fill(bg,0xff000002);bg.setCornerRadius(dp(9));b.setBackground(bg);AppColors.text(b,0xff9cbce3);b.setOnClickListener(v->{if(allowed.getAsBoolean())action.run();});return b;}
    private void holdParent(String id){if(page==null||id==null||!id.matches("[0-9]+")){notice("Could not open the parent link menu.");return;}held.accept(ForumParser.origin(page.url)+"/thread.php?thread_id="+page.id+"&forum_id=2#"+id);}
    private TextView body(ForumParser.Post post){
        TextView v=text("",17,0xffe1dfdc);v.setTag("reply-preview-body:"+post.id);SpannableStringBuilder styled=new SpannableStringBuilder(cache.get(post.html==null?"":post.html));
        for(URLSpan span:styled.getSpans(0,styled.length(),URLSpan.class)){int start=styled.getSpanStart(span),end=styled.getSpanEnd(span);String url=span.getURL();styled.removeSpan(span);styled.setSpan(new URLSpan(url){@Override public void onClick(View view){if(allowed.getAsBoolean())link.accept(url);}},start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        v.setText(styled);v.setTextIsSelectable(true);AppColors.link(v,0xff9cbce3);v.setMovementMethod(new LinkHoldMovementMethod(url->{if(allowed.getAsBoolean())held.accept(url);}));v.setLineSpacing(dp(3),1);v.setPadding(0,dp(8),0,dp(6));return v;
    }
    private void header(LinearLayout card,ForumParser.Post p,int depth){
        TextView label=text("Earlier reply · "+depth+" back",12,0xffa8adb5);card.addView(label);TextView name=text(p.author,17,0xffdf9291);name.setTypeface(Typeface.SERIF,Typeface.BOLD);name.setPadding(0,dp(6),0,0);card.addView(name);
        TextView date=text(p.date,12,0xffa8adb5);date.setMinHeight(dp(36));date.setGravity(Gravity.CENTER_VERTICAL);date.setOnLongClickListener(v->{if(allowed.getAsBoolean())copy.accept(p.id);return true;});card.addView(date);
    }
    private TextView reference(ForumParser.Post post){
        if(post.parent.isEmpty()){TextView end=text("Start of this conversation",12,0xffa8adb5);end.setPadding(0,dp(10),0,dp(6));return end;}
        ForumParser.Post parent=posts.get(post.parent);TextView ref=text("▸ >>"+post.parent+" · Reply to "+(parent==null?"earlier post":parent.author),14,0xff9cbce3);ref.setTag("reply-preview-parent:"+post.id);ref.setMinHeight(dp(44));ref.setGravity(Gravity.CENTER_VERTICAL);ref.setOnLongClickListener(v->{if(allowed.getAsBoolean())holdParent(post.parent);return true;});return ref;
    }
    private boolean canOpen(String owner,List<String> path,String id){
        if(id.equals(owner)||path.contains(id)){notice("This reply chain returns to a post already open.");return false;}
        if(!posts.containsKey(id)){notice("That earlier post is not in the loaded thread.");return false;}return true;
    }
    private void notice(String text){Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
    void bind(TextView context,LinearLayout card,ForumParser.Post post){
        if(post.parent.isEmpty())return;
        String mode=mode(prefs);
        if(mode.equals("links")){context.setOnClickListener(v->{if(allowed.getAsBoolean())jump.accept(post.parent);});return;}
        context.setMinHeight(dp(44));context.setGravity(Gravity.CENTER_VERTICAL);context.setOnLongClickListener(v->{if(allowed.getAsBoolean())holdParent(post.parent);return true;});
        if(mode.equals("stacked")){context.setContentDescription(context.getText()+". Open parent reply preview.");context.setOnClickListener(v->{if(allowed.getAsBoolean())openStack(post);});return;}
        LinearLayout host=column();host.setTag("reply-inline:"+post.id);card.addView(host,new LinearLayout.LayoutParams(-1,-2));new InlineBranch(post,context,host);
    }
    /** Only two inset containers are used; deeper previews are full-width siblings inside the second. */
    private final class InlineBranch {
        final String owner;final TextView context;final CharSequence base;final LinearLayout host;final List<String> path;final List<Row> rows=new ArrayList<>();LinearLayout deepFlow;
        final class Row {ForumParser.Post post;LinearLayout card,slot;TextView ref;Row(ForumParser.Post p){post=p;}}
        InlineBranch(ForumParser.Post post,TextView context,LinearLayout host){owner=post.id;this.context=context;this.base=new SpannedString(context.getText());this.host=host;List<String> previous=expanded.get(owner);List<String> restore=previous==null?Collections.emptyList():new ArrayList<>(previous);path=new ArrayList<>();expanded.put(owner,path);for(String id:restore)append(id);update();context.setOnClickListener(v->{if(!allowed.getAsBoolean())return;keepPosition(context,()->{if(path.isEmpty()){ForumParser.Post p=posts.get(owner);if(p!=null&&canOpen(owner,path,p.parent))append(p.parent);}else collapse(0);update();});});}
        void append(String id){
            ForumParser.Post p=posts.get(id);if(p==null)return;Row row=new Row(p);row.card=column();row.card.setTag("reply-inline-card:"+id);row.card.setPadding(dp(10),dp(10),dp(10),dp(10));row.card.setBackground(surface(rows.size()%2==0?0xff292b30:0xff000001));header(row.card,p,rows.size()+1);row.ref=reference(p);row.card.addView(row.ref);row.slot=column();row.card.addView(row.slot);row.card.addView(body(p));int index=rows.size();if(!p.parent.isEmpty())row.ref.setOnClickListener(v->{if(!allowed.getAsBoolean()||p.parent.isEmpty())return;keepPosition(row.ref,()->{if(rows.size()>index+1)collapse(index+1);else if(canOpen(owner,path,p.parent))append(p.parent);update();});});
            LinearLayout target=rows.isEmpty()?host:rows.size()==1?rows.get(0).slot:deepFlow;LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(0,dp(8),0,dp(4));target.addView(row.card,space);rows.add(row);path.add(id);if(rows.size()==2)deepFlow=row.slot;
        }
        void collapse(int from){for(int i=rows.size()-1;i>=from;i--){Row row=rows.remove(i);if(row.card.getParent() instanceof LinearLayout)((LinearLayout)row.card.getParent()).removeView(row.card);path.remove(i);}if(rows.size()<2)deepFlow=null;}
        void update(){SpannableStringBuilder label=new SpannableStringBuilder(base);label.append(path.isEmpty()?" ▸":" ▾");context.setText(label);context.setContentDescription(base+(path.isEmpty()?". Show parent inline.":". Hide parent previews."));for(int i=0;i<rows.size();i++){Row r=rows.get(i);if(!r.post.parent.isEmpty()){ForumParser.Post parent=posts.get(r.post.parent);r.ref.setText((i+1<rows.size()?"▾":"▸")+" >>"+r.post.parent+" · Reply to "+(parent==null?"earlier post":parent.author));}}}
    }
    private void keepPosition(View reference,Runnable change){
        ViewParent parent=reference.getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();ScrollView scroll=parent instanceof ScrollView?(ScrollView)parent:null;int[] before=new int[2];reference.getLocationOnScreen(before);change.run();if(scroll==null)return;
        reference.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(reference.getViewTreeObserver().isAlive())reference.getViewTreeObserver().removeOnPreDrawListener(this);if(reference.isAttachedToWindow()){int[] after=new int[2];reference.getLocationOnScreen(after);scroll.scrollBy(0,after[1]-before[1]);}return true;}});
    }
    private void openStack(ForumParser.Post owner){if(!canOpen(owner.id,Collections.emptyList(),owner.parent))return;if(stack!=null)stack.dismiss();stack=new StackPreview(owner.id,owner.parent);stack.open();}
    private final class StackPreview extends Dialog {
        final String owner;final List<String> path=new ArrayList<>();final Map<String,Integer> positions=new HashMap<>();ScrollView bodyScroll;String displayed="";
        StackPreview(String owner,String parent){super(activity,R.style.AppTheme);this.owner=owner;path.add(parent);setCanceledOnTouchOutside(true);setOnDismissListener(d->{if(stack==this)stack=null;});}
        void open(){AppLock.attach(this);render(false);show();resize();}
        void savePosition(){if(bodyScroll!=null&&!displayed.isEmpty())positions.put(displayed,bodyScroll.getScrollY());}
        void resize(){Window w=getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.55f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(600)),-2);}}
        void render(boolean preserve){
            if(preserve)savePosition();String id=path.get(path.size()-1);ForumParser.Post p=posts.get(id);if(p==null){dismiss();return;}displayed=id;LinearLayout panel=new StackPanel(activity);panel.setTag("reply-stack");panel.setPadding(dp(12),dp(12),dp(12),dp(12));panel.setBackground(surface(0xff34383f));
            if(path.size()>1){ForumParser.Post behind=posts.get(path.get(path.size()-2));TextView peek=text("Behind: "+(behind==null?"previous reply":behind.author),12,0xffa8adb5);peek.setPadding(dp(10),0,dp(10),dp(8));panel.addView(peek);}
            LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBaselineAligned(false);Button back=button("‹ Back one",this::back);back.setTag("reply-stack-back");back.setEnabled(path.size()>1);back.setAlpha(path.size()>1?1:.35f);bar.addView(back,new LinearLayout.LayoutParams(-2,dp(44)));TextView count=text(path.size()+" "+(path.size()==1?"preview":"previews")+" open",13,0xffa8adb5);count.setGravity(Gravity.CENTER);count.setTag("reply-stack-count");bar.addView(count,new LinearLayout.LayoutParams(0,-2,1));Button close=button("×",this::dismiss);close.setContentDescription("Close reply previews");close.setTag("reply-stack-close");bar.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(bar);
            bodyScroll=new LimitedScroll(activity);LinearLayout body=column();header(body,p,path.size());TextView parent=reference(p);if(!p.parent.isEmpty())parent.setOnClickListener(v->{if(!allowed.getAsBoolean()||p.parent.isEmpty())return;if(canOpen(owner,path,p.parent)){savePosition();path.add(p.parent);render(false);}});body.addView(parent);body.addView(body(p));bodyScroll.addView(body);panel.addView(bodyScroll,new LinearLayout.LayoutParams(-1,-2));Button go=button("Go to post",()->{dismiss();jump.accept(id);});go.setTag("reply-stack-go");panel.addView(go,new LinearLayout.LayoutParams(-1,dp(44)));setContentView(panel);int y=positions.getOrDefault(id,0);ScrollView restored=bodyScroll;restored.post(()->restored.scrollTo(0,y));if(isShowing())resize();
        }
        void back(){if(path.size()<=1){dismiss();return;}savePosition();path.remove(path.size()-1);render(false);}
        @Override public void dismiss(){if(stack==this)stack=null;super.dismiss();}
        @Override public void onBackPressed(){back();}
    }
    private static final class StackPanel extends LinearLayout {
        StackPanel(Activity activity){super(activity);setOrientation(1);}
        @Override protected void onMeasure(int width,int height){int max=(int)(getResources().getDisplayMetrics().heightPixels*.82f);if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)max=Math.min(max,View.MeasureSpec.getSize(height));int fixed=getPaddingTop()+getPaddingBottom();int childWidth=View.MeasureSpec.makeMeasureSpec(Math.max(1,View.MeasureSpec.getSize(width)-getPaddingLeft()-getPaddingRight()),View.MeasureSpec.EXACTLY);for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(!(child instanceof LimitedScroll)){child.measure(childWidth,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));fixed+=child.getMeasuredHeight();}}for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof LimitedScroll)((LimitedScroll)getChildAt(i)).limit=Math.max(1,max-fixed);super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));}
    }
    private static final class LimitedScroll extends ScrollView {
        int limit=Integer.MAX_VALUE;
        LimitedScroll(Activity activity){super(activity);}
        @Override protected void onMeasure(int width,int height){int max=limit;int supplied=View.MeasureSpec.getSize(height);if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)max=Math.min(max,supplied);super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(Math.max(1,max),View.MeasureSpec.AT_MOST));}
    }
}
