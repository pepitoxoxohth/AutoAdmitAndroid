package com.xoclassic.app;
import android.app.*;
import android.content.Context;
/** Uses a layout listener so callers can keep their own OnShowListener and editor state. */
final class ThemeDialogs extends AlertDialog.Builder {
 ThemeDialogs(Context context){super(context,AppColors.light()?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert);}
 @Override public AlertDialog create(){AlertDialog d=super.create();AppLock.attach(d);AppColors.dialog(d);d.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(()->{AppColors.nativeTree(d.getWindow().getDecorView());});return d;}
}
