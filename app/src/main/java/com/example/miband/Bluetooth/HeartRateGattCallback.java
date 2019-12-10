package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.miband.Activities.DeviceControlActivity;
import com.example.miband.DataStructures.HeartRate;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.Device.MiBandService;
import com.example.miband.Device.MiBandSupport;

import java.io.IOException;
import java.util.Calendar;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.M)
public class HeartRateGattCallback extends BluetoothGattCallback {
    public static String TAG = "MiBand: HeartRateGattCallback";

    private static final byte[] stopHeartMeasurementManual = new byte[]{0x15, MiBandService.COMMAND_SET_HR_MANUAL, 0};
    private static final byte[] startHeartMeasurementContinuous = new byte[]{0x15, MiBandService.COMMAND_SET__HR_CONTINUOUS, 1};
    private static final byte[] stopHeartMeasurementContinuous = new byte[]{0x15, MiBandService.COMMAND_SET__HR_CONTINUOUS, 0};

    private MiBandSupport mSupport;
    private Context mContext;

    private boolean heartRateNotifyEnabled;

    public HeartRateGattCallback(MiBandSupport support, Context context){
        mSupport = support;
        mContext = context;
    }

    MiBandDevice getDevice(){
        return mSupport.getDevice();
    }

    private BluetoothQueue getQueue(){
        return mSupport.getQueue();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private TransactionBuilder performInitialized() throws IOException {
        TransactionBuilder builder = mSupport.performInitialized();
        builder.setGattCallback(this);
        return builder;
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        return mSupport.getCharacteristic(uuid);
    }

    private void enableNotifyHeartRateMeasurements(boolean enable, TransactionBuilder builder) {
        if (heartRateNotifyEnabled != enable) {
            BluetoothGattCharacteristic heartrateCharacteristic = getCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT);
            if (heartrateCharacteristic != null) {
                builder.notify(heartrateCharacteristic, enable);
                heartRateNotifyEnabled = enable;
            }
        }
    }

    private void enableRealtimeSamplesTimer() {
        //TODO handle HR timer
        /*if (enable) {
            getRealtimeSamplesSupport().start();
        } else {
            if (realtimeSamplesSupport != null) {
                realtimeSamplesSupport.stop();
            }
        }*/
    }


    public void enableRealtimeHeartRateMeasurement(boolean enable) {
        BluetoothGattCharacteristic characteristicHRControlPoint = getCharacteristic(MiBandService.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT);
        if (characteristicHRControlPoint == null) {
            return;
        }
        try {
            TransactionBuilder builder = performInitialized();
            enableNotifyHeartRateMeasurements(enable, builder);
            if (enable) {
                builder.write(characteristicHRControlPoint, stopHeartMeasurementManual);
                builder.write(characteristicHRControlPoint, startHeartMeasurementContinuous);
            } else {
                builder.write(characteristicHRControlPoint, stopHeartMeasurementContinuous);
            }
            builder.queue(getQueue());
            enableRealtimeSamplesTimer();
        } catch (IOException ex) {
            Log.d(HeartRateGattCallback.TAG, "Unable to enable realtime heart rate measurement", ex);
        }
    }

    public void onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {

        Log.d(HeartRateGattCallback.TAG, "On characteristic changed");

        UUID characteristicUUID = characteristic.getUuid();
        if (MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT.equals(characteristicUUID)) {
            Log.d(MiBandSupport.TAG, "Heart rate characteristic captured");
            handleHeartRate(characteristic.getValue());
        }else {
            mSupport.onCharacteristicChanged(gatt, characteristic);
        }
    }

    private void handleHeartRate(byte[] value) {
        if (value.length == 2 && value[0] == 0) {
            int hrValue = (value[1] & 0xff);

            Log.d(HeartRateGattCallback.TAG, "heart rate: " + hrValue);

            DeviceControlActivity activity = (DeviceControlActivity) mContext;

            if (hrValue > 0) {
                activity.addEntry(new HeartRate(hrValue, Calendar.getInstance().getTime()));
            }
        }
    }
}
