package com.xoclassic.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import org.json.*;
import java.util.concurrent.*;

/** Native forum views. Forum HTML is parsed as data; external previews have their own isolated renderers. */
public final class MainActivity extends Activity {
    private static final int POST_HTML_FLAGS=Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV|Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM;
    private final RenderedPostCache renderedPosts=new RenderedPostCache(POST_HTML_FLAGS);
    private static final int BG=0xff292b30,PANEL=0xff34383f,INK=0xffe1dfdc,MUTED=0xffa8adb5,BLUE=0xff9cbce3,LINE=0xff494e57,MONIKER=0xffdf9291;
    private final ExecutorService mediaIndexer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"thread-media-index");return t;});
    private UnreadJumpView unreadJump;
    private ReplyPreviews replyPreviews; private ReplyHighlights replyHighlights; private AppColors.Store themeStore; private String appliedTheme=""; private int appliedThemeHash; private final Runnable themeTick=new Runnable(){public void run(){if(!resumed)return;applyTheme();refreshHandler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private Future<?> mediaIndexTask;
    private List<ThreadMedia.Item> threadMediaItems=Collections.emptyList();
    private boolean threadMediaReady;
    private PopupMenu threadToolsPopup;
    private final ExecutorService findIndexer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"thread-find-index");return t;});
    private Future<?> findIndexTask,findMatchTask;
    private ThreadFind.Index findIndex;
    private ThreadFind.Results findResults;
    private ThreadFind.Hit findPreferred;
    private ForumParser.ThreadPage findSource;
    private String findThreadKey="",findQuery="";
    private boolean findOpen,findError,findJumpAfterIndex;
    private int findSelected=-1,findPageGeneration,findQueryGeneration,findNavigation;
    private LinearLayout threadFindBar;
    private EditText findInput;
    private TextView findCounter;
    private Button findPrevious,findNext;
    private final Runnable findDebounce=()->searchThreadFind(true);
    private final ForegroundExecutor worker=new ForegroundExecutor();
    private android.content.SharedPreferences preferences;
    private SecureVault vault; private AppLock appLock; private boolean appContentStarted,activityResumed; private Intent deferredUploadResult;
    private ForumClient client;
    private LinearLayout root,content,toolbar;
    private ScrollView scroll;
    private LazyThreadRenderer lazyThread;
    private DeferredBoardRenderer deferredBoard;
    private TextView status;
    private boolean busy;
    private boolean automaticSessionBusy;
    private volatile boolean destroyed;
    private volatile int generation;
    private int page=1;
    private String screen="board",filter="All",search="",draft="",subject="",parent="";
    private List<ForumParser.ThreadItem> threads=new ArrayList<>();
    private ForumParser.ThreadPage current;
    private ForumParser.Form composeForm;
    private static final int PICK_UPLOAD_IMAGE=7314;
    private ImageUpload activeUpload;
    private ForumParser.Form pickerOwner;
    private boolean uploadSettingsFromComposer;
    private String accountSection="Login",lastAccountSection="Login",lastPrivacySection="AppLock"; private boolean savedAccountsUnlocked=false; private AlertDialog savedAccountDialog; private static final int UNLOCK_SAVED_ACCOUNTS=8462,DISABLE_ACCOUNTS_PIN=8463;
    private int accountsCredentialRequest;
    private String nicknameSelectedAccount="",nicknameSelectedOrigin="";
    private int helpSection=-1,helpTopic=-1;
    private static final class ThreadTab {String origin,id,title,own="";ReadingAnchor anchor;}
    private final LinkedHashMap<String,ThreadTab> threadTabs=new LinkedHashMap<>();
    private String activeTab="";
    private Dialog tabsDialog;
    private ThreadTab recentlyClosedTab;
    private int recentlyClosedIndex;
    private long undoUntil;
    private LinearLayout undoTabRow;
    private final Runnable expireTabUndo=()->{recentlyClosedTab=null;if(undoTabRow!=null)undoTabRow.setVisibility(View.GONE);};
    private final Runnable persistTabPosition=()->saveThreadTab();
    private ReadingAnchor replyReturnAnchor;
    private String replyReturnThread="";
    private final List<LinkPreviewDialog> previews=new ArrayList<>();
    private EditText messageInput,subjectInput;
    private long checked;
    private Boolean sessionSignedIn;
    private static final long REFRESH_MS=60000;
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private boolean resumed,autoBusy;
    private volatile int viewRevision,operationRevision;
    private long lastInteraction,lastAutoRefresh;
    private EditText searchInput;
    private TextView hiddenInfo;
    private LinearLayout threadHideNotice;
    private String hiddenUndoOrigin="",hiddenUndoId="";
    private HiddenThreadPool.Change hiddenUndoChange;
    private long hiddenUndoUntil;
    private final Runnable expireHiddenThreadUndo=()->{hiddenUndoOrigin="";hiddenUndoId="";hiddenUndoChange=null;removeThreadHideNotice();};
    private TextView ownCounter;
    private Button ownPrevious,ownNext;
    private final List<String> ownPosts=new ArrayList<>();
    private String ownThread="",ownSelected="";
    private int ownIndex=-1;
    private long ownJumpUntil;
    private boolean authorBusy;
    private ReadCancellation authorCancellation;
    private String authorRequestId="";
    private long authorRetryAt;
    private static final int THREAD_LIMIT=30;
    private int loadedThrough=1;
    private boolean boardExhausted,backfillBusy;
    private long backfillRetryAt;
    private final Set<String> baseThreadIds=new HashSet<>();
    private final Map<String,Long> authorFailures=new HashMap<>();
    private final Runnable authorTick=()->resolveVisibleAuthor();
    private final Runnable automaticSessionTick=new Runnable(){public void run(){
        if(!resumed||destroyed||client==null||System.currentTimeMillis()-checked<=60000)return;
        if(busy||pendingSearch()||pullingToRefresh()||SystemClock.uptimeMillis()-lastInteraction<1500){refreshHandler.postDelayed(this,300);return;}
        checkSessionWhenIdle();
    }};
    private void scheduleSessionCheck(){refreshHandler.removeCallbacks(automaticSessionTick);if(resumed&&!destroyed&&!automaticSessionBusy&&client!=null&&System.currentTimeMillis()-checked>60000)refreshHandler.postDelayed(automaticSessionTick,1500);}
    private void checkSessionWhenIdle(){
        if(automaticSessionBusy||busy||destroyed)return;
        automaticSessionBusy=true;checked=System.currentTimeMillis();cancelBackgroundAuthor("");operationRevision++;
        final ForumClient source=client;final int host=generation,operation=operationRevision;
        // Keep session/login work exclusive, without disabling controls or shifting the reading view.
        worker.execute(()->{if(destroyed||host!=generation||source!=client||operation!=operationRevision){runOnUiThread(()->{automaticSessionBusy=false;if(!destroyed){scheduleSessionCheck();scheduleAuthors();}});return;}boolean signed=false;Exception failure=null;try{signed=source.maintainSession();}catch(Exception e){failure=e;}final boolean result=signed;final Exception error=failure;
            runOnUiThread(()->{automaticSessionBusy=false;if(destroyed)return;if(host!=generation||source!=client){scheduleSessionCheck();return;}
                if(error==null)sessionSignedIn=result;
                else Toast.makeText(this,"Couldn't check the saved session. Use Account if sign-in is needed.",Toast.LENGTH_LONG).show();
                if(!busy)idleStatus();scheduleAuthors();
            });
        });
    }
    private final Runnable refreshTick=new Runnable(){public void run(){if(!resumed)return;refreshVisible();refreshHandler.postDelayed(this,REFRESH_MS);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);themeStore=new AppColors.Store(this);applyTheme();
        preferences=getSharedPreferences("classic",MODE_PRIVATE);vault=new SecureVault(this);
        client=new ForumClient(this,preferences.getString("origin","https://xoxohth.com"),vault);
        getWindow().setStatusBarColor(AppColors.color(BG));getWindow().setNavigationBarColor(AppColors.color(BG));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        appLock=new AppLock(this,preferences,this::afterAppUnlock,this::appLockSettingChanged);if(!appLock.locked())startAppContent();
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(replyHighlights!=null){replyHighlights.setActive(resumed&&focused);}}
    @Override protected void onResume(){super.onResume();activityResumed=true;if(appLock!=null&&!appLock.resume()){resumed=false;return;}resumeAppContent();}
    private void resumeAppContent(){resumed=true;applyTheme();refreshHandler.removeCallbacks(themeTick);refreshHandler.postDelayed(themeTick,60000-System.currentTimeMillis()%60000);if(replyHighlights!=null)replyHighlights.setActive(true);for(LinkPreviewDialog dialog:previews)dialog.resumeMedia();refreshHandler.removeCallbacks(refreshTick);refreshHandler.postDelayed(refreshTick,REFRESH_MS);scheduleAuthors();scheduleSessionCheck();}
    @Override protected void onPause(){operationRevision++;activityResumed=false;if(threadToolsPopup!=null)threadToolsPopup.dismiss();if(appLock!=null)appLock.background();if(savedAccountDialog!=null){savedAccountDialog.dismiss();savedAccountDialog=null;}savedAccountsUnlocked=false;if(screen.equals("account")&&accountSection.equals("Saved")&&accountsPinRequired())account();saveThreadTab();resumed=false;refreshHandler.removeCallbacks(themeTick);if(replyHighlights!=null)replyHighlights.setActive(false);for(LinkPreviewDialog dialog:previews)dialog.pauseMedia();refreshHandler.removeCallbacks(refreshTick);refreshHandler.removeCallbacks(authorTick);refreshHandler.removeCallbacks(automaticSessionTick);super.onPause();}
    @Override public void onUserInteraction(){super.onUserInteraction();lastInteraction=SystemClock.uptimeMillis();}
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);for(LinkPreviewDialog dialog:previews)dialog.resize();if(screen.equals("thread")&&current!=null){ReadingAnchor anchor=readingAnchor();thread();ScrollView rotated=scroll;rotated.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){rotated.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(rotated==scroll)restoreAnchor(anchor);}});}}
    @Override protected void onDestroy(){destroyed=true;if(replyPreviews!=null)replyPreviews.leave();if(appLock!=null)appLock.close();refreshHandler.removeCallbacks(themeTick);resetThreadFind();findIndexer.shutdownNow();refreshHandler.removeCallbacks(expireHiddenThreadUndo);removeThreadHideNotice();refreshHandler.removeCallbacks(persistTabPosition);refreshHandler.removeCallbacks(expireTabUndo);if(tabsDialog!=null)tabsDialog.dismiss();if(activeUpload!=null)activeUpload.cancel();if(replyHighlights!=null)replyHighlights.close();mediaIndexer.shutdownNow();cancelBackgroundAuthor("");if(lazyThread!=null)lazyThread.close();if(deferredBoard!=null)deferredBoard.close();renderedPosts.clear();for(LinkPreviewDialog dialog:new ArrayList<>(previews))dialog.dismiss();refreshHandler.removeCallbacks(refreshTick);refreshHandler.removeCallbacks(authorTick);refreshHandler.removeCallbacks(automaticSessionTick);worker.shutdown();super.onDestroy();}
    private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private void applyTheme(){if(themeStore==null)return;String name=themeStore.current();int[] palette=themeStore.themes().get(name);int hash=Arrays.hashCode(palette);if(name.equals(appliedTheme)&&hash==appliedThemeHash)return;appliedTheme=name;appliedThemeHash=hash;AppColors.apply(palette);getTheme().applyStyle(AppColors.light()?R.style.AppThemeLight:R.style.AppTheme,true);AppColors.window(getWindow());if(root!=null)AppColors.nativeTree(root);if(replyHighlights!=null)replyHighlights.retheme();if(tabsDialog!=null)AppColors.window(tabsDialog.getWindow());for(LinkPreviewDialog dialog:previews)AppColors.window(dialog.getWindow());}    private GradientDrawable box(int color,int radius){GradientDrawable d=new GradientDrawable();AppColors.fill(d,color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);AppColors.text(t,color);t.setTypeface(Typeface.SERIF);return t;}
    private void gap(LinearLayout layout,int n){View v=new View(this);layout.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);AppColors.text(b,BLUE);b.setTypeface(Typeface.SERIF);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(10),0,dp(10),0);b.setBackground(box(0xff000002,10));b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    private void addButton(LinearLayout r,String label,Runnable action){Button b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(b,p);}
    private Button boardButton(String label,Runnable action){Button b=button(label,action);b.setTextSize(16);return b;}
    private void addBoardButton(LinearLayout row,String label,Runnable action){if(row.getChildCount()>0){View space=new View(this);row.addView(space,new LinearLayout.LayoutParams(dp(8),1));}row.addView(boardButton(label,action),new LinearLayout.LayoutParams(0,dp(48),1));}
    private EditText field(String hint,boolean multiline){EditText e=new EditText(this);AppColors.text(e,INK);AppColors.hint(e,MUTED);e.setTextSize(17);e.setTypeface(Typeface.SERIF);e.setHint(hint);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(box(0xff000003,10));e.setSingleLine(!multiline);return e;}
    private void shell(String title){ if(!screen.equals("thread")&&replyPreviews!=null)replyPreviews.leave();if(threadToolsPopup!=null)threadToolsPopup.dismiss();if(!screen.equals("thread"))resetThreadFind();else detachThreadFindBar();removeThreadHideNotice();if(!screen.equals("account")){savedAccountsUnlocked=false;clearAccountPrivacy();}
        viewRevision++;if(replyHighlights!=null){replyHighlights.close();replyHighlights=null;}if(mediaIndexTask!=null){mediaIndexTask.cancel(true);mediaIndexTask=null;}
        if(deferredBoard!=null){deferredBoard.close();deferredBoard=null;}
        if(lazyThread!=null){lazyThread.close();lazyThread=null;}
        messageInput=null;subjectInput=null;
        root=column();AppColors.background(root,BG);root.setPadding(dp(14),dp(8),dp(14),dp(8));
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(14),dp(8)+i.getSystemWindowInsetTop(),dp(14),dp(8)+i.getSystemWindowInsetBottom());return i;});
        toolbar=row();if(screen.equals("thread")){toolbar.setTag("thread-title-row");toolbar.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",()->{board();loadBoard();});back.setTextSize(26);back.setPadding(0,0,0,0);back.setBackgroundColor(android.graphics.Color.TRANSPARENT);back.setContentDescription("Back to main page");toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));ThreadHeadingView heading=new ThreadHeadingView(this,18,16);heading.setText(current.title);AppColors.text(heading,INK);LinearLayout.LayoutParams headingSpace=new LinearLayout.LayoutParams(0,-2,1);headingSpace.setMargins(dp(6),0,dp(4),0);toolbar.addView(heading,headingSpace);addTabsButton();}else{if(screen.equals("account"))toolbar.addView(button("‹ Back",()->{if(!helpBack()){board();loadBoard();}}));TextView name=text(title,24,INK);if(screen.equals("account"))name.setPadding(dp(12),0,0,0);name.setTypeface(Typeface.SERIF,Typeface.BOLD);toolbar.addView(name,new LinearLayout.LayoutParams(0,-2,1));if(screen.equals("board"))addTabsButton();if(!screen.equals("account"))toolbar.addView(button("Account",this::account));}root.addView(toolbar);if(screen.equals("board"))gap(root,2);
        status=text("",12,MUTED);status.setPadding(0,dp(5),0,dp(8));root.addView(status);idleStatus();
        PullToRefreshScrollView refreshScroll=new PullToRefreshScrollView(this);scroll=refreshScroll;refreshScroll.setPullRefresh(()->scroll==refreshScroll&&!busy&&!pendingSearch()&&(screen.equals("board")||screen.equals("thread")),()->{if(scroll!=refreshScroll||busy)return;if(screen.equals("board"))loadBoard();else if(screen.equals("thread")&&current!=null)openThread(current.id);});scroll.setFillViewport(true);content=column();scroll.addView(content);unreadJump=null;if(screen.equals("thread")){FrameLayout readingLayer=new FrameLayout(this);readingLayer.setTag("thread-reading-layer");readingLayer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));unreadJump=new UnreadJumpView(this);unreadJump.setCount(0);unreadJump.setTooltipText("Jump to closest unread reply");unreadJump.setOnClickListener(v->{if(!busy)jumpClosestUnread();});FrameLayout.LayoutParams jumpSpace=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.BOTTOM|Gravity.RIGHT);jumpSpace.setMargins(dp(12),dp(12),dp(12),dp(16));readingLayer.addView(unreadJump,jumpSpace);content.setPadding(0,0,0,dp(80));root.addView(readingLayer,new LinearLayout.LayoutParams(-1,0,1));}else root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.post(()->{AppColors.nativeTree(root);AppColors.window(getWindow());});root.requestApplyInsets();
    }
    private interface Job<T>{T get()throws Exception;}
    private interface Done<T>{void accept(T value);}
    private void idleStatus(){String value=(screen.equals("thread")||screen.equals("account"))?"":screen.equals("board")?(Boolean.FALSE.equals(sessionSignedIn)?"Signed out":""):client.origin.replace("https://", "");status.setText(value);status.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
    private void cancelBackgroundAuthor(String keep){refreshHandler.removeCallbacks(authorTick);ReadCancellation pending=authorCancellation;if(pending!=null&&(keep.isEmpty()||!pending.hasStarted()))pending.cancel();}
    private <T> void run(String label,Job<T> job,Done<T> done){run(label,job,done,"");}
    private <T> void run(String label,Job<T> job,Done<T> done,String keepAuthor){
        if(busy||appLock!=null&&appLock.locked())return;cancelBackgroundAuthor(keepAuthor);operationRevision++;busy=true;int token=generation;status.setVisibility(View.VISIBLE);status.setText(label);getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        Runnable action=()->{T value=null;Exception failure=null;try{value=job.get();}catch(Exception e){failure=e;}final T result=value;final Exception error=failure;
            runOnUiThread(()->{if(destroyed)return;busy=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);if(token!=generation)return;idleStatus();if(error!=null){if(label.equals("Signing in…"))sessionSignedIn=null;String note=error.getMessage();if(!(error instanceof ImageUpload.Failure)&&!(error instanceof ForumClient.PostingException)&&!(error instanceof IllegalArgumentException)&&!(note!=null&&(note.startsWith("Sign")||note.startsWith("Automatic")||note.startsWith("The ")||note.startsWith("Could")||note.startsWith("No ")||note.startsWith("Open")||note.startsWith("A previous")||note.startsWith("This ")||note.startsWith("Enter"))))note="Couldn't complete that request. Check your connection and try again.";
                showRequestError(error,note);scheduleAuthors();return;}done.accept(result);scheduleAuthors();});
        };if(keepAuthor.isEmpty())worker.execute(action);else worker.executeRead(action);
    }
    private AlertDialog showRequestError(Exception error,String note){
        boolean uncertain=error instanceof ForumClient.PostingException&&((ForumClient.PostingException)error).uncertain;
        boolean recover=client.pendingPost()&&(uncertain||note!=null&&note.startsWith("A previous"));
        AlertDialog.Builder dialog=new ThemeDialogs(this).setTitle(error instanceof ForumClient.PostingException?"Check your post":"XO Classic");
        if(recover){
            ForumClient pendingClient=client;
            dialog.setMessage((note==null?"The previous submission was not confirmed.":note)+"\n\nCheck By you or the thread first. If it appeared, do not submit the same draft again. After checking, unlock posting below. Nothing is resent automatically; your draft stays here.");
            dialog.setNegativeButton("Later",null).setPositiveButton("I've checked · unlock",(d,n)->{
                pendingClient.acknowledgePending();
                Toast.makeText(this,pendingClient.pendingPost()?"Could not unlock posting. Try again.":"Posting unlocked. Nothing was resent.",Toast.LENGTH_LONG).show();
            });
        }else dialog.setMessage(note==null?"Request failed.":note).setPositiveButton("OK",null);
        return dialog.show();
    }
    private void session(){if(busy)return;refreshHandler.removeCallbacks(automaticSessionTick);checked=System.currentTimeMillis();run("Checking saved session…",()->client.maintainSession(),ok->{sessionSignedIn=ok;if(screen.equals("account")&&accountSection.equals("Session"))account();else idleStatus();});}
    private String key(String suffix){return client.origin+":"+suffix;}
    private Set<String> hiddenAuthors(){return new HashSet<>(preferences.getStringSet("hiddenThreadAuthors",Collections.emptySet()));}
    private Set<String> hiddenThreadIds(String origin){return new HiddenThreadPool(preferences).ids(origin);}
    private HiddenThreadPool.Change setIndividualThreadHidden(String origin,String id,String title,boolean hidden){
        HiddenThreadPool pool=new HiddenThreadPool(preferences);if(hidden)return pool.hide(origin,id,title);pool.remove(origin,id);return null;
    }
    private ReadingAnchor anchorWithoutThread(String id){
        ReadingAnchor anchor=readingAnchor();if(!("thread:"+id).equals(anchor.tag))return anchor;
        View previous=null;boolean found=false;for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child.getVisibility()!=View.VISIBLE||!(child.getTag() instanceof String)||!((String)child.getTag()).startsWith("thread:"))continue;if(found){anchor.tag=(String)child.getTag();return anchor;}if(("thread:"+id).equals(child.getTag()))found=true;else previous=child;}
        if(previous!=null){anchor.tag=(String)previous.getTag();anchor.offset=scroll.getScrollY()-previous.getTop();}else anchor.tag=null;return anchor;
    }
    private void hideBoardThread(ForumParser.ThreadItem item){
        if(busy||!screen.equals("board")||hiddenThreadIds(client.origin).contains(item.id))return;
        ReadingAnchor anchor=anchorWithoutThread(item.id);hiddenUndoChange=setIndividualThreadHidden(client.origin,item.id,item.title,true);
        hiddenUndoOrigin=client.origin;hiddenUndoId=item.id;long duration=6000;
        android.view.accessibility.AccessibilityManager access=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);if(Build.VERSION.SDK_INT>=29&&access!=null)duration=access.getRecommendedTimeoutMillis(6000,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT|android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS);
        hiddenUndoUntil=SystemClock.uptimeMillis()+duration;refreshHandler.removeCallbacks(expireHiddenThreadUndo);refreshHandler.postDelayed(expireHiddenThreadUndo,duration);
        applyAuthorHiding();showThreadHideNotice();restoreAnchor(anchor);
    }
    private void removeThreadHideNotice(){if(threadHideNotice!=null&&threadHideNotice.getParent() instanceof ViewGroup)((ViewGroup)threadHideNotice.getParent()).removeView(threadHideNotice);threadHideNotice=null;}
    private void showThreadHideNotice(){
        removeThreadHideNotice();if(!screen.equals("board")||hiddenUndoId.isEmpty()||!hiddenUndoOrigin.equals(client.origin)||SystemClock.uptimeMillis()>=hiddenUndoUntil)return;
        threadHideNotice=row();threadHideNotice.setTag("thread-hidden-notice");threadHideNotice.setBackground(box(PANEL,12));threadHideNotice.setPadding(dp(12),dp(4),dp(4),dp(4));TextView message=text("Thread hidden",15,INK);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);threadHideNotice.addView(message,new LinearLayout.LayoutParams(0,-2,1));Button undo=button("Undo",this::undoHiddenThread);undo.setTag("undo-hide-thread");undo.setOnClickListener(v->undoHiddenThread());threadHideNotice.addView(undo,new LinearLayout.LayoutParams(-2,dp(44)));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(0,dp(8),0,0);root.addView(threadHideNotice,space);
    }
    private void undoHiddenThread(){
        if(hiddenUndoId.isEmpty()||SystemClock.uptimeMillis()>=hiddenUndoUntil){expireHiddenThreadUndo.run();return;}
        String origin=hiddenUndoOrigin,id=hiddenUndoId;ReadingAnchor anchor=screen.equals("board")?readingAnchor():null;if(hiddenUndoChange!=null)new HiddenThreadPool(preferences).undo(hiddenUndoChange);else setIndividualThreadHidden(origin,id,"",false);refreshHandler.removeCallbacks(expireHiddenThreadUndo);expireHiddenThreadUndo.run();
        if(screen.equals("board")&&client.origin.equals(origin)){applyAuthorHiding();restoreAnchor(anchor);}
    }
    private boolean authorContextAllowed(){return search.isEmpty()&&!filter.equals("By you");}
    private boolean authorEligible(ForumParser.ThreadItem item){if(!authorContextAllowed())return false;String createdKey=key("createdAt:"+item.id);long date=preferences.contains(createdKey)?preferences.getLong(createdKey,0):item.createdAt>0?item.createdAt:ForumParser.createdAt(item.date);return ForumParser.recentAuthor(date,System.currentTimeMillis());}
    private String knownAuthor(String id){if(screen.equals("board")&&!authorContextAllowed())return "";if(!ForumParser.recentAuthor(preferences.getLong(key("createdAt:"+id),0),System.currentTimeMillis()))return "";return preferences.getString(key("author:"+id),"");}
    private boolean hiddenThread(String id){String author=knownAuthor(id);return hiddenThreadIds(client.origin).contains(id)||!author.isEmpty()&&hiddenAuthors().contains(author);}
    private void rememberAuthor(ForumParser.ThreadPage page){rememberAuthor(client.origin,page);}
    private void rememberAuthor(String origin,ForumParser.ThreadPage page){if(page.posts.isEmpty())return;android.content.SharedPreferences.Editor edit=preferences.edit().putLong(origin+":createdAt:"+page.id,page.createdAt);if(ForumParser.recentAuthor(page.createdAt,System.currentTimeMillis()))edit.putString(origin+":author:"+page.id,page.posts.get(0).author);else edit.remove(origin+":author:"+page.id);edit.apply();}
    private static final java.time.format.DateTimeFormatter BOARD_CREATED_DATE=java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy",Locale.US).withZone(java.time.ZoneId.of("America/New_York"));
    private String threadMeta(ForumParser.ThreadItem item){long created=preferences.getLong(key("createdAt:"+item.id),0);return item.count+" replies"+(created<=0?"":"  ·  "+BOARD_CREATED_DATE.format(java.time.Instant.ofEpochMilli(created)));}
    private void updateThreadMeta(ForumParser.ThreadItem item){TextView meta=content.findViewWithTag("meta:"+item.id),author=content.findViewWithTag("author:"+item.id);if(meta!=null)meta.setText(threadMeta(item));if(author!=null)author.setText(knownAuthor(item.id));}
    private TextView threadTitle(ForumParser.ThreadItem item,boolean fresh){boolean visited=preferences.contains(key("seen:"+item.id));TextView title=text("",18,visited?0xffd1b8d4:BLUE);SpannableStringBuilder label=new SpannableStringBuilder((fresh?"★ ":"")+item.title);if(fresh)label.setSpan(new AppColors.Ink(0xff000004),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);title.setText(label);title.setTag("title:"+item.id);title.setContentDescription(item.title+(visited?". Visited thread.":"")+(fresh?". New replies since your last visit.":""));return title;}
    private void setAuthorHidden(String author,boolean hide){if(author.isEmpty())return;Set<String> names=hiddenAuthors();if(hide)names.add(author);else names.remove(author);preferences.edit().putStringSet("hiddenThreadAuthors",names).apply();Toast.makeText(this,hide?"Threads by "+author+" are hidden":"Threads by "+author+" are visible",Toast.LENGTH_SHORT).show();if(screen.equals("thread")){board();}else if(screen.equals("board")){ReadingAnchor a=readingAnchor();board();restoreAnchor(a);}}
    private void authorActions(String author){if(screen.equals("thread")&&!ForumParser.recentAuthor(current.createdAt,System.currentTimeMillis()))return;boolean hidden=hiddenAuthors().contains(author);new ThemeDialogs(this).setTitle(author).setItems(new String[]{hidden?"Show threads by this poster":"Hide threads by this poster"},(d,n)->setAuthorHidden(author,!hidden)).setNegativeButton("Cancel",null).show();}
    private void manageHiddenAuthors(){List<String> names=new ArrayList<>(hiddenAuthors());Collections.sort(names);if(names.isEmpty()){new ThemeDialogs(this).setTitle("Hidden thread authors").setMessage("None yet. Hold a thread on the main page, or tap a poster's name inside a thread, to hide their threads.").setPositiveButton("OK",null).show();return;}new ThemeDialogs(this).setTitle("Tap a poster to unhide their threads").setItems(names.toArray(new String[0]),(d,n)->setAuthorHidden(names.get(n),false)).setNegativeButton("Close",null).show();}
    private void threadActions(ForumParser.ThreadItem item){
        List<String> actions=new ArrayList<>();actions.add("Open in new tab");actions.add("Hide this thread");actions.add("Copy URL");String author=knownAuthor(item.id);if(authorEligible(item))actions.add(author.isEmpty()?"Hide threads by this author":"Hide threads by "+author);
        new ThemeDialogs(this).setTitle(item.title).setItems(actions.toArray(new String[0]),(d,n)->{if(n==0){openBackgroundThreadTab(item);}else if(n==1)hideBoardThread(item);else if(n==2)copyThreadUrl(item);else if(!author.isEmpty())setAuthorHidden(author,true);else run("Finding thread author…",()->client.author(item.id,item.count),result->{rememberAuthor(result);String name=knownAuthor(result.id);if(!name.isEmpty())setAuthorHidden(name,true);});}).setNegativeButton("Cancel",null).show();
    }
    private void copyThreadUrl(ForumParser.ThreadItem item){
        try{String url=ForumParser.safeUrl(client.origin,item.url==null||item.url.isEmpty()?"/thread.php?thread_id="+item.id+"&forum_id=2":item.url);android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Thread link",url));Toast.makeText(this,"URL copied",Toast.LENGTH_SHORT).show();}catch(Exception unavailable){Toast.makeText(this,"Could not copy this thread URL.",Toast.LENGTH_SHORT).show();}
    }
    private void scheduleAuthors(){refreshHandler.removeCallbacks(authorTick);if(resumed&&!destroyed&&!automaticSessionBusy&&screen.equals("board")&&authorContextAllowed())refreshHandler.postDelayed(authorTick,350);}
    private boolean limitedBoard(){return search.isEmpty()&&filter.equals("All");}
    private String serverMode(String selected){return selected.equals("By you")||selected.equals("Poasted in")?selected:"All";}
    private void selectBoardFilter(String selected){boolean changed=!serverMode(filter).equals(serverMode(selected));filter=selected;if(changed){page=1;threads=new ArrayList<>();board();loadBoard();}else board();}
    private void applyAuthorHiding(){
        if(!screen.equals("board"))return;int hidden=0,visible=0;boolean limited=limitedBoard();Set<String> individual=hiddenThreadIds(client.origin),authors=hiddenAuthors();
        for(int n=0;n<content.getChildCount();n++){View child=content.getChildAt(n);Object tag=child.getTag();if(!(tag instanceof String)||!((String)tag).startsWith("thread:"))continue;String id=((String)tag).substring(7);String author=knownAuthor(id);boolean blocked=individual.contains(id)||!author.isEmpty()&&authors.contains(author),show=!blocked&&(!limited||visible<THREAD_LIMIT);if(show)visible++;if(blocked)hidden++;child.setVisibility(show?View.VISIBLE:View.GONE);View separator=content.findViewWithTag("separator:"+id);if(separator!=null)separator.setVisibility(show?View.VISIBLE:View.GONE);}
        if(hiddenInfo!=null){hiddenInfo.setText(hidden+(hidden==1?" thread hidden · Manage":" threads hidden · Manage"));hiddenInfo.setVisibility(hidden>0?View.VISIBLE:View.GONE);}TextView empty=content.findViewWithTag("board-empty");if(empty!=null){empty.setText(hidden>0?"No visible threads here. Restore hidden threads in Account → Privacy → Threads.":filter.equals("New replies")?"No new replies in threads you've read on this page.":"The board will refresh automatically.");empty.setVisibility(visible==0?View.VISIBLE:View.GONE);}if(deferredBoard!=null)deferredBoard.request();if(limited&&visible<THREAD_LIMIT)loadMoreCandidates();
    }
    private void loadMoreCandidates(){
        if(client.backgroundPaused()||!resumed||destroyed||busy||automaticSessionBusy||autoBusy||backfillBusy||boardExhausted||!screen.equals("board")||!limitedBoard()||SystemClock.uptimeMillis()<backfillRetryAt||threads.isEmpty()||!baseThreadIds.contains(threads.get(0).id))return;
        final int sourcePage=loadedThrough+1,host=generation,view=viewRevision,operation=operationRevision,startPage=page;final ForumClient source=client;backfillBusy=true;
        worker.executeBackground(()->{if(destroyed||operation!=operationRevision||view!=viewRevision){runOnUiThread(()->backfillBusy=false);return;}List<ForumParser.ThreadItem> more=null;try{more=source.index("",sourcePage);}catch(Exception ignored){}final List<ForumParser.ThreadItem> result=more;
            runOnUiThread(()->{backfillBusy=false;if(destroyed||!resumed||pullingToRefresh()||host!=generation||view!=viewRevision||operation!=operationRevision||page!=startPage||!screen.equals("board")||!limitedBoard())return;
                if(result==null){backfillRetryAt=SystemClock.uptimeMillis()+60000;status.setVisibility(View.VISIBLE);status.setText("Couldn't load more threads · retrying automatically");return;}
                Set<String> seen=new HashSet<>();for(ForumParser.ThreadItem item:threads)seen.add(item.id);List<ForumParser.ThreadItem> combined=new ArrayList<>(threads);for(ForumParser.ThreadItem item:result)if(seen.add(item.id))combined.add(item);
                loadedThrough=sourcePage;boardExhausted=combined.size()==threads.size();if(!boardExhausted){ReadingAnchor anchor=readingAnchor();threads=combined;board();restoreAnchor(anchor);}else scheduleAuthors();
            });
        });
    }
    private void resolveVisibleAuthor(){
        if(!resumed||destroyed||authorBusy||!screen.equals("board")||!authorContextAllowed())return;
        if(client.backgroundPaused()){refreshHandler.postDelayed(authorTick,60000);return;}
        if(busy||automaticSessionBusy||autoBusy||pendingSearch()||pullingToRefresh()||SystemClock.uptimeMillis()-lastInteraction<1500){refreshHandler.postDelayed(authorTick,1600);return;}
        if(SystemClock.uptimeMillis()<authorRetryAt){refreshHandler.postDelayed(authorTick,Math.max(350,authorRetryAt-SystemClock.uptimeMillis()));return;}
        String next=null;int nextCount=0;int y=scroll.getScrollY(),bottom=y+scroll.getHeight();boolean scanHidden=!hiddenAuthors().isEmpty();
        for(int pass=0;pass<(scanHidden?2:1)&&next==null;pass++){
        for(int n=0;n<content.getChildCount();n++){View child=content.getChildAt(n);Object tag=child.getTag();if(child.getVisibility()!=View.VISIBLE||((pass==0&&(child.getBottom()<y||child.getTop()>bottom))||(pass==1&&child.getBottom()>=y&&child.getTop()<=bottom))||!(tag instanceof String)||!((String)tag).startsWith("thread:"))continue;String id=((String)tag).substring(7);ForumParser.ThreadItem item=null;for(ForumParser.ThreadItem candidate:threads)if(candidate.id.equals(id)){item=candidate;break;}if(item==null||!authorEligible(item)||!knownAuthor(id).isEmpty()||SystemClock.uptimeMillis()-authorFailures.getOrDefault(key(id),-60000L)<60000)continue;next=id;nextCount=item.count;break;}}
        if(next==null){applyAuthorHiding();return;}
        final int count=nextCount;final String id=next,origin=client.origin;final ForumClient source=client;final int host=generation;authorBusy=true;final ReadCancellation cancellation=new ReadCancellation();authorCancellation=cancellation;authorRequestId=id;
        worker.executeBackground(()->{ForumParser.ThreadPage loaded=null;try{cancellation.begin();loaded=source.author(id,count,cancellation);}catch(Exception ignored){}final ForumParser.ThreadPage result=loaded;
            runOnUiThread(()->{authorBusy=false;if(authorCancellation==cancellation){authorCancellation=null;authorRequestId="";}if(destroyed)return;if(cancellation.isCancelled()){scheduleAuthors();return;}if(result!=null)rememberAuthor(origin,result);else {authorFailures.put(origin+":"+id,SystemClock.uptimeMillis());authorRetryAt=SystemClock.uptimeMillis()+60000;}if(host!=generation||!screen.equals("board"))return;
                ReadingAnchor anchor=readingAnchor();for(ForumParser.ThreadItem item:threads)if(item.id.equals(id)){updateThreadMeta(item);break;}
                applyAuthorHiding();restoreAnchor(anchor);scheduleAuthors();
            });
        });
    }
    private static final class ReadingAnchor {String tag;int offset,y;}
    private ReadingAnchor readingAnchor(){ReadingAnchor a=new ReadingAnchor();a.y=scroll.getScrollY();for(int n=0;n<content.getChildCount();n++){View v=content.getChildAt(n);if(v.getVisibility()==View.VISIBLE&&v.getTag() instanceof String&&v.getBottom()>a.y){a.tag=(String)v.getTag();a.offset=a.y-v.getTop();break;}}return a;}
    private void restoreAnchor(ReadingAnchor a){if(lazyThread!=null&&a.tag!=null&&lazyThread.position(a.tag,a.offset))return;if(deferredBoard!=null&&a.tag!=null&&deferredBoard.position(a.tag,a.offset))return;ScrollView target=scroll;LinearLayout body=content;target.post(()->{if(target!=scroll)return;View anchor=a.tag==null?null:body.findViewWithTag(a.tag);target.scrollTo(0,anchor==null?a.y:anchor.getTop()+a.offset);});}
    private boolean sameBoard(List<ForumParser.ThreadItem> a,List<ForumParser.ThreadItem> b){if(a.size()!=b.size())return false;for(int n=0;n<a.size();n++){ForumParser.ThreadItem x=a.get(n),y=b.get(n);if(!Objects.equals(x.id,y.id)||!Objects.equals(x.title,y.title)||!Objects.equals(x.date,y.date)||x.count!=y.count)return false;}return true;}
    private boolean sameThread(ForumParser.ThreadPage a,ForumParser.ThreadPage b){if(!Objects.equals(a.id,b.id)||!Objects.equals(a.title,b.title)||a.posts.size()!=b.posts.size())return false;for(int n=0;n<a.posts.size();n++){ForumParser.Post x=a.posts.get(n),y=b.posts.get(n);if(x.editable!=y.editable||!Objects.equals(x.id,y.id)||!Objects.equals(x.parent,y.parent)||!Objects.equals(x.author,y.author)||!Objects.equals(x.date,y.date)||!Objects.equals(x.html,y.html))return false;}return true;}
    private boolean pendingSearch(){return screen.equals("board")&&searchInput!=null&&!searchInput.getText().toString().equals(search);}
    private boolean swipingThread;
    private boolean pullingToRefresh(){return tabsDialog!=null||swipingThread|| !previews.isEmpty()||scroll instanceof PullToRefreshScrollView&&((PullToRefreshScrollView)scroll).isPulling();}
    private void refreshVisible(){
        if(client.backgroundPaused()||!resumed||destroyed||busy||automaticSessionBusy||autoBusy||backfillBusy||pullingToRefresh()||SystemClock.uptimeMillis()-lastInteraction<2000||pendingSearch()||!(screen.equals("board")||screen.equals("thread")))return;
        final String visible=screen,id=current==null?"":current.id,query=search,mode=serverMode(filter);final int whichPage=page,view=viewRevision,operation=operationRevision,host=generation;final ForumClient source=client;
        autoBusy=true;
        worker.executeBackground(()->{if(destroyed||operation!=operationRevision||view!=viewRevision){runOnUiThread(()->autoBusy=false);return;}List<ForumParser.ThreadItem> items=null;ForumParser.ThreadPage updated=null;Exception failure=null;
            try{if(visible.equals("board"))items=source.index(query,whichPage,mode);else updated=source.thread(id);}catch(Exception e){failure=e;}
            final List<ForumParser.ThreadItem> boardResult=items;final ForumParser.ThreadPage threadResult=updated;final boolean failed=failure!=null;
            runOnUiThread(()->{autoBusy=false;if(destroyed||!resumed||busy||pullingToRefresh()||host!=generation||view!=viewRevision||operation!=operationRevision||!visible.equals(screen)||pendingSearch())return;
                if(failed){status.setVisibility(View.VISIBLE);status.setText("Connection unavailable · retrying automatically");return;}
                ReadingAnchor anchor=readingAnchor();
                if(visible.equals("board")){List<ForumParser.ThreadItem> refreshed=new ArrayList<>(boardResult);if(limitedBoard()&&!boardResult.isEmpty()){Set<String> ids=new HashSet<>();for(ForumParser.ThreadItem item:refreshed)ids.add(item.id);for(ForumParser.ThreadItem item:threads)if(!baseThreadIds.contains(item.id)&&ids.add(item.id))refreshed.add(item);}baseThreadIds.clear();for(ForumParser.ThreadItem item:boardResult)baseThreadIds.add(item.id);if(!sameBoard(threads,refreshed)){threads=refreshed;board();restoreAnchor(anchor);}}
                else if(!sameThread(current,threadResult)){current=threadResult;thread();restoreAnchor(anchor);}
                if(screen.equals("board")){for(ForumParser.ThreadItem item:threads)updateThreadMeta(item);applyAuthorHiding();}
                lastAutoRefresh=SystemClock.uptimeMillis();idleStatus();scheduleAuthors();
            });
        });
    }
    private void bindBoardRow(LinearLayout card,ForumParser.ThreadItem item,boolean fresh){card.setPadding(dp(7),dp(13),dp(7),dp(13));TextView title=threadTitle(item,fresh);card.addView(title);
            LinearLayout metadata=row();TextView author=text(knownAuthor(item.id),12,MONIKER);author.setTag("author:"+item.id);author.setSingleLine(true);author.setEllipsize(TextUtils.TruncateAt.END);author.setPadding(0,0,dp(8),0);metadata.addView(author,new LinearLayout.LayoutParams(0,-2,1));TextView meta=text(threadMeta(item),12,MUTED);meta.setTag("meta:"+item.id);meta.setSingleLine(true);meta.setGravity(Gravity.END);metadata.addView(meta,new LinearLayout.LayoutParams(-2,-2));gap(card,5);card.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
    }
    private void board(){saveThreadTab();swipingThread=false;screen="board";shell("AutoAdmit");
        EditText q=field("Search threads",false);q.setText(search);q.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput=q;q.setTextSize(16);q.setPadding(dp(12),0,dp(12),0);q.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);q.setTag("search-input");
        q.setOnEditorActionListener((v,a,event)->{search=q.getText().toString();page=1;hideKeyboard(q);loadBoard();return true;});
        LinearLayout controls=column();controls.setTag("board-controls");content.addView(controls);
        LinearLayout searchRow=row();searchRow.setTag("search-row");searchRow.addView(q,new LinearLayout.LayoutParams(0,dp(48),1));
        Button searchAction=boardButton(search.isEmpty()?"Search":"Clear",()->{hideKeyboard(q);if(!search.isEmpty()){search="";filter="All";q.setText("");}else search=q.getText().toString();page=1;loadBoard();});searchAction.setTag("search-action");LinearLayout.LayoutParams searchSpace=new LinearLayout.LayoutParams(dp(82),dp(48));searchSpace.setMargins(dp(8),0,0,0);searchRow.addView(searchAction,searchSpace);ComposeButtonView composeButton=new ComposeButtonView(this);composeButton.setTag("new-thread");composeButton.setTooltipText("New thread");composeButton.setBackground(box(0xff000002,10));composeButton.setOnClickListener(v->{if(!busy)compose("",true,"");});LinearLayout.LayoutParams composeSpace=new LinearLayout.LayoutParams(dp(46),dp(48));composeSpace.setMargins(dp(8),0,0,0);searchRow.addView(composeButton,composeSpace);controls.addView(searchRow);
        gap(controls,8);LinearLayout filters=row();filters.setTag("board-filters");for(String f:new String[]{"All","New replies","By you","Poasted in"}){LinearLayout cell=column();Button choice=button(f,()->selectBoardFilter(f));choice.setTag("board-filter:"+f);choice.setTextSize(12);choice.setPadding(dp(2),0,dp(2),0);choice.setGravity(Gravity.CENTER);choice.setMaxLines(2);choice.setSelected(filter.equals(f));AppColors.text(choice,filter.equals(f)?INK:BLUE);AppColors.background(choice,Color.TRANSPARENT);cell.addView(choice,new LinearLayout.LayoutParams(-1,-2));View underline=new View(this);AppColors.background(underline,filter.equals(f)?BLUE:LINE);cell.addView(underline,new LinearLayout.LayoutParams(-1,dp(2)));filters.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}controls.addView(filters);
        hiddenInfo=text("",13,BLUE);hiddenInfo.setPadding(0,dp(10),0,dp(10));hiddenInfo.setOnClickListener(v->{accountSection="Hidden";account();});content.addView(hiddenInfo);
        android.view.accessibility.AccessibilityManager access=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        if(threads.size()>8&&(access==null||!access.isTouchExplorationEnabled()))deferredBoard=new DeferredBoardRenderer(scroll,content);
        int shown=0;
        for(ForumParser.ThreadItem item:threads){boolean fresh=preferences.contains(key("seen:"+item.id))&&item.count>preferences.getInt(key("seen:"+item.id),0);if(filter.equals("New replies")&&!fresh)continue;
            shown++;LinearLayout card=column();card.setTag("thread:"+item.id);
            if(filter.equals("New replies"))card.setOnTouchListener(new SwipeReadTouchListener(()->{if(busy)return;ReadingAnchor anchor=readingAnchor();preferences.edit().putInt(key("seen:"+item.id),Math.max(item.count,preferences.getInt(key("seen:"+item.id),0))).putString(key("title:"+item.id),item.title).apply();board();restoreAnchor(anchor);Toast.makeText(this,"Marked as read",Toast.LENGTH_SHORT).show();},active->swipingThread=active));else card.setOnTouchListener(new SwipeReadTouchListener(()->hideBoardThread(item),active->swipingThread=active));
            card.setOnClickListener(v->{if(!busy&&canOpenThread(client.origin,item.id)){saveThreadTab();run("Loading thread…",()->client.openThread(item.id,item.count),t->acceptThreadTab(t,false),item.id);}});card.setOnLongClickListener(v->{if(!busy)threadActions(item);return true;});content.addView(card);if(deferredBoard==null)bindBoardRow(card,item,fresh);else deferredBoard.add(card,dp(32)+(int)(64*getResources().getDisplayMetrics().scaledDensity),()->bindBoardRow(card,item,fresh));View line=new View(this);line.setTag("separator:"+item.id);AppColors.background(line,LINE);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        TextView empty=text("",16,MUTED);empty.setTag("board-empty");empty.setPadding(0,dp(24),0,0);empty.setVisibility(View.GONE);content.addView(empty);
        applyAuthorHiding();if(deferredBoard!=null){deferredBoard.prime(4);deferredBoard.request();}scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(deferredBoard!=null)deferredBoard.request();scheduleAuthors();});scheduleAuthors();showThreadHideNotice();
    }
    private void loadBoard(){run("Loading threads…",()->client.index(search,page,serverMode(filter)),items->{threads=items;loadedThrough=page;boardExhausted=items.isEmpty();backfillRetryAt=0;baseThreadIds.clear();for(ForumParser.ThreadItem item:items)baseThreadIds.add(item.id);board();scheduleSessionCheck();});}
    private void openThread(String id){if(!canOpenThread(client.origin,id))return;saveThreadTab();run("Loading thread…",()->client.thread(id),t->acceptThreadTab(t,false));}
    private String threadTabKey(String id){return client.origin+":"+id;}
    private boolean canOpenThread(String origin,String id){return true;}
    private boolean canAddThreadTab(String id){if(threadTabs.containsKey(threadTabKey(id))||threadTabs.size()<12)return true;Toast.makeText(this,"Close a tab before opening another (12 maximum).",Toast.LENGTH_LONG).show();return false;}
    private void saveThreadTab(){if(!screen.equals("thread")||current==null||scroll==null)return;ThreadTab tab=threadTabs.get(threadTabKey(current.id));if(tab!=null){tab.anchor=readingAnchor();tab.own=ownSelected;}persistThreadTabs();}
    private void openBackgroundThreadTab(ForumParser.ThreadItem item){
        if(!canAddThreadTab(item.id))return;String key=threadTabKey(item.id);if(threadTabs.containsKey(key)){Toast.makeText(this,"Thread is already open in a tab.",Toast.LENGTH_SHORT).show();return;}
        saveThreadTab();ThreadTab tab=new ThreadTab();tab.origin=client.origin;tab.id=item.id;tab.title=item.title;threadTabs.put(key,tab);persistThreadTabs();
        View count=toolbar.findViewWithTag("thread-tabs");if(count instanceof TabCountView)((TabCountView)count).setCount(threadTabs.size());Toast.makeText(this,"Opened in new tab",Toast.LENGTH_SHORT).show();
    }
    private void rememberThreadTab(){if(current==null)return;String key=threadTabKey(current.id);ThreadTab tab=threadTabs.get(key);activeTab=tab==null?"":key;if(tab!=null)tab.title=current.title;persistThreadTabs();}
    private void acceptThreadTab(ForumParser.ThreadPage page,boolean newTab){
        acceptThreadTab(page,newTab,"");
    }
    private void acceptThreadTab(ForumParser.ThreadPage page,boolean newTab,String postId){
        if(!canOpenThread(client.origin,page.id))return;
        boolean targeted=false;for(ForumParser.Post post:page.posts)if(post.id.equals(postId)){targeted=true;break;}
        String key=threadTabKey(page.id);ThreadTab existing=threadTabs.get(key);ReadingAnchor anchor=targeted||existing==null?null:existing.anchor;

        current=page;rememberThreadTab();ThreadTab selected=threadTabs.get(key);ownThread=page.id;ownSelected=selected==null?"":selected.own;thread();if(targeted)jump(postId);else if(anchor!=null)restoreAnchor(anchor);
    }
    private void switchThreadTab(String key){ThreadTab tab=threadTabs.get(key);if(tab==null)return;saveThreadTab();if(tabsDialog!=null)tabsDialog.dismiss();if(tab.origin.equals(client.origin)&&screen.equals("thread")&&current!=null&&current.id.equals(tab.id))return;
        navigateForumThread(tab.origin,tab.id,"",true);
    }
    private void navigateForumThread(String origin,String id,String postId,boolean newTab){
        if(busy||appLock!=null&&appLock.locked()||!canOpenThread(origin,id))return;saveThreadTab();
        final ForumClient source=origin.equals(client.origin)?client:new ForumClient(this,origin,vault);
        run("Loading thread…",()->source.thread(id),page->{
            for(LinkPreviewDialog preview:new ArrayList<>(previews))preview.dismiss();
            if(source!=client){generation++;renderedPosts.clear();client=source;preferences.edit().putString("origin",client.origin).apply();threads.clear();checked=0;sessionSignedIn=null;}
            acceptThreadTab(page,newTab,postId);scheduleSessionCheck();
        });
    }
    private String linkedPost(String url){try{String id=java.net.URI.create(url).getFragment();return id!=null&&id.matches("[0-9]{1,30}")?id:"";}catch(Exception invalid){return "";}}
    private void closeThreadTab(String key){saveThreadTab();int index=new ArrayList<>(threadTabs.keySet()).indexOf(key);ThreadTab removed=threadTabs.remove(key);if(removed==null)return;recentlyClosedTab=removed;recentlyClosedIndex=index;undoUntil=SystemClock.uptimeMillis()+6000;refreshHandler.removeCallbacks(expireTabUndo);refreshHandler.postDelayed(expireTabUndo,6000);if(tabsDialog!=null)tabsDialog.dismiss();if(activeTab.equals(key)){activeTab="";if(screen.equals("thread")){current=null;board();loadBoard();}}persistThreadTabs();if(screen.equals("board"))board();else if(screen.equals("thread")){ReadingAnchor anchor=readingAnchor();thread();restoreAnchor(anchor);}showThreadTabs();}
    private void undoClosedThreadTab(){
        ThreadTab tab=recentlyClosedTab;if(tab==null||SystemClock.uptimeMillis()>=undoUntil)return;String key=tab.origin+":"+tab.id;if(!threadTabs.containsKey(key)&&threadTabs.size()>=12){Toast.makeText(this,"Close another tab before restoring this one.",Toast.LENGTH_SHORT).show();return;}
        if(!threadTabs.containsKey(key)){LinkedHashMap<String,ThreadTab> restored=new LinkedHashMap<>();int i=0;for(Map.Entry<String,ThreadTab> entry:threadTabs.entrySet()){if(i++==recentlyClosedIndex)restored.put(key,tab);restored.put(entry.getKey(),entry.getValue());}restored.putIfAbsent(key,tab);threadTabs.clear();threadTabs.putAll(restored);}recentlyClosedTab=null;refreshHandler.removeCallbacks(expireTabUndo);persistThreadTabs();View count=toolbar.findViewWithTag("thread-tabs");if(count instanceof TabCountView)((TabCountView)count).setCount(threadTabs.size());showThreadTabs();
    }
    private void addTabsButton(){TabCountView tabs=new TabCountView(this);tabs.setTag("thread-tabs");tabs.setCount(threadTabs.size());tabs.setBackground(box(0xff000002,10));tabs.setOnClickListener(v->{if(!busy)showThreadTabs();});LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(44),dp(46));space.setMargins(dp(8),0,dp(8),0);toolbar.addView(tabs,space);}
    private void showThreadTabs(){
        saveThreadTab();if(tabsDialog!=null)tabsDialog.dismiss();Dialog dialog=new Dialog(this);tabsDialog=dialog;LinearLayout panel=column();panel.setPadding(dp(16),dp(12),dp(16),dp(24));panel.setBackground(box(BG,20));View handle=new View(this);handle.setBackground(box(MUTED,3));LinearLayout.LayoutParams handleSpace=new LinearLayout.LayoutParams(dp(36),dp(4));handleSpace.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(handle,handleSpace);gap(panel,12);LinearLayout header=row();header.addView(text("Open threads ("+threadTabs.size()+")",19,INK),new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Done",dialog::dismiss));panel.addView(header);gap(panel,8);undoTabRow=null;if(recentlyClosedTab!=null&&SystemClock.uptimeMillis()<undoUntil){undoTabRow=row();undoTabRow.addView(text("Tab closed",14,INK),new LinearLayout.LayoutParams(0,-2,1));Button undo=button("Undo",this::undoClosedThreadTab);undo.setTag("undo-close-tab");undo.setOnClickListener(v->undoClosedThreadTab());undoTabRow.addView(undo);panel.addView(undoTabRow);gap(panel,8);}
        ScrollView listScroll=new ScrollView(this);LinearLayout list=column();listScroll.addView(list);int maxHeight=(int)(getResources().getDisplayMetrics().heightPixels*0.58f);panel.addView(listScroll,new LinearLayout.LayoutParams(-1,Math.min(dp(Math.max(1,threadTabs.size())*91),maxHeight)));
        if(threadTabs.isEmpty())list.addView(text("No open threads. Hold a thread title to open a new tab.",16,MUTED));
        for(Map.Entry<String,ThreadTab> entry:threadTabs.entrySet()){String key=entry.getKey();ThreadTab tab=entry.getValue();LinearLayout line=row();boolean selected=screen.equals("thread")&&key.equals(activeTab);Button select=button(tab.title+"\n"+(selected?"Current thread":tab.anchor==null?"Not read yet":"Reading position saved"),()->switchThreadTab(key));select.setSelected(selected);if(selected){select.setBackground(box(0xff40536b,10));AppColors.text(select,INK);}select.setContentDescription(tab.title+(selected?". Current thread":"")+". Swipe left to close.");select.setOnTouchListener(new SwipeReadTouchListener(()->{if(!busy)closeThreadTab(key);},active->{}));select.setTag("tab:"+key);select.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);select.setMaxLines(3);select.setEllipsize(TextUtils.TruncateAt.END);select.setMinHeight(dp(78));line.addView(select,new LinearLayout.LayoutParams(0,-2,1));Button close=button("×",()->closeThreadTab(key));close.setContentDescription("Close tab: "+tab.title);LinearLayout.LayoutParams closeSpace=new LinearLayout.LayoutParams(dp(44),dp(46));closeSpace.setMargins(dp(8),0,0,0);line.addView(close,closeSpace);list.addView(line);gap(list,8);}
        dialog.setContentView(panel);dialog.setOnDismissListener(d->{if(tabsDialog==dialog)tabsDialog=null;scheduleAuthors();});Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setDimAmount(0.5f);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}registerAppWindow(dialog.getWindow());dialog.show();if(window!=null)window.setLayout(-1,-2);
    }
    private void addOwnNavigator(){
        if(!current.id.equals(ownThread)){ownThread=current.id;ownSelected="";}
        ownPosts.clear();for(ForumParser.Post post:current.posts)if(post.editable)ownPosts.add(post.id);
        ownIndex=ownPosts.indexOf(ownSelected);if(ownIndex<0)ownSelected="";
        ownPrevious=button("‹",()->cycleOwn(-1));ownPrevious.setContentDescription("Previous post by me");LinearLayout.LayoutParams previousSpace=new LinearLayout.LayoutParams(dp(44),dp(46));previousSpace.setMargins(0,0,dp(4),0);toolbar.addView(ownPrevious,previousSpace);
        ownCounter=text("",14,INK);ownCounter.setGravity(Gravity.CENTER);ownCounter.setTag("own-counter");toolbar.addView(ownCounter,new LinearLayout.LayoutParams(dp(40),dp(46)));
        ownNext=button("›",()->cycleOwn(1));ownNext.setContentDescription("Next post by me");LinearLayout.LayoutParams nextSpace=new LinearLayout.LayoutParams(dp(44),dp(46));nextSpace.setMargins(dp(4),0,0,0);toolbar.addView(ownNext,nextSpace);updateOwnCounter();
    }
    private void updateOwnCounter(){if(ownCounter==null)return;ownCounter.setText((ownIndex<0?0:ownIndex+1)+"/"+ownPosts.size());ownCounter.setContentDescription(ownPosts.isEmpty()?"No posts by you in this thread":"Your post "+(ownIndex<0?0:ownIndex+1)+" of "+ownPosts.size());ownPrevious.setEnabled(!ownPosts.isEmpty());ownNext.setEnabled(!ownPosts.isEmpty());ownPrevious.setAlpha(ownPosts.isEmpty()?0.35f:1);ownNext.setAlpha(ownPosts.isEmpty()?0.35f:1);}
    private void cycleOwn(int direction){if(!screen.equals("thread")||ownPosts.isEmpty())return;ownIndex=ownIndex<0?(direction>0?0:ownPosts.size()-1):Math.floorMod(ownIndex+direction,ownPosts.size());ownSelected=ownPosts.get(ownIndex);ownJumpUntil=SystemClock.uptimeMillis()+500;updateOwnCounter();jump(ownSelected);scroll.postDelayed(this::updateOwnPosition,550);}
    private void updateOwnPosition(){
        if(!screen.equals("thread")||ownPosts.isEmpty()||SystemClock.uptimeMillis()<ownJumpUntil)return;
        int top=scroll.getScrollY(),bottom=top+scroll.getHeight(),first=ownPosts.size();
        // Post cards are direct children, including the stable holders of virtualized posts.
        // Only visible cards need identification; avoid a recursive tree search for every own post.
        for(int n=0;n<content.getChildCount();n++){
            View card=content.getChildAt(n);if(card.getTop()>=bottom||card.getBottom()<=top)continue;
            Object tag=card.getTag();if(!(tag instanceof String))continue;
            if(tag.equals(ownSelected))return;
            int index=ownPosts.indexOf(tag);if(index>=0&&index<first)first=index;
        }
        if(first<ownPosts.size()){ownIndex=first;ownSelected=ownPosts.get(first);updateOwnCounter();}
    }
    private void thread(){if(current==null||!canOpenThread(client.origin,current.id)){board();return;}boolean restoreFindFocus=findInput!=null&&findInput.hasFocus();int findCursor=findInput==null?0:Math.max(0,findInput.getSelectionStart());prepareThreadFind();rememberThreadTab();screen="thread";shell("Thread");replyHighlights=new ReplyHighlights(scroll,content,preferences,key("replyHighlight:"+current.id),current);replyHighlights.setActive(resumed);rememberAuthor(current);if(replyPreviews==null||!replyPreviews.uses(preferences)){if(replyPreviews!=null)replyPreviews.leave();replyPreviews=new ReplyPreviews(this,preferences,renderedPosts,this::jump,this::link,this::linkActions,this::copyPostLink,()->!busy&&(appLock==null||!appLock.locked()));}replyPreviews.page(current,client.origin);preferences.edit().putInt(key("seen:"+current.id),current.posts.size()).putString(key("title:"+current.id),current.title).apply();
        LinearLayout tools=row();tools.setTag("thread-action-strip");tools.setGravity(Gravity.CENTER_VERTICAL);tools.setPadding(dp(4),dp(2),dp(4),dp(2));tools.setBackground(box(PANEL,12));

        Button reply=button("Reply",()->compose("",false,""));reply.setTag("thread-reply");reply.setContentDescription("Reply to thread");reply.setBackgroundColor(android.graphics.Color.TRANSPARENT);reply.setPadding(dp(4),0,dp(4),0);tools.addView(reply,new LinearLayout.LayoutParams(0,dp(44),1));addQuickButton(tools,"");LinearLayout titleToolbar=toolbar;toolbar=tools;addOwnNavigator();toolbar=titleToolbar;ownPrevious.setBackgroundColor(android.graphics.Color.TRANSPARENT);ownNext.setBackgroundColor(android.graphics.Color.TRANSPARENT);ownPrevious.setPadding(0,0,0,0);ownNext.setPadding(0,0,0,0);
        Button threadTools=button("⋮",()->{});threadTools.setTextSize(25);threadTools.setPadding(0,0,0,0);threadTools.setBackgroundColor(android.graphics.Color.TRANSPARENT);threadTools.setTag("thread-tools");threadTools.setContentDescription("Thread tools: find in thread and thread media");threadTools.setOnClickListener(v->{if(!busy)showThreadTools(v);});tools.addView(threadTools,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout.LayoutParams stripSpace=new LinearLayout.LayoutParams(-1,-2);stripSpace.setMargins(0,dp(6),0,dp(6));root.addView(tools,root.indexOfChild((View)scroll.getParent()),stripSpace);replyHighlights.setUnreadChanged(this::updateUnreadButton);updateUnreadButton();indexMedia();
        Map<String,ForumParser.Post> byId=new HashMap<>();for(ForumParser.Post p:current.posts)byId.put(p.id,p);
        Map<String,Integer> depths=ReplyDepths.calculate(byId);
        android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        boolean lazy=current.posts.size()>40&&(accessibility==null||!accessibility.isTouchExplorationEnabled());
        if(lazy){lazyThread=new LazyThreadRenderer(scroll,content);((PullToRefreshScrollView)scroll).setBottomScroll(lazyThread::bottom);}
        for(ForumParser.Post post:current.posts){
            int depth=depths.get(post.id),indentLevels=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?12:4;
            LinearLayout card=column();card.setTag(post.id);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(dp(Math.min(depth,indentLevels)*12),dp(6),0,dp(6));content.addView(card,cp);
            if(lazy)lazyThread.add(post.id,card,estimatePostHeight(post,Math.min(depth,indentLevels)),()->bindPost(card,post,byId,depth));else bindPost(card,post,byId,depth);
        }
        gap(content,16);LinearLayout footer=row();addButton(footer,"↑ Top",this::top);addButton(footer,"Reply to thread",()->compose("",false,""));addQuickButton(footer,"");content.addView(footer);
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(lazyThread!=null)lazyThread.request();updateOwnPosition();refreshHandler.removeCallbacks(persistTabPosition);refreshHandler.postDelayed(persistTabPosition,800);});scroll.post(()->{if(lazyThread!=null)lazyThread.request();updateOwnPosition();});
        if(findOpen){renderThreadFindBar();if(restoreFindFocus){findInput.requestFocus();findInput.setSelection(Math.min(findCursor,findInput.length()));}if(findResults==null&&!findQuery.trim().isEmpty())searchThreadFind(false);}
    }
    private void cancelThreadFindJobs(){findPageGeneration++;findQueryGeneration++;findNavigation++;if(findIndexTask!=null)findIndexTask.cancel(true);if(findMatchTask!=null)findMatchTask.cancel(true);findIndexTask=null;findMatchTask=null;refreshHandler.removeCallbacks(findDebounce);findJumpAfterIndex=false;}
    private void detachThreadFindBar(){refreshHandler.removeCallbacks(findDebounce);if(threadFindBar!=null&&threadFindBar.getParent() instanceof ViewGroup)((ViewGroup)threadFindBar.getParent()).removeView(threadFindBar);threadFindBar=null;findInput=null;findCounter=null;findPrevious=null;findNext=null;}
    private void resetThreadFind(){cancelThreadFindJobs();findOpen=false;findQuery="";findThreadKey="";findSource=null;findIndex=null;findResults=null;findPreferred=null;findSelected=-1;findError=false;detachThreadFindBar();}
    private void prepareThreadFind(){
        String key=threadTabKey(current.id);if(!findThreadKey.equals(key)){resetThreadFind();findThreadKey=key;}
        if(findSource!=current){cancelThreadFindJobs();findSource=current;findIndex=null;findResults=null;findSelected=-1;findError=false;}
    }
    private void openThreadFind(){
        if(!screen.equals("thread")||current==null)return;prepareThreadFind();findOpen=true;if(threadFindBar==null)renderThreadFindBar();EditText input=findInput;input.requestFocus();input.post(()->{if(findInput==input&&findOpen)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});
    }
    private void renderThreadFindBar(){
        detachThreadFindBar();threadFindBar=row();threadFindBar.setTag("thread-find-bar");EditText input=field("Find in thread…",false);findInput=input;input.setTag("thread-find-input");input.setContentDescription("Find text in this thread");input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});input.setTextSize(14);input.setPadding(dp(8),0,dp(8),0);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);input.setText(findQuery);threadFindBar.addView(input,new LinearLayout.LayoutParams(0,dp(46),1));
        findPrevious=button("‹",()->moveThreadFind(-1));findPrevious.setTag("thread-find-previous");findPrevious.setContentDescription("Previous match");threadFindBar.addView(findPrevious,new LinearLayout.LayoutParams(dp(44),dp(46)));findCounter=text("0/0",12,MUTED);findCounter.setTag("thread-find-count");findCounter.setGravity(Gravity.CENTER);findCounter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);threadFindBar.addView(findCounter,new LinearLayout.LayoutParams(dp(72),dp(46)));findNext=button("›",()->moveThreadFind(1));findNext.setTag("thread-find-next");findNext.setContentDescription("Next match");threadFindBar.addView(findNext,new LinearLayout.LayoutParams(dp(44),dp(46)));Button close=button("×",this::closeThreadFind);close.setTag("thread-find-close");close.setContentDescription("Close find in thread");threadFindBar.addView(close,new LinearLayout.LayoutParams(dp(44),dp(46)));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(0,dp(6),0,dp(8));root.addView(threadFindBar,1,space);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(findInput!=input)return;findQuery=value.toString();findQueryGeneration++;findNavigation++;if(findMatchTask!=null)findMatchTask.cancel(true);findMatchTask=null;findResults=null;findPreferred=null;findSelected=-1;findError=false;refreshHandler.removeCallbacks(findDebounce);applyThreadFindHighlights();updateThreadFindControls();if(!findQuery.trim().isEmpty())refreshHandler.postDelayed(findDebounce,250);}});
        input.setOnEditorActionListener((v,action,event)->{if(findInput!=input)return false;hideKeyboard(input);refreshHandler.removeCallbacks(findDebounce);searchThreadFind(true);return true;});updateThreadFindControls();
    }
    private void closeThreadFind(){
        ReadingAnchor anchor=readingAnchor();if(findInput!=null)hideKeyboard(findInput);resetThreadFind();applyThreadFindHighlights();restoreAnchor(anchor);
    }
    private void updateThreadFindControls(){
        if(findCounter==null)return;int total=findResults==null?0:findResults.hits.size();findPrevious.setEnabled(total>0&&!findError);findNext.setEnabled(total>0&&!findError);String value=findError?"Error":findResults==null&&!findQuery.trim().isEmpty()?"…":total==0?"0/0":(findSelected+1)+"/"+total+(findResults.capped?"+":"");findCounter.setText(value);findCounter.setContentDescription(findError?"Search unavailable":value.equals("…")?"Searching":total==0?"No matches":"Match "+(findSelected+1)+" of "+total+(findResults.capped?" or more. Refine the search to see fewer matches.":""));
    }
    private void searchThreadFind(boolean jump){
        if(destroyed||!findOpen||!screen.equals("thread")||current!=findSource)return;
        if(findQuery.trim().isEmpty()){findResults=ThreadFind.empty();findSelected=-1;applyThreadFindHighlights();updateThreadFindControls();return;}
        findError=false;
        if(findIndex==null){
            findJumpAfterIndex|=jump;updateThreadFindControls();if(findIndexTask!=null)return;
            final int page=findPageGeneration;final ForumParser.ThreadPage source=current;final List<ForumParser.Post> posts=new ArrayList<>(source.posts);
            findIndexTask=findIndexer.submit(()->{try{ThreadFind.Index index=ThreadFind.build(posts,POST_HTML_FLAGS);runOnUiThread(()->{if(destroyed||!findOpen||page!=findPageGeneration||current!=source)return;findIndexTask=null;findIndex=index;boolean move=findJumpAfterIndex;findJumpAfterIndex=false;searchThreadFind(move);});}catch(CancellationException cancelled){}catch(Exception failure){runOnUiThread(()->{if(destroyed||!findOpen||page!=findPageGeneration||current!=source)return;findIndexTask=null;findError=true;findResults=ThreadFind.empty();updateThreadFindControls();});}});return;
        }
        if(findMatchTask!=null)findMatchTask.cancel(true);final int token=++findQueryGeneration;final ThreadFind.Index index=findIndex;final String query=findQuery;final ThreadFind.Hit preferred=findPreferred;findResults=null;updateThreadFindControls();
        findMatchTask=findIndexer.submit(()->{try{ThreadFind.Results results=index.find(query);runOnUiThread(()->{if(destroyed||!findOpen||token!=findQueryGeneration||findIndex!=index||!findQuery.equals(query)||!screen.equals("thread"))return;findMatchTask=null;findResults=results;findSelected=results.hits.isEmpty()?-1:0;for(int i=0;i<results.hits.size();i++)if(results.hits.get(i).same(preferred)){findSelected=i;break;}findPreferred=findSelected<0?null:results.hits.get(findSelected);applyThreadFindHighlights();updateThreadFindControls();if(jump&&findSelected>=0)jumpThreadFind();});}catch(CancellationException cancelled){}catch(Exception failure){runOnUiThread(()->{if(destroyed||!findOpen||token!=findQueryGeneration)return;findMatchTask=null;findError=true;findResults=ThreadFind.empty();updateThreadFindControls();});}});
    }
    private void highlightThreadFindBody(TextView body,String postId){
        SpannableStringBuilder styled=new SpannableStringBuilder(body.getText());ThreadFind.Highlight[] old=styled.getSpans(0,styled.length(),ThreadFind.Highlight.class);ThreadFind.HighlightInk[] ink=styled.getSpans(0,styled.length(),ThreadFind.HighlightInk.class);for(Object span:old)styled.removeSpan(span);for(Object span:ink)styled.removeSpan(span);List<ThreadFind.Hit> hits=findOpen&&findResults!=null?findResults.posts.get(postId):null;if(old.length==0&&ink.length==0&&(hits==null||hits.isEmpty()))return;
        if(hits!=null)for(ThreadFind.Hit hit:hits)if(hit.start>=0&&hit.end<=styled.length()){styled.setSpan(new ThreadFind.Highlight(hit.same(findPreferred)),hit.start,hit.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new ThreadFind.HighlightInk(),hit.start,hit.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}body.setText(styled);
    }
    private void applyThreadFindHighlights(){if(content==null)return;for(int i=0;i<content.getChildCount();i++){View card=content.getChildAt(i);Object id=card.getTag();if(!(id instanceof String))continue;TextView body=card.findViewWithTag("body:"+id);if(body!=null)highlightThreadFindBody(body,(String)id);}}
    private void moveThreadFind(int delta){if(findResults==null||findResults.hits.isEmpty())return;findSelected=Math.floorMod(findSelected+delta,findResults.hits.size());findPreferred=findResults.hits.get(findSelected);applyThreadFindHighlights();updateThreadFindControls();jumpThreadFind();}
    private void jumpThreadFind(){
        if(findPreferred==null)return;final ThreadFind.Hit hit=findPreferred;final int navigation=++findNavigation,revision=viewRevision;final ScrollView target=scroll;final LinearLayout posts=content;if(lazyThread!=null)lazyThread.position(hit.postId,0);TextView body=posts.findViewWithTag("body:"+hit.postId);if(body==null)return;
        posts.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(posts.getViewTreeObserver().isAlive())posts.getViewTreeObserver().removeOnPreDrawListener(this);if(findOpen&&navigation==findNavigation&&revision==viewRevision&&target==scroll&&hit.same(findPreferred)&&body.getLayout()!=null&&hit.start<body.length()){android.graphics.Rect bounds=new android.graphics.Rect();body.getDrawingRect(bounds);posts.offsetDescendantRectToMyCoords(body,bounds);int line=body.getLayout().getLineForOffset(hit.start);target.scrollTo(0,Math.max(0,bounds.top+body.getTotalPaddingTop()+body.getLayout().getLineTop(line)-dp(16)));}return true;}});posts.requestLayout();posts.invalidate();
    }
    private void copyPostLink(String postId){
        try{String url=ThreadMedia.permalink(current,postId);android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Post link",url));Toast.makeText(this,"Link copied",Toast.LENGTH_SHORT).show();}catch(Exception unavailable){Toast.makeText(this,"Could not copy this post link.",Toast.LENGTH_SHORT).show();}
    }
    private void showThreadTools(View anchor){
        if(threadToolsPopup!=null)threadToolsPopup.dismiss();PopupMenu popup=new PopupMenu(this,anchor);threadToolsPopup=popup;if(Build.VERSION.SDK_INT>=29)popup.setForceShowIcon(true);popup.getMenu().add(0,1,0,"Find in thread").setIcon(android.R.drawable.ic_menu_search);popup.getMenu().add(0,2,1,threadMediaReady?"Thread media ("+threadMediaItems.size()+")":"Thread media · loading…").setIcon(android.R.drawable.ic_menu_gallery).setEnabled(threadMediaReady&&!threadMediaItems.isEmpty());popup.setOnMenuItemClickListener(item->{if(item.getItemId()==1)openThreadFind();else if(item.getItemId()==2&&!threadMediaItems.isEmpty())openThreadMedia(current.id,threadMediaItems,0);return true;});popup.setOnDismissListener(menu->{if(threadToolsPopup==popup)threadToolsPopup=null;});popup.show();
    }
    private void updateUnreadButton(){if(unreadJump!=null)unreadJump.setCount(replyHighlights==null?0:replyHighlights.unreadCount());}
    private String closestUnread(){
        if(replyHighlights==null||current==null||scroll==null)return "";Set<String> unread=replyHighlights.unreadIds();String closest="";long distance=Long.MAX_VALUE;int y=scroll.getScrollY();
        for(ForumParser.Post post:current.posts){if(!unread.contains(post.id))continue;View card=content.findViewWithTag(post.id);if(card==null)continue;long delta=card.getTop()<=y&&card.getBottom()>y?0:Math.abs((long)card.getTop()-y);if(delta<distance){closest=post.id;distance=delta;}}
        return closest;
    }
    private void jumpClosestUnread(){String id=closestUnread();if(!id.isEmpty())jump(id);else Toast.makeText(this,"No unread replies left",Toast.LENGTH_SHORT).show();}
    private void indexMedia(){
        final ForumParser.ThreadPage source=current;final int revision=viewRevision;threadMediaItems=Collections.emptyList();threadMediaReady=false;
        mediaIndexTask=mediaIndexer.submit(()->{List<ThreadMedia.Item> items=ThreadMedia.collect(source);runOnUiThread(()->{if(destroyed||revision!=viewRevision||current!=source)return;threadMediaItems=items;threadMediaReady=true;if(threadToolsPopup!=null)threadToolsPopup.getMenu().findItem(2).setTitle("Thread media ("+items.size()+")").setEnabled(!items.isEmpty());});});
    }
    private void openThreadMedia(String threadId,List<ThreadMedia.Item> items,int selected){
        if(destroyed||!screen.equals("thread")||current==null||!current.id.equals(threadId)||items.isEmpty())return;
        int index=Math.floorMod(selected,items.size());ThreadMedia.Item item=items.get(index);
        LinkPreviewDialog dialog=new LinkPreviewDialog(this,item.url,url->{if(url.equals(item.url))openThreadMedia(threadId,items,index);else link(url);});
        dialog.setSweep(index,items.size(),()->{dialog.dismiss();openThreadMedia(threadId,items,index-1);},()->{dialog.dismiss();openThreadMedia(threadId,items,index+1);},()->{dialog.dismiss();if(current!=null&&current.id.equals(threadId))jump(item.postId);});
        dialog.setLinkHold(this::linkActions);previews.add(dialog);dialog.setOnDismissListener(d->{previews.remove(dialog);scheduleAuthors();});dialog.open();
    }
    private int estimatePostHeight(ForumParser.Post post,int levels){
        float width=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-54-levels*12;
        int lineChars=Math.max(12,(int)(width/(9*getResources().getConfiguration().fontScale)));
        int paragraphs=0;for(int at=0;(at=post.html.indexOf("<p",at))>=0;at+=2)paragraphs++;
        return dp(Math.min(20000,240+(post.text.length()/lineChars+1)*25+Math.min(paragraphs,100)*12));
    }
    private void bindPost(LinearLayout card,ForumParser.Post post,Map<String,ForumParser.Post> byId,int depth){
        card.setPadding(dp(13),dp(13),dp(13),dp(13));GradientDrawable surface=box(0xff000001,12);AppColors.stroke(surface,dp(1),depth>0?0xff5d7898:LINE);card.setBackground(surface);if(replyHighlights!=null)replyHighlights.bind(post.id,card,surface);
            ForumParser.Post parentPost=byId.get(post.parent);
            String relation=post.id.equals(current.posts.get(0).id)?"Original post":post.parent.isEmpty()?"Thread reply":"↳ Reply to "+(parentPost==null?"earlier post":parentPost.author)+(depth>1?" · level "+depth:"");
            TextView context=text(relation,13,post.parent.isEmpty()?MUTED:BLUE);if(parentPost!=null&&relation.startsWith("↳ Reply to ")){SpannableStringBuilder label=new SpannableStringBuilder(relation);int start="↳ Reply to ".length();label.setSpan(new AppColors.Ink(MONIKER),start,start+parentPost.author.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);context.setText(label);}context.setTag("relation:"+post.id);card.addView(context);gap(card,6);
            if(!post.parent.isEmpty()){context.setMinHeight(dp(40));context.setGravity(Gravity.CENTER_VERTICAL);replyPreviews.bind(context,card,post);}
            boolean reliableAuthor=ForumParser.recentAuthor(current.createdAt,System.currentTimeMillis());TextView author=text(post.author,17,MONIKER);author.setTypeface(Typeface.SERIF,Typeface.BOLD);author.setContentDescription(post.author+(reliableAuthor?". Tap for thread hiding options.":""));author.setMinHeight(dp(40));author.setGravity(Gravity.CENTER_VERTICAL);if(reliableAuthor)author.setOnClickListener(v->{if(!busy)authorActions(post.author);});card.addView(author);TextView date=text(post.date,12,MUTED);date.setTag("permalink:"+post.id);date.setMinHeight(dp(36));date.setGravity(Gravity.CENTER_VERTICAL);date.setContentDescription(post.date+". Hold to copy this post link.");date.setOnLongClickListener(v->{copyPostLink(post.id);return true;});card.setOnLongClickListener(v->{copyPostLink(post.id);return true;});card.addView(date);gap(card,12);
            TextView body=text("",18,INK);body.setTag("body:"+post.id);SpannableStringBuilder styled=new SpannableStringBuilder(renderedPosts.get(post.html));
            for(URLSpan span:styled.getSpans(0,styled.length(),URLSpan.class)){int start=styled.getSpanStart(span),end=styled.getSpanEnd(span);String url=span.getURL();styled.removeSpan(span);styled.setSpan(new URLSpan(url){@Override public void onClick(View view){link(url);}},start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            body.setText(styled);AppColors.link(body,BLUE);body.setTextIsSelectable(true);body.setMovementMethod(new LinkHoldMovementMethod(this::linkActions));body.setLineSpacing(dp(3),1);card.addView(body);if(findOpen&&findResults!=null)highlightThreadFindBody(body,post.id);
            LinearLayout controls=row();addButton(controls,"↑ Top",this::top);addButton(controls,"Reply",()->compose(post.id,false,""));if(post.editable){addButton(controls,"Edit",()->editPost(post.id));controls.getChildAt(controls.getChildCount()-1).setTag("edit:"+post.id);}
            addQuickButton(controls,post.id);gap(card,10);card.addView(controls);

    }
    private int replyDepth(ForumParser.Post post,Map<String,ForumParser.Post> byId){int depth=0;Set<String> seen=new HashSet<>();seen.add(post.id);while(!post.parent.isEmpty()&&seen.add(post.parent)){depth++;post=byId.get(post.parent);if(post==null)break;}return depth;}
    private void top(){scroll.smoothScrollTo(0,0);}
    private void addQuickButton(LinearLayout controls,String target){String[] choices={"180","cr","cq","mfcr","120","tcr","ntcr"};Arrays.sort(choices,String.CASE_INSENSITIVE_ORDER);Button bb=button("BB",()->quick(target,""));bb.setContentDescription((target.isEmpty()?"Bump thread. Hold for ":"Bump this post. Hold for ")+String.join(", ",choices)+".");bb.setBackground(box(LINE,99));bb.setOnLongClickListener(v->{if(!busy)new ThemeDialogs(this).setTitle(target.isEmpty()?"Quick message to thread":"Quick reply to post").setItems(choices,(d,n)->quick(target,choices[n])).show();return true;});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(46),dp(46));bp.setMargins(dp(5),0,0,0);controls.addView(bb,bp);}
    private void jump(String id){if(lazyThread!=null&&lazyThread.position(id,0))return;View found=content.findViewWithTag(id);if(found!=null)scroll.post(()->scroll.smoothScrollTo(0,found.getTop()));}
    private void quick(String target,String value){ReadingAnchor anchor=readingAnchor();run("Posting…",()->{ForumParser.Form f=client.prepare(current.id,target,false);return client.post(f,"",value,current.id);},result->completeQuick(result,anchor));}
    private void completeQuick(ForumClient.Submission result,ReadingAnchor anchor){current=result.thread;thread();restoreAnchor(anchor);}
    private void rememberReplyPosition(boolean newThread){
        replyReturnAnchor=!newThread&&screen.equals("thread")&&current!=null?readingAnchor():null;
        replyReturnThread=replyReturnAnchor==null?"":client.origin+":"+current.id;
    }
    private void compose(String target,boolean newThread,String initial){
        if(!draft.isEmpty()&&!initial.equals(draft)){new ThemeDialogs(this).setTitle("Keep your existing draft?").setMessage("Continue the draft or discard it to start this reply.").setPositiveButton("Continue draft",(d,n)->composer()).setNegativeButton("Discard",(d,n)->{draft="";compose(target,newThread,initial);}).show();return;}
        rememberReplyPosition(newThread);
        run("Preparing reply…",()->client.prepare(newThread?"":current.id,target,newThread),form->{composeForm=form;parent=target;draft=initial;subject="";composer();});
    }
    private void capture(){if(messageInput!=null)draft=messageInput.getText().toString();if(subjectInput!=null)subject=subjectInput.getText().toString();}
    private void editPost(String id){if(!draft.isEmpty()){new ThemeDialogs(this).setTitle("Keep your existing draft?").setMessage("Continue the draft or discard it to edit this post.").setPositiveButton("Continue draft",(d,n)->composer()).setNegativeButton("Discard",(d,n)->{draft="";subject="";editPost(id);}).show();return;}rememberReplyPosition(false);run("Loading edit form…",()->client.prepareEdit(current.id,id),form->{composeForm=form;draft=form.initialMessage;subject=form.initialSubject;composer();});}
    private void composer(){screen="compose";shell(!composeForm.editPostId.isEmpty()?"Edit post":composeForm.newThread?"New thread":composeForm.threadLevel?"Reply to thread":"Reply to post");
        if(!composeForm.author.isEmpty())content.addView(text("Posting as "+composeForm.author,15,BLUE));
        if(!composeForm.subjectField.isEmpty()){gap(content,12);subjectInput=field(composeForm.newThread?"Thread title":"Subject (optional)",false);subjectInput.setText(subject);content.addView(subjectInput);FormLimitHint.bind(subjectInput,content,FormLimits.rule(composeForm,composeForm.subjectField));}
        if(composeForm.hasMessage||composeForm.newThread){gap(content,12);messageInput=field("Your message",true);messageInput.setGravity(Gravity.TOP);messageInput.setMinLines(9);messageInput.setText(draft);content.addView(messageInput);FormLimitHint.bind(messageInput,content,FormLimits.rule(composeForm,"message"));}
        
        gap(content,12);LinearLayout actions=row();addButton(actions,"Cancel",this::closeComposer);if(messageInput!=null){UploadImageView upload=new UploadImageView(this);upload.setTag("upload-image");upload.setContentDescription("Upload image to "+ImageUpload.name(uploadHost()));upload.setTooltipText("Upload image to "+ImageUpload.name(uploadHost()));upload.setBackground(box(PANEL,9));upload.setOnClickListener(v->pickUploadImage());LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(46),dp(46));space.setMargins(dp(6),0,dp(6),0);actions.addView(upload,space);}addButton(actions,!composeForm.editPostId.isEmpty()?"Save changes":composeForm.hasMessage||composeForm.newThread?"Post":"Continue",()->{capture();if(composeForm.newThread&&!composeForm.subjectField.isEmpty()&&subject.trim().isEmpty()){Toast.makeText(this,"Enter a title.",Toast.LENGTH_SHORT).show();return;}send();});content.addView(actions);
        content.addView(text("Draft stays here while you browse. Closing the app discards an unsent draft.",13,MUTED));
    }
    private void send(){try{FormLimits.posting(composeForm,subject,draft);}catch(IllegalArgumentException invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();return;}String id=composeForm.thread.matches("\\d+")&&!composeForm.thread.equals("0")?composeForm.thread:"",editId=composeForm.editPostId;boolean newThread=composeForm.newThread;run(!editId.isEmpty()?"Saving changes…":composeForm.hasMessage||newThread?"Posting once…":"Preparing composer…",()->!editId.isEmpty()?client.saveEdit(composeForm,subject,draft,id):newThread?client.createThread(composeForm,subject,draft):client.post(composeForm,subject,draft,id),result->completeSubmission(result,newThread,editId));}
    private void completeSubmission(ForumClient.Submission result,boolean newThread,String editId){
        if(result.next!=null){composeForm=result.next;composer();return;}
        ReadingAnchor returnTo=replyReturnThread.equals(client.origin+":"+result.thread.id)?replyReturnAnchor:null;replyReturnAnchor=null;replyReturnThread="";draft="";subject="";current=result.thread;
        if(newThread){composeForm=null;page=1;search="";filter="All";board();loadBoard();return;}
        thread();if(!screen.equals("thread"))return;if(!editId.isEmpty())jump(editId);else if(returnTo!=null)restoreAnchor(returnTo);
    }
    private String accountGroup(){if(Arrays.asList("Login","Saved","Moniker","Session").contains(accountSection))return "Account";if(Arrays.asList("AppLock","Hidden","Authors").contains(accountSection))return "Privacy";return accountSection;}
    private void navigateAccount(String section){hideKeyboard(root);if(!section.equals(accountSection))savedAccountsUnlocked=false;accountSection=section;account();}
    private void accountNavigation(String[] values,String[] labels,String selected,boolean primary){
        HorizontalScrollView bar=new HorizontalScrollView(this);bar.setTag(primary?"account-tab-scroll":"account-subtab-scroll");bar.setFillViewport(!primary);bar.setHorizontalScrollBarEnabled(true);bar.setContentDescription(primary?"Account categories. Swipe sideways for more.":"Section tabs");LinearLayout tabs=row();tabs.setBaselineAligned(false);tabs.setTag(primary?"account-tabs":"account-subtabs");
        for(int i=0;i<values.length;i++){String value=values[i],label=labels[i];boolean active=value.equals(selected);LinearLayout cell=column();Button tab=button(label,()->{String destination=primary?(value.equals("Account")?lastAccountSection:value.equals("Privacy")?lastPrivacySection:value):value;navigateAccount(destination);});tab.setTag(primary&&(value.equals("Account")||value.equals("Privacy"))?"account-group:"+value:"account-tab:"+value);tab.setSelected(active);tab.setTextSize(14);tab.setMaxLines(2);tab.setPadding(dp(8),0,dp(8),0);AppColors.text(tab,active?INK:BLUE);AppColors.background(tab,Color.TRANSPARENT);cell.addView(tab,new LinearLayout.LayoutParams(-1,dp(48)));View underline=new View(this);AppColors.background(underline,active?BLUE:LINE);cell.addView(underline,new LinearLayout.LayoutParams(-1,dp(2)));tabs.addView(cell,new LinearLayout.LayoutParams(dp(primary?82:72),-2,primary?0:1));if(active)bar.post(()->bar.smoothScrollTo(Math.max(0,cell.getLeft()-(bar.getWidth()-cell.getWidth())/2),0));}
        bar.addView(tabs);content.addView(bar,new LinearLayout.LayoutParams(-1,-2));gap(content,primary?8:12);
    }
    private void accountWebsite(){
        LinearLayout website=row();website.setTag("account-website-row");website.addView(text("Website",14,MUTED));Spinner hosts=new Spinner(this);hosts.setTag("account-website");String[] sites={"xoxohth.com","autoadmit.com"};hosts.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sites));hosts.setSelection(client.origin.endsWith("autoadmit.com")?1:0);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,dp(48),1);space.setMargins(dp(12),0,0,0);website.addView(hosts,space);content.addView(website);gap(content,12);
        hosts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String origin="https://"+sites[position];if(client.origin.equals(origin))return;savedAccountsUnlocked=false;generation++;renderedPosts.clear();client=new ForumClient(MainActivity.this,origin,vault);preferences.edit().putString("origin",origin).apply();threads.clear();current=null;draft="";checked=0;sessionSignedIn=null;account();}});
    }
    private Button settingsButton(String label,Runnable action){Button b=button(label,action);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(space);return b;}
    private void account(){
        saveThreadTab();capture();screen="account";shell("Settings");String group=accountGroup();if(!Arrays.asList("Account","Reading","Theme","Uploads","Privacy","Updates","Help").contains(group)){accountSection="Login";group="Account";}if(group.equals("Account"))lastAccountSection=accountSection;else if(group.equals("Privacy"))lastPrivacySection=accountSection;
        accountNavigation(new String[]{"Account","Reading","Theme","Uploads","Privacy","Updates","Help"},new String[]{"Account","Reading","Theme","Uploads","Privacy","Updates","Help"},group,true);
        if(group.equals("Account"))accountNavigation(new String[]{"Login","Saved","Moniker","Session"},new String[]{"Login","Accounts","Moniker","Session"},accountSection,false);else if(group.equals("Privacy"))accountNavigation(new String[]{"AppLock","Hidden","Authors"},new String[]{"App lock","Threads","Authors"},accountSection,false);
        if(group.equals("Account")||accountSection.equals("Hidden"))accountWebsite();
        if(!accountSection.equals("Saved"))clearAccountPrivacy();if(accountSection.equals("Saved"))accountSaved();else if(accountSection.equals("Login"))accountLogin();else if(accountSection.equals("Moniker"))accountName();else if(accountSection.equals("Session"))accountSession();else if(accountSection.equals("AppLock"))renderAppLockSetting();else if(accountSection.equals("Reading"))accountReading();else if(accountSection.equals("Uploads"))renderUploadSettings(false,true);else if(accountSection.equals("Theme")){LinearLayout settings=column();content.addView(settings);new ThemeSettings(this,themeStore,settings,this::applyTheme);}else if(accountSection.equals("Updates"))accountUpdates();else if(accountSection.equals("Help"))accountHelp();else if(accountSection.equals("Authors"))accountHiddenAuthors();else accountHidden();
    }
    private void accountUpdates(){
        content.addView(text("App updates",20,INK));String installed;try{installed=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){installed="Unknown";}final String version=installed;content.addView(text("Installed version: "+version,14,MUTED));gap(content,12);
        EditText repository=field("GitHub owner/repository",false);repository.setTag("update-repository");repository.setSaveEnabled(false);repository.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);repository.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);repository.setText(preferences.getString("updateRepository",""));content.addView(repository);
        content.addView(text("Not configured yet? Leave this empty until your public GitHub repository is ready. Enter owner/repository or its HTTPS URL. No GitHub password or token is needed. This address is stored only on this phone; nothing personal is built into the APK.",14,MUTED));gap(content,10);
        content.addView(settingsButton("Save update repository",()->{try{String value=repository.getText().toString().trim();String saved=value.isEmpty()?"":AppUpdater.repository(value);preferences.edit().putString("updateRepository",saved).apply();hideKeyboard(repository);Toast.makeText(this,saved.isEmpty()?"Update repository cleared":"Update repository saved",Toast.LENGTH_SHORT).show();}catch(IllegalArgumentException e){repository.setError(e.getMessage());}}));
        Button check=settingsButton("Check for updates",()->{String repo;try{repo=AppUpdater.repository(repository.getText().toString());}catch(IllegalArgumentException e){repository.setError("Set up a public GitHub repository first.");return;}hideKeyboard(repository);String source=repo;run("Checking GitHub releases…",()->{try{return AppUpdater.check(source,version);}catch(Exception e){throw new IllegalArgumentException("Update check failed. "+e.getMessage());}},release->{if(release==null)Toast.makeText(this,"You have the latest release",Toast.LENGTH_LONG).show();else presentUpdate(release);});});check.setTag("check-app-update");content.addView(check);
        Button versions=settingsButton("Choose version",()->{try{String repo=AppUpdater.repository(repository.getText().toString());hideKeyboard(repository);chooseRelease(repo,version,1,new ArrayList<>());}catch(IllegalArgumentException e){repository.setError("Set up a public GitHub repository first.");}});versions.setTag("choose-app-version");content.addView(versions);content.addView(text("Choose from published stable releases and review their notes. Installed and older versions are labelled for reference; only newer versions can be installed without removing app data. Use More releases to load older pages. Drafts, prereleases and releases without a supported APK are omitted.",14,MUTED));
        if(AppUpdater.file(this).isFile()){Button install=settingsButton("Install downloaded update",this::installDownloadedUpdate);install.setTag("install-app-update");content.addView(install);}
        gap(content,14);content.addView(text("Checks run only when you tap the button. Publish a stable GitHub Release with a numeric tag such as v1.0.0 and attach the signed APK as AutoAdmit.apk. Keep the same signing key and increase the Android version code for every release. Private repositories, draft releases and prereleases are not used. If Android asks, allow AutoAdmit to install updates, return here and tap Install downloaded update again. Installing as an update preserves your data; do not uninstall.",14,MUTED));
    }
    private void presentUpdate(AppUpdater.Release release){
        if(!activityResumed||appLock!=null&&appLock.locked())return;
        new ThemeDialogs(this).setTitle("Update · "+release.version).setMessage((release.notes.isEmpty()?"No release notes provided.":release.notes)+"\n\nDownload size: "+String.format(Locale.ROOT,"%.1f MB",release.size/1048576.0)+"\nThe APK must match this app's package and signing identity. Android will ask before installation.").setNegativeButton("Cancel",null).setPositiveButton("Download",(d,n)->run("Downloading and verifying update…",()->{try{return AppUpdater.download(this,release);}catch(Exception e){throw new IllegalArgumentException("Update download failed. "+e.getMessage());}},file->{Toast.makeText(this,"Verified update ready to install",Toast.LENGTH_LONG).show();if(activityResumed&&(appLock==null||!appLock.locked())){if(screen.equals("account")&&accountSection.equals("Updates"))account();installDownloadedUpdate();}})).show();
    }
    private void chooseRelease(String repo,String installed,int page,List<AppUpdater.Release> previous){
        run("Loading available releases…",()->{try{return AppUpdater.releases(repo,page);}catch(Exception e){throw new IllegalArgumentException("Could not load releases. "+e.getMessage());}},result->{
            if(!activityResumed||appLock!=null&&appLock.locked())return;List<AppUpdater.Release> releases=new ArrayList<>(previous);Set<String> tags=new HashSet<>();for(AppUpdater.Release r:releases)tags.add(r.version);for(AppUpdater.Release r:result.releases)if(tags.add(r.version))releases.add(r);releases.sort((a,b)->AppUpdater.compare(b.version,a.version));
            showLoadedReleases(repo,installed,page,releases,result.more);
        });
    }
    private void showLoadedReleases(String repo,String installed,int page,List<AppUpdater.Release> releases,boolean more){
            if(releases.isEmpty()&&!more){new ThemeDialogs(this).setTitle("No available versions").setMessage("Publish a stable numeric release and attach a supported signed APK. Drafts, prereleases and releases without a supported APK are not listed.").setPositiveButton("OK",null).show();return;}
            String[] labels=new String[releases.size()];for(int i=0;i<labels.length;i++){AppUpdater.Release r=releases.get(i);int comparison=AppUpdater.compare(r.version,installed);labels[i]=r.version+(comparison==0?" · Installed":comparison<0?" · Older version":" · Available update");}
            AlertDialog.Builder dialog=new ThemeDialogs(this).setTitle("Choose version · Installed "+installed).setItems(labels,(d,n)->{AppUpdater.Release selected=releases.get(n);if(AppUpdater.compare(selected.version,installed)>0)presentUpdate(selected);else new ThemeDialogs(this).setTitle(selected.version).setMessage((selected.notes.isEmpty()?"No release notes provided.":selected.notes)+"\n\nThis version is installed or older. It cannot replace the current app while preserving its data. Choose a newer release to update.").setPositiveButton("Back to versions",(back,which)->showLoadedReleases(repo,installed,page,releases,more)).show();}).setNegativeButton("Cancel",null);
            if(more&&page<1000)dialog.setNeutralButton("More releases",(d,n)->chooseRelease(repo,installed,page+1,releases));dialog.show();
    }
    private void installDownloadedUpdate(){
        if(!activityResumed||appLock!=null&&appLock.locked())return;
        try{AppUpdater.verify(this,AppUpdater.file(this));if(!getPackageManager().canRequestPackageInstalls()){startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));Toast.makeText(this,"Allow updates from AutoAdmit, then return and tap Install downloaded update",Toast.LENGTH_LONG).show();return;}
            Uri uri=Uri.parse("content://"+getPackageName()+".updates/update.apk");Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("AutoAdmit update",uri));startActivity(intent);
        }catch(Exception e){new ThemeDialogs(this).setTitle("Update not installed").setMessage(e instanceof android.content.ActivityNotFoundException?"Android's installer is unavailable. Install the APK manually using your file manager.":e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void helpNavigate(int section,int topic){helpSection=section;helpTopic=topic;accountSection="Help";account();}
    private boolean helpBack(){if(!screen.equals("account")||!accountSection.equals("Help")||helpSection<0)return false;helpNavigate(helpTopic>=0?helpSection:-1,-1);return true;}
    private void helpHeading(LinearLayout panel,String value){TextView heading=text(value,21,INK);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);panel.addView(heading);gap(panel,10);}
    private void helpEntry(LinearLayout panel,String label,String summary,Runnable action,String tag){Button entry=button(label+"  ›",action);entry.setTag(tag);entry.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);entry.setTextSize(16);entry.setPadding(dp(14),dp(10),dp(14),dp(10));panel.addView(entry,new LinearLayout.LayoutParams(-1,-2));if(!summary.isEmpty()){gap(panel,5);panel.addView(text(summary,14,MUTED));}gap(panel,14);}
    private void accountHelp(){
        LinearLayout panel=column();panel.setTag("help-content");content.addView(panel);
        if(helpSection<0||helpSection>=HelpGuide.SECTIONS.length){helpSection=-1;helpTopic=-1;helpHeading(panel,"Help");panel.addView(text("Choose a section, then a topic. This guide is available offline.",15,MUTED));gap(panel,18);for(int i=0;i<HelpGuide.SECTIONS.length;i++){final int index=i;HelpGuide.Section section=HelpGuide.SECTIONS[i];helpEntry(panel,section.title,section.summary,()->helpNavigate(index,-1),"help-section:"+i);}return;}
        HelpGuide.Section section=HelpGuide.SECTIONS[helpSection];LinearLayout trail=row();Button home=button("‹ Help",()->helpNavigate(-1,-1));home.setTag("help-home");trail.addView(home);if(helpTopic>=0){Button contents=button("Topics",()->helpNavigate(helpSection,-1));contents.setTag("help-topics");LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-2,-2);space.setMargins(dp(8),0,0,0);trail.addView(contents,space);}panel.addView(trail);gap(panel,16);
        if(helpTopic<0||helpTopic>=section.topics.length){helpTopic=-1;helpHeading(panel,section.title);panel.addView(text(section.summary,15,MUTED));gap(panel,18);for(int i=0;i<section.topics.length;i++){final int index=i;helpEntry(panel,section.topics[i].title,"",()->helpNavigate(helpSection,index),"help-topic:"+i);}return;}
        HelpGuide.Topic topic=section.topics[helpTopic];TextView location=text(section.title+" · "+(helpTopic+1)+" of "+section.topics.length,14,MUTED);location.setTag("help-location");panel.addView(location);gap(panel,8);helpHeading(panel,topic.title);
        for(String paragraph:topic.body.split("\\n\\n")){TextView body=text(paragraph,16,INK);body.setTextIsSelectable(true);body.setLineSpacing(dp(3),1);panel.addView(body);gap(panel,16);}
        LinearLayout navigation=row();Button previous=button("‹ Previous",()->helpNavigate(helpSection,helpTopic-1));previous.setEnabled(helpTopic>0);previous.setTag("help-previous");navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(46),1));View spacer=new View(this);navigation.addView(spacer,new LinearLayout.LayoutParams(dp(8),1));Button next=button("Next ›",()->helpNavigate(helpSection,helpTopic+1));next.setEnabled(helpTopic+1<section.topics.length);next.setTag("help-next");navigation.addView(next,new LinearLayout.LayoutParams(0,dp(46),1));panel.addView(navigation);gap(panel,12);
    }
    void registerAppWindow(Window window){if(appLock!=null)appLock.register(window);}
    private void clearAccountPrivacy(){if(appLock==null||!appLock.enabled())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void startAppContent(){if(appContentStarted||appLock!=null&&appLock.locked())return;appContentStarted=true;restoreThreadTabs();board();loadBoard();}
    private void afterAppUnlock(){startAppContent();if(deferredUploadResult!=null){Intent result=deferredUploadResult;deferredUploadResult=null;onActivityResult(PICK_UPLOAD_IMAGE,RESULT_OK,result);}if(activityResumed)resumeAppContent();}
    private void appLockSettingChanged(){if(screen.equals("account")&&accountSection.equals("AppLock"))account();}
    private void renderAppLockSetting(){
        content.addView(text("App lock",18,INK));Switch required=new Switch(this);required.setTag("app-require-pin");required.setText("Require phone PIN to open AutoAdmit");required.setTextSize(16);required.setTypeface(Typeface.SERIF);required.setMinHeight(dp(48));AppColors.text(required,INK);required.setChecked(appLock.enabled());content.addView(required);content.addView(text("Off by default. Uses your phone PIN, pattern or password. When on, leaving the app locks it again and screenshots/recent-app previews are blocked. Enabling or turning it off requires phone confirmation. Saved-accounts protection is separate.",14,MUTED));gap(content,14);boolean[] resetting={false};required.setOnCheckedChangeListener((view,on)->{if(resetting[0])return;resetting[0]=true;required.setChecked(appLock.enabled());resetting[0]=false;appLock.setting(on);});
    }
    private boolean accountsPinRequired(){return preferences.getBoolean("accountsPinRequired",true);}
    private boolean accountsAccessible(){return !accountsPinRequired()||savedAccountsUnlocked;}
    private void saveAccountsPinRequirement(boolean required){
        if(!preferences.edit().putBoolean("accountsPinRequired",required).commit()){Toast.makeText(this,"Could not save the PIN setting. Try again.",Toast.LENGTH_LONG).show();account();return;}
        savedAccountsUnlocked=false;account();
    }
    private void requestAccountsCredential(boolean disable){
        KeyguardManager manager=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(manager==null){Toast.makeText(this,"Phone screen lock is unavailable.",Toast.LENGTH_LONG).show();account();return;}
        if(!manager.isDeviceSecure()){if(disable)saveAccountsPinRequirement(false);else Toast.makeText(this,"Set a screen lock in Android Settings first.",Toast.LENGTH_LONG).show();return;}
        int request=disable?DISABLE_ACCOUNTS_PIN:UNLOCK_SAVED_ACCOUNTS;
        Intent prompt=manager.createConfirmDeviceCredentialIntent(disable?"Turn off account PIN requirement":"Unlock saved accounts","Confirm your phone screen lock");
        if(prompt==null){Toast.makeText(this,"Phone unlock is unavailable. PIN setting unchanged.",Toast.LENGTH_LONG).show();account();return;}
        try{accountsCredentialRequest=request;startActivityForResult(prompt,request);}catch(ActivityNotFoundException unavailable){accountsCredentialRequest=0;Toast.makeText(this,"Phone unlock is unavailable. PIN setting unchanged.",Toast.LENGTH_LONG).show();account();}
    }
    private void finishAccountsCredential(int request,int result){
        if(accountsCredentialRequest!=request)return;accountsCredentialRequest=0;savedAccountsUnlocked=false;
        if(!screen.equals("account")||!accountSection.equals("Saved"))return;
        if(request==DISABLE_ACCOUNTS_PIN&&result==RESULT_OK){saveAccountsPinRequirement(false);return;}
        savedAccountsUnlocked=request==UNLOCK_SAVED_ACCOUNTS&&result==RESULT_OK;account();
    }
    private void accountSaved(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Switch pin=new Switch(this);pin.setTag("accounts-require-pin");pin.setText("Require phone PIN");pin.setTextSize(16);pin.setTypeface(Typeface.SERIF);AppColors.text(pin,INK);pin.setMinHeight(dp(48));pin.setChecked(accountsPinRequired());content.addView(pin,new LinearLayout.LayoutParams(-1,-2));gap(content,6);
        content.addView(text(accountsPinRequired()?"Use your phone PIN, pattern or password to open saved accounts. Turning this off requires phone unlock if a screen lock is set.":"PIN is off. Anyone using this app can view and switch saved accounts. Logins remain encrypted on this phone.",14,MUTED));gap(content,16);
        pin.setOnCheckedChangeListener((view,required)->{if(required)saveAccountsPinRequirement(true);else{pin.setOnCheckedChangeListener(null);pin.setChecked(true);requestAccountsCredential(true);}});
        if(accountsAccessible()){if(accountsPinRequired())content.addView(settingsButton("Lock saved accounts",()->{savedAccountsUnlocked=false;account();}));accountLogin();return;}
        content.addView(text("Saved accounts are locked",18,INK));gap(content,12);content.addView(text("Unlock with your phone PIN, pattern or password to view, switch or remove saved accounts.",14,MUTED));gap(content,12);
        Button unlock=settingsButton("Unlock saved accounts",()->requestAccountsCredential(false));unlock.setTag("unlock-saved-accounts");content.addView(unlock);
    }
private void accountLogin(){ final boolean protectedAccounts=accountSection.equals("Saved")&&accountsAccessible();
        List<ForumClient.SavedAccount> saved=new ArrayList<>();final String[] selected={""},selectedLabel={""},selectedNickname={""},active={""};final int[] previousSelection={-1};if(protectedAccounts)content.addView(text("Saved accounts",15,MUTED));LinearLayout chooser=row();Spinner accounts=new Spinner(this);accounts.setTag("saved-accounts");accounts.setContentDescription("Saved accounts for this website");chooser.addView(accounts,new LinearLayout.LayoutParams(0,dp(48),1));Button nickname=button("Nickname",()->editSavedAccountNickname(selected[0],selectedNickname[0]));nickname.setTag("edit-account-nickname");nickname.setEnabled(false);LinearLayout profileActions=row();LinearLayout.LayoutParams nicknameSpace=new LinearLayout.LayoutParams(0,dp(48),1);nicknameSpace.setMargins(0,0,dp(4),0);profileActions.addView(nickname,nicknameSpace);Button remove=button("Remove",()->confirmRemoveSavedAccount(selected[0],selectedLabel[0]));remove.setTag("remove-saved-account");remove.setEnabled(false);LinearLayout.LayoutParams removeSpace=new LinearLayout.LayoutParams(0,dp(48),1);removeSpace.setMargins(dp(4),0,0,0);profileActions.addView(remove,removeSpace);if(protectedAccounts){content.addView(chooser);gap(content,8);content.addView(profileActions);}gap(content,8);
        EditText username=field("Account name",false);username.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);username.setSaveEnabled(false);username.setTag("account-username");content.addView(username);FormLimitHint.bind(username,content,FormLimits.fallback("username"));gap(content,10);EditText password=field("Password",false);password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setTypeface(Typeface.SERIF);password.setSaveEnabled(false);password.setTag("account-password");content.addView(password);FormLimitHint.bind(password,content,FormLimits.fallback("password"));gap(content,8);
        CheckBox remember=new CheckBox(this);remember.setText("Save login securely on this phone");AppColors.text(remember,INK);remember.setChecked(true);content.addView(remember);CheckBox automatic=new CheckBox(this);automatic.setText("Sign in again when my session expires");AppColors.text(automatic,INK);automatic.setChecked(true);content.addView(automatic);remember.setOnCheckedChangeListener((v,yes)->{automatic.setEnabled(yes);if(!yes)automatic.setChecked(false);});
        gap(content,8);Button signIn=settingsButton("Sign in",()->{String name=username.getText().toString(),secret=password.getText().toString();boolean save=remember.isChecked(),auto=automatic.isChecked();if(!protectedAccounts&&name.trim().isEmpty()&&secret.isEmpty()){Toast.makeText(this,"Open Account → Accounts to use a saved login, or enter both fields here.",Toast.LENGTH_LONG).show();return;}if(name.trim().isEmpty()!=secret.isEmpty()){Toast.makeText(this,"Enter both account name and password to switch accounts.",Toast.LENGTH_LONG).show();return;}hideKeyboard(password);run("Signing in…",()->{if(!selected[0].isEmpty()&&name.trim().isEmpty()&&secret.isEmpty())client.loginSavedAccount(selected[0],save,auto);else client.login(name,secret,save,auto);return true;},ok->{renderedPosts.clear();sessionSignedIn=true;username.setText("");password.setText("");Toast.makeText(this,"Signed in. Saved settings updated.",Toast.LENGTH_LONG).show();board();loadBoard();});});content.addView(signIn);
        gap(content,12);if(protectedAccounts)content.addView(text("Choose a saved account, then tap Switch account. Choose Add account to save another login after signing in. Accounts are encrypted on this phone and separate for each website.",14,MUTED));
        run("Loading saved accounts…",()->{if(protectedAccounts)saved.addAll(client.savedAccounts());active[0]=client.savedUsername();return true;},ok->{if(!protectedAccounts){username.setHint(active[0].isEmpty()?"Account name":"********");password.setHint(active[0].isEmpty()?"Password":"********");content.addView(text("Manage saved logins under Account → Accounts.",14,MUTED));return;}if(!accountsAccessible()||!accountSection.equals("Saved"))return;List<String> labels=new ArrayList<>();int chosen=saved.size();for(int i=0;i<saved.size();i++){ForumClient.SavedAccount item=saved.get(i);boolean current=item.username.equalsIgnoreCase(active[0]);labels.add(item.displayName()+(current?" · Current":""));if(current)chosen=i;}if(nicknameSelectedOrigin.equals(client.origin)){for(int i=0;i<saved.size();i++)if(saved.get(i).username.equalsIgnoreCase(nicknameSelectedAccount)){chosen=i;break;}nicknameSelectedAccount="";nicknameSelectedOrigin="";}labels.add("Add account…");accounts.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));accounts.setSelection(chosen);Runnable update=()->{int position=accounts.getSelectedItemPosition();if(position==previousSelection[0])return;previousSelection[0]=position;boolean existing=position>=0&&position<saved.size();selected[0]=existing?saved.get(position).username:"";selectedLabel[0]=existing?saved.get(position).displayName():"";selectedNickname[0]=existing?saved.get(position).nickname:"";nickname.setEnabled(existing);username.setText("");password.setText("");username.setHint(existing?"********":"Account name");password.setHint(existing?"********":"Password");username.setContentDescription(existing?"Account name. Saved account selected; type both fields to use a different login.":"Account name");password.setContentDescription(existing?"Password. Saved account selected.":"Password");remove.setEnabled(existing);automatic.setChecked(remember.isChecked()&&(!existing||saved.get(position).automatic));signIn.setText(existing?(selected[0].equalsIgnoreCase(active[0])?"Sign in":"Switch account"):"Add account");};accounts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){update.run();}});update.run();});
    }
    private AlertDialog confirmRemoveSavedAccount(String username){
        return confirmRemoveSavedAccount(username,username);
    }
    private AlertDialog confirmRemoveSavedAccount(String username,String label){
        if(username.isEmpty()||!accountsAccessible()||!accountSection.equals("Saved"))return null;return savedAccountDialog=new ThemeDialogs(this).setTitle("Remove saved account?").setMessage("Remove "+label+" from this phone? If it is the current saved account, its local session is cleared too. Other accounts and the website account are not deleted.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,n)->run("Removing saved account…",()->{boolean current=client.savedUsername().equalsIgnoreCase(username);client.removeSavedAccount(username);return current;},current->{if(current){renderedPosts.clear();sessionSignedIn=false;}account();})).show();
    }
    private AlertDialog editSavedAccountNickname(String username,String currentNickname){
        if(username.isEmpty()||!accountsAccessible()||!accountSection.equals("Saved"))return null;
        final ForumClient source=client;LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),0);panel.addView(text("Private to this phone. Does not change your login or public moniker. Clear it to show the login name again.",14,MUTED));gap(panel,12);EditText entry=field("Main, Alt…",false);entry.setTag("account-nickname-input");entry.setContentDescription("Private account nickname");entry.setSaveEnabled(false);entry.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});entry.setText(currentNickname);entry.setSelection(entry.length());panel.addView(entry);
        AlertDialog dialog=new ThemeDialogs(this).setTitle("Account nickname").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();savedAccountDialog=dialog;
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=entry.getText().toString();hideKeyboard(entry);run("Saving account nickname…",()->{source.setSavedAccountNickname(username,value);return true;},ok->{dialog.dismiss();if(screen.equals("account")&&accountSection.equals("Saved")&&client==source){nicknameSelectedAccount=username;nicknameSelectedOrigin=source.origin;account();}Toast.makeText(this,"Account nickname saved",Toast.LENGTH_SHORT).show();});}));dialog.setOnDismissListener(d->{entry.setText("");if(savedAccountDialog==dialog)savedAccountDialog=null;});registerAppWindow(dialog.getWindow());dialog.show();return dialog;
    }
    private void accountName(){
        content.addView(text("Moniker",18,INK));gap(content,8);content.addView(text("Choose a previous name or create a new one. This changes your posting name on the website, not your saved login username.",14,MUTED));gap(content,12);LinearLayout panel=column();panel.setTag("alias-settings");content.addView(panel);String knownThread=current!=null?current.id:threads.isEmpty()?"":threads.get(0).id;run("Loading monikers…",()->client.aliases(knownThread),data->renderAliasSettings(panel,data));
    }
    private void renderAliasSettings(LinearLayout panel,ForumParser.AliasSettings settings){
        panel.removeAllViews();if(!settings.current.isEmpty()){TextView currentName=text("Current: "+settings.current,16,MONIKER);currentName.setTag("current-posting-name");panel.addView(currentName);gap(panel,12);}
        if(settings.choose!=null){panel.addView(text("Current / previous monikers",15,MUTED));Spinner names=new Spinner(this);names.setTag("previous-posting-names");names.setContentDescription("Current and previous monikers");List<String> labels=new ArrayList<>();labels.add("Choose a previous name…");for(ForumParser.AliasOption option:settings.options)labels.add(option.name);names.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));int selected=0;for(int i=0;i<settings.options.size();i++)if(settings.options.get(i).name.trim().equals(settings.current.trim())&&!settings.current.isEmpty()){selected=i+1;break;}names.setSelection(selected);panel.addView(names,new LinearLayout.LayoutParams(-1,dp(48)));gap(panel,8);Button use=settingsButton("Use selected name",()->{int position=names.getSelectedItemPosition()-1;if(position<0||position>=settings.options.size()){Toast.makeText(this,"Choose a previous name first.",Toast.LENGTH_SHORT).show();return;}changePostingName(panel,false,settings.options.get(position).id);});use.setTag("use-posting-name");panel.addView(use);gap(panel,22);}
        if(settings.create!=null){panel.addView(text("New posting name",15,MUTED));gap(panel,8);EditText name=field("New name",false);name.setTag("new-posting-name");name.setContentDescription("New posting name");name.setSaveEnabled(false);panel.addView(name);FormLimitHint.bind(name,panel,FormLimits.rule(settings.create,"alias_name"));gap(panel,8);Button create=settingsButton("Create and use name",()->{String value=name.getText().toString().trim();if(value.isEmpty()){Toast.makeText(this,"Enter a new posting name.",Toast.LENGTH_SHORT).show();return;}try{FormLimits.rule(settings.create,"alias_name").validate(value);}catch(IllegalArgumentException invalid){name.setError(invalid.getMessage());return;}hideKeyboard(name);changePostingName(panel,true,value);});create.setTag("create-posting-name");panel.addView(create);}
    }
    private void changePostingName(LinearLayout panel,boolean create,String value){String verificationThread=current!=null?current.id:threads.isEmpty()?"":threads.get(0).id;run("Changing posting name…",()->client.changeAlias(create,value,verificationThread),updated->{renderedPosts.clear();sessionSignedIn=true;if(composeForm!=null&&composeForm.target!=null&&composeForm.target.startsWith(client.origin+"/")){composeForm.author=updated.current;composeForm.values.put("txtAuthor",updated.current);}renderAliasSettings(panel,updated);Toast.makeText(this,"Posting name updated",Toast.LENGTH_SHORT).show();});}
    private void accountReading(){
        content.addView(text("Reply previews",18,INK));content.addView(text("Choose what happens when you tap Reply to above a post.",14,MUTED));gap(content,12);RadioGroup modes=new RadioGroup(this);modes.setTag("reply-preview-modes");String[] values={"links","nested","stacked"},labels={"Jump links (Default)","Nested cards","Stacked popovers"},details={"Tap a parent reference to scroll to that post. This is the original behavior.","Parents open inside the post. Follow another parent reference to continue; tap an open reference again to collapse it. Deep chains stop adding indentation.","Parents open in a popover. Back one or Android Back returns one preview; × closes the whole stack. Go to post jumps to the original comment."};String selected=ReplyPreviews.mode(preferences);TextView description=text("",14,MUTED);description.setTag("reply-mode-description");
        for(int i=0;i<values.length;i++){RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setTag("reply-mode:"+values[i]);option.setText(labels[i]);option.setTextSize(16);option.setTypeface(Typeface.SERIF);option.setMinHeight(dp(48));AppColors.text(option,INK);modes.addView(option);if(values[i].equals(selected)){modes.check(option.getId());description.setText(details[i]);}}modes.setOnCheckedChangeListener((group,id)->{View choice=group.findViewById(id);if(choice!=null)for(int i=0;i<values.length;i++)if(("reply-mode:"+values[i]).equals(choice.getTag()))description.setText(details[i]);});content.addView(modes);gap(content,10);content.addView(description);gap(content,12);content.addView(text("Previews use only the loaded thread and make no extra requests.",13,MUTED));gap(content,8);
        Button save=settingsButton("Save reply preview mode",()->{View choice=modes.findViewById(modes.getCheckedRadioButtonId());if(choice==null)return;String value=String.valueOf(choice.getTag()).substring("reply-mode:".length());if(!preferences.edit().putString(ReplyPreviews.KEY,value).commit()){Toast.makeText(this,"Could not save the preview setting.",Toast.LENGTH_LONG).show();return;}if(replyPreviews!=null)replyPreviews.leave();Toast.makeText(this,"Reply preview mode saved",Toast.LENGTH_SHORT).show();});save.setTag("save-reply-preview-mode");content.addView(save);
    }
    private void accountSession(){
        content.addView(text("Website session",18,INK));TextView state=text(sessionSignedIn==null?"Session not checked":sessionSignedIn?"Signed in":"Signed out",16,INK);state.setTag("account-session-state");content.addView(state);gap(content,8);content.addView(settingsButton("Check session",this::session));content.addView(settingsButton("Open website",()->external(client.origin+"/main.php?forum_id=2")));gap(content,8);content.addView(text("The website can expire or revoke a session. A saved login lets the app sign in again.",14,MUTED));
        if(client.pendingPost()){gap(content,20);content.addView(text("Posting recovery",18,INK));content.addView(text("A previous post could not be confirmed. Check whether it appeared before allowing another submission.",14,MUTED));content.addView(settingsButton("I've checked the previous post",()->new ThemeDialogs(this).setTitle("Resume posting?").setMessage("Only continue after checking whether your earlier post appeared. It will not be resent automatically.").setNegativeButton("Cancel",null).setPositiveButton("I've checked",(d,n)->{client.acknowledgePending();account();}).show()));}
        gap(content,24);content.addView(text("Remove saved access",18,INK));content.addView(text("Forget the current saved login and its session on this phone. Other saved accounts and your website account stay intact.",14,MUTED));content.addView(settingsButton("Forget current login",()->new ThemeDialogs(this).setTitle("Forget this saved login?").setMessage("Removes the current saved account and its local session from this phone. Other saved accounts remain.").setNegativeButton("Cancel",null).setPositiveButton("Forget",(d,n)->run("Removing saved login…",()->{client.forget();return true;},ok->{renderedPosts.clear();sessionSignedIn=false;account();})).show()));
    }
    private void accountHidden(){
        List<String> ids=new ArrayList<>(hiddenThreadIds(client.origin));Collections.reverse(ids);content.addView(text("Hidden threads ("+ids.size()+"/"+HiddenThreadPool.LIMIT+")",18,INK));gap(content,8);
        if(ids.isEmpty())content.addView(text("None yet. Swipe a thread left to hide it. In New replies, swipe-left still marks it as read; hold a thread for Hide this thread.",14,MUTED));
        else{content.addView(text("Up to 30 hidden threads for this website, newest first. Hides expire after 7 days; hiding another at the limit releases the oldest sooner. Unhide leaves read status unchanged.",14,MUTED));gap(content,10);for(String id:ids){LinearLayout line=row();TextView title=text(preferences.getString(key("hiddenThreadTitle:"+id),"Thread "+id),16,INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button unhide=button("Unhide",()->{setIndividualThreadHidden(client.origin,id,"",false);if(hiddenUndoOrigin.equals(client.origin)&&hiddenUndoId.equals(id)){refreshHandler.removeCallbacks(expireHiddenThreadUndo);expireHiddenThreadUndo.run();}account();});unhide.setTag("unhide-thread:"+id);line.addView(unhide);content.addView(line);gap(content,8);}}
    }
    private void accountHiddenAuthors(){
        content.addView(text("Hidden authors",18,INK));gap(content,8);List<String> authors=new ArrayList<>(hiddenAuthors());Collections.sort(authors,String.CASE_INSENSITIVE_ORDER);content.addView(text("Threads started by these authors are hidden on both websites. Their replies inside other threads are unaffected.",14,MUTED));gap(content,10);if(authors.isEmpty()){content.addView(text("None yet. Hold a thread on the board, or tap an eligible author inside a recent thread, to hide threads they started.",14,MUTED));return;}
        for(String author:authors){LinearLayout line=row();TextView name=text(author,16,MONIKER);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button unhide=button("Unhide",()->{setAuthorHidden(author,false);account();});line.addView(unhide);content.addView(line);gap(content,8);}
    }
    private String uploadHost(){return "imgbb";}
    private void appendUploadedImage(String url){
        if(!screen.equals("compose")||messageInput==null)return;
        Editable text=messageInput.getText();if(text.length()>0&&text.charAt(text.length()-1)!='\n')text.append("\n");text.append(url);messageInput.setSelection(text.length());capture();
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Uploaded image link",url));Toast.makeText(this,"image url in clipboard",Toast.LENGTH_SHORT).show();
    }
    private void pickUploadImage(){
        if(busy||messageInput==null)return;capture();String host=uploadHost();try{if(!ImageUpload.credentialValid(vault.get("uploads:"+host))){uploadSettings(true);Toast.makeText(this,"Set the upload credential first.",Toast.LENGTH_LONG).show();return;}}catch(Exception unavailable){Toast.makeText(this,"Upload settings could not be read.",Toast.LENGTH_LONG).show();return;}
        pickerOwner=composeForm;Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/gif","image/webp"});
        try{startActivityForResult(picker,PICK_UPLOAD_IMAGE);}catch(ActivityNotFoundException missing){pickerOwner=null;Toast.makeText(this,"No image picker is available.",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(appLock!=null&&appLock.result(request,result))return;if(request==UNLOCK_SAVED_ACCOUNTS||request==DISABLE_ACCOUNTS_PIN){if(appLock!=null&&accountsCredentialRequest==request&&result==RESULT_OK)appLock.relatedCredentialAccepted();finishAccountsCredential(request,result);return;}if(request!=PICK_UPLOAD_IMAGE)return;if(appLock!=null&&appLock.locked()&&result==RESULT_OK&&data!=null){deferredUploadResult=data;return;}ForumParser.Form selectedOwner=pickerOwner;pickerOwner=null;
        if(result!=RESULT_OK||data==null)return;if(selectedOwner==null||selectedOwner!=composeForm||!screen.equals("compose")||busy)return;
        LinkedHashSet<Uri> unique=new LinkedHashSet<>();ClipData clips=data.getClipData();if(clips!=null){for(int i=0;i<clips.getItemCount();i++){unique.add(clips.getItemAt(i).getUri());if(unique.size()>20)break;}}else if(data.getData()!=null)unique.add(data.getData());
        if(unique.isEmpty())return;if(unique.size()>20){Toast.makeText(this,"Select up to 20 files at once.",Toast.LENGTH_LONG).show();return;}
        ArrayList<Uri> selected=new ArrayList<>(unique);String host=uploadHost();ImageUpload uploader=new ImageUpload();activeUpload=uploader;ProgressDialog progress=new ProgressDialog(this,AppColors.light()?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert);progress.setMessage("Uploading to "+ImageUpload.name(host)+"…");progress.setCancelable(false);progress.setButton(ProgressDialog.BUTTON_NEGATIVE,"Cancel",(d,n)->uploader.cancel());AppLock.attach(progress);progress.show();AppColors.dialog(progress);
        run("Uploading media…",()->{StringBuilder links=new StringBuilder();String failure="";try{String credential=vault.get("uploads:"+host);for(int i=0;i<selected.size();i++){final int number=i+1;runOnUiThread(()->progress.setMessage("Uploading "+number+" of "+selected.size()+" to "+ImageUpload.name(host)+"…"));try{String url=uploader.upload(this,selected.get(i),host,credential);if(links.length()>0)links.append("\n");links.append(url);}catch(Exception e){failure=e instanceof ImageUpload.Failure||e instanceof IllegalArgumentException?e.getMessage():"Upload was not confirmed. Check the host before retrying.";break;}}return new String[]{links.toString(),failure};}finally{runOnUiThread(()->{if(progress.isShowing())progress.dismiss();if(activeUpload==uploader)activeUpload=null;});}},resultLinks->{if(composeForm!=selectedOwner||!screen.equals("compose"))return;if(!resultLinks[0].isEmpty())appendUploadedImage(resultLinks[0]);if(!resultLinks[1].isEmpty())new ThemeDialogs(this).setTitle("Upload stopped").setMessage(resultLinks[1]+(resultLinks[0].isEmpty()?"":"\n\nCompleted links were added to your message and clipboard. Remaining files were not uploaded.")).setPositiveButton("OK",null).show();});
    }
    private void uploadSettings(boolean fromComposer){
        capture();uploadSettingsFromComposer=fromComposer;screen="upload-settings";shell("Image uploads");renderUploadSettings(fromComposer,false);
    }
    private void renderUploadSettings(boolean fromComposer,boolean inAccount){content.addView(text("ImgBB API key",17,INK));
        EditText credential=field("",false);credential.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);credential.setSaveEnabled(false);credential.setContentDescription("ImgBB API key");content.addView(credential);
        TextView configured=text("",13,MUTED);content.addView(configured);try{configured.setText(ImageUpload.credentialValid(vault.get("uploads:imgbb"))?"Credential configured on this phone":"Not configured");}catch(Exception e){configured.setText("Credential unavailable");}gap(content,12);
        content.addView(settingsButton("Save upload settings",()->{String value=credential.getText().toString().trim();run("Saving upload settings…",()->{if(!value.isEmpty()){if(!ImageUpload.credentialValid(value))throw new IllegalArgumentException("Enter a valid ImgBB API key.");vault.put("uploads:imgbb",value);}if(!ImageUpload.credentialValid(vault.get("uploads:imgbb")))throw new IllegalArgumentException("Enter the ImgBB API key first.");return true;},ok->{credential.setText("");if(fromComposer&&composeForm!=null)composer();else if(inAccount)account();else uploadSettings(false);Toast.makeText(this,"Upload settings saved.",Toast.LENGTH_SHORT).show();});}));
        gap(content,8);content.addView(settingsButton("Get API credential",()->external("https://api.imgbb.com/")));gap(content,12);content.addView(text("Select up to 20 JPEG, PNG, GIF or WebP images, up to 20 MB each. Videos are not supported. Images upload to ImgBB one at a time; successful links append to your message and copy together. The API key is encrypted on this phone and is not included in the APK. Images retain embedded metadata; the original filename is not sent. Uploading does not submit your reply.",14,MUTED));if(!inAccount){gap(content,12);content.addView(button(fromComposer?"‹ Back to composer":"‹ Back to Account",()->{if(fromComposer&&composeForm!=null)composer();else account();}));}
    }
    private void hideKeyboard(View view){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private void persistThreadTabs(){
        if(preferences==null)return;try{JSONArray list=new JSONArray();for(ThreadTab tab:threadTabs.values()){JSONObject record=new JSONObject().put("origin",tab.origin).put("id",tab.id).put("title",tab.title).put("own",tab.own);if(tab.anchor!=null)record.put("anchor",new JSONObject().put("tag",tab.anchor.tag==null?JSONObject.NULL:tab.anchor.tag).put("offset",tab.anchor.offset).put("y",tab.anchor.y));list.put(record);}preferences.edit().putString("openThreadTabs",new JSONObject().put("tabs",list).put("active",activeTab).toString()).apply();}catch(Exception ignored){}
    }
    private void restoreThreadTabs(){
        threadTabs.clear();activeTab="";try{String saved=preferences.getString("openThreadTabs","");if(saved.isEmpty()||saved.length()>30000)return;JSONObject state=new JSONObject(saved);JSONArray records=state.getJSONArray("tabs");for(int i=0;i<records.length()&&threadTabs.size()<12;i++){try{JSONObject record=records.getJSONObject(i);ThreadTab tab=new ThreadTab();tab.origin=ForumParser.origin(record.getString("origin")).toString();tab.id=record.getString("id");if(!tab.id.matches("[0-9]{1,20}"))continue;tab.title=record.optString("title","Thread "+tab.id);if(tab.title.length()>500)tab.title=tab.title.substring(0,500);tab.own=record.optString("own","");if(!tab.own.matches("[0-9]{0,30}"))tab.own="";JSONObject anchor=record.optJSONObject("anchor");if(anchor!=null){tab.anchor=new ReadingAnchor();String tag=anchor.optString("tag","");tab.anchor.tag=tag.matches("[0-9]{1,30}")?tag:null;tab.anchor.offset=Math.max(-1000000,Math.min(1000000,anchor.optInt("offset")));tab.anchor.y=Math.max(0,Math.min(10000000,anchor.optInt("y")));}threadTabs.put(tab.origin+":"+tab.id,tab);}catch(Exception invalid){}}String selected=state.optString("active","");if(threadTabs.containsKey(selected))activeTab=selected;}catch(Exception invalid){}
    }
    private AlertDialog linkActions(String address){
        if(busy)return null;String safe;try{safe=java.net.URI.create(client.origin).resolve(address).toString();PreviewFetch.webUrl(safe);}catch(Exception invalid){return null;}final String url=safe;String threadId=ForumParser.query(url,"thread_id");String forumOrigin="";try{forumOrigin=ForumParser.origin(url).toString();}catch(Exception ignored){}boolean threadLink=!forumOrigin.isEmpty()&&"/thread.php".equals(java.net.URI.create(url).getPath())&&threadId.matches("[0-9]{1,20}");final String origin=forumOrigin;
        String[] actions=threadLink?new String[]{"Open in new tab","Open thread","Open in browser","Copy link"}:new String[]{"Open in browser","Open preview","Copy link"};
        return new ThemeDialogs(this).setTitle(url).setItems(actions,(dialog,index)->{String action=actions[index];if(action.equals("Open in browser"))external(url);else if(action.equals("Copy link")){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Link",url));Toast.makeText(this,"Link copied",Toast.LENGTH_SHORT).show();}else if(action.equals("Open in new tab")){addLinkedThreadTab(origin,threadId,url);}else if(action.equals("Open thread")){String key=origin+":"+threadId;addLinkedThreadTab(origin,threadId,url);if(threadTabs.containsKey(key))navigateForumThread(origin,threadId,linkedPost(url),true);}else link(url);}).setNegativeButton("Cancel",null).show();
    }
    private void addLinkedThreadTab(String origin,String id,String title){String key=origin+":"+id;if(threadTabs.containsKey(key))return;if(threadTabs.size()>=12){Toast.makeText(this,"Close a tab before opening another (12 maximum).",Toast.LENGTH_LONG).show();return;}saveThreadTab();ThreadTab tab=new ThreadTab();tab.origin=origin;tab.id=id;tab.title="Thread "+id;String post=linkedPost(title);if(!post.isEmpty()){tab.anchor=new ReadingAnchor();tab.anchor.tag=post;}threadTabs.put(key,tab);persistThreadTabs();View count=toolbar.findViewWithTag("thread-tabs");if(count instanceof TabCountView)((TabCountView)count).setCount(threadTabs.size());Toast.makeText(this,"Opened in new tab",Toast.LENGTH_SHORT).show();}
    private void link(String url){
        if(busy||appLock!=null&&appLock.locked())return;
        try{String safe=java.net.URI.create(client.origin).resolve(url).toString(),origin=ForumParser.origin(safe).toString(),id=ForumParser.query(safe,"thread_id");if("/thread.php".equals(java.net.URI.create(safe).getPath())&&id.matches("[0-9]{1,20}")){if(!canOpenThread(origin,id))return;for(LinkPreviewDialog dialog:new ArrayList<>(previews))dialog.dismiss();navigateForumThread(origin,id,linkedPost(safe),false);return;}}catch(IllegalArgumentException invalid){}
        try{PreviewFetch.webUrl(url);LinkPreviewDialog dialog=new LinkPreviewDialog(this,url,this::link);dialog.setLinkHold(this::linkActions);previews.add(dialog);dialog.setOnDismissListener(d->{previews.remove(dialog);scheduleAuthors();});dialog.open();}catch(Exception e){Toast.makeText(this,"This link cannot be previewed.",Toast.LENGTH_SHORT).show();}
    }
    private void external(String url){Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))return;try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){Toast.makeText(this,"No browser is installed.",Toast.LENGTH_SHORT).show();}}
    private void closeComposer(){capture();if(composeForm!=null&&!composeForm.editPostId.isEmpty()&&draft.equals(composeForm.initialMessage)&&subject.equals(composeForm.initialSubject)){draft="";subject="";}if(composeForm!=null&&!composeForm.newThread&&current!=null){ReadingAnchor anchor=replyReturnThread.equals(client.origin+":"+current.id)?replyReturnAnchor:null;thread();if(anchor!=null&&screen.equals("thread"))restoreAnchor(anchor);}else board();}
    @Override public void onBackPressed(){if(appLock!=null&&appLock.locked()){moveTaskToBack(true);return;}if(busy)return;if(screen.equals("thread")&&findOpen){closeThreadFind();return;}if(helpBack())return;if(screen.equals("upload-settings")){if(uploadSettingsFromComposer&&composeForm!=null)composer();else account();}else if(screen.equals("compose")){closeComposer();}else if(!screen.equals("board")){board();}else super.onBackPressed();}
}
