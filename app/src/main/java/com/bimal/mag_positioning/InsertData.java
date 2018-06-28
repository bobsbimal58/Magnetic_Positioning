package com.bimal.mag_positioning;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PointF;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by wmcs on 7/21/2017.
 */

public class InsertData extends AppCompatActivity implements SensorEventListener,View.OnClickListener {
    Sensor magnetometer;
    SensorManager sm;
    TextView magnetismx;
    TextView magnetismy;
    TextView magnetismz;
    TextView magnetismd;
    public Float xaxis;
    public Float yaxis;
    public Float zaxis;
    public Float average;
    public Float total;
    public Integer id;
    boolean recording = false;
    boolean stoprecord = false;

    public boolean isStart = false;
    public FileWriter file_writer;
    public boolean success = true;
    public boolean isInsert = false;
    public boolean calcdiff = false;
    public boolean islocalize = false;
    PointF get;
    public boolean isErrror = false;
    public double errorvalue;

    //Sensor events
    private float [] RotationMatrix = new float[16];
    private float [] mOrientationAngles = new float[3];
    private SensorEvent MagnetData;;
    private SensorEvent RotVectorData;
    private SensorEvent AccelVector;

    //Average filter
    private static int k;
    private float[] prevAvg;
    private static float alpha;
    private float[] avg;

    //For coordinates
    public static final int COL = 5;
    public static final int LOW = 10;
    public boolean ismeasure=false;

