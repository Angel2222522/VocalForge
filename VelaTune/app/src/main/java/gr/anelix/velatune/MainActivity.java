package gr.anelix.velatune;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;
public final class MainActivity extends Activity {
    private Design d;
    private float[] p;
    private ProjectStore project;
    private final Playback playback=new Playback();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content,root,bottom;
    private TextView status,keyLabel,editorStatus;
    private PitchView pitch;
    private WaveformView waveform;
    private SeekBar seek;
    private Button record,live,play,afterButton,render,export;
    private Switch monitoring;
    private boolean editor=false,after=false,monitor=false,pendingRecord=true;
    private int exportDepth=24;
    private boolean exportProcessed=false;
    private long projectVersion=-1;
    private float position=0;
    private Uri pendingExport;
    private final Runnable refresh=new Runnable() {
        public void run() {
            if(status!=null)status.setText(AudioService.message);
            if(pitch!=null)pitch.update(AudioService.meters);
            if(record!=null) {
                record.setText(AudioService.running?"■  Τέλος λήψης":"●  Εγγραφή");
                live.setText(AudioService.running?"■  Διακοπή":"Ακρόαση");
            }
            if(editorStatus!=null)editorStatus.setText(project.status);
            if(waveform!=null) {
                if(projectVersion!=project.version) {
                    waveform.setWaveform(project.waveform);
                    projectVersion=project.version;
                }
                if(playback.playing) {
                    position=playback.fraction;
                    waveform.setPosition(position);
                    seek.setProgress((int)(position*1000));
                }
            }
            if(play!=null) {
                play.setText(playback.playing?"■  Παύση":"▶  Αναπαραγωγή");
                play.setEnabled(!project.busy&&project.source!=null&&!AudioService.running);
                afterButton.setEnabled(project.result!=null&&!project.busy);
                render.setEnabled(!project.busy&&project.source!=null&&!AudioService.running);
                export.setEnabled(!project.busy&&(after?project.result:project.source)!=null);
            }
            if(!playback.error.isEmpty()) {
                Toast.makeText(MainActivity.this,playback.error,Toast.LENGTH_LONG).show();
                playback.error="";
            }
            handler.postDelayed(this,100);
        }
    }
    ;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        d=new Design(this);
        p=TuneSettings.load(this);
        project=ProjectStore.get(this);
        if(saved!=null) {
            editor=saved.getBoolean("editor");
            position=saved.getFloat("position");
            after=saved.getBoolean("after");
            exportDepth=saved.getInt("exportDepth",24);
            exportProcessed=saved.getBoolean("exportProcessed");
        }
        build();
    }
    private void build() {
        pitch=null;
        waveform=null;
        editorStatus=null;
        play=null;
        record=null;
        status=null;
        projectVersion=-1;
        root=d.column();
        root.setBackgroundColor(Design.BG);
        root.setPadding(d.dp(16),d.dp(10),d.dp(16),0);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            v.setPadding(d.dp(16),insets.getSystemWindowInsetTop()+d.dp(8),d.dp(16),insets.getSystemWindowInsetBottom());
            return insets;
        }
        );
        setContentView(root);
        root.requestApplyInsets();
        LinearLayout header=d.row();
        TextView logo=d.title("VELA / TUNE",20);
        header.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(d.button("ⓘ",false,this::diagnostics));
        d.add(root,header,0);
        LinearLayout tabs=d.row();
        d.equal(tabs,d.button("Μικρόφωνο",!editor,()-> {
            playback.close();
            editor=false;
            build();
        }
        ));
        d.equal(tabs,d.button("Ηχογραφήσεις",editor,()-> {
            editor=true;
            build();
        }
        ));
        d.add(root,tabs,8);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=d.column();
        content.setPadding(0,0,0,d.dp(20));
        scroll.addView(content);
        bottom=d.column();
        bottom.setPadding(0,d.dp(8),0,d.dp(8));
        root.addView(bottom);
        if(editor)editorScreen();
        else liveScreen();
    }
    private void liveScreen() {
        keyLabel=d.title("",16);
        d.add(content,keyLabel,14);
        updateKey();
        LinearLayout hero=d.card();
        pitch=new PitchView(this,d);
        hero.addView(pitch,new LinearLayout.LayoutParams(-1,d.dp(218)));
        d.add(content,hero,12);
        monitoring=new Switch(this);
        monitoring.setText("Ακρόαση από ακουστικά");
        monitoring.setTextColor(Design.TEXT);
        monitoring.setChecked(monitor);
        monitoring.setMinHeight(d.dp(52));
        monitoring.setOnCheckedChangeListener((v,on)-> {
            if(on&&!wired()) {
                new AlertDialog.Builder(this).setTitle("Σύνδεσε ενσύρματα / USB ακουστικά").setMessage("Το Bluetooth προσθέτει καθυστέρηση που δεν έχει μετρηθεί. Η ζωντανή ακρόαση από ηχείο απενεργοποιείται για να αποφευχθεί μικροφωνισμός. Μπορείς να ηχογραφήσεις χωρίς monitoring.").setPositiveButton("Εντάξει",null).show();
                monitoring.setChecked(false);
                return;
            }
            monitor=on;
            settingsChanged();
        }
        );
        d.add(content,monitoring,8);
        d.add(content,new ControlPanel(this,d,p,this::settingsChanged).build(),0);
        status=d.text(AudioService.message,12,Design.MUTED);
        d.add(bottom,status,0);
        LinearLayout actions=d.row();
        live=d.button("Ακρόαση",false,()->start(false));
        record=d.button("●  Εγγραφή",true,()->start(true));
        d.equal(actions,live);
        d.equal(actions,record);
        d.add(bottom,actions,4);
    }
    private void editorScreen() {
        d.add(content,d.title("Η φωνή σου, στη νότα σου.",22),16);
        d.add(content,d.text("Εισαγωγή → ρυθμίσεις → εφαρμογή → σύγκριση",12,Design.MUTED),8);
        LinearLayout imports=d.row();
        d.equal(imports,d.button("＋ Αρχείο",false,()-> {
            if(project.busy||AudioService.running) {
                toast("Σταμάτησε πρώτα τη ζωντανή λήψη");
                return;
            }
            playback.close();
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE),10);
        }
        ));
        d.equal(imports,d.button("Τελευταία λήψη",false,()-> {
            if(AudioService.running) {
                toast("Σταμάτησε πρώτα τη λήψη");
                return;
            }
            String take=getSharedPreferences("session-v1",0).getString("take","");
            File f=new File(take);
            if(f.isFile()) {
                playback.close();
                position=0;
                after=false;
                project.useTake(f);
            }
            else toast("Δεν υπάρχει αποθηκευμένη λήψη");
        }
        ));
        d.add(content,imports,12);
        waveform=new WaveformView(this,d,f-> {
            position=f;
            playback.seek(f);
            if(seek!=null)seek.setProgress((int)(f*1000));
        }
        );
        waveform.setWaveform(project.waveform);
        d.add(content,waveform,16);
        seek=new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress((int)(position*1000));
        seek.setMinimumHeight(d.dp(48));
        seek.setContentDescription("Θέση αναπαραγωγής");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int value,boolean user) {
                if(user) {
                    position=value/1000f;
                    waveform.setPosition(position);
                    playback.seek(position);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        }
        );
        d.add(content,seek,0);
        LinearLayout compare=d.row();
        d.equal(compare,d.button("Πριν",!after,()->compare(false)));
        afterButton=d.button("Μετά",after,()->compare(true));
        d.equal(compare,afterButton);
        d.add(content,compare,0);
        play=d.button("▶  Αναπαραγωγή",false,()-> {
            if(playback.playing) {
                position=playback.fraction;
                playback.close();
            }
            else {
                File f=after?project.result:project.source;
                if(f!=null) {
                    if(position>.995)position=0;
                    playback.play(f,position);
                }
            }
        }
        );
        d.add(content,play,8);
        editorStatus=d.text(project.status,12,Design.MUTED);
        d.add(content,editorStatus,12);
        d.add(content,d.button("Ακύρωση τρέχουσας εργασίας",false,project::cancel),8);
        d.add(content,new ControlPanel(this,d,p,this::settingsChanged).build(),12);
        LinearLayout actions=d.row();
        render=d.button("Εφαρμογή",true,()-> {
            playback.close();
            project.render(p);
        }
        );
        export=d.button("Εξαγωγή WAV",false,this::export);
        d.equal(actions,render);
        d.equal(actions,export);
        d.add(bottom,actions,0);
    }
    private void compare(boolean processed) {
        if(project.busy||processed&&project.result==null)return;
        boolean was=playback.playing;
        if(was)position=playback.fraction;
        playback.close();
        after=processed;
        build();
        if(was) {
            File f=after?project.result:project.source;
            if(f!=null)playback.play(f,position);
        }
    }
    private void settingsChanged() {
        TuneSettings.save(this,p);
        updateKey();
        if(AudioService.running)startService(new Intent(this,AudioService.class).setAction(AudioService.UPDATE).putExtra("parameters",p).putExtra("monitor",monitor));
    }
    private void updateKey() {
        if(keyLabel!=null) {
            String[] keys= {
                "C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"
            }
            ;
            String[] scales= {
                "Χρωματική","Μείζονα","Ελάσσονα","Αρμονική ελάσσονα","Πεντατονική ελάσσονα"
            }
            ;
            keyLabel.setText(keys[Math.max(0,Math.min(11,(int)p[0]))]+"  /  "+scales[Math.max(0,Math.min(4,(int)p[1]))]);
        }
    }
    private boolean wired() {
        AudioManager m=getSystemService(AudioManager.class);
        for(AudioDeviceInfo v:m.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t=v.getType();
            if(t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||t==AudioDeviceInfo.TYPE_USB_DEVICE||t==AudioDeviceInfo.TYPE_USB_HEADSET)return true;
        }
        return false;
    }
    private void start(boolean rec) {
        if(AudioService.running) {
            startService(new Intent(this,AudioService.class).setAction(AudioService.STOP));
            return;
        }
        if(project.busy) {
            toast("Περίμενε να τελειώσει η επεξεργασία");
            return;
        }
        if(!rec&&!wired()) {
            toast("Για ακρόαση χρειάζονται ενσύρματα / USB ακουστικά");
            return;
        }
        pendingRecord=rec;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                Manifest.permission.RECORD_AUDIO
            }
            ,20);
            return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("notificationAsked",false)) {
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();
            requestPermissions(new String[] {
                Manifest.permission.POST_NOTIFICATIONS
            }
            ,21);
            return;
        }
        playback.close();
        if(!rec) {
            monitor=true;
            if(monitoring!=null)monitoring.setChecked(true);
        }
        try {
            startForegroundService(new Intent(this,AudioService.class).setAction(AudioService.START).putExtra("parameters",p).putExtra("record",rec).putExtra("monitor",monitor));
        }
        catch(RuntimeException e) {
            toast("Δεν ξεκίνησε: "+e.getMessage());
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==21) {
            start(pendingRecord);
        }
        else if(code==20&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)start(pendingRecord);
        else if(code==20)new AlertDialog.Builder(this).setTitle("Χρειάζεται πρόσβαση στο μικρόφωνο").setMessage("Η επεξεργασία αρχείων λειτουργεί και χωρίς αυτή την άδεια.").setPositiveButton("Ρυθμίσεις",(a,b)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Κλείσιμο",null).show();
    }
    private void export() {
        if(project.busy)return;
        File f=after?project.result:project.source;
        if(f==null) {
            toast("Διάλεξε ή επεξεργάσου μια λήψη");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Ποιότητα WAV · "+(after?"Μετά":"Πριν")).setItems(new String[] {
            "24-bit PCM · μουσική παραγωγή","16-bit PCM · συμβατότητα","32-bit float · περαιτέρω μίξη"
        }
        ,(dialog,i)-> {
            exportDepth=i==0?24:i==1?16:32;
            exportProcessed=after;
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/wav").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Vela-"+System.currentTimeMillis()+".wav"),11);
        }
        ).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==10) {
            after=false;
            position=0;
            project.importAudio(data.getData());
        }
        else if(request==11) {
            pendingExport=data.getData();
            project.export(pendingExport,exportDepth,exportProcessed);
        }
    }
    private void diagnostics() {
        float[] m=AudioService.meters;
        String text="Πραγματική end-to-end latency: ΔΕΝ έχει μετρηθεί.\n\n"+(m[0]>0?String.format(Locale.ROOT,"Ρυθμός: %.0f Hz\nΚαθυστέρηση DSP: %.1f ms\nOutput buffer: %.0f frames\nΤρέχον callback / deadline: %.1f%%\nΧειρότερο callback / deadline: %.1f%%\nOutput xruns: %.0f\nΕλλιπείς αναγνώσεις input: %.0f\n",m[0],1000*m[1]/m[0],m[2],100*m[9],100*m[10],m[11],m[12]):"Ξεκίνησε μικρόφωνο για στοιχεία ροής.\n")+"\nΗ μέτρηση DSP δεν περιλαμβάνει μικρόφωνο, Android mixer, ακουστικά ή Bluetooth. Απαιτείται loopback test σε πραγματική συσκευή.\n\nΗ έκδοση αυτή δεν έχει πιστοποιηθεί για επαγγελματική χρήση.\n\nOffline · χωρίς λογαριασμό ή σύνδεση. Oboe: Apache-2.0. DSP: MIT. DejaVu: Bitstream Vera / public domain additions. Αναλυτικές άδειες περιλαμβάνονται στο source project.";
        new AlertDialog.Builder(this).setTitle("Ήχος & μετρήσεις").setMessage(text).setPositiveButton("Κλείσιμο",null).show();
    }
    private void toast(String text) {
        Toast.makeText(this,text,Toast.LENGTH_LONG).show();
    }
    @Override protected void onStart() {
        super.onStart();
        handler.post(refresh);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        playback.close();
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putBoolean("editor",editor);
        b.putFloat("position",position);
        b.putBoolean("after",after);
        b.putInt("exportDepth",exportDepth);
        b.putBoolean("exportProcessed",exportProcessed);
        super.onSaveInstanceState(b);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        playback.close();
        super.onDestroy();
    }
}
