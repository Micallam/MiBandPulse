package com.example.miband.Activities;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.miband.Bluetooth.HeartRateGattCallback;
import com.example.miband.DataStructures.HeartRate;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.MainActivity;
import com.example.miband.R;
import com.example.miband.Utils.AndroidUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeviceControlActivity extends AppCompatActivity {
    public static String TAG = "MiBand: DeviceControlActivity";

    HeartRateGattCallback heartrateGattCallback;
    ScheduledExecutorService service;

    Button clickBtn;
    Button offBtn;
    MiBandDevice mDevice;

    private LineChart mChart;

    private static final float TOTAL_MEMORY = 150.0f;
    private static final float LIMIT_MAX_MEMORY = 140.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_control);

        Intent intent = getIntent();
        Bundle bundle = intent.getBundleExtra("bundle");
        mDevice = bundle.getParcelable(MiBandDevice.EXTRA_DEVICE);

        clickBtn = findViewById(R.id.onBtn);
        clickBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                AndroidUtils.toast(DeviceControlActivity.this, "Odczyt pulsu rozpoczęty", Toast.LENGTH_SHORT);

                heartrateGattCallback = new HeartRateGattCallback(MainActivity.getMiBandSupport(), DeviceControlActivity.this);
                service = Executors.newSingleThreadScheduledExecutor();
                service.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        FragmentActivity activity = DeviceControlActivity.this;
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    heartrateGattCallback.enableRealtimeHeartRateMeasurement(true);
                                }
                            });
                        }
                    }
                }, 0, 2000, TimeUnit.MILLISECONDS);
            }
        });

        offBtn = findViewById(R.id.offBtn);
        offBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                AndroidUtils.toast(DeviceControlActivity.this, "Wyłączono czytnik", Toast.LENGTH_SHORT);

                service.shutdownNow();
                heartrateGattCallback.enableRealtimeHeartRateMeasurement(false);

                LineData data = mChart.getData();

                data.clearValues();
                data.notifyDataChanged();
                mChart.notifyDataSetChanged();
            }
        });


        mChart = findViewById(R.id.chart);

        setupChart();
        setupAxes();
        setupData();
        setLegend();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setupChart() {
        // disable description text
        mChart.getDescription().setEnabled(false);
        // enable touch gestures
        mChart.setTouchEnabled(true);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);
        // enable scaling
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);
        // set an alternative background color
        //mChart.setBackgroundColor(Color.DKGRAY);
        mChart.setBackgroundColor(getResources().getColor(R.color.colorBackground));
    }

    private void setupAxes() {
        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(TOTAL_MEMORY);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        // Add a limit line
        LimitLine ll = new LimitLine(LIMIT_MAX_MEMORY, "Upper Limit");
        ll.setLineWidth(2f);
        ll.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        ll.setTextSize(10f);
        ll.setTextColor(Color.WHITE);
        ll.setLineColor(Color.BLACK);
        // reset all limit lines to avoid overlapping lines
        leftAxis.removeAllLimitLines();
        leftAxis.addLimitLine(ll);
        // limit lines are drawn behind data (and not on top)
        leftAxis.setDrawLimitLinesBehindData(true);
    }

    private void setupData() {
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);
    }

    private void setLegend() {
        // get the legend (only possible after setting data)
      /*  Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setTextColor(Color.WHITE);
        */
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Memory Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        //set.setColors(ColorTemplate.VORDIPLOM_COLORS[1]);
        set.setCircleColor(Color.RED);
        //set.setFillColor(Color.RED);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setValueTextColor(Color.WHITE);

        set.setValueTextSize(10f);
        // To show values of each point
        set.setDrawValues(true);

        return set;
    }

    public void addEntry(HeartRate heartRate) {
        LineData data = mChart.getData();

        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), heartRate.getValue()), 0);

            // let the chart know it's data has changed
            data.notifyDataChanged();
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(15);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }
    }
}
