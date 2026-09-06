package gr.anelix.velatune;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
public final class Design {
    public static final int BG=0xff101211,SURFACE=0xff1b201d,TEXT=0xfff0f3ed,MUTED=0xffaab5ab,ACCENT=0xffbaf47a,RED=0xffff796e;
    private final Context context;
    public final Typeface regular,bold;
    public Design(Context c) {
        context=c;
        regular=Typeface.createFromAsset(c.getAssets(),"fonts/DejaVuSans.ttf");
        bold=Typeface.createFromAsset(c.getAssets(),"fonts/DejaVuSans-Bold.ttf");
    }
    public int dp(float n) {
        return Math.round(n*context.getResources().getDisplayMetrics().density);
    }
    public GradientDrawable background(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    public TextView text(String value,int size,int color) {
        TextView v=new TextView(context);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setTypeface(regular);
        v.setLineSpacing(dp(3),1);
        return v;
    }
    public TextView title(String value,int size) {
        TextView t=text(value,size,TEXT);
        t.setTypeface(bold);
        return t;
    }
    public LinearLayout column() {
        LinearLayout l=new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    public LinearLayout row() {
        LinearLayout l=new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }
    public Button button(String text,boolean primary,Runnable action) {
        Button b=new Button(context);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(primary?BG:TEXT);
        b.setTextSize(14);
        b.setTypeface(bold);
        b.setMinHeight(dp(52));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));
        b.setBackground(background(primary?ACCENT:SURFACE,16));
        b.setOnClickListener(v->action.run());
        return b;
    }
    public LinearLayout card() {
        LinearLayout c=column();
        c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(background(SURFACE,20));
        return c;
    }
    public void add(LinearLayout parent,View child,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(margin);
        parent.addView(child,p);
    }
    public void equal(LinearLayout row,View view) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),dp(4),dp(3),dp(4));
        row.addView(view,p);
    }
}
