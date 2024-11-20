package com.example.stepappv8.ui.Home;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.stepappv8.R;
import com.example.stepappv8.StepAppOpenHelper;
import com.example.stepappv8.databinding.FragmentHomeBinding;

// TODO 5: import BuildConfig!
import com.example.stepappv8.BuildConfig;

// TODO 7: import gemini libraries
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;


import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private TextView stepCountsView;
    private CircularProgressIndicator progressBar;

    private MaterialButtonToggleGroup toggleButtonGroup;

    private Sensor accSensor;

    private SensorManager sensorManager;

    private StepCounterListener sensorListener;


    private Sensor stepDetectorSensor;

    public Integer progressMultiplier = 100000;
    public Button buttonStart;
    public Button buttonStop;

    //    TODO: don't forget to define the variables for the whole class
    public TextView llm_output_box;
    public ImageButton refresh_button;
    public String output_llm;

    public View root;

    Date cDate = new Date();
    String current_time = new SimpleDateFormat("yyyy-MM-dd").format(cDate);

    public boolean firstVisit = true;



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        root = binding.getRoot();

        stepCountsView = (TextView) root.findViewById(R.id.counter);
        if (firstVisit){
            stepCountsView.setText("0");
        }else{
            Map<Integer, Integer> stepsByHour = StepAppOpenHelper.loadStepsByHour(getContext(), current_time);
            // Initialize a variable to hold the sum
            Integer steps_taken_today = 0;
            // Iterate through the values and add them to the sum
            for (Integer value : stepsByHour.values()) {
                steps_taken_today += value;
            }
            stepCountsView.setText(String.valueOf(steps_taken_today));
        }
        firstVisit = false;




        progressBar = (CircularProgressIndicator) root.findViewById(R.id.progressBar);
//        progressBar.setMax(root.getResources().getInteger(R.integer.goal));
        progressBar.setMax(root.getResources().getInteger(R.integer.goal)*progressMultiplier);
        progressBar.setProgress(0);

//        TODO 2: make the textview scrollable
        llm_output_box = (TextView) root.findViewById(R.id.llm_output);
        llm_output_box.setMovementMethod(new ScrollingMovementMethod());

//        TODO 5: test if you can read correctly the apiKey variable
        Log.d("Secrets Plugin", "BuildConfig.apiKey: " + BuildConfig.apiKey);

        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        StepAppOpenHelper databaseOpenHelper = new StepAppOpenHelper(this.getContext());
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();


        buttonStop = (Button) root.findViewById(R.id.stop_button);
        buttonStart = (Button) root.findViewById(R.id.start_button);
        buttonStart.setEnabled(true);
        buttonStop.setEnabled(false);

        buttonStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (accSensor != null)
                {
                    sensorListener = new StepCounterListener(stepCountsView,progressBar, database);
                    sensorManager.registerListener(sensorListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
                else
                {
                    Toast.makeText(getContext(), R.string.acc_sensor_not_available, Toast.LENGTH_LONG).show();
                }
                buttonStart.setEnabled(false);
                buttonStop.setEnabled(true);
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sensorManager.unregisterListener(sensorListener);
                buttonStart.setEnabled(true);
                buttonStop.setEnabled(false);
            }
        });



        //   TODO 7: define the model and retrieve the API keys
        GenerativeModel gm = new GenerativeModel(
                /* modelName */ "gemini-1.5-flash",
                // Access your API key as a Build Configuration variable (see "Set up your API key"
                // above)
                /* apiKey */
                BuildConfig.apiKey
        );

