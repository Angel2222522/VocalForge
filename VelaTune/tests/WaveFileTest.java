import gr.anelix.velatune.WaveFile;
import java.io.*;
import java.nio.file.*;
public class WaveFileTest {
    static int count=0;
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vela-wave-test");
        for(int depth:new int[]{16,24,32})for(int rate:new int[]{8000,44100,48000,96000}){
            File file=root.resolve("roundtrip-"+depth+"-"+rate+".wav").toFile();float[] data=new float[10001];for(int i=0;i<data.length;i++)data[i]=(float)(.8*Math.sin(i*.04));
            try(WaveFile.Writer w=new WaveFile.Writer(file,rate,depth)){w.write(data,0,17);w.write(data,17,data.length-17);w.checkpoint();}
            try(WaveFile.Reader r=new WaveFile.Reader(file)){check(r.rate==rate&&r.bits==depth&&r.frames==data.length,"header "+rate+"/"+depth);float[] read=new float[data.length];int n=r.read(read,read.length);check(n==data.length,"length");float max=0;for(int i=0;i<n;i++)max=Math.max(max,Math.abs(data[i]-read[i]));check(max<(depth==16?.00007:depth==24?.000001:.00000001),"quantization tolerance");r.seek(9000);check(r.read(read,20)==20&&Math.abs(read[0]-data[9000])<.00007,"seek");}
        }
        File recovery=root.resolve("recovery.part").toFile();try(WaveFile.Writer w=new WaveFile.Writer(recovery,48000,32)){w.write(new float[1000],0,1000);}
        try(RandomAccessFile f=new RandomAccessFile(recovery,"rw")){f.seek(40);f.writeInt(0);f.seek(f.length());f.writeByte(5);}
        WaveFile.recover(recovery);try(WaveFile.Reader r=new WaveFile.Reader(recovery)){check(r.frames==1000,"interrupted recording recovery");}
        File bad=root.resolve("bad.wav").toFile();Files.write(bad.toPath(),new byte[22]);boolean failed=false;try(WaveFile.Reader ignored=new WaveFile.Reader(bad)){}catch(IOException e){failed=true;}check(failed,"malformed rejected");
        for(File f:root.toFile().listFiles())f.delete();root.toFile().delete();System.out.println("PASS_COUNT="+count);
    }
}
