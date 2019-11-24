package com.example.miband.Device;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.miband.MainActivity;
import com.example.miband.Utils.AndroidUtils;

public class DeviceService extends Service {
    public static String TAG = "MiBand: DeviceService";

    private boolean mStarted;
    public MiBandSupport mMiBandSupport;
    public MiBandDevice mDevice;

    final String PREFIX = "com.example.miband";

    final String ACTION_START = PREFIX + ".action.start";
    final String ACTION_CONNECT = PREFIX + ".action.connect";
    String ACTION_DISCONNECT = PREFIX + ".action.disconnect";
    String EXTRA_CONNECT_FIRST_TIME = "connect_first_time";

    @Deprecated
    String EXTRA_REALTIME_STEPS = "realtime_steps";

    @Deprecated
    String EXTRA_HEART_RATE_VALUE = "hr_value";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(DeviceService.TAG, "no intent");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        boolean firstTime = intent.getBooleanExtra(EXTRA_CONNECT_FIRST_TIME, true);

        if (action == null) {
            Log.d(DeviceService.TAG, "no action");
            return START_NOT_STICKY;
        }

        Log.d(DeviceService.TAG, "Service startcommand: " + action);

        if (!action.equals(ACTION_START) && !action.equals(ACTION_CONNECT)) {
            if (!mStarted) {
                // using the service before issuing ACTION_START
                Log.d(DeviceService.TAG, "Must start service with " + ACTION_START + " or " + ACTION_CONNECT + " before using it: " + action);
                return START_NOT_STICKY;
            }
        }

        switch (action) {
            case ACTION_START:
                start();
                break;
            case ACTION_CONNECT:
                start(); // ensure started
                MiBandDevice device = intent.getParcelableExtra(MiBandDevice.EXTRA_DEVICE);

                if (!device.isConnecting() && !device.isConnected()) {
                    setDeviceSupport(null);
                    try {
                        MiBandSupport miBandSupport = new MiBandSupport();
                        miBandSupport.setContext(device, BluetoothAdapter.getDefaultAdapter(), this);

                        setDeviceSupport(miBandSupport);
                        if (firstTime) {
                            miBandSupport.connectFirstTime();
                        } else {
                            miBandSupport.connect();
                        }
                    } catch (Exception e) {
                        Log.d(DeviceService.TAG, e.getMessage());
                        AndroidUtils.toast(this, "Cannot connect:" + e.getMessage(), Toast.LENGTH_SHORT);
                        setDeviceSupport(null);
                    }
                } else {
                    // send an update at least
                    device.sendDeviceUpdateIntent(this);
                }
                break;
            default:
                Log.d(DeviceService.TAG, "Unable to recognize action: " + action);

                break;
        }
        return START_STICKY;
    }

    private void start() {
        if (!mStarted) {
            mStarted = true;
        }
    }

    private void setDeviceSupport(@Nullable MiBandSupport deviceSupport) {
        if (deviceSupport != mMiBandSupport && mMiBandSupport != null) {
            mMiBandSupport.dispose();
            mMiBandSupport = null;
        }
        mMiBandSupport = deviceSupport;

        if (mMiBandSupport != null) {
            MainActivity.setMiBandSupport(mMiBandSupport);
        }

        mDevice = mMiBandSupport != null ? mMiBandSupport.getDevice() : null;
    }
}
