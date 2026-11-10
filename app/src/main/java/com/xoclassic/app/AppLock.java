package com.xoclassic.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Device-credential gate. Unlock grants exist in memory only and expire when the activity pauses. */
final class AppLock {
    static final int UNLOCK=9470,ENABLE=9471,DISABLE=9472;
    interface CredentialPrompt {boolean secure();void launch(int request,String title);}
    private final Activity activity;private final SharedPreferences prefs;private final Runnable opened,changed;private final CredentialPrompt prompt;
    private final WeakHashMap<Window,Cover> windows=new WeakHashMap<>();
    private boolean unlocked,automatic=true,closed;private int pending;private Dialog gate;
    private static final class Cover {View mask;boolean originalSecure,forcedSecure;final Map<View,Integer> accessibility=new WeakHashMap<>();}
    AppLock(Activity activity,SharedPreferences prefs,Runnable opened,Runnable changed){this(activity,prefs,opened,changed,new CredentialPrompt(){
        public boolean secure(){KeyguardManager k=(KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE);return k!=null&&k.isDeviceSecure();}
        public void launch(int request,String title){KeyguardManager k=(KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE);Intent intent=k==null?null:k.createConfirmDeviceCredentialIntent(title,"Confirm your phone PIN, pattern or password");if(intent==null)throw new ActivityNotFoundException();activity.startActivityForResult(intent,request);}
    });}
    AppLock(Activity activity,SharedPreferences prefs,Runnable opened,Runnable changed,CredentialPrompt prompt){this.activity=activity;this.prefs=prefs;this.opened=opened;this.changed=changed;this.prompt=prompt;register(activity.getWindow());}
    boolean enabled(){return prefs.getBoolean("appLockRequired",false);}
    boolean locked(){return enabled()&&!unlocked;}
    void register(Window window){if(window==null||closed)return;Cover cover=windows.get(window);if(cover==null){cover=new Cover();cover.originalSecure=(window.getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0;windows.put(window,cover);}paint(window,cover);}
    static void attach(Dialog dialog){Context context=dialog.getContext();for(int i=0;i<12&&context instanceof ContextWrapper;i++){if(context instanceof MainActivity){((MainActivity)context).registerAppWindow(dialog.getWindow());return;}Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}}
    private void paint(Window window,Cover cover){
        if(enabled()){window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);cover.forcedSecure=true;}else if(cover.forcedSecure&&!cover.originalSecure){window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);cover.forcedSecure=false;}
        ViewGroup decor=(ViewGroup)window.getDecorView();
        if(locked()){
            if(cover.mask==null){LinearLayout mask=new LinearLayout(activity);mask.setTag("app-lock-cover");mask.setGravity(Gravity.CENTER);mask.setOrientation(1);AppColors.background(mask,0xff292b30);TextView label=new TextView(activity);label.setText("AutoAdmit is locked");label.setTextSize(22);AppColors.text(label,0xffe1dfdc);mask.addView(label);mask.addView(button("Unlock",()->request(UNLOCK)));mask.addView(button("Exit",()->activity.moveTaskToBack(true)));mask.setClickable(true);mask.setFocusable(true);cover.mask=mask;}
            for(int i=0;i<decor.getChildCount();i++){View child=decor.getChildAt(i);if(child==cover.mask)continue;cover.accessibility.putIfAbsent(child,child.getImportantForAccessibility());child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
            if(cover.mask.getParent()==null)decor.addView(cover.mask,new ViewGroup.LayoutParams(-1,-1));cover.mask.bringToFront();
        }else{if(cover.mask!=null&&cover.mask.getParent()==decor)decor.removeView(cover.mask);for(Map.Entry<View,Integer> entry:cover.accessibility.entrySet())entry.getKey().setImportantForAccessibility(entry.getValue());cover.accessibility.clear();}
    }
    private void repaint(){for(Map.Entry<Window,Cover> entry:new ArrayList<>(windows.entrySet()))paint(entry.getKey(),entry.getValue());}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTypeface(Typeface.SERIF);v.setGravity(Gravity.CENTER);v.setPadding(12,12,12,12);AppColors.text(v,0xffe1dfdc);return v;}
    private Button button(String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setAllCaps(false);b.setOnClickListener(v->action.run());AppColors.text(b,0xff9cbce3);return b;}
    private void cover(){if(!locked()||closed)return;android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0);repaint();if(gate!=null&&gate.isShowing())return;gate=new Dialog(activity,R.style.AppTheme);gate.setCancelable(false);LinearLayout page=new LinearLayout(activity);page.setTag("app-lock-screen");page.setOrientation(1);page.setGravity(Gravity.CENTER);int pad=Math.round(24*activity.getResources().getDisplayMetrics().density);page.setPadding(pad,pad,pad,pad);AppColors.background(page,0xff292b30);page.addView(text("AutoAdmit is locked",24));page.addView(text(prompt.secure()?"Unlock with your phone PIN, pattern or password.":"Set a phone screen lock in Android Settings to unlock this app.",16));Button unlock=button("Unlock",()->request(UNLOCK));unlock.setTag("app-lock-unlock");page.addView(unlock,new LinearLayout.LayoutParams(-1,-2));if(!prompt.secure())page.addView(button("Android security settings",()->{try{activity.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));}catch(ActivityNotFoundException unavailable){notice("Open Android Settings and set a screen lock.");}}));page.addView(button("Exit",()->activity.moveTaskToBack(true)));gate.setContentView(page);Window w=gate.getWindow();w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);gate.show();w.setLayout(-1,-1);AppColors.window(w);}
    boolean resume(){if(!locked()){repaint();return true;}cover();if(automatic&&pending==0){automatic=false;request(UNLOCK);}return false;}
    void background(){if(!enabled())return;unlocked=false;if(pending==0)automatic=true;cover();}
    void setting(boolean required){if(required==enabled())return;request(required?ENABLE:DISABLE);}
    private void request(int request){if(closed||pending!=0)return;if(request==UNLOCK&&!locked())return;if(!prompt.secure()){notice("Set a screen lock in Android Settings first. App lock setting unchanged.");if(locked())cover();return;}pending=request;try{prompt.launch(request,request==ENABLE?"Enable AutoAdmit app lock":request==DISABLE?"Turn off AutoAdmit app lock":"Unlock AutoAdmit");}catch(RuntimeException unavailable){pending=0;automatic=false;notice("Phone unlock is unavailable. App lock setting unchanged.");if(locked())cover();}}
    boolean result(int request,int result){if(request!=UNLOCK&&request!=ENABLE&&request!=DISABLE)return false;if(pending!=request)return true;pending=0;automatic=false;if(result!=Activity.RESULT_OK||!prompt.secure()){if(locked())cover();return true;}if(request!=UNLOCK&&!prefs.edit().putBoolean("appLockRequired",request==ENABLE).commit()){notice("Could not save app lock. Try again.");if(locked())cover();return true;}unlocked=true;reveal();if(request!=UNLOCK)changed.run();opened.run();return true;}
    void relatedCredentialAccepted(){if(!enabled()||pending!=0||!prompt.secure())return;unlocked=true;reveal();opened.run();}
    private void reveal(){repaint();if(gate!=null){gate.dismiss();gate=null;}}
    void privacy(){register(activity.getWindow());}
    void close(){closed=true;if(gate!=null){gate.dismiss();gate=null;}for(Map.Entry<Window,Cover> entry:new ArrayList<>(windows.entrySet())){Cover c=entry.getValue();ViewGroup decor=(ViewGroup)entry.getKey().getDecorView();if(c.mask!=null&&c.mask.getParent()==decor)decor.removeView(c.mask);for(Map.Entry<View,Integer> a:c.accessibility.entrySet())a.getKey().setImportantForAccessibility(a.getValue());if(c.forcedSecure&&!c.originalSecure)entry.getKey().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}windows.clear();}
    private void notice(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
}
