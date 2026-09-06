package gr.anelix.velatune;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;
public final class PitchView extends View {
    private final Paint paint=new Paint(3);
    private final Design d;
    private float hz=0,target=0,in=0,out=0;
    private long clips=0;
    public PitchView(Context c,Design design) {
        super(c);
        d=design;
        setMinimumHeight(d.dp(218));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Δεν ανιχνεύεται νότα");
    }
    public void update(float[] m) {
        hz=m[3];
        target=m[4];
        in=m[6];
        out=m[7];
        clips=(long)m[8];
        setContentDescription(hz>0?"Νότα "+note(hz)+", στόχος "+note(target):"Δεν ανιχνεύεται νότα");
        invalidate();
    }
    public static String note(float f) {
        if(f<=0)return "—";
        int n=Math.round(69+12*(float)(Math.log(f/440)/Math.log(2)));
        String[] names= {
            "C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"
        }
        ;
        return names[(n%12+12)%12]+(n/12-1);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),cx=w/2;
        paint.setTypeface(d.regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(d.dp(12));
        paint.setColor(Design.MUTED);
        c.drawText("ΖΩΝΤΑΝΗ ΦΩΝΗ",cx,d.dp(22),paint);
        paint.setTypeface(d.bold);
        paint.setTextSize(d.dp(58));
        paint.setColor(Design.TEXT);
        c.drawText(note(hz),cx,d.dp(93),paint);
        paint.setTypeface(d.regular);
        paint.setTextSize(d.dp(12));
        paint.setColor(Design.ACCENT);
        c.drawText(target>0?String.format(Locale.ROOT,"%.1f Hz  →  %s",hz,note(target)):"Το μικρόφωνο περιμένει",cx,d.dp(119),paint);
        float y=d.dp(142),left=d.dp(25),right=w-d.dp(25);
        paint.setColor(0xff38433b);
        paint.setStrokeWidth(d.dp(2));
        c.drawLine(left,y,right,y,paint);
        c.drawLine(cx,y-d.dp(5),cx,y+d.dp(5),paint);
        if(hz>0&&target>0) {
            float cents=1200*(float)(Math.log(hz/target)/Math.log(2));
            float pos=cx+Math.max(-1,Math.min(1,cents/100))*(right-left)/2;
            paint.setColor(Design.ACCENT);
            c.drawCircle(pos,y,d.dp(5),paint);
        }
        meter(c,"IN",in,d.dp(165),w);
        meter(c,"OUT",out,d.dp(191),w);
        if(clips>0) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(d.dp(10));
            paint.setColor(Design.RED);
            c.drawText("CLIP "+clips,w-d.dp(12),d.dp(22),paint);
        }
    }
    private void meter(Canvas c,String label,float value,float y,float w) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(d.dp(10));
        paint.setColor(Design.MUTED);
        c.drawText(label,d.dp(10),y+d.dp(4),paint);
        float left=d.dp(46),right=w-d.dp(12);
        paint.setColor(0xff344036);
        c.drawRoundRect(left,y-d.dp(3),right,y+d.dp(3),d.dp(3),d.dp(3),paint);
        float level=(float)Math.max(0,Math.min(1,(20*Math.log10(Math.max(0.001,value))+60)/60));
        paint.setColor(value>=.98?Design.RED:Design.ACCENT);
        c.drawRoundRect(left,y-d.dp(3),left+(right-left)*level,y+d.dp(3),d.dp(3),d.dp(3),paint);
    }
}
