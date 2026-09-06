package gr.anelix.velatune;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.os.ParcelFileDescriptor;
import java.io.File;

/** Executes real Activity creation and navigation on Android. */
@SuppressWarnings("deprecation")
public final class ActivitySmokeTest extends InstrumentationTestCase {
    private Activity activity;
    private Button find(View v,String text) {
        if(v instanceof Button && ((Button)v).getText().toString().equals(text))return (Button)v;
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++) {
                Button b=find(group.getChildAt(i),text);
                if(b!=null)return b;
            }
        }
        return null;
    }
    public void testLaunchAndEditorNavigation() throws Throwable {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()-> {
            Button editor=find(activity.getWindow().getDecorView(),"Ηχογραφήσεις");
            assertNotNull(editor);
            editor.performClick();
            assertNotNull(find(activity.getWindow().getDecorView(),"＋ Αρχείο"));
            Button mic=find(activity.getWindow().getDecorView(),"Μικρόφωνο");
            assertNotNull(mic);
            mic.performClick();
            assertNotNull(find(activity.getWindow().getDecorView(),"●  Εγγραφή"));
        });
        getInstrumentation().waitForIdleSync();
    }
    public void testForegroundRecordingCreatesWave() throws Throwable {
        String pkg=getInstrumentation().getTargetContext().getPackageName();
        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(
                getInstrumentation().getUiAutomation().executeShellCommand("pm grant "+pkg+" android.permission.RECORD_AUDIO"))) {
            byte[] scratch=new byte[1024];
            while(in.read(scratch)!=-1) { }
        }
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(()->activity.startForegroundService(
            new Intent(activity,AudioService.class).setAction(AudioService.START)
                .putExtra("parameters",TuneSettings.preset(1)).putExtra("record",true).putExtra("monitor",false)));
        try {
            long deadline=android.os.SystemClock.elapsedRealtime()+10000;
            while(!AudioService.running&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            assertTrue(AudioService.message,AudioService.running);
            Thread.sleep(1500);
            assertTrue("Audio callback executed",AudioService.meters[9]>0);
        } finally {
            getInstrumentation().runOnMainSync(()->activity.startService(new Intent(activity,AudioService.class).setAction(AudioService.STOP)));
        }
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        while(AudioService.running&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        assertFalse(AudioService.running);
        File take=new File(AudioService.lastRecording);
        assertTrue(AudioService.message,take.isFile());
        try(WaveFile.Reader reader=new WaveFile.Reader(take)) {
            assertTrue("Recorded frames",reader.frames>0);
        }
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
        super.tearDown();
    }
}
