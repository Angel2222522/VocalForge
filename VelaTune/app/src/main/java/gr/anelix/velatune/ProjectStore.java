package gr.anelix.velatune;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Process-owned editor jobs; survives rotation without retaining Activity. */
public final class ProjectStore {
    private static ProjectStore instance;
    public static synchronized ProjectStore get(Context c) {
        if(instance==null)instance=new ProjectStore(c.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final File directory;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel=new AtomicBoolean();
    public volatile boolean busy=false;
    public volatile String status="Εισήγαγε μια καθαρή φωνή χωρίς beat.";
    public volatile File source,result;
    public volatile float[] waveform=new float[512];
    public volatile long version=0;
    private final SharedPreferences prefs;
    private ProjectStore(Context c) {
        context=c;
        directory=new File(c.getFilesDir(),"projects");
        directory.mkdirs();
        prefs=c.getSharedPreferences("session-v1",0);
        File s=new File(prefs.getString("source","/nonexistent")),r=new File(prefs.getString("result","/nonexistent"));
        if(s.isFile())source=s;
        if(r.isFile())result=r;
        worker.execute(()-> {
            try {
                File takes=new File(c.getFilesDir(),"takes");
                File[] files=takes.listFiles();
                if(files!=null)for(File f:files)if(f.getName().endsWith(".part")) {
                    WaveFile.recover(f);
                    File recovered=new File(takes,f.getName().replace(".part","-recovered.wav"));
                    if(f.renameTo(recovered))prefs.edit().putString("take",recovered.getAbsolutePath()).apply();
                }
                File[] stale=directory.listFiles();
                if(stale!=null)for(File f:stale)if(f.getName().endsWith(".part"))f.delete();
                if(source!=null)waveform=AudioFiles.waveform(source,cancel);
                version++;
            }
            catch(Exception e) {
                status="Ανάκτηση: "+e.getMessage();
            }
        }
        );
    }
    private interface Job {
        void run()throws Exception;
    }
    private synchronized void job(Job action) {
        if(busy)return;
        busy=true;
        cancel.set(false);
        worker.execute(()-> {
            try {
                action.run();
            }
            catch(CancellationException e) {
                status="Ακυρώθηκε";
            }
            catch(Exception e) {
                status="Σφάλμα: "+e.getMessage();
            }
            finally {
                busy=false;
                version++;
            }
        }
        );
    }
    private void select(File file)throws Exception {
        float[] peaks=AudioFiles.waveform(file,cancel);
        source=file;
        result=null;
        waveform=peaks;
        prefs.edit().putString("source",file.getAbsolutePath()).remove("result").apply();
        status="Έτοιμο για επεξεργασία";
    }
    public void importAudio(Uri uri) {
        job(()-> {
            status="Εισαγωγή…";
            File f=AudioFiles.importAudio(context,uri,directory,cancel,s->status=s);
            select(f);
        }
        );
    }
    public void useTake(File file) {
        job(()->select(file));
    }
    public void render(float[] p) {
        File selected=source;
        if(selected==null)return;
        job(()-> {
            status="Επεξεργασία…";
            File f=AudioFiles.render(selected,directory,p.clone(),cancel,s->status=s);
            result=f;
            prefs.edit().putString("result",f.getAbsolutePath()).apply();
            status="Έτοιμο · σύγκρινε Πριν / Μετά";
        }
        );
    }
    public void export(Uri uri,int bits,boolean processed) {
        File selected=processed?result:source;
        if(selected==null)return;
        job(()-> {
            status="Εξαγωγή…";
            AudioFiles.export(context,selected,uri,bits,cancel);
            status="Το WAV αποθηκεύτηκε";
        }
        );
    }
    public void cancel() {
        cancel.set(true);
    }
}
