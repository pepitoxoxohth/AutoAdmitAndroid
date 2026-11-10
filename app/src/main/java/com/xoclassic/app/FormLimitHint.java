package com.xoclassic.app;

import android.text.*;
import android.widget.*;

/** Show over-limit edits without silently truncating drafts, pasted text or upload links. */
final class FormLimitHint {
    static void bind(EditText input,LinearLayout parent,FormLimits.Rule rule){
        TextView count=new TextView(input.getContext());count.setTextSize(12);count.setTag("limit:"+rule.label);AppColors.text(count,0xffa8adb5);if(rule.max>=0&&!rule.label.equals("Password"))parent.addView(count);
        Runnable update=()->{String text=input.getText().toString(),error=rule.error(text,false);input.setError(error);if(rule.max>=0){count.setText(rule.length(text)+" / "+rule.max);count.setContentDescription(rule.label+": "+rule.length(text)+" of "+rule.max+" characters");AppColors.text(count,error==null?0xffa8adb5:0xffdf9291);}};
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int n,int after){}public void onTextChanged(CharSequence s,int start,int before,int n){update.run();}public void afterTextChanged(Editable e){}});update.run();
    }
}