    //Array
    long curTimeAcc,lastTimeAcc;
    public ArrayList<Float>sensorData= new ArrayList<>();
    public ArrayList<Double> xcordList= new ArrayList<>();
    public ArrayList<Double>ycordList =  new ArrayList<>();
    float sum_x=0;
    float sum_y=0;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.insert_data);


        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);

       sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
       /* sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

*/
        magnetismx = (TextView) findViewById(R.id.magnetismx);
        magnetismy = (TextView) findViewById(R.id.magnetismy);
        magnetismz = (TextView) findViewById(R.id.magnetismz);
        magnetismd = (TextView) findViewById(R.id.magnetismd);

        magnetometer = sm.getDefaultSensor(magnetometer.TYPE_MAGNETIC_FIELD);
        if (magnetometer == null) {
            Toast.makeText(this, "Magnetometer not available", Toast.LENGTH_SHORT).show();
            finish();
        }

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.saveData:
                try {
                    BackupDatabase();
                    Toast.makeText(getApplicationContext(), "saving data to sd//bimal/Mag_Positioning.db,", Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.btnRecord:
                recording = true;
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);

                File folder = new File(Environment.getExternalStorageDirectory() + "/bimal" + hour + "-" + minute + "-" + second);
                if (!folder.exists()) {
                    success = folder.mkdir();
                }
                // Do something on success
                String csv = folder.getAbsolutePath() + "/Accelerometer.csv";
                try {
                    file_writer = new FileWriter(csv, true);

                    if (isStart == false) {
                        String s = "X-Axis, Y-Axis, Z-Axis, ERROR \n";
                        file_writer.append(s);
                        isStart = true;
                        Toast.makeText(getBaseContext(), "Data Recording Started", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.btnStop:
                stoprecord = true;
                try {
                    file_writer.close();
                    Toast.makeText(getBaseContext(), "Data Recording Stopped", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.showDiff:
                calcdiff = true;
                Toast.makeText(getApplicationContext(), "finding difference", Toast.LENGTH_SHORT).show();
                break;

            case R.id.loadData:
                LoadData();
                break;

            case R.id.insertData:
                insertData();
                break;

            case R.id.localaizeData:
                islocalize = true;
                break;
            case R.id.errorData:
                isErrror = true;
                break;
            case R.id.measure:
                ismeasure=true;
        }
    }



    public static void BackupDatabase() throws IOException {
        boolean success = true;
        File file = null;
        file = new File(Environment.getExternalStorageDirectory() + "/bimal");

        if (file.exists()) {
            success = true;
        } else {
            success = file.mkdir();
        }

        if (success) {
            String inFileName = "/data/data/com.bimal.mag_positioning/databases/Mag_Positioning.db";
            File dbFile = new File(inFileName);
            FileInputStream fis = new FileInputStream(dbFile);

            String outFileName = Environment.getExternalStorageDirectory() + "/bimal/Mag_Positioning.db";

            // Open the empty db as the output stream
            OutputStream output = new FileOutputStream(outFileName);

            // Transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            fis.close();
        }
    }

    public void LoadData() {
        /*
        TextView view = (TextView) findViewById(R.id.load);
        SQLiteDatabase db;
        db = openOrCreateDatabase(
                "Mag_Positioning.db"
                , SQLiteDatabase.CREATE_IF_NECESSARY
                , null
        );
        db.setVersion(1);
        db.setLocale(Locale.getDefault());
        db.setLockingEnabled(true);
        Cursor cur = db.query("Fingerprint", null, null, null, null, null, null);
        cur.moveToFirst();
        while (cur.isAfterLast() == false) {
            view.append("\n" + cur.getString(2) + "," + cur.getString(3) + "," + cur.getString(4) + "," +
                    cur.getString(7) + "," + "\n");
            cur.moveToNext();
        }
        cur.close();
    }
*/
        Cursor res = DBHelper.getInstance().getAllData();
        if (res.getCount() == 0) {
            showMessage("Error", "Nothing found");
            return;
        }
        StringBuffer buffer = new StringBuffer();
        while (res.moveToNext()) {
            buffer.append("Id :" + res.getInt(0) + "\n");
            buffer.append("MapId :" + res.getInt(1) + "\n");
            buffer.append("X :" + res.getFloat(2) + "\n");
            buffer.append("Y :" + res.getFloat(3) + "\n");
            buffer.append("X_Axis :" + res.getString(4) + "\n");
            buffer.append("Y_Axis :" + res.getFloat(5) + "\n");
            buffer.append("Z_Axis :" + res.getFloat(6) + "\n");
            buffer.append("Average :" + res.getString(7) + "\n");
            buffer.append("SD :" + res.getFloat(8) + "\n\n");
        }
        showMessage("Data", buffer.toString());
    }
    public void showMessage(String title, String Message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(title);
        builder.setMessage(Message);
        builder.show();
    }

    public void writeToCsv(String x, String y, String z, String v, String u) throws IOException {
        if (isStart == true) {
            String s = x + "," + y + "," + z + "," + v + u + "\n";
            file_writer.append(s);
        }
    }

    // public  void CalcDiff(){
    //   float total;
    //   String z;
    //   TextView diff= (TextView) findViewById(R.id.calcDiff);
    //   SQLiteDatabase db;
    //  db= openOrCreateDatabase(
    //          "Mag_Positioning.db"
    //          , SQLiteDatabase.CREATE_IF_NECESSARY
    //          , null
    //  );
    //   db.setVersion(1);
    //  db.setLocale(Locale.getDefault());
    //  db.setLockingEnabled(true);
    //  Cursor cur = db.query("Fingerprint", null, null, null, null, null, null);
    // cur.moveToFirst();
    //  while (cur.isAfterLast() == false) {
    //     z= cur.getString(6);
    //     diff.setText(d + "\n");
    //     cur.moveToNext();
    //  }
    //  }


    @Override
    public void onSensorChanged(SensorEvent event) {

            switch(event.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD:
                    curTimeAcc = System.currentTimeMillis();
                    if(curTimeAcc - lastTimeAcc >40 ){

                            MagnetData = event;
                            calculate();


                    }
                    break;
                case Sensor.TYPE_ROTATION_VECTOR:
                    RotVectorData = event;
                    sm.getRotationMatrixFromVector(RotationMatrix, RotVectorData.values);
            /*    break;
            case Sensor.TYPE_ACCELEROMETER:
                AccelVector=event;
*/

            }
        }

    private void calculate() {


       /* k = 1;
        alpha = 0.0f;
        avg = new float[3];
        prevAvg = new float[3];
        */

       //probability test
        Double probability;
        Float probDiff;
        Float SD;
        double xcordProb;
        double ycordProb;


        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        float [] res = new float[3];

        //Database values
        Float xaxis_database;
        Float yaxis_database;
        Float zaxis_database;
        Float average_database;
        String cordTime="";

        // initial magnetic sensor value
        xaxis = MagnetData.values[0];
        yaxis = MagnetData.values[1];
        zaxis = MagnetData.values[2];

       /* //Applying rotation matrix
        xaxis= RotationMatrix[0]*MagnetData.values[0] + RotationMatrix[1]*MagnetData.values[1] +  RotationMatrix[2]*MagnetData.values[2];
        yaxis = RotationMatrix[4]*MagnetData.values[0] + RotationMatrix[5]*MagnetData.values[1] +  RotationMatrix[6]*MagnetData.values[2];
        zaxis = RotationMatrix[8]*MagnetData.values[0] + RotationMatrix[9]*MagnetData.values[1] + RotationMatrix[10]*MagnetData.values[2];
*/
        //Average filtering
      /* alpha = ((float)(k - 1) / k);
        avg[0] =  alpha * prevAvg[0] + (1 - alpha) * a;
        avg[1] = alpha * prevAvg[1] + (1 - alpha) * b;
        avg[2] = alpha * prevAvg[2] + (1 - alpha) * c;

        prevAvg[0] = avg[0];
        prevAvg[1] = avg[1];
        prevAvg[2] = avg[2];
        */
        average = (float) Math.sqrt((Math.pow(xaxis, 2) + Math.pow(yaxis, 2) + Math.pow(zaxis, 2)));
        if(sensorData.size()<100){
            sensorData.add(average);
        }









        //Dispaly the values
        magnetismx.setText(String.valueOf(xaxis));
        magnetismy.setText(Float.toString(yaxis));
        magnetismz.setText(Float.toString(zaxis));
        //magnetismd.setText(String.valueOf(calculateStandarddeviation(sensorData)));
        magnetismd.setText(String.valueOf(calculateStandarddeviation(sensorData)));


        if(calcdiff)
        {
            TextView diff= (TextView) findViewById(R.id.calcDiff);
            TextView localize=(TextView)findViewById(R.id.localization);
            SQLiteDatabase db;
            db= openOrCreateDatabase(
                    "Mag_Positioning.db"
                    , SQLiteDatabase.CREATE_IF_NECESSARY
                    , null
            );
            db.setVersion(1);
            db.setLocale(Locale.getDefault());
            db.setLockingEnabled(true);

            Cursor cur = DBHelper.getInstance().getAllData();
            cur.moveToFirst();
            HashMap<PointF, Float> difference = new HashMap<>();
            Multimap<PointF, Float> result= HashMultimap.create();
            Multimap<PointF, Double> probResult= HashMultimap.create();
            //This hashmap is for creating array of pointf in one location id
            //HashMap<Integer,List<PointF>>find=new HashMap<>();

            //List<PointF> []tmp = new List[4];
            // for(int i = 0; i < 4; i++){
            // tmp[i] = new ArrayList<>();
            // }


            // Integer mapid;

            if (cur.isLast() == false) {
                do{

                    //mapid=cur.getInt(1);
                    PointF location = new PointF(cur.getInt(2), cur.getInt(3));
                    xaxis_database=Float.valueOf(cur.getString(4));
                    yaxis_database=Float.valueOf(cur.getString(5));
                    zaxis_database=Float.valueOf(cur.getString(6));
                    average_database= Float.valueOf(cur.getString(7));
                    SD= Float.valueOf(cur.getString(8));
                    probDiff= average-average_database;
                    probability= Math.exp(-((probDiff)*(probDiff))/(2*SD*SD));
                   /* total = Float.valueOf((float) Math.sqrt((Math.pow((xaxis - xaxis_database), 2) + Math.pow((yaxis - yaxis_database), 2) +
                            Math.pow((zaxis - zaxis_database), 2) + Math.pow((average - average_database), 2))));

                    */
                    xcordProb= cur.getFloat(2)*probability;
                    xcordList.add(xcordProb);
                    ycordProb = cur.getFloat(3)* probability;
                    ycordList.add(ycordProb);
                   //result.put(location, total);
                   // probResult.put(location, probability);
                    //array.add(location);

                    //
                    //tmp[mapid - 1].add(location);




                    //find.put(mapid, array);


                }while(cur.moveToNext());



                //for(int i = 0; i < 4; i++){
                //find.put(i + 1, tmp[i]);
                // }

                diff.setText("\n" +  calculateCoordinates(xcordList,ycordList)  +"\n");
                calcdiff=false;
                xcordList.clear();
                ycordList.clear();
            }

            if(islocalize){


                Map.Entry<PointF, Float> min = Collections.min(result.entries(), new Comparator<Map.Entry<PointF, Float>>() {
                    @Override
                    public int compare(Map.Entry<PointF, Float> entry1, Map.Entry<PointF, Float> entry2) {
                        return entry1.getValue().compareTo(entry2.getValue());
                    }
                });
                get=min.getKey();
                localize.setText(String.valueOf(get) + ts);
                cordTime= String.valueOf(get)+ ts;
                int xloc= (int) get.x;
                int yloc= (int) get.y;
                if(isErrror){
                    TextView E1 = (EditText) findViewById(R.id.xCord);
                    TextView E2 = (EditText) findViewById(R.id.yCord);
                    TextView errortext=(TextView) findViewById(R.id.errorText) ;

                    String tmp2 = E1.getText().toString();
                    String tmp3 = E2.getText().toString();
                    if(tmp2.equals("") || tmp2.equals(""))
                        Toast.makeText(getApplicationContext(), "Please insert  Reference Co-Ordinates", Toast.LENGTH_SHORT).show();
                    int test1 = Integer.parseInt(String.valueOf(tmp2));
                    int test2 = Integer.parseInt(String.valueOf(tmp3));

                    errorvalue=Math.sqrt((xloc-test1)*(xloc-test1) + (yloc-test2)*(yloc-test2));
                    errortext.setText(String.valueOf(errorvalue));
                }

                //code for comparing string in a hashmap and iterating
                //Iterator<Entry<Integer,List<PointF>>> iter = find.entrySet().iterator();

                // while(iter.hasNext()){
                //    Map.Entry<Integer,List<PointF>> entry = iter.next();
                //   if(String.valueOf(entry.getValue()).equals(get)){

                //    Integer done=entry.getKey();
                //     localize.setText(String.valueOf(done));
                // }
                // localize.setText(String.valueOf(entry.getKey())+"\n");
                // Toast.makeText(getApplicationContext(),"done",Toast.LENGTH_SHORT).show();
                // }

                //code for iterating the hashmap
                //Set<Map.Entry<Integer,List<PointF>>>set=find.entrySet();

                // for(Map.Entry<Integer,List<PointF>> me:set){
                //  localize.setText(String.valueOf(me.getValue()));
                // }

            }
            cur.close();

        }



        if (!recording) {
            return;
        }
        if(stoprecord){
            return;
        }

        try {

            writeToCsv(Float.toString(xaxis), Float.toString(yaxis), Float.toString(zaxis), String.valueOf(errorvalue), cordTime);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();

        }
    }
    private void insertData() {
        TextView x = (EditText) findViewById(R.id.editText1);
        TextView y = (EditText) findViewById(R.id.editText2);
        TextView e = (EditText) findViewById(R.id.mapid);

        String tmp1 = e.getText().toString();
        String tmp2 = x.getText().toString();
        String tmp3 = y.getText().toString();

        if(tmp1.equals("") || tmp2.equals("") || tmp3.equals(""))
            Toast.makeText(getApplicationContext(), "Please insert Co-Ordinates First", Toast.LENGTH_SHORT).show();

        if(!tmp1.equals("") && !tmp2.equals("") && !tmp3.equals("")){
            int z1= Integer.parseInt(tmp1);
            int x1 = Integer.parseInt(tmp2);
            int y1 = Integer.parseInt(tmp3);

            Toast.makeText(getApplicationContext(),"Data insertion started", Toast.LENGTH_SHORT).show();
            DBHelper.getInstance().insert(z1, x1, y1, xaxis, yaxis, zaxis, average,calculateStandarddeviation(sensorData));

        }
    }

    private double calculateMean(ArrayList<Float>sensorData){
        int sum = 0;
        double mean;
        if (!sensorData.isEmpty())
            for (float data : sensorData) {
                sum += data;
            }
           mean = sum / sensorData.size();
           return  mean;
    }

    private float calculateStandarddeviation(List<Float> sensorData) {
        int sum = 0;
        if (!sensorData.isEmpty())
            for (float data : sensorData) {
                sum += data;
            }
        double mean = sum / sensorData.size();
        double temp=0;
        for(int i=0 ; i<sensorData.size(); i++){
            float val= sensorData.get(i);
            double squrDiffToMean = Math.pow(val-mean,2);
            temp+=squrDiffToMean;

        }
        double meanofDiffs=  temp /  (sensorData.size());

       return (float) Math.sqrt(meanofDiffs);
       //magnetismd.setText(String.valueOf(sensorData.get(1)));

    }

    public String calculateCoordinates(List<Double>xcordList, List<Double>ycordList){
        if (!xcordList.isEmpty())
            for (Double data : xcordList) {
                sum_x += data;
            }
            if(!ycordList.isEmpty()){
                for (Double data : ycordList) {
                    sum_y += data;
                }
            }
        return sum_x+"\n"+sum_y;

    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
        sensorData.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sm.unregisterListener(this);
        sensorData.clear();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
