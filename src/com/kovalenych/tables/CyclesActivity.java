package com.kovalenych.tables;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.*;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.kovalenych.Const;
import com.kovalenych.R;
import com.kovalenych.Table;
import com.kovalenych.Utils;

import java.io.*;
import java.util.ArrayList;

public class CyclesActivity extends Activity implements Soundable, Const {

    ListView lv;

    Table curTable;

    private static final String LOG_TAG = "CO2 CyclesActivity";
    String name;
    Button add_button, ok_button, melody;
    Dialog newDialog;
    Activity ptr;
    EditText holdEdit, breathEdit, timesEdit;
    Dialog voiceDialog;
    int chosenTable;
    Dialog delDialog;
    private Button del_button;
    private SharedPreferences _preferedSettings;
    boolean isvibro;
    private Button stopButton;
    public ArrayList<MultiCycle> multiCycles;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ptr = this;
        Bundle bun = getIntent().getExtras();
        name = bun.getString("name");
        multiCycles = new ArrayList<MultiCycle>();
        Log.d(LOG_TAG, "onCreate");

        unPackTable();
        setContentView(R.layout.cycles);

        initViews();

        _preferedSettings = getSharedPreferences("sharedSettings", MODE_PRIVATE);

        isvibro = _preferedSettings.getBoolean("vibro", true);

