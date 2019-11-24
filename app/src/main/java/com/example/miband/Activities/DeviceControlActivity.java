package com.example.miband.Activities;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.miband.Bluetooth.HeartRateGattCallback;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.MainActivity;
import com.example.miband.R;
import com.example.miband.Utils.AndroidUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeviceControlActivity extends AppCompatActivity {

    HeartRateGattCallback heartrateGattCallback;
    ScheduledExecutorService service;

    Button clickBtn;
    Button offBtn;
    MiBandDevice mDevice;

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
                AndroidUtils.toast(DeviceControlActivity.this, "Fetching started...", Toast.LENGTH_SHORT);

                heartrateGattCallback = new HeartRateGattCallback(MainActivity.getMiBandSupport());
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


                //   heartrateGattCallback.perform();
            }
        });

        offBtn = findViewById(R.id.offBtn);
        offBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                AndroidUtils.toast(DeviceControlActivity.this, "Offfff....", Toast.LENGTH_SHORT);

                service.shutdownNow();
                heartrateGattCallback.enableRealtimeHeartRateMeasurement(false);
            }
        });
    }


}
