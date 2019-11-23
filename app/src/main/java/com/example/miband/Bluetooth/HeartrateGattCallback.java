package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.example.miband.Bluetooth.Actions.AbstractGattListenerWriteAction;
import com.example.miband.Bluetooth.Actions.SetDeviceBusyAction;
import com.example.miband.Bluetooth.Gatt.GattCharacteristic;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.Device.MiBandService;
import com.example.miband.Device.MiBandSupport;
import com.example.miband.Enums.OperationStatus;
import com.example.miband.R;
import com.example.miband.Utils.AndroidUtils;
import com.example.miband.Utils.ArrayUtils;
import com.example.miband.Utils.CalendarUtils;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.M)
public class HeartrateGattCallback extends BluetoothGattCallback {
    public static String TAG = "MiBand: HeartrateGattCallback";

    private static final byte[] fetch = new byte[]{MiBandService.COMMAND_FETCH_DATA};

    protected byte lastPacketCounter;
    int fetchCount;
    protected BluetoothGattCharacteristic characteristicActivityData;
    protected BluetoothGattCharacteristic characteristicFetch;
    protected BluetoothGattCharacteristic characteristicHRControlPoint;


    private static final byte[] startHeartMeasurementManual = new byte[]{0x15, MiBandService.COMMAND_SET_HR_MANUAL, 1};
    private static final byte[] stopHeartMeasurementManual = new byte[]{0x15, MiBandService.COMMAND_SET_HR_MANUAL, 0};
    private static final byte[] startHeartMeasurementContinuous = new byte[]{0x15, MiBandService.COMMAND_SET__HR_CONTINUOUS, 1};
    private static final byte[] stopHeartMeasurementContinuous = new byte[]{0x15, MiBandService.COMMAND_SET__HR_CONTINUOUS, 0};


    Calendar startTimestamp;

    MiBandSupport mSupport;

    boolean heartRateNotifyEnabled;

    protected OperationStatus operationStatus = OperationStatus.INITIAL;

    public HeartrateGattCallback(MiBandSupport support){
        mSupport = support;
    }

    MiBandSupport getSupport(){
        return mSupport;
    }

    Context getContext(){
        return mSupport.getContext();
    }

    MiBandDevice getDevice(){
        return mSupport.getDevice();
    }

    BluetoothQueue getQueue(){
        return mSupport.getQueue();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public TransactionBuilder performInitialized(String taskName) throws IOException {
        TransactionBuilder builder = mSupport.performInitialized(taskName);
        builder.setGattCallback(this);
        return builder;
    }

    public void performImmediately(TransactionBuilder builder) throws IOException {
        mSupport.performImmediately(builder);
    }

    protected BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        return mSupport.getCharacteristic(uuid);
    }


    public TransactionBuilder createTransactionBuilder(String taskName) {
        TransactionBuilder builder = getSupport().createTransactionBuilder(taskName);
        builder.setGattCallback(this);
        return builder;
    }

