package gr.anelix.velatune;
import android.media.*;
import java.io.*;
import java.util.concurrent.atomic.*;
/** Preview PCM via platform mixer; never used as the live microphone path. */
public final class Playback implements AutoCloseable {
    private final AtomicBoolean stopping=new AtomicBoolean();
    private final AtomicLong seek=new AtomicLong(-1);
    private Thread thread;
    private volatile AudioTrack track;
    public volatile float fraction=0;
    public volatile String error="";
    public volatile boolean playing=false;
    public synchronized void play(File file,float start) {
        close();
        if(thread!=null&&thread.isAlive()) {
            error="Η προηγούμενη αναπαραγωγή δεν έχει τερματιστεί";
            return;
        }
        stopping.set(false);
        fraction=start;
        playing=true;
        thread=new Thread(()-> {
            AudioTrack audio=null;
            try(WaveFile.Reader in=new WaveFile.Reader(file)) {
                in.seek((long)(start*in.frames));
                long sent=(long)(start*in.frames);
                int minimum=AudioTrack.getMinBufferSize(in.rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_FLOAT);
                if(minimum<=0)throw new IOException("Μη υποστηριζόμενη έξοδος");
                audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()) .setAudioFormat(new AudioFormat.Builder().setSampleRate(in.rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()) .setBufferSizeInBytes(Math.max(minimum,8192)).setTransferMode(AudioTrack.MODE_STREAM).build();
                track=audio;
                audio.play();
                float[] block=new float[1024];
                while(!stopping.get()) {
                    long requested=seek.getAndSet(-1);
                    if(requested>=0) {
                        audio.pause();
                        audio.flush();
                        sent=requested*in.frames/1_000_000;
                        in.seek(sent);
                        audio.play();
                    }
                    int n=in.read(block,block.length);
                    if(n==0)break;
                    int offset=0;
                    while(offset<n&&!stopping.get()) {
                        int wrote=audio.write(block,offset,n-offset,AudioTrack.WRITE_BLOCKING);
                        if(wrote<0)throw new IOException("Αποσυνδέθηκε η έξοδος ήχου");
                        offset+=wrote;
                    }
                    sent+=offset;
                    fraction=sent/(float)Math.max(1,in.frames);
                }
                // Wait for queued tail, bounded by the buffer duration.
                if(!stopping.get())Thread.sleep(Math.max(30,Math.max(minimum,8192)*1000L/(4*in.rate)));
            }
            catch(Exception e) {
                if(!stopping.get())error=e.getMessage()==null?e.toString():e.getMessage();
            }
            finally {
                if(audio!=null) {
                    try {
                        audio.stop();
                    }
                    catch(RuntimeException ignored) {
                    }
                    audio.release();
                }
                track=null;
                playing=false;
            }
        }
        ,"Vela preview");
        thread.start();
    }
    public void seek(float fraction) {
        seek.set((long)(Math.max(0,Math.min(1,fraction))*1_000_000));
        this.fraction=fraction;
    }
    @Override public synchronized void close() {
        stopping.set(true);
        AudioTrack t=track;
        if(t!=null)try {
            t.stop();
        }
        catch(RuntimeException ignored) {
        }
        if(thread!=null) {
            thread.interrupt();
            try {
                thread.join(1000);
            }
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if(!thread.isAlive())thread=null;
        }
        playing=false;
        seek.set(-1);
    }
}
