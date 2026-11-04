package local.offline.clicker;

import android.content.Context;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final String PREFERENCES="appearance";
    static int BG=Color.WHITE,SURFACE=Color.WHITE,INK=Color.rgb(32,33,36),MUTED=Color.rgb(107,114,128),GREEN=Color.rgb(48,52,59),PALE=Color.rgb(243,244,246),RED=Color.rgb(173,50,59),BORDER=Color.rgb(229,231,235),ON_ACCENT=Color.WHITE;
    static int mode(Context c){return c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getInt("mode",Appearance.LIGHT);}
    static void setMode(Context c,int mode){c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putInt("mode",mode).apply();}
    static boolean isDark(Context c){return Appearance.isDark(mode(c),(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
    static void configure(Context c){
        boolean dark=isDark(c);
        BG=Color.parseColor(dark?"#121212":"#FFFFFF");SURFACE=Color.parseColor(dark?"#1D1E20":"#FFFFFF");
        INK=Color.parseColor(dark?"#F3F4F6":"#202124");MUTED=Color.parseColor(dark?"#A5AAB3":"#6B7280");
        GREEN=Color.parseColor(dark?"#E5E7EB":"#30343B");ON_ACCENT=Color.parseColor(dark?"#161719":"#FFFFFF");
        PALE=Color.parseColor(dark?"#2A2C30":"#F3F4F6");BORDER=Color.parseColor(dark?"#464A51":"#E5E7EB");RED=Color.parseColor(dark?"#FF929A":"#AD323B");
    }
    static void applyTheme(Activity a){configure(a);a.setTheme(isDark(a)?R.style.AppThemeDark:R.style.AppTheme);}
    static void systemBars(Activity a){
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        int flags=a.getWindow().getDecorView().getSystemUiVisibility();
        flags=isDark(a)?flags&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(android.os.Build.VERSION.SDK_INT>=26)flags=isDark(a)?flags&~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static GradientDrawable card(Context c){GradientDrawable d=bg(SURFACE,dp(c,18));d.setStroke(dp(c,1),BORDER);return d;}
    static GradientDrawable selector(Context c){GradientDrawable d=bg(PALE,dp(c,12));d.setStroke(dp(c,1),MUTED);return d;}
    static TextView text(Context c,String value,int sp,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(true);return t;}
    static Button button(Context c,String label,boolean primary,Runnable click){
        Button b=new Button(c);b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setOutlineProvider(null);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,8),0,dp(c,8),0);b.setTextColor(primary?ON_ACCENT:GREEN);b.setBackground(bg(primary?GREEN:PALE,dp(c,13)));b.setOnClickListener(v->click.run());return b;
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static void space(LinearLayout l,int height){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),height)));}
    static void weight(LinearLayout l,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(l.getContext(),3),dp(l.getContext(),3),dp(l.getContext(),3),dp(l.getContext(),3));l.addView(v,p);}
    static TextView title(Context c,String value,int size){TextView t=text(c,value,size,INK);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
}
