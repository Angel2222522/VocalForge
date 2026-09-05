package gr.anelix.velatune;
import java.io.*;
import java.nio.*;
import java.util.Random;
/** Bounded, streaming RIFF reader/writer. Canonical private audio is mono IEEE float WAV. */
public final class WaveFile {
    public static final long MAX_BYTES=1_800_000_000L;
    public static final class Reader implements AutoCloseable {
        private final RandomAccessFile file;
        public final int rate,channels,bits,format;
        public final long frames,offset;
        private long position=0;
        private final byte[] bytes=new byte[65536];
        public Reader(File path)throws IOException {
            file=new RandomAccessFile(path,"r");
            try {
                if(file.readInt()!=0x52494646) {
                    throw new IOException("Απαιτείται RIFF WAV");
                }
                readU32(file);
                if(file.readInt()!=0x57415645)throw new IOException("Μη έγκυρο WAV");
                int r=0,c=0,b=0,f=0;
                long start=-1,length=0;
                while(file.getFilePointer()+8<=file.length()) {
                    int id=file.readInt();
                    long size=readU32(file),begin=file.getFilePointer();
                    if(size>file.length()-begin)throw new IOException("Ελλιπές WAV");
                    if(id==0x666d7420) {
                        if(size<16)throw new IOException("Ελλιπές fmt");
                        f=readU16(file);
                        c=readU16(file);
                        r=(int)readU32(file);
                        readU32(file);
                        readU16(file);
                        b=readU16(file);
                    }
                    if(id==0x64617461) {
                        start=begin;
                        length=size;
                    }
                    file.seek(begin+size+(size&1));
                }
                if(start<0||r<8000||r>96000||c<1||c>2||!((f==1&&(b==16||b==24||b==32))||(f==3&&b==32)))throw new IOException("WAV: mono/stereo PCM16/24/32 ή float32, 8–96 kHz");
                rate=r;
                channels=c;
                bits=b;
                format=f;
                offset=start;
                frames=length/(c*b/8);
                file.seek(start);
            }
            catch(IOException|RuntimeException e) {
                file.close();
                throw e;
            }
        }
        public void seek(long frame)throws IOException {
            position=Math.max(0,Math.min(frames,frame));
            file.seek(offset+position*channels*(bits/8));
        }
        public int read(float[] out,int maximum)throws IOException {
            int n=(int)Math.min(Math.min(maximum,bytes.length/(channels*(bits/8))),frames-position);
            file.readFully(bytes,0,n*channels*(bits/8));
            ByteBuffer bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<n;i++) {
                double sum=0;
                for(int c=0;c<channels;c++) {
                    float x;
                    if(format==3)x=bb.getFloat();
                    else if(bits==16)x=bb.getShort()/32768f;
                    else if(bits==24) {
                        int v=(bb.get()&255)|((bb.get()&255)<<8)|(bb.get()<<16);
                        x=v/8388608f;
                    }
                    else x=bb.getInt()/2147483648f;
                    sum+=Float.isFinite(x)?x:0;
                }
                out[i]=(float)(sum/channels);
            }
            position+=n;
            return n;
        }
        public void close()throws IOException {
            file.close();
        }
    }
    public static final class Writer implements AutoCloseable {
        private final RandomAccessFile file;
        private final int rate,bits,format;
        private long samples=0;
        private boolean closed=false;
        private final byte[] bytes=new byte[65536];
        private final Random random=new Random(5841);
        public Writer(File path,int sampleRate,int depth)throws IOException {
            rate=sampleRate;
            bits=depth;
            format=depth==32?3:1;
            if((bits!=16&&bits!=24&&bits!=32)||rate<8000||rate>96000)throw new IOException("Μη υποστηριζόμενη ποιότητα");
            file=new RandomAccessFile(path,"rw");
            file.setLength(0);
            header();
        }
        public void write(float[] input,int from,int n)throws IOException {
            if(closed)throw new IOException("Writer closed");
            if((samples+n)*(bits/8)>MAX_BYTES)throw new IOException("Όριο 1,8 GB ανά αρχείο");
            while(n>0) {
                int count=Math.min(n,bytes.length/(bits/8));
                ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                for(int i=0;i<count;i++) {
                    float x=input[from+i];
                    if(!Float.isFinite(x))x=0;
                    if(bits==32)b.putFloat(x);
                    else {
                        double scale=bits==16?32768:8388608;
                        double dither=random.nextDouble()-random.nextDouble();
                        int v=(int)Math.max(-scale,Math.min(scale-1,Math.rint(x*scale+dither)));
                        if(bits==16)b.putShort((short)v);
                        else {
                            b.put((byte)v);
                            b.put((byte)(v>>8));
                            b.put((byte)(v>>16));
                        }
                    }
                }
                file.write(bytes,0,count*(bits/8));
                samples+=count;
                from+=count;
                n-=count;
            }
        }
        public void checkpoint()throws IOException {
            long end=file.getFilePointer();
            header();
            file.seek(end);
            file.getFD().sync();
        }
        private void header()throws IOException {
            file.seek(0);
            file.writeBytes("RIFF");
            write32(file,36+samples*(bits/8));
            file.writeBytes("WAVEfmt ");
            write32(file,16);
            write16(file,format);
            write16(file,1);
            write32(file,rate);
            write32(file,rate*(bits/8));
            write16(file,bits/8);
            write16(file,bits);
            file.writeBytes("data");
            write32(file,samples*(bits/8));
        }
        public void close()throws IOException {
            if(closed)return;
            closed=true;
            try {
                header();
                file.getFD().sync();
            }
            finally {
                file.close();
            }
        }
    }
    public static void recover(File path)throws IOException {
        try(RandomAccessFile f=new RandomAccessFile(path,"rw")) {
            if(f.length()<44)return;
            f.seek(34);
            int bits=readU16(f);
            if(bits!=32)return;
            long data=(f.length()-44)/4*4;
            f.setLength(44+data);
            f.seek(4);
            write32(f,36+data);
            f.seek(40);
            write32(f,data);
            f.getFD().sync();
        }
    }
    private static int readU16(RandomAccessFile f)throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(f.readShort()));
    }
    private static long readU32(RandomAccessFile f)throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
    }
    private static void write16(RandomAccessFile f,int v)throws IOException {
        f.writeShort(Short.reverseBytes((short)v));
    }
    private static void write32(RandomAccessFile f,long v)throws IOException {
        f.writeInt(Integer.reverseBytes((int)v));
    }
}
