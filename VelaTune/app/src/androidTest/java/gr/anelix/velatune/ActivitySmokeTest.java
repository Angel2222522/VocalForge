package gr.anelix.velatune;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

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
    @Override protected void tearDown() throws Exception {
        if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
        super.tearDown();
    }
}