        stopButton = (Button) findViewById(R.id.stop_button_cycles);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(ptr, ClockService.class));
                stopButton.setVisibility(View.GONE);
                curCycle = -1;
                invalidateList();
            }
        });
        if (Utils.isMyServiceRunning(this)) {
            stopButton.setVisibility(View.VISIBLE);
            subscribeToService();
            Log.d(LOG_TAG, "onResume VISIBLE");
        } else {
            curCycle = -1;
            stopButton.setVisibility(View.GONE);
            Log.d(LOG_TAG, "onResume GONE");
        }
    }

    public void initViews() {

        lv = (ListView) findViewById(R.id.cycles_list);

        TextView id = (TextView) findViewById(R.id.chosen_table_name);
        id.setText(name);

        delDialog = new Dialog(ptr);
        delDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        delDialog.setCancelable(true);
        delDialog.setContentView(R.layout.delete_dialog);

        del_button = (Button) delDialog.findViewById(R.id.delete_button);


        newDialog = new Dialog(ptr);
        newDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        newDialog.setCancelable(true);
        newDialog.setContentView(R.layout.new_cycle_dialog);

        timesEdit = (EditText) newDialog.findViewById(R.id.repeat_edit);
        holdEdit = (EditText) newDialog.findViewById(R.id.hold_edit);
        breathEdit = (EditText) newDialog.findViewById(R.id.breath_edit);
        ok_button = (Button) newDialog.findViewById(R.id.new_cycle_ok);


        invalidateList();

        add_button = (Button) findViewById(R.id.add_cycle);
        melody = (Button) findViewById(R.id.melody);


        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume ");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (Utils.isMyServiceRunning(this)) {
            stopButton.setVisibility(View.VISIBLE);
            subscribeToService();
            Log.d(LOG_TAG, "onResume VISIBLE");
            Toast.makeText(CyclesActivity.this, "timer is still running", Toast.LENGTH_SHORT).show();
        } else {
            curCycle = -1;
            invalidateList();
            stopButton.setVisibility(View.GONE);
            Log.d(LOG_TAG, "onResume GONE");
        }
    }

    private void subscribeToService() {
        PendingIntent pi;
        Intent intent;

        // Создаем PendingIntent для Task1
        pi = createPendingResult(1, null, 0);
        // Создаем Intent для вызова сервиса, кладем туда параметр времени
        // и созданный PendingIntent
        intent = new Intent(this, ClockService.class)
                .putExtra(FLAG, FLAG_SUBSCRIBE_CYCLES)
                .putExtra(PARAM_PINTENT, pi);
        // стартуем сервис
        startService(intent);
    }

    private void invalidateList() {

        multiCycles.clear();
        int sameCounter = 1;
        ArrayList<Cycle> cycles = curTable.getCycles();
        for (int i = 0, size = cycles.size(); i < size; i++) {
            if (i + 1 < size) {
                if (cycleEqualsToNext(cycles, i))
                    sameCounter++;
                else {
                    ArrayList<Cycle> sameCycles = new ArrayList<Cycle>();
                    for (int j = 0; j < sameCounter; j++) {
                        sameCycles.add(new Cycle(cycles.get(i).breathe, cycles.get(i).hold));
                    }
                    multiCycles.add(new MultiCycle(sameCycles));
                    sameCounter = 1;
                }
            } else {
                ArrayList<Cycle> sameCycles = new ArrayList<Cycle>();
                for (int j = 0; j < sameCounter; j++) {
                    sameCycles.add(new Cycle(cycles.get(i).breathe, cycles.get(i).hold));
                }
                multiCycles.add(new MultiCycle(sameCycles));
                sameCounter = 1;
            }
        }

        CyclesArrayAdapter adapter = new CyclesArrayAdapter(this, curTable.getCycles());
        lv.setAdapter(adapter);
        lv.setVisibility(View.VISIBLE);
    }

    private boolean cycleEqualsToNext(ArrayList<Cycle> cycles, int i) {
        return cycles.get(i + 1).breathe == cycles.get(i).breathe &&
                cycles.get(i + 1).hold == cycles.get(i).hold;
    }

    @Override
    protected void onPause() {
        try {
            ContextWrapper cw = new ContextWrapper(this);
            File tablesDir = cw.getDir("tables", Context.MODE_PRIVATE);

            File f = new File(tablesDir, name);
            FileOutputStream fos = new FileOutputStream(f);
            ObjectOutputStream obj_out = new ObjectOutputStream(fos);
            obj_out.writeObject(curTable);
            obj_out.close();
            fos.close();

        } catch (IOException ex) {
            Log.d(LOG_TAG, "IOException");

        }

        super.onPause();
    }

    void unPackTable() {

        try {
            ContextWrapper cw = new ContextWrapper(this);
            File tablesDir = cw.getDir("tables", Context.MODE_PRIVATE);
            String[] sl = tablesDir.list();
            int chosenNum = -1;
            for (int numInList = 0; numInList < sl.length; numInList++)
                if (sl[numInList].equals(name))
                    chosenNum = numInList;
            File[] fl = tablesDir.listFiles();
            if (chosenNum == -1) throw new FileNotFoundException();
            FileInputStream fis = new FileInputStream(fl[chosenNum]);
            ObjectInputStream obj_in = new ObjectInputStream(fis);
            Object obj = obj_in.readObject();
            if (obj instanceof Table)
                curTable = (Table) obj;
            obj_in.close();
            fis.close();

        } catch (FileNotFoundException ex) {
            if (name.equals("O2 Table"))
                curTable = new Table(fill_O2());
            else if (name.equals("CO2 Table"))
                curTable = new Table(fill_CO2());
            else {
                curTable = new Table();
                Log.d(LOG_TAG, "FileNotFoundException ");
            }
        } catch (IOException
                ex) {
            Log.d(LOG_TAG, "Error parsing file");
        } catch (ClassNotFoundException
                ex) {
            Log.d(LOG_TAG, "Error class not found");
        }

    }

    public void setListeners() {

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

//                stopService(new Intent(ptr, ClockService.class));

                Intent intent = new Intent(lv.getContext(), ClockActivity.class);
                Bundle bun = new Bundle();


                bun.putInt("tablesize", curTable.getCycles().size());

                for (int i = 0; i < curTable.getCycles().size(); i++) {
                    bun.putInt("breathe" + Integer.toString(i), curTable.getCycles().get(i).breathe);
                    bun.putInt("hold" + Integer.toString(i), curTable.getCycles().get(i).hold);
                }

                bun.putIntegerArrayList("voices", curTable.getVoices());
                bun.putInt("number", position);
                bun.putBoolean("vibro", isvibro);
                bun.putString("table_name", name);
                bun.putBoolean("isRunning", curCycle == position);
                intent.putExtras(bun);
                startActivity(intent);

            }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d("onLongClick", "zzz");
                chosenTable = i;
                delDialog.show();
                return false;
            }
        });

        del_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                curTable.getCycles().remove(chosenTable);
                delDialog.dismiss();
                invalidateList();
            }
        });


        add_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                newDialog.show();

            }
        });

        melody.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                voiceDialog = new Dialog(ptr);
                voiceDialog.setCancelable(true);
                voiceDialog.setTitle(getResources().getString(R.string.voices));
                LayoutInflater inf = getLayoutInflater();
                ScrollView view1 = (ScrollView) inf.inflate(R.layout.voice, null);
                voiceDialog.setContentView(view1, new RelativeLayout.LayoutParams(Utils.smallerDim - 30, ViewGroup.LayoutParams.FILL_PARENT));
