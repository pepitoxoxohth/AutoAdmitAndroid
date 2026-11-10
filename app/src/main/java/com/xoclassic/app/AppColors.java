package com.xoclassic.app;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.text.TextPaint;
import android.text.style.*;
import java.util.*;
import org.json.*;

/** Semantic bindings repaint existing views without replacing drafts or reading position. */
public final class AppColors {
 public static final int[] KEYS={0xff292b30,0xff34383f,0xffe1dfdc,0xffa8adb5,0xff9cbce3,0xff494e57,0xffdf9291,0xffd1b8d4,0xff5d7898,0xff40536b,0xff4c3d47,0xffe5d59a,0xfff0a94d,0xff242424,0xff000001,0xff000002,0xff000003,0xff000004};
 public static final String[] LABELS={"Page background","Panels and dialogs","Text","Secondary text","Links, buttons and icons","Borders","Moniker names","Visited links","Nested reply borders","Selected tab","New reply tint","Search match","Selected search match","Search match text","Post cards","Buttons","Input background","New reply star"};
 private static int[] colors=defaults();
 private static final WeakHashMap<View,int[]> views=new WeakHashMap<>();
 private static final WeakHashMap<GradientDrawable,int[]> shapes=new WeakHashMap<>();
 private static final WeakHashMap<android.app.Dialog,Boolean> dialogs=new WeakHashMap<>();
 public static void dialog(android.app.Dialog d){AppLock.attach(d);dialogs.put(d,true);GradientDrawable panel=new GradientDrawable();fill(panel,0xff34383f);panel.setCornerRadius(18*d.getContext().getResources().getDisplayMetrics().density);d.getWindow().setBackgroundDrawable(panel);nativeTree(d.getWindow().getDecorView());}
 public static int[] defaults(){int[] c=KEYS.clone();c[14]=c[15]=c[16]=0xff34383f;c[17]=0xffdf9291;return c;}
 public static int color(int key){for(int i=0;i<KEYS.length;i++)if(KEYS[i]==key)return colors[i];return key;}
 public static String hex(int value){return String.format(Locale.ROOT,"#%06X",value&0xffffff);}
 public static int parse(String value){if(!value.matches("#?[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use six hex digits, for example #292B30");return Color.parseColor(value.startsWith("#")?value:"#"+value);}
 public static boolean light(){int c=colors[0];return .2126*Color.red(c)+.7152*Color.green(c)+.0722*Color.blue(c)>160;}
 private static void bind(View v,int slot,int key){int[] a=views.get(v);if(a==null){a=new int[5];views.put(v,a);}a[slot]=key;a[4]|=1<<slot;paint(v,a);}
 public static void text(TextView v,int key){bind(v,0,key);} public static void hint(TextView v,int key){bind(v,1,key);} public static void link(TextView v,int key){bind(v,2,key);} public static void background(View v,int key){bind(v,3,key);}
 private static void paint(View v,int[] a){if(v instanceof TextView){TextView t=(TextView)v;if((a[4]&1)!=0)t.setTextColor(color(a[0]));if((a[4]&2)!=0)t.setHintTextColor(color(a[1]));if((a[4]&4)!=0)t.setLinkTextColor(color(a[2]));}if((a[4]&8)!=0)v.setBackgroundColor(color(a[3]));v.invalidate();}
 public static void fill(GradientDrawable d,int key){int[] a=shapes.get(d);if(a==null){a=new int[]{key,0,0};shapes.put(d,a);}a[0]=key;d.setColor(color(key));}
 public static void stroke(GradientDrawable d,int width,int key){int[] a=shapes.get(d);if(a==null){a=new int[3];shapes.put(d,a);}a[1]=width;a[2]=key;d.setStroke(width,color(key));}
 public static void apply(int[] palette){colors=palette.clone();for(Map.Entry<View,int[]> e:new ArrayList<>(views.entrySet()))paint(e.getKey(),e.getValue());for(Map.Entry<GradientDrawable,int[]> e:new ArrayList<>(shapes.entrySet())){int[] a=e.getValue();if(a[0]!=0)e.getKey().setColor(color(a[0]));if(a[2]!=0)e.getKey().setStroke(a[1],color(a[2]));}for(android.app.Dialog d:new ArrayList<>(dialogs.keySet()))if(d.isShowing()){nativeTree(d.getWindow().getDecorView());invalidate(d.getWindow().getDecorView());}}
 public static void window(Window w){if(w==null)return;w.setStatusBarColor(color(KEYS[0]));w.setNavigationBarColor(color(KEYS[0]));w.setBackgroundDrawable(new ColorDrawable(color(KEYS[0])));int flags=w.getDecorView().getSystemUiVisibility(),mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;w.getDecorView().setSystemUiVisibility(light()?flags|mask:flags&~mask);invalidate(w.getDecorView());}
 public static void nativeTree(View v){if(v instanceof TextView&&!views.containsKey(v)){text((TextView)v,v instanceof Button?0xff9cbce3:0xffe1dfdc);if(v instanceof EditText)hint((TextView)v,0xffa8adb5);}if(v instanceof Spinner)v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(0xff9cbce3)));if(v instanceof CompoundButton)((CompoundButton)v).setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{color(0xff9cbce3),color(0xffa8adb5)}));if(v instanceof SeekBar)((SeekBar)v).setProgressTintList(android.content.res.ColorStateList.valueOf(color(0xff9cbce3)));if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)nativeTree(((ViewGroup)v).getChildAt(i));}
 private static void invalidate(View v){v.invalidate();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)invalidate(((ViewGroup)v).getChildAt(i));}
 public static class Ink extends ForegroundColorSpan {private final int key;public Ink(int key){super(key);this.key=key;}@Override public void updateDrawState(TextPaint p){p.setColor(color(key));}}
 public static class Mark extends BackgroundColorSpan {private final int key;public Mark(int key){super(key);this.key=key;}@Override public void updateDrawState(TextPaint p){p.bgColor=color(key);}}
 public static final class Store {
  final SharedPreferences prefs;
  public Store(Context c){prefs=c.getSharedPreferences("appearance",0);}
  public LinkedHashMap<String,int[]> themes(){LinkedHashMap<String,int[]> m=new LinkedHashMap<>();m.put("Default",defaults());m.put("Light",preset(new int[]{0xfff5f3ef,0xffe5e4e1,0xff24272d,0xff626975,0xff285a8b,0xffc5c9cf,0xffa34345,0xff775388,0xff94abc2,0xffcbdced,0xfff1dce3}));m.put("Midnight",preset(new int[]{0xff0d1117,0xff161d27,0xffe6edf3,0xff98a7b9,0xff7ab8ff,0xff303b4a,0xfff09b9a,0xffc3a0df,0xff486988,0xff244664,0xff42303f}));m.put("Sepia",preset(new int[]{0xfff1e8d6,0xffe3d6bc,0xff3f3529,0xff786b59,0xff426575,0xffbcad92,0xff9b4c46,0xff80637c,0xffa89a7f,0xffd0c2a5,0xffe9cdb8}));m.put("Ocean",preset(new int[]{0xff112a32,0xff1b3943,0xffe0edec,0xff9ab8bc,0xff80cfd5,0xff365963,0xffefa59d,0xffc6afe0,0xff5e8795,0xff315c69,0xff51414b}));try{JSONObject all=new JSONObject(prefs.getString("custom","{}"));Iterator<String> keys=all.keys();while(keys.hasNext()){String n=keys.next();JSONArray a=all.getJSONArray(n);if(a.length()!=KEYS.length||m.containsKey(n))continue;int[] c=new int[KEYS.length];for(int i=0;i<c.length;i++)c[i]=a.getInt(i)|0xff000000;m.put(n,c);}}catch(Exception ignored){}return m;}
  private static int[] preset(int[] first){int[] c=defaults();System.arraycopy(first,0,c,0,first.length);c[14]=c[15]=c[16]=c[1];c[17]=c[6];return c;}
  public String selected(int minute){if(!prefs.getBoolean("scheduled",false))return valid(prefs.getString("active","Default"));int day=prefs.getInt("dayTime",420),night=prefs.getInt("nightTime",1140);boolean daytime=day<night?minute>=day&&minute<night:minute>=day||minute<night;return valid(prefs.getString(daytime?"day":"night",daytime?"Light":"Default"));}
  String valid(String n){return themes().containsKey(n)?n:"Default";}
  public String current(){Calendar c=Calendar.getInstance();return selected(c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE));}
  public void save(String name,int[] c){if(name.trim().isEmpty()||name.length()>40)throw new IllegalArgumentException("Use a name from 1 to 40 characters");if(Arrays.asList("Default","Light","Midnight","Sepia","Ocean").contains(name))throw new IllegalArgumentException("Choose a new name; presets are preserved");try{JSONObject all=new JSONObject(prefs.getString("custom","{}"));JSONArray a=new JSONArray();for(int x:c)a.put(x);all.put(name,a);prefs.edit().putString("custom",all.toString()).apply();}catch(JSONException e){throw new IllegalArgumentException("Could not save theme",e);}}
  public void remove(String name){try{JSONObject all=new JSONObject(prefs.getString("custom","{}"));all.remove(name);SharedPreferences.Editor e=prefs.edit().putString("custom",all.toString());for(String k:new String[]{"active","day","night"})if(prefs.getString(k,"").equals(name))e.putString(k,k.equals("day")?"Light":"Default");e.apply();}catch(Exception ignored){}}
 }
}
