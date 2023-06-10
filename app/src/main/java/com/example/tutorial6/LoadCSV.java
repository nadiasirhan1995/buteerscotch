package com.example.tutorial6;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.ArrayList;

public class LoadCSV extends AppCompatActivity {

    private EditText csvFileNameEditText;
    private LineChart lineChart;
    private TextView estimatedStepsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);

        csvFileNameEditText = findViewById(R.id.csv_file_name_edit_text);
        lineChart = findViewById(R.id.line_chart);
        estimatedStepsTextView = findViewById(R.id.estimated_steps_text_view);

        Button loadButton = findViewById(R.id.load_button); // Initialize the load button
        Button backButton1 = findViewById(R.id.back_button); // Initialize the back button

        loadButton.setOnClickListener(v -> {
            String fileName = csvFileNameEditText.getText().toString();
            if (!fileName.isEmpty()) {
                ArrayList<String[]> csvData = readCSVFile(fileName + ".csv");
                if (csvData != null) {
                    LineData lineData = createLineData(csvData);
                    lineChart.setData(lineData);
                    lineChart.invalidate();
                    displayEstimatedSteps(fileName + ".csv");
                } else {
                    Toast.makeText(LoadCSV.this, "Error loading CSV file", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(LoadCSV.this, "Please enter a CSV file name", Toast.LENGTH_SHORT).show();
            }
        });


        backButton1.setOnClickListener(v -> finish());


    }

    private ArrayList<String[]> readCSVFile(String fileName) {
        try {
            String filePath = Environment.getExternalStorageDirectory() + "/" + fileName;
            CSVReader reader = new CSVReader(new FileReader(filePath));
            ArrayList<String[]> csvData = new ArrayList<>();
            String[] nextLine;


            for (int i = 0; i < 6; i++) {
                reader.readNext();
            }


            while ((nextLine = reader.readNext()) != null) {
                csvData.add(nextLine);
            }

            reader.close();
            return csvData;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LoadCSV", "Error reading CSV file", e);
            return null;
        }
    }


    private void displayEstimatedSteps(String fileName) {
        try {
            String filePath = Environment.getExternalStorageDirectory() + "/" + fileName;
            CSVReader reader = new CSVReader(new FileReader(filePath));

            // Skip the first four lines containing experiment information
            for (int i = 0; i < 4; i++) {
                reader.readNext();
            }

            // The fifth line should contain "ESTIMATED STEPS"
            String[] estimatedStepsLine = reader.readNext();
            if (estimatedStepsLine != null && estimatedStepsLine.length > 0) {
                String info = estimatedStepsLine[0];
                if (info.startsWith("ESTIMATED STEPS:")) {
                    String estimatedSteps = info.substring(info.indexOf(":") + 1).trim();
                    estimatedStepsTextView.setText(getString(R.string.estimated_steps, estimatedSteps));
                }
            }

            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LoadCSV", "Error reading ESTIMATED STEPS from CSV file", e);
        }
    }


    private LineData createLineData(ArrayList<String[]> csvData) {
        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        for (int i = 1; i < csvData.size(); i++) {
            String[] data = csvData.get(i);

            try {
                float time = convertTimestampToSeconds(data[0]);
                float x = Float.parseFloat(data[1]);
                float y = Float.parseFloat(data[2]);
                float z = Float.parseFloat(data[3]);

                entriesX.add(new Entry(time, x));
                entriesY.add(new Entry(time, y));
                entriesZ.add(new Entry(time, z));
            } catch (NumberFormatException e) {
                Log.e("LoadCSV", "Error parsing float", e);
            }
        }

        LineDataSet dataSetX = new LineDataSet(entriesX, "ACC X");
        dataSetX.setColor(Color.RED);
        dataSetX.setDrawCircles(false);
        dataSetX.setDrawValues(false);

        LineDataSet dataSetY = new LineDataSet(entriesY, "ACC Y");
        dataSetY.setColor(Color.GREEN);
        dataSetY.setDrawCircles(false);
        dataSetY.setDrawValues(false);

        LineDataSet dataSetZ = new LineDataSet(entriesZ, "ACC Z");
        dataSetZ.setColor(Color.BLUE);
        dataSetZ.setDrawCircles(false);
        dataSetZ.setDrawValues(false);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSetX);
        dataSets.add(dataSetY);
        dataSets.add(dataSetZ);

        return new LineData(dataSets);
    }

    private float convertTimestampToSeconds(String timestamp) {
        double elapsedTimeSec = Double.parseDouble(timestamp);
        return (float) elapsedTimeSec;
    }


}
