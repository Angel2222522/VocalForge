package gr.anelix.velatune;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
public final class AudioFiles {
    public interface Progress {
        void update(String message);
    }
    public static void cancelled(AtomicBoolean cancel) {
        if(cancel.get()||Thread.currentThread().isInterrupted())throw new CancellationException();
    }
    public static File importAudio(Context context,Uri uri,File directory,AtomicBoolean cancel,Progress progress)throws Exception {
        File stage=new File(directory,"import-"+System.nanoTime()+".part");
        File complete=new File(directory,stage.getName().replace(".part",".wav"));
        // WAV gets a direct PCM path; Android has no guaranteed audio/raw MediaCodec.
        File copied=new File(directory,"selected-"+System.nanoTime()+".tmp");
        try(java.io.InputStream selected=context.getContentResolver().openInputStream(uri)) {
            if(selected==null)throw new IOException("Το αρχείο δεν είναι διαθέσιμο");
            byte[] head=new byte[12];
            int used=0,n;
            while(used<12&&(n=selected.read(head,used,12-used))>0)used+=n;
            boolean wav=used==12&&head[0]=='R'&&head[1]=='I'&&head[2]=='F'&&head[3]=='F'&&head[8]=='W'&&head[9]=='A'&&head[10]=='V'&&head[11]=='E';
            if(wav) {
                try(java.io.OutputStream copy=new FileOutputStream(copied)) {
                    copy.write(head);
                    byte[] bytes=new byte[65536];
                    long total=12;
                    while((n=selected.read(bytes))>=0) {
                        cancelled(cancel);
                        total+=n;
                        if(total>WaveFile.MAX_BYTES)throw new IOException("Όριο εισαγωγής 1,8 GB");
                        copy.write(bytes,0,n);
                    }
                }
                try(WaveFile.Reader in=new WaveFile.Reader(copied);WaveFile.Writer out=new WaveFile.Writer(stage,in.rate,32)) {
                    float[] block=new float[4096];
                    while((n=in.read(block,block.length))>0) {
                        cancelled(cancel);
                        out.write(block,0,n);
                    }
                }
                if(!stage.renameTo(complete))throw new IOException("Αποτυχία αποθήκευσης WAV");
                return complete;
            }
        }
        finally {
            copied.delete();
            if(stage.exists())stage.delete();
        }
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec decoder=null;
        WaveFile.Writer writer=null;
        try {
            extractor.setDataSource(context,uri,null);
            int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++)if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                track=i;
                break;
            }
            if(track<0)throw new IOException("Δεν βρέθηκε υποστηριζόμενος ήχος");
            extractor.selectTrack(track);
            MediaFormat format=extractor.getTrackFormat(track);
            String mime=format.getString(MediaFormat.KEY_MIME);
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_FLOAT);
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(format,null,null,0);
            decoder.start();
            boolean inputEnd=false,outputEnd=false;
            int rate=0,channels=0,encoding=AudioFormat.ENCODING_PCM_16BIT;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            float[] mono=new float[16384];
            long frames=0,lastProgress=0,lastActivity=System.nanoTime();
            while(!outputEnd) {
                cancelled(cancel);
                if(!inputEnd) {
                    int index=decoder.dequeueInputBuffer(10000);
                    if(index>=0) {
                        ByteBuffer buffer=decoder.getInputBuffer(index);
                        if(buffer==null)throw new IOException("Decoder input unavailable");
                        int size=extractor.readSampleData(buffer,0);
                        if(size<0) {
                            decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnd=true;
                        }
                        else {
                            decoder.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);
                            extractor.advance();
                        }
                        lastActivity=System.nanoTime();
                    }
                }
                int index=decoder.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out=decoder.getOutputFormat();
                    int newRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE),newChannels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(newRate<8000||newRate>96000||newChannels<1||newChannels>2)throw new IOException("Υποστηρίζεται mono/stereo 8–96 kHz");
                    if(writer!=null&&(newRate!=rate||newChannels!=channels))throw new IOException("Αλλαγή format μέσα στο αρχείο");
                    rate=newRate;
                    channels=newChannels;
                    encoding=out.containsKey(MediaFormat.KEY_PCM_ENCODING)?out.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    if(encoding!=AudioFormat.ENCODING_PCM_FLOAT&&encoding!=AudioFormat.ENCODING_PCM_16BIT)throw new IOException("Ο decoder δεν παρέχει PCM16 ή float32");
                    if(writer==null)writer=new WaveFile.Writer(stage,rate,32);
                    lastActivity=System.nanoTime();
                }
                else if(index>=0) {
                    try {
                        ByteBuffer buffer=decoder.getOutputBuffer(index);
                        if(info.size>0) {
                            if(writer==null||buffer==null)throw new IOException("Απουσιάζει το format ήχου");
                            buffer.position(info.offset);
                            buffer.limit(info.offset+info.size);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                            int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                            while(buffer.remaining()>=bytes*channels) {
                                int n=Math.min(mono.length,buffer.remaining()/(bytes*channels));
                                for(int i=0;i<n;i++) {
                                    double sum=0;
                                    for(int c=0;c<channels;c++)sum+=bytes==4?buffer.getFloat():buffer.getShort()/32768f;
                                    mono[i]=(float)(sum/channels);
                                }
                                writer.write(mono,0,n);
                                frames+=n;
                            }
                            if(frames-lastProgress>rate) {
                                progress.update("Εισαγωγή · "+frames/rate+" s");
                                lastProgress=frames;
                            }
                        }
                        outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    }
                    finally {
                        decoder.releaseOutputBuffer(index,false);
                    }
                    lastActivity=System.nanoTime();
                }
                if(System.nanoTime()-lastActivity>15_000_000_000L)throw new IOException("Ο αποκωδικοποιητής δεν ανταποκρίνεται");
            }
            if(writer==null||frames==0)throw new IOException("Κενό αρχείο ήχου");
            writer.close();
            writer=null;
            if(!stage.renameTo(complete))throw new IOException("Αποτυχία αποθήκευσης");
            return complete;
        }
        finally {
            if(writer!=null)try {
                writer.close();
            }
            catch(IOException ignored) {
            }
            if(decoder!=null) {
                try {
                    decoder.stop();
                }
                catch(RuntimeException ignored) {
                }
                decoder.release();
            }
            extractor.release();
            if(stage.exists())stage.delete();
        }
    }
    public static File render(File source,File directory,float[] parameters,AtomicBoolean cancel,Progress progress)throws Exception {
        File stage=new File(directory,"render-"+System.nanoTime()+".part"),complete=new File(directory,stage.getName().replace(".part",".wav"));
        long engine=0;
        try(WaveFile.Reader in=new WaveFile.Reader(source);WaveFile.Writer out=new WaveFile.Writer(stage,in.rate,32)) {
            engine=NativeAudio.createProcessor(in.rate,parameters);
            if(engine==0)throw new IOException("DSP unavailable");
            int delay=NativeAudio.processorDelay(engine);
            long processed=0,written=0,lastProgress=0;
            float[] block=new float[2048];
            while(written<in.frames) {
                cancelled(cancel);
                int n=in.read(block,block.length);
                if(n==0) {
                    n=block.length;
                    Arrays.fill(block,0);
                }
                NativeAudio.process(engine,block,n);
                int skip=(int)Math.min(n,Math.max(0,delay-processed));
                int available=(int)Math.min(n-skip,in.frames-written);
                if(available>0) {
                    out.write(block,skip,available);
                    written+=available;
                }
                processed+=n;
                if(written-lastProgress>in.rate) {
                    progress.update("Επεξεργασία · "+(100*written/Math.max(1,in.frames))+"%");
                    lastProgress=written;
                }
            }
        }
        catch(Exception e) {
            stage.delete();
            throw e;
        }
        finally {
            if(engine!=0)NativeAudio.destroyProcessor(engine);
        }
        if(!stage.renameTo(complete)) {
            stage.delete();
            throw new IOException("Αποτυχία αποθήκευσης αποτελέσματος");
        }
        return complete;
    }
    public static float[] waveform(File file,AtomicBoolean cancel)throws IOException {
        float[] peaks=new float[512],block=new float[4096];
        long position=0;
        try(WaveFile.Reader in=new WaveFile.Reader(file)) {
            int n;
            while((n=in.read(block,block.length))>0) {
                cancelled(cancel);
                for(int i=0;i<n;i++) {
                    int bin=(int)Math.min(peaks.length-1,position++*peaks.length/Math.max(1,in.frames));
                    peaks[bin]=Math.max(peaks[bin],Math.abs(block[i]));
                }
            }
        }
        return peaks;
    }
    public static void export(Context context,File source,Uri target,int depth,AtomicBoolean cancel)throws Exception {
        File temp=File.createTempFile("export-",".wav",context.getCacheDir());
        try {
            try(WaveFile.Reader in=new WaveFile.Reader(source);WaveFile.Writer out=new WaveFile.Writer(temp,in.rate,depth)) {
                float[] block=new float[4096];
                int n;
                while((n=in.read(block,block.length))>0) {
                    cancelled(cancel);
                    out.write(block,0,n);
                }
            }
            try(InputStream in=new FileInputStream(temp);OutputStream out=context.getContentResolver().openOutputStream(target,"wt")) {
                if(out==null)throw new IOException("Αποτυχία ανοίγματος προορισμού");
                byte[] b=new byte[65536];
                int n;
                while((n=in.read(b))>=0) {
                    cancelled(cancel);
                    out.write(b,0,n);
                }
                out.flush();
            }
        }
        finally {
            temp.delete();
        }
    }
}
