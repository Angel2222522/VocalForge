package gr.anelix.velatune;
import android.content.Context;
import android.graphics.*;
import android.view.*;
public final class WaveformView extends View {
    public interface Seek {
        void to(float fraction);
    }
    private final Paint paint=new Paint(3);
    private final Design d;
    private float[] peaks=new float[512];
    private float position=0;
    private final Seek seek;
    public WaveformView(Context c,Design design,Seek callback) {
        super(c);
        d=design;
        seek=callback;
        setMinimumHeight(d.dp(150));
        setContentDescription("Κυματομορφή. Χρησιμοποίησε τη μπάρα θέσης για προσβάσιμη μετακίνηση.");
    }
    public void setWaveform(float[] data) {
        peaks=data;
        invalidate();
    }
    public void setPosition(float x) {
        position=x;
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        float width=getWidth(),height=getHeight();
        paint.setColor(Design.SURFACE);
        canvas.drawRoundRect(0,0,width,height,d.dp(16),d.dp(16),paint);
        paint.setStrokeWidth(Math.max(1,width/peaks.length*.65f));
        for(int i=0;i<peaks.length;i++) {
            float x=(i+.5f)*width/peaks.length,amp=Math.max(1,Math.min(1,peaks[i])*height*.42f);
            paint.setColor(x<position*width?Design.ACCENT:0xff637768);
            canvas.drawLine(x,height/2-amp,x,height/2+amp,paint);
        }
        paint.setColor(Design.TEXT);
        paint.setStrokeWidth(d.dp(2));
        canvas.drawLine(width*position,8,width*position,height-8,paint);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(true);
            position=Math.max(0,Math.min(1,e.getX()/getWidth()));
            seek.to(position);
            invalidate();
            if(e.getAction()==MotionEvent.ACTION_UP)performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
