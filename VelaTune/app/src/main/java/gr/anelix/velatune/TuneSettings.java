package gr.anelix.velatune;
import android.content.*;
public final class TuneSettings {
    private TuneSettings() {
    }
    public static float[] load(Context c) {
        SharedPreferences s=c.getSharedPreferences("settings-v1",0);
        float[] p= {
            0,0,35,.35f,1,1,0,-3,0,0
        }
        ;
        for(int i=0;i<p.length;i++)p[i]=s.getFloat("p"+i,p[i]);
        return p;
    }
    public static void save(Context c,float[] p) {
        SharedPreferences.Editor e=c.getSharedPreferences("settings-v1",0).edit();
        for(int i=0;i<p.length;i++)e.putFloat("p"+i,p[i]);
        e.apply();
    }
    public static float[] preset(int index) {
        switch(index) {
            case 1:return new float[] {
                0,2,0,0,1,1,0,-3,1,0
            }
            ;
            case 2:return new float[] {
                0,0,70,.7f,.8f,1,0,-3,0,0
            }
            ;
            default:return new float[] {
                0,0,35,.35f,1,1,0,-3,0,0
            }
            ;
        }
    }
    public static void saveUser(Context c,String name,float[] p) {
        StringBuilder b=new StringBuilder();
        for(float v:p)b.append(v).append(',');
        c.getSharedPreferences("presets-v1",0).edit().putString(name,b.toString()).apply();
    }
    public static float[] loadUser(Context c,String name) {
        String s=c.getSharedPreferences("presets-v1",0).getString(name,"");
        String[] parts=s.split(",");
        if(parts.length!=10)return preset(0);
        float[] a=new float[10];
        try {
            for(int i=0;i<10;i++)a[i]=Float.parseFloat(parts[i]);
            return a;
        }
        catch(NumberFormatException e) {
            return preset(0);
        }
    }
}
