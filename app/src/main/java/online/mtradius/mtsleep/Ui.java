package online.mtradius.mtsleep;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG=Color.rgb(7,15,33), CARD=Color.rgb(17,31,57),
        CARD2=Color.rgb(25,43,70), CYAN=Color.rgb(90,224,218),
        TEXT=Color.rgb(236,244,255), MUTED=Color.rgb(164,182,208),
        YELLOW=Color.rgb(244,202,100), RED=Color.rgb(255,113,133);
    private Ui(){}

    public static int dp(Context c,float value){return (int)(value*c.getResources().getDisplayMetrics().density+0.5f);}
    public static GradientDrawable bg(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static LinearLayout column(Context c) {
        LinearLayout l=new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    public static LinearLayout row(Context c) {
        LinearLayout l=new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    public static LinearLayout card(Context c){
        LinearLayout l=column(c); l.setBackground(bg(c,CARD,22));
        l.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(c,13)); l.setLayoutParams(p);
        return l;
    }
    public static TextView text(Context c,String s,int size,int color,boolean bold){
        TextView v=new TextView(c); v.setText(s); v.setTextColor(color);
        v.setTextSize(size); v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    public static void add(LinearLayout parent,View v,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(parent.getContext(),bottom); parent.addView(v,p);
    }
    public static Button button(Context c,String caption,boolean primary){
        Button b=new Button(c);
        b.setAllCaps(false); b.setText(caption); b.setTextSize(15);
        b.setTextColor(primary?BG:TEXT);
        b.setBackground(bg(c,primary?CYAN:CARD2,15));
        b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        b.setMinHeight(dp(c,51)); return b;
    }
    public static void space(LinearLayout p,int h) {
        View v=new View(p.getContext());
        p.addView(v,new LinearLayout.LayoutParams(1,dp(p.getContext(),h)));
    }
}
