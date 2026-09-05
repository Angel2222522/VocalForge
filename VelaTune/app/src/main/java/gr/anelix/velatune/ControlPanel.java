package gr.anelix.velatune;
import android.app.*;
import android.content.*;
import android.widget.*;
import android.view.View;
import java.util.*;
public final class ControlPanel {
    private final Activity activity;
    private final Design d;
    private final float[] p;
    private final Runnable change;
    public ControlPanel(Activity a,Design design,float[] settings,Runnable onChange) {
        activity=a;
        d=design;
        p=settings;
        change=onChange;
    }
    public LinearLayout build() {
        LinearLayout root=d.column();
        LinearLayout keys=d.row();
        d.equal(keys,spinner("Τονική",new String[] {
            "C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"
        }
        ,0));
        d.equal(keys,spinner("Κλίμακα",new String[] {
            "Χρωματική","Μείζονα","Ελάσσονα","Αρμονική ελάσσονα","Πεντατονική ελάσσονα"
        }
        ,1));
        d.add(root,keys,4);
        d.add(root,d.button("Presets · επιλογή / αποθήκευση",false,this::presets),12);
        LinearLayout card=d.card();
        Switch hard=new Switch(activity);
        hard.setText("Hard Tune");
        hard.setTextColor(Design.TEXT);
        hard.setTypeface(d.bold);
        hard.setMinHeight(d.dp(52));
        hard.setChecked(p[8]>.5);
        hard.setOnCheckedChangeListener((v,on)-> {
            p[8]=on?1:0;
            change.run();
        }
        );
        card.addView(hard);
        d.add(card,d.text("Γρήγορο κλείδωμα νότας για melodic rap και trap.",12,Design.MUTED),0);
        slider(card,"Retune speed",2,0,200," ms");
        slider(card,"Humanize",3,0,1,"%");
        slider(card,"Διόρθωση",4,0,1,"%");
        slider(card,"Wet / Dry",5,0,1,"%");
        d.add(card,d.text("Formants: διατήρηση μέσω pitch-synchronous σύνθεσης. Δεν υπάρχει ανεξάρτητη μετατόπιση formants.",12,Design.MUTED),12);
        d.add(root,card,12);
        LinearLayout gains=d.card();
        slider(gains,"Input gain",6,-24,24," dB");
        slider(gains,"Output gain",7,-24,6," dB");
        Switch bypass=new Switch(activity);
        bypass.setText("Bypass · χωρίς διόρθωση");
        bypass.setTextColor(Design.TEXT);
        bypass.setMinHeight(d.dp(52));
        bypass.setChecked(p[9]>.5);
        bypass.setOnCheckedChangeListener((v,on)-> {
            p[9]=on?1:0;
            change.run();
        }
        );
        gains.addView(bypass);
        d.add(root,gains,12);
        return root;
    }
    private LinearLayout spinner(String label,String[] values,int index) {
        LinearLayout box=d.column();
        box.addView(d.text(label,12,Design.MUTED));
        Spinner s=new Spinner(activity);
        s.setMinimumHeight(d.dp(52));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values);
        s.setAdapter(adapter);
        s.setSelection((int)p[index]);
        s.setContentDescription(label);
        s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id) {
                p[index]=position;
                change.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) {
            }
        }
        );
        box.addView(s);
        return box;
    }
    private void slider(LinearLayout parent,String name,int index,float min,float max,String unit) {
        TextView label=d.text("",13,Design.TEXT);
        SeekBar bar=new SeekBar(activity);
        bar.setMinHeight(d.dp(48));
        bar.setMax(1000);
        bar.setProgress(Math.round((p[index]-min)/(max-min)*1000));
        Runnable update=()->label.setText(name+"  "+Math.round(unit.equals("%")?p[index]*100:p[index])+unit);
        update.run();
        bar.setContentDescription(name);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int v,boolean user) {
                if(user) {
                    p[index]=min+(max-min)*v/1000;
                    update.run();
                    change.run();
                }
            }
            public void onStartTrackingTouch(SeekBar b) {
            }
            public void onStopTrackingTouch(SeekBar b) {
            }
        }
        );
        d.add(parent,label,14);
        parent.addView(bar);
    }
    private void presets() {
        ArrayList<String> names=new ArrayList<>(Arrays.asList("Studio · ισορροπημένο","Trap · hard lock","Natural · απαλή διόρθωση","Αποθήκευση δικού μου…"));
        ArrayList<String> saved=new ArrayList<>(activity.getSharedPreferences("presets-v1",0).getAll().keySet());
        Collections.sort(saved);
        names.addAll(saved);
        new AlertDialog.Builder(activity).setTitle("Presets").setItems(names.toArray(new String[0]),(dialog,index)-> {
            if(index==3) {
                EditText name=new EditText(activity);
                name.setHint("Όνομα preset");
                new AlertDialog.Builder(activity).setTitle("Αποθήκευση preset").setView(name).setPositiveButton("Αποθήκευση",(a,b)-> {
                    String n=name.getText().toString().trim();
                    if(!n.isEmpty())TuneSettings.saveUser(activity,n,p);
                }
                ).setNegativeButton("Άκυρο",null).show();
            }
            else {
                float[] chosen=index<3?TuneSettings.preset(index):TuneSettings.loadUser(activity,names.get(index));
                System.arraycopy(chosen,0,p,0,10);
                change.run();
                activity.recreate();
            }
        }
        ).show();
    }
}