//        TODO 6: get the refresh button and call the setOnClickListener
        refresh_button = (android.widget.ImageButton) root.findViewById(R.id.refresh_button);
        refresh_button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                getLLMOutput(gm);
            }
        });

        return root;
    }

    //        TODO 7: write function for LLM
    public void getLLMOutput(GenerativeModel gm){


        String current_steps = stepCountsView.getText().toString();

        GenerativeModelFutures model = GenerativeModelFutures.from(gm);
        String steps_goal = root.getResources().getString(R.string.goal);

        String input_llm = "You are a motivational online coach. You have to provide the user with" +
                "activities they should do to achieve their desired number of steps, based on the current number of steps. " +
                "WRITE IN MAX 10 WORDS. The step goal is " + steps_goal + ". The current number of steps is " + current_steps + "." +
                "PROVIDE THE ACTIVITIES AS A LIST LIKE: - walk to the grocery store today - run for 10 minutes. PROVIDE MAX 3 EXAMPLES. DO NOT REPEAT THE NUMBER OF STEPS OR THE GOAL IN YOUR OUTPUT.";


        Content content = new Content.Builder().addText(input_llm).build();
        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        output_llm = result.getText();
                        llm_output_box.setText(output_llm);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        t.printStackTrace();
                        output_llm = t.getMessage();
                        llm_output_box.setText(output_llm);
                    }
                },
                executor);


    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public class ProgressBarAnimation extends Animation {
        private CircularProgressIndicator progressBar;
        private float from;
        private float to;



        public ProgressBarAnimation(CircularProgressIndicator progressBar, float from, float to) {
            super();
            this.progressBar = progressBar;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            float value = from + (to - from) * interpolatedTime;
            progressBar.setProgress((int) value);
        }

        public void animateProgressBarStep(Integer step){
            ProgressBarAnimation anim = new ProgressBarAnimation(progressBar, step*progressMultiplier, 0);
            stepCountsView.setText(Integer.toString(step));
            anim.setDuration(200);
            progressBar.startAnimation(anim);
        }
    }



    class  StepCounterListener implements SensorEventListener{

        private long lastSensorUpdate = 0;
        public int accStepCounter = 0;
        ArrayList<Integer> accSeries = new ArrayList<Integer>();
        ArrayList<String> timestampsSeries = new ArrayList<String>();
        private double accMag = 0;
        private int lastAddedIndex = 1;
        int stepThreshold = 6;

        TextView stepCountsView;

        CircularProgressIndicator progressBar;
        private SQLiteDatabase database;

        private String timestamp;
        private String day;
        private String hour;


        public StepCounterListener(TextView stepCountsView, CircularProgressIndicator progressBar,  SQLiteDatabase databse)
        {
            this.stepCountsView = stepCountsView;
            this.database = databse;
            this.progressBar = progressBar;
        }


        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            switch (sensorEvent.sensor.getType())
            {
                case Sensor.TYPE_LINEAR_ACCELERATION:

                    float x = sensorEvent.values[0];
                    float y = sensorEvent.values[1];
                    float z = sensorEvent.values[2];

                    long currentTimeInMilliSecond = System.currentTimeMillis();

                    long timeInMillis = currentTimeInMilliSecond + (sensorEvent.timestamp - SystemClock.elapsedRealtimeNanos()) / 1000000;

                    // Convert the timestamp to date
                    SimpleDateFormat jdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                    jdf.setTimeZone(TimeZone.getTimeZone("GMT+2"));
                    String sensorEventDate = jdf.format(timeInMillis);




                    if ((currentTimeInMilliSecond - lastSensorUpdate) > 1000)
                    {
                        lastSensorUpdate = currentTimeInMilliSecond;
                        String sensorRawValues = "  x = "+ String.valueOf(x) +"  y = "+ String.valueOf(y) +"  z = "+ String.valueOf(z);
                        Log.d("Acc. Event", "last sensor update at " + String.valueOf(sensorEventDate) + sensorRawValues);
                    }


                    accMag = Math.sqrt(x*x+y*y+z*z);


                    accSeries.add((int) accMag);

                    // Get the date, the day and the hour
                    timestamp = sensorEventDate;
                    day = sensorEventDate.substring(0,10);
                    hour = sensorEventDate.substring(11,13);

                    Log.d("SensorEventTimestampInMilliSecond", timestamp);


                    timestampsSeries.add(timestamp);
                    peakDetection();

                    break;

            }


        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

        private void peakDetection() {

            int windowSize = 20;
            /* Peak detection algorithm derived from: A Step Counter Service for Java-Enabled Devices Using a Built-In Accelerometer Mladenov et al.
             */
            int currentSize = accSeries.size(); // get the length of the series
            if (currentSize - lastAddedIndex < windowSize) { // if the segment is smaller than the processing window size skip it
                return;
            }

            List<Integer> valuesInWindow = accSeries.subList(lastAddedIndex,currentSize);
            List<String> timePointList = timestampsSeries.subList(lastAddedIndex,currentSize);
            lastAddedIndex = currentSize;

            for (int i = 1; i < valuesInWindow.size()-1; i++) {
                int forwardSlope = valuesInWindow.get(i + 1) - valuesInWindow.get(i);
                int downwardSlope = valuesInWindow.get(i) - valuesInWindow.get(i - 1);

                if (forwardSlope < 0 && downwardSlope > 0 && valuesInWindow.get(i) > stepThreshold) {
                    accStepCounter += 1;
                    Log.d("ACC STEPS: ", String.valueOf(accStepCounter));
                    stepCountsView.setText(String.valueOf(accStepCounter));
                    progressBar.setProgress(accStepCounter);

                    ContentValues databaseEntry = new ContentValues();
                    databaseEntry.put(StepAppOpenHelper.KEY_TIMESTAMP, timePointList.get(i));

                    databaseEntry.put(StepAppOpenHelper.KEY_DAY, this.day);
                    databaseEntry.put(StepAppOpenHelper.KEY_HOUR, this.hour);

                    database.insert(StepAppOpenHelper.TABLE_NAME, null, databaseEntry);

                    stepCountsView.setText(Integer.toString(accStepCounter));
                    ProgressBarAnimation anim = new ProgressBarAnimation((CircularProgressIndicator) progressBar, (accStepCounter-1)*progressMultiplier, accStepCounter*progressMultiplier);
                    anim.setDuration(300);
                    progressBar.startAnimation(anim);

                }
            }
        }


    }
}

