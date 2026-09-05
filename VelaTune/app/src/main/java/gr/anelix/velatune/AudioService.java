package gr.anelix.velatune;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
public final class AudioService extends Service {
    public static final String START="start",STOP="stop",UPDATE="update";
    public static volatile boolean running=false,recording=false;
    public static volatile String message="Έτοιμο",lastRecording="";
    public static volatile float[] meters=new float[14];
    private static final ScheduledExecutorService audio=Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tick;
    private volatile int lastStartId;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AudioManager manager;
    private AudioFocusRequest focus;
    private WaveFile.Writer writer;
    private File pending;
    private long checkpoint=0;
    private float[] parameters;
    private boolean monitor;
    private final float[] buffer=new float[8192];
    private final BroadcastReceiver noisy=new BroadcastReceiver() {
        public void onReceive(Context c,Intent i) {
            requestStop("Αποσυνδέθηκαν τα ακουστικά");
        }
    }
    ;
    private final AudioDeviceCallback devices=new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] list) {
            if(running)requestStop("Άλλαξε η συσκευή ήχου · ξεκίνησε ξανά");
        }
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] list) {
            if(running)requestStop("Άλλαξε η συσκευή ήχου · ξεκίνησε ξανά");
        }
    }
    ;
    @Override public void onCreate() {
        super.onCreate();
        manager=getSystemService(AudioManager.class);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("audio","Ηχογράφηση",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        manager.registerAudioDeviceCallback(devices,main);
        tick=audio.scheduleWithFixedDelay(()-> {
            try {
                if(running) {
                    drain();
                    meters=NativeAudio.stats();
                    if(meters[13]>0) {
                        stopSession(meters[13]==2?"Η αποθήκευση δεν προλαβαίνει · η λήψη έχει κενό και διακόπηκε":"Η ροή διακόπηκε · το διαθέσιμο take αποθηκεύτηκε");
                        main.post(this::stopSelf);
                    }
                    if(writer!=null&&SystemClock.elapsedRealtime()-checkpoint>2000) {
                        writer.checkpoint();
                        checkpoint=SystemClock.elapsedRealtime();
                    }
                }
            }
            catch(Exception e) {
                stopSession("Σφάλμα εγγραφής: "+e.getMessage());
                main.post(this::stopSelf);
            }
        }
        ,20,20,TimeUnit.MILLISECONDS);
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AudioService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"audio").setContentTitle("Vela Tune").setContentText("Το μικρόφωνο είναι ενεργό").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Διακοπή",stop).build()).setOngoing(true).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        lastStartId=id;
        if(intent==null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action=intent.getAction();
        if(STOP.equals(action)) {
            requestStop("Η λήψη αποθηκεύτηκε");
            return START_NOT_STICKY;
        }
        if(UPDATE.equals(action)) {
            float[] p=intent.getFloatArrayExtra("parameters");
            boolean m=intent.getBooleanExtra("monitor",false);
            audio.execute(()-> {
                if(p!=null&&p.length==10)parameters=p;
                monitor=m&&hasWiredOutput();
                if(running)NativeAudio.configure(parameters,monitor,recording);
            }
            );
            return START_NOT_STICKY;
        }
        if(START.equals(action)) {
            try {
                if(Build.VERSION.SDK_INT>=30)startForeground(42,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else startForeground(42,notification());
            }
            catch(RuntimeException e) {
                message="Δεν επιτρέπεται έναρξη μικροφώνου: "+e.getMessage();
                stopSelf();
                return START_NOT_STICKY;
            }
            final float[] p=intent.getFloatArrayExtra("parameters");
            final boolean rec=intent.getBooleanExtra("record",true),mon=intent.getBooleanExtra("monitor",false);
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change-> {
                if(change!=AudioManager.AUDIOFOCUS_GAIN)requestStop("Διακοπή λόγω άλλου ήχου / κλήσης");
            }
            ,main).build();
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                message="Το μικρόφωνο/η έξοδος δεν είναι διαθέσιμα";
                stopSelf();
                return START_NOT_STICKY;
            }
            audio.execute(()-> {
                stopSession("");
                parameters=p!=null&&p.length==10?p:TuneSettings.load(this);
                monitor=mon&&hasWiredOutput();
                try {
                    int sr=NativeAudio.start();
                    if(rec) {
                        File root=new File(getFilesDir(),"takes");
                        if(!root.exists()&&!root.mkdirs())throw new IOException("Δεν υπάρχει χώρος αποθήκευσης");
                        pending=new File(root,"take-"+System.currentTimeMillis()+".part");
                        writer=new WaveFile.Writer(pending,sr,32);
                    }
                    recording=rec;
                    NativeAudio.configure(parameters,monitor,rec);
                    running=true;
                    message=rec?"Γράφει…":"Ζωντανή ακρόαση";
                }
                catch(Exception e) {
                    stopSession("Αποτυχία έναρξης: "+e.getMessage());
                    main.post(()->stopSelfResult(id));
                }
            }
            );
        }
        return START_NOT_STICKY;
    }
    private boolean hasWiredOutput() {
        for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t=d.getType();
            if(t==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_USB_HEADSET||t==AudioDeviceInfo.TYPE_USB_DEVICE)return true;
        }
        return false;
    }
    private void drain()throws IOException {
        int n;
        while((n=NativeAudio.drain(buffer))>0)if(writer!=null)writer.write(buffer,0,n);
    }
    private void requestStop(String reason) {
        int commandId=lastStartId;
        audio.execute(()-> {
            stopSession(reason);
            main.post(()->stopSelfResult(commandId));
        }
        );
    }
    private void stopSession(String reason) {
        try {
            NativeAudio.stop();
            drain();
        }
        catch(Exception e) {
            reason="Η εγγραφή διακόπηκε: "+e.getMessage();
        }
        if(writer!=null) {
            try {
                writer.close();
                File done=new File(pending.getParentFile(),pending.getName().replace(".part",".wav"));
                if(!pending.renameTo(done))throw new IOException("rename failed");
                lastRecording=done.getAbsolutePath();
                getSharedPreferences("session-v1",0).edit().putString("take",lastRecording).apply();
            }
            catch(IOException e) {
                reason="Ανάκτηση απαιτείται: "+e.getMessage();
            }
            writer=null;
            pending=null;
        }
        NativeAudio.release();
        running=false;
        recording=false;
        if(!reason.isEmpty())message=reason;
    }
    @Override public void onDestroy() {
        manager.unregisterAudioDeviceCallback(devices);
        unregisterReceiver(noisy);
        if(focus!=null)manager.abandonAudioFocusRequest(focus);
        if(tick!=null)tick.cancel(false);
        audio.execute(()->stopSession(""));
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
