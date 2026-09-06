package gr.anelix.velatune;
import android.test.AndroidTestCase;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
/** Requires Android runtime; compilation is not execution evidence. */
@SuppressWarnings("deprecation") public final class NativeIntegrationTest extends AndroidTestCase {
    public void testNativeOfflineLengthAndSourcePreservation()throws Exception {
        File directory=getContext().getCacheDir();
        File source=new File(directory,"test-original.wav");
        float[] a=new float[48000];
        for(int i=0;i<a.length;i++)a[i]=(float)(.2*Math.sin(2*Math.PI*225*i/48000));
        try(WaveFile.Writer w=new WaveFile.Writer(source,48000,32)) {
            w.write(a,0,a.length);
        }
        long before=source.length();
        float[] p=TuneSettings.preset(1);
        File rendered=AudioFiles.render(source,directory,p,new AtomicBoolean(),s-> {
        }
        );
        try(WaveFile.Reader r=new WaveFile.Reader(rendered)) {
            assertEquals(48000,r.frames);
            assertEquals(48000,r.rate);
            float[] b=new float[48000];
            assertEquals(16384,r.read(b,b.length));
        }
        assertEquals(before,source.length());
        assertTrue(source.exists());
        rendered.delete();
        source.delete();
    }
    public void testNoNetworkPermission()throws Exception {
        String[] permissions=getContext().getPackageManager().getPackageInfo(getContext().getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        for(String p:permissions)assertFalse(p.equals("android.permission.INTERNET"));
    }
    public void testBadRateRejected() {
        boolean threw=false;
        try {
            long h=NativeAudio.createProcessor(0,TuneSettings.preset(0));
            NativeAudio.destroyProcessor(h);
        }
        catch(IllegalStateException e) {
            threw=true;
        }
        assertTrue(threw);
    }
}