//                voiceDialog.setContentView(R.layout.voice);
                setVoiceRadios();
                voiceDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        getVoiceRadios();
                    }
                });
                voiceDialog.show();
            }
        });

        ok_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                try {
                    int b = Integer.parseInt(breathEdit.getText().toString());
                    int h = Integer.parseInt(holdEdit.getText().toString());
                    int times = Integer.parseInt(timesEdit.getText().toString());

                    if (b < 3600 && h < 3600) {
                        for (int i = 0; i < times; i++) {
                            curTable.getCycles().add(new Cycle(b, h));
                        }
                        invalidateList();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(CyclesActivity.this, "Wrong format", Toast.LENGTH_LONG).show();
                } finally {
                    newDialog.dismiss();
                }

            }
        });

    }

    private void setVoiceRadios() {

        if (curTable.getVoices().contains(TO_START_2_MIN))
            ((CheckBox) voiceDialog.findViewById(R.id.voice2to)).setChecked(true);
        if (curTable.getVoices().contains(TO_START_1_MIN))
            ((CheckBox) voiceDialog.findViewById(R.id.voice1to)).setChecked(true);
        if (curTable.getVoices().contains(TO_START_30_SEC))
            ((CheckBox) voiceDialog.findViewById(R.id.voice30to)).setChecked(true);
        if (curTable.getVoices().contains(TO_START_10_SEC))
            ((CheckBox) voiceDialog.findViewById(R.id.voice10to)).setChecked(true);
        if (curTable.getVoices().contains(TO_START_5_SEC))
            ((CheckBox) voiceDialog.findViewById(R.id.voice5to)).setChecked(true);
        if (curTable.getVoices().contains(START))
            ((CheckBox) voiceDialog.findViewById(R.id.voicestart)).setChecked(true);
        if (curTable.getVoices().contains(AFTER_START_1))
            ((CheckBox) voiceDialog.findViewById(R.id.voice1after)).setChecked(true);
        if (curTable.getVoices().contains(AFTER_START_2))
            ((CheckBox) voiceDialog.findViewById(R.id.voice2after)).setChecked(true);
        if (curTable.getVoices().contains(AFTER_START_3))
            ((CheckBox) voiceDialog.findViewById(R.id.voice3after)).setChecked(true);
        if (curTable.getVoices().contains(AFTER_START_4))
            ((CheckBox) voiceDialog.findViewById(R.id.voice4after)).setChecked(true);
        if (curTable.getVoices().contains(AFTER_START_5))
            ((CheckBox) voiceDialog.findViewById(R.id.voice5after)).setChecked(true);
        if (curTable.getVoices().contains(BREATHE))
            ((CheckBox) voiceDialog.findViewById(R.id.voicebreathe)).setChecked(true);
        if (isvibro)
            ((CheckBox) voiceDialog.findViewById(R.id.vibro)).setChecked(true);
    }

    private void getVoiceRadios() {
        curTable.getVoices().clear();
        if (((CheckBox) voiceDialog.findViewById(R.id.voice2to)).isChecked())
            curTable.getVoices().add(TO_START_2_MIN);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice1to)).isChecked())
            curTable.getVoices().add(TO_START_1_MIN);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice30to)).isChecked())
            curTable.getVoices().add(TO_START_30_SEC);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice10to)).isChecked())
            curTable.getVoices().add(TO_START_10_SEC);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice5to)).isChecked())
            curTable.getVoices().add(TO_START_5_SEC);
        if (((CheckBox) voiceDialog.findViewById(R.id.voicestart)).isChecked())
            curTable.getVoices().add(START);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice1after)).isChecked())
            curTable.getVoices().add(AFTER_START_1);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice2after)).isChecked())
            curTable.getVoices().add(AFTER_START_2);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice3after)).isChecked())
            curTable.getVoices().add(AFTER_START_3);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice4after)).isChecked())
            curTable.getVoices().add(AFTER_START_4);
        if (((CheckBox) voiceDialog.findViewById(R.id.voice5after)).isChecked())
            curTable.getVoices().add(AFTER_START_5);
        if (((CheckBox) voiceDialog.findViewById(R.id.voicebreathe)).isChecked())
            curTable.getVoices().add(BREATHE);

        SharedPreferences.Editor editor = _preferedSettings.edit();
        isvibro = ((CheckBox) voiceDialog.findViewById(R.id.vibro)).isChecked();
        editor.putBoolean("vibro", ((CheckBox) voiceDialog.findViewById(R.id.vibro)).isChecked());
        editor.commit();
    }

    public ArrayList<Cycle> fill_O2() {
        ArrayList<Cycle> O2 = new ArrayList<Cycle>();
        O2.add(new Cycle(60, 120));
        O2.add(new Cycle(90, 120));
        O2.add(new Cycle(120, 180));
        O2.add(new Cycle(180, 240));
        O2.add(new Cycle(240, 300));
        O2.add(new Cycle(300, 360));
        return O2;

    }

    public ArrayList<Cycle> fill_CO2() {
        ArrayList<Cycle> CO2 = new ArrayList<Cycle>();
        CO2.add(new Cycle(60, 120));
        CO2.add(new Cycle(150, 120));
        CO2.add(new Cycle(135, 120));
        CO2.add(new Cycle(120, 120));
        CO2.add(new Cycle(105, 120));
        CO2.add(new Cycle(90, 120));
        CO2.add(new Cycle(75, 120));
        CO2.add(new Cycle(60, 120));
        CO2.add(new Cycle(60, 120));
        return CO2;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "onActivityResult" + resultCode);
        if (name.equals(data.getStringExtra(PARAM_TABLE)))
            curCycle = data.getIntExtra(PARAM_CYCLE_NUM, 0);
        else
            curCycle = -1;
        invalidateList();
    }

    public static int curCycle = -1;

}