    private void enableNotifyHeartRateMeasurements(boolean enable, TransactionBuilder builder) {
        if (heartRateNotifyEnabled != enable) {
            BluetoothGattCharacteristic heartrateCharacteristic = getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT);
            if (heartrateCharacteristic != null) {
                builder.notify(heartrateCharacteristic, enable);
                heartRateNotifyEnabled = enable;
            }
        }
    }

    private void enableRealtimeSamplesTimer(boolean enable) {
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
        characteristicHRControlPoint = getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT);
        if (characteristicHRControlPoint == null) {
            return;
        }
        try {
            TransactionBuilder builder = performInitialized("Enable realtime heart rate measurement");
            enableNotifyHeartRateMeasurements(enable, builder);
            if (enable) {
                builder.write(characteristicHRControlPoint, stopHeartMeasurementManual);
                builder.write(characteristicHRControlPoint, startHeartMeasurementContinuous);
            } else {
                builder.write(characteristicHRControlPoint, stopHeartMeasurementContinuous);
            }
            builder.queue(getQueue());
            enableRealtimeSamplesTimer(enable);
        } catch (IOException ex) {
            Log.d(HeartrateGattCallback.TAG, "Unable to enable realtime heart rate measurement", ex);
        }
    }

    public void onSetHeartRateMeasurementInterval(int seconds) {
        try {
            int minuteInterval = seconds / 60;
            minuteInterval = Math.min(minuteInterval, 120);
            minuteInterval = Math.max(0,minuteInterval);

            Log.d(HeartrateGattCallback.TAG, "Setting heart rate interval to: " + minuteInterval);

            TransactionBuilder builder = performInitialized("set heart rate interval to: " + minuteInterval + " minutes");
            setHeartrateMeasurementInterval(builder, minuteInterval);
            builder.queue(getQueue());
        } catch (IOException e) {
            AndroidUtils.toast(getContext(), "Error toggling heart rate sleep support: " + e.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }


    private void setHeartrateMeasurementInterval(TransactionBuilder builder, int minutes) {
        if (characteristicHRControlPoint != null) {
            builder.notify(characteristicHRControlPoint, true);
            Log.d(HeartrateGattCallback.TAG, "Setting heart rate measurement interval to " + minutes + " minutes");
            builder.write(characteristicHRControlPoint, new byte[]{MiBandService.COMMAND_SET_PERIODIC_HR_MEASUREMENT_INTERVAL, (byte) minutes});
            builder.notify(characteristicHRControlPoint, false); // TODO: this should actually be in some kind of finally-block in the queue. It should also be sent asynchronously after the notifications have completely arrived and processed.
        }
    }

    public final void perform() throws IOException {
        operationStatus = OperationStatus.STARTED;

        enableRealtimeHeartRateMeasurement(true);

        onSetHeartRateMeasurementInterval(0);

        getDevice().setBusyTask("Operation starting..."); // mark as busy quickly to avoid interruptions from the outside
        TransactionBuilder builder = performInitialized("disabling some notifications");
        enableOtherNotifications(builder, true);
        enableNeededNotifications(builder, true);
        builder.queue(getQueue());


        operationStatus = OperationStatus.RUNNING;

      //  startFetching();
      /*  builder = performInitialized("fetch activity data");
        getSupport().setLowLatency(builder);
        builder.add(new SetDeviceBusyAction(getDevice(), "Fetching activity data", getContext()));
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_CONTROL_POINT), fetch);
        builder.queue(getQueue());*/
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startFetching() throws IOException {
        lastPacketCounter = -1;

        TransactionBuilder builder = performInitialized("Busy task");
        getSupport().setLowLatency(builder);
        if (fetchCount == 0) {
            builder.add(new SetDeviceBusyAction(getDevice(), "Busy task: fetch data", getContext()));
        }
        fetchCount++;

        characteristicActivityData = getCharacteristic(MiBandService.UUID_CHARACTERISTIC_5_ACTIVITY_DATA);
        builder.notify(characteristicActivityData, false);

        characteristicFetch = getCharacteristic(MiBandService.UUID_UNKNOWN_CHARACTERISTIC4);
        builder.notify(characteristicFetch, true);

        startFetching(builder);
        builder.queue(getQueue());
    }

    protected void startFetching(TransactionBuilder builder) {
        final String taskName = AndroidUtils.ensureNotNull(builder.getTaskName());
        GregorianCalendar sinceWhen = getLastSuccessfulSyncTime();
        startFetching(builder, MiBandService.COMMAND_ACTIVITY_DATA_TYPE_ACTIVTY, sinceWhen);
    }

    protected void startFetching(TransactionBuilder builder, byte fetchType, GregorianCalendar sinceWhen) {
        final String taskName = AndroidUtils.ensureNotNull(builder.getTaskName());
        byte[] fetchBytes = ArrayUtils.join(new byte[]{
                        MiBandService.COMMAND_ACTIVITY_DATA_START_DATE,
                        fetchType},
                getSupport().getTimeBytes(sinceWhen, TimeUnit.MINUTES));
        builder.add(new AbstractGattListenerWriteAction(getQueue(), characteristicFetch, fetchBytes) {
            @Override
            protected boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                UUID characteristicUUID = characteristic.getUuid();
                if (MiBandService.UUID_UNKNOWN_CHARACTERISTIC4.equals(characteristicUUID)) {
                    byte[] value = characteristic.getValue();

                    if (ArrayUtils.equals(value, MiBandService.RESPONSE_ACTIVITY_DATA_START_DATE_SUCCESS, 0)) {
                        handleActivityMetadata(value);
                        TransactionBuilder newBuilder = createTransactionBuilder(taskName + " Step 2");
                        newBuilder.notify(characteristicActivityData, true);
                        newBuilder.write(characteristicFetch, new byte[]{MiBandService.COMMAND_FETCH_DATA});
                        try {
                            performImmediately(newBuilder);
                        } catch (IOException ex) {
                            AndroidUtils.toast(getContext(), "Error fetching debug logs: " + ex.getMessage(), Toast.LENGTH_LONG);
                        }
                        return true;
                    } else {
                        handleActivityMetadata(value);
                    }
                }
                return false;
            }
        });
    }

    private void setStartTimestamp(Calendar startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    private void handleActivityMetadata(byte[] value) {
        if (value.length == 15) {
            // first two bytes are whether our request was accepted
            if (ArrayUtils.equals(value, MiBandService.RESPONSE_ACTIVITY_DATA_START_DATE_SUCCESS, 0)) {
                // the third byte (0x01 on success) = ?
                // the 4th - 7th bytes represent the number of bytes/packets to expect, excluding the counter bytes

                // last 8 bytes are the start date
                Calendar startTimestamp = CalendarUtils.fromTimeBytes(Arrays.copyOfRange(value, 7, value.length));
                setStartTimestamp(startTimestamp);

                Log.d(HeartrateGattCallback.TAG, "Fetching activity data");
            } else {
                Log.d(HeartrateGattCallback.TAG, "Unexpected activity metadata: " + value);
                handleActivityFetchFinish(false);
            }
        } else if (value.length == 3) {
            if (Arrays.equals(MiBandService.RESPONSE_FINISH_SUCCESS, value)) {
                handleActivityFetchFinish(true);
            } else {
                Log.d(HeartrateGattCallback.TAG, "Unexpected activity metadata: " + value);
                handleActivityFetchFinish(false);
            }
        } else {
            Log.d(HeartrateGattCallback.TAG, "Unexpected activity metadata: " + value);
            handleActivityFetchFinish(false);
        }
    }

    protected void handleActivityFetchFinish(boolean success) {
        Log.d(HeartrateGattCallback.TAG, "Fetching finished with: " + success);
        operationFinished();
        unsetBusy();
    }

    protected void operationFinished() {
        operationStatus = OperationStatus.FINISHED;
        if (getDevice() != null && getDevice().isConnected()) {
            unsetBusy();
            try {
                TransactionBuilder builder = performInitialized("reenabling disabled notifications");
                handleFinished(builder);
                builder.setGattCallback(null); // unset ourselves from being the queue's gatt callback
                builder.queue(getQueue());
            } catch (IOException ex) {
                AndroidUtils.toast(getContext(), "Error enabling Mi Band notifications, you may need to connect and disconnect", Toast.LENGTH_LONG);
            }
        }
    }

    protected void unsetBusy() {
        if (getDevice().isBusy()) {
            getDevice().unsetBusyTask();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void handleFinished(TransactionBuilder builder) {
        enableNeededNotifications(builder, false);
        enableOtherNotifications(builder, true);
    }

    protected void enableNeededNotifications(TransactionBuilder builder, boolean enable) {
        if (!enable) {
            // dynamically enabled, but always disabled on finish
            builder.notify(characteristicFetch, enable);
            builder.notify(characteristicActivityData, enable);
        }
    }

    protected void enableOtherNotifications(TransactionBuilder builder, boolean enable) {
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_REALTIME_STEPS), enable)
                .notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_SENSOR_DATA), enable);
    }

    protected GregorianCalendar getLastSuccessfulSyncTime() {
      //TODO Check if there should be time stamp set
        /*  long timeStampMillis = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).getLong(getLastSyncTimeKey(), 0);
        if (timeStampMillis != 0) {
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(timeStampMillis);
            return calendar;
        }*/

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, -100);
        return calendar;
    }

    public boolean isOperationRunning() {
        return operationStatus == OperationStatus.RUNNING;
    }

    protected void handleActivityNotif(byte[] value) {
        if (!isOperationRunning()) {
            Log.d(HeartrateGattCallback.TAG, "ignoring activity data notification because operation is not running. Data length: " + value.length);

            return;
        }

        if ((value.length % 4) == 1) {
            if ((byte) (lastPacketCounter + 1) == value[0] ) {
                lastPacketCounter++;
                bufferActivityData(value);
            } else {
                AndroidUtils.toast(getContext(), "Error , invalid package counter: " + value[0], Toast.LENGTH_LONG);
                handleActivityFetchFinish(false);
                return;
            }
        } else {
            AndroidUtils.toast(getContext(), "Error , unexpected package length: " + value.length, Toast.LENGTH_LONG);
            handleActivityFetchFinish(false);
        }
    }

    protected void bufferActivityData(byte[] value) {
        int len = value.length;

        if (len % 4 != 1) {
            throw new AssertionError("Unexpected activity array size: " + len);
        }

        for (int i = 1; i < len; i+=4) {
            Log.d(HeartrateGattCallback.TAG, value[i] + " " + value[i + 1] + " " + value[i + 2] + " " + value[i + 3]); // lgtm [java/index-out-of-bounds]
        }
    }

    public void onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {

        Log.d(HeartrateGattCallback.TAG, "On characteristic changed");

        UUID characteristicUUID = characteristic.getUuid();
        if (MiBandService.UUID_CHARACTERISTIC_5_ACTIVITY_DATA.equals(characteristicUUID)) {
            handleActivityNotif(characteristic.getValue());

            Log.d(HeartrateGattCallback.TAG, "Characteristic changed with: " + true);
        } else if (MiBandService.UUID_UNKNOWN_CHARACTERISTIC4.equals(characteristicUUID)) {
            handleActivityMetadata(characteristic.getValue());

            Log.d(HeartrateGattCallback.TAG, "Characteristic changed with: " + true);
        } else {
            mSupport.onCharacteristicChanged(gatt, characteristic);
        }
    }
}
