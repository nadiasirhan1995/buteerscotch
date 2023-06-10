package com.example.tutorial6;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

public class TerminalFragment extends Fragment implements SensorEventListener, SerialListener {

    private EditText stepsEditText;
    private EditText fileNameEditText;
    private LineChart lineChart;
    private Button startButton;
    private Button stopButton;
    private Button resetButton;
    private Button saveButton;
    private RadioGroup activityTypeRadioGroup;
    private RadioButton radioRunning, radioWalking;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView stepsTextView;
    private Button loadCsv;

    private boolean recording = false;
    private List<String[]> sensorData;

    // Bluetooth variables
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Sample UUID, replace with your own
    private final String DEVICE_ADDRESS = "00:00:00:00:00:00"; // Sample device address, replace with your own

    private SerialService serialService;

    private boolean initialStart = true;
    private SerialSocket socket;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_terminal, container, false);
        fileNameEditText = rootView.findViewById(R.id.file_name_edit_text);
        stepsEditText = rootView.findViewById(R.id.steps_edit_text);
        lineChart = rootView.findViewById(R.id.line_chart);
        startButton = rootView.findViewById(R.id.start_button);
        stopButton = rootView.findViewById(R.id.stop_button);
        resetButton = rootView.findViewById(R.id.reset_button);
        saveButton = rootView.findViewById(R.id.save_button);
        stepsTextView = rootView.findViewById(R.id.stepsTextView);
        SensorManager sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);

        BluetoothDevice device = ...; // Get your BluetoothDevice instance here
        socket = new SerialSocket(getActivity().getApplicationContext(), device);

        try {
            socket.connect(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Set up the text view to display the data from the other device.
        TextView textView = (TextView) rootView.findViewById(R.id.textView);
        textView.setText("");
        Button button = (Button) rootView.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] data = {1, 2, 3, 4, 5};
                try {
                    socket.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


        return rootView;
    }


    @Override
    public void onSerialConnect() {
        // This method is called when the connection is established.
        Toast.makeText(getActivity(), "Connected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        // This method is called when there was an error while connecting.
        Toast.makeText(getActivity(), "Connection Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialRead(byte[] data) {
        // This method is called when data is read from the socket.
        String dataStr = new String(data, StandardCharsets.UTF_8);
        Toast.makeText(getActivity(), "Received: " + dataStr, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialIoError(Exception e) {
        // This method is called when there was an IO error.
        Toast.makeText(getActivity(), "IO Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }


        stopButton.setOnClickListener(v -> {
            stopRecording();
            setButtonStates(true, false, true, true);
        });

        resetButton.setOnClickListener(v -> {
            resetRecording();
            setButtonStates(true, false, false, false);
        });

        saveButton.setOnClickListener(v -> {
            saveDataToFile();
            setButtonStates(true, false, true, false);
        });

        // Bluetooth initialization
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        }

        return rootView;
    }

    private void setButtonStates(boolean startEnabled, boolean stopEnabled, boolean resetEnabled, boolean saveEnabled) {
        startButton.setEnabled(startEnabled);
        stopButton.setEnabled(stopEnabled);
        resetButton.setEnabled(resetEnabled);
        saveButton.setEnabled(saveEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (serialService != null) {
            serialService.attach(this);
        } else {
            getActivity().startService(new Intent(getActivity(), SerialService.class));
        }

        if (bluetoothDevice != null) {
            connectBluetoothDevice();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);

        if (serialService != null) {
            serialService.detach();
        }

        if (bluetoothSocket != null) {
            disconnectBluetoothDevice();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private double calculateAccelerationNorm(double accX, double accY, double accZ) {
        return Math.sqrt(accX * accX + accY * accY + accZ * accZ);
    }



    private Queue<Double> window = new LinkedList<>();
    private double sum = 0;
    private int currentSteps = 0;
    private double prevNorm = 0.0;
    private double currentNorm = 0.0;
    private List<Entry> peakEntries = new ArrayList<>();

    private void processNewAccelerationData(double accX, double accY, double accZ) {

        double norm = calculateAccelerationNorm(accX, accY, accZ);

        final double THRESHOLD = 15.0;
        final int WINDOW_SIZE = 10;
        if (currentNorm > THRESHOLD && currentNorm > prevNorm && currentNorm > norm) {
            peakEntries.add(new Entry((float) sensorData.size(), (float) currentNorm));
        }

        window.add(norm);
        sum += norm;

        if (window.size() > WINDOW_SIZE) {
            sum -= window.remove();
        }

        double average = sum / window.size();
        if (average > THRESHOLD) {
            currentSteps++;
            stepsTextView.setText("Steps: " + currentSteps);
        }
        prevNorm = currentNorm;
        currentNorm = norm;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (recording) {
            long timeMillis = System.currentTimeMillis();
            double timeSec = timeMillis / 1000.0;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            sensorData.add(new String[]{String.valueOf(timeSec), String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(timeSec)});

            double n = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));

            // sensorData.add(new String[]{String.valueOf(timeSec), String.valueOf(n)});
            updateLineChart(n);

            processNewAccelerationData(x, y, z);
        }
    }


    private void updateLineChart(double n) {
        LineData lineData = lineChart.getData();
        if (lineData == null) {
            lineData = new LineData();
            lineChart.setData(lineData);
        }

        LineDataSet dataSetN = createLineDataSet(lineData, "ACC N", Color.RED);
        dataSetN.addEntry(new Entry(dataSetN.getEntryCount(), (float) n));

        LineDataSet dataSetPeaks = createLineDataSet(lineData, "Peaks", Color.BLUE);

        dataSetPeaks.clear();

        for (Entry entry : peakEntries) {
            dataSetPeaks.addEntry(entry);
        }
        dataSetPeaks.setCircleColor(Color.BLUE);
        dataSetPeaks.setCircleRadius(5f);
        dataSetPeaks.setDrawCircleHole(true);

        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }



    private void startRecording() {
        recording = true;
        sensorData.clear();
    }

    private void stopRecording() {
        recording = false;
    }

    private void resetRecording() {
        recording = false;
        sensorData.clear();
        if (lineChart.getData() != null) {
            lineChart.clearValues();
            lineChart.invalidate();
        }
        stepsTextView.setText("Steps: 0");
    }

    private void saveDataToFile() {
        if (sensorData.isEmpty()) {
            Toast.makeText(requireContext(), "No data to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = fileNameEditText.getText().toString();

        if (fileName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String currentTime = dateFormat.format(new Date());
        String steps = stepsEditText.getText().toString();

        String activityType = "";
        if (activityTypeRadioGroup.getCheckedRadioButtonId() == R.id.radio_running) {
            activityType = "Running";
        } else if (activityTypeRadioGroup.getCheckedRadioButtonId() == R.id.radio_walking) {
            activityType = "Walking";
        }

        String[] header = {"Time [sec]", "ACC X", "ACC Y", "ACC Z"};

        String[] experimentInfo = {
                "NAME: " + fileName,
                "EXPERIMENT TIME: " + currentTime,
                "ACTIVITY TYPE: " + activityType,
                "COUNT OF ACTUAL STEPS: " + steps,
                "ESTIMATED STEPS: " + currentSteps
        };

        String csvPath = Environment.getExternalStorageDirectory() + "/" + fileName + ".csv";
        try {
            File file = new File(csvPath);
            FileWriter fileWriter = new FileWriter(file);
            CSVWriter csvWriter = new CSVWriter(fileWriter);

            // Write experiment info as separate lines
            for (String info : experimentInfo) {
                String[] entries = {info};
                csvWriter.writeNext(entries);
            }

            csvWriter.writeNext(header);

            long startTimeMillis = System.currentTimeMillis();
            double startTimeSec = startTimeMillis / 1000.0;

            for (String[] data : sensorData) {
                double elapsedTimeSec = Double.valueOf(data[4]) - startTimeSec;
                String[] newData = {String.valueOf(elapsedTimeSec), data[0], data[1], data[2]};
                csvWriter.writeNext(newData);
            }



            csvWriter.close();

            Toast.makeText(requireContext(), "Data saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error saving data", Toast.LENGTH_SHORT).show();
        }
    }





    private LineDataSet createLineDataSet(LineData lineData, String label, int color) {
        LineDataSet dataSet = (LineDataSet) lineData.getDataSetByLabel(label, false);
        if (dataSet == null) {
            dataSet = new LineDataSet(null, label);
            dataSet.setColor(color);
            dataSet.setDrawCircles(false);
            dataSet.setDrawValues(false);
            lineData.addDataSet(dataSet);
        }
        return dataSet;
    }

    private void connectBluetoothDevice() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Request the missing permissions (ActivityCompat#requestPermissions) if needed.
                return;
            }
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            Toast.makeText(requireContext(), "Bluetooth device connected", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to connect to Bluetooth device", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectBluetoothDevice() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
                Toast.makeText(requireContext(), "Bluetooth device disconnected", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to disconnect Bluetooth device", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendDataToBluetoothDevice(float x, float y, float z) {
        if (bluetoothSocket != null) {
            try {
                String data = String.format(Locale.getDefault(), "%.2f,%.2f,%.2f\n", x, y, z);
                bluetoothSocket.getOutputStream().write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "Failed to send data to Bluetooth device", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "Bluetooth device is not connected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * SerialListener callbacks
     */

    @Override
    public void onSerialConnect() {
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getActivity(), "Serial device connected", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSerialConnectError(Exception e) {
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getActivity(), "Failed to connect to serial device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSerialRead(byte[] data) {
        // Process the received serial data here
    }

    @Override
    public void onSerialIoError(Exception e) {
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getActivity(), "Serial communication error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}
