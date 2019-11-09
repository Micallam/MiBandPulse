package com.example.miband.Device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.example.miband.Bluetooth.Actions.SetDeviceStateAction;
import com.example.miband.Bluetooth.BluetoothQueue;
import com.example.miband.Bluetooth.Gatt.GattCharacteristic;
import com.example.miband.Bluetooth.Gatt.GattService;
import com.example.miband.Bluetooth.TransactionBuilder;
import com.example.miband.Utils.AndroidUtils;
import com.example.miband.Utils.BleNamesResolver;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MiBandSupport extends BluetoothGattCallback {
    public static String TAG = "MiBand: MiBandSupport";

    BluetoothQueue mQueue;
    MiBandDevice mDevice;

    private Map<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics;
    private final Set<UUID> mSupportedServices = new HashSet<>(4);

    public static final String BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb"; //this is common for all BTLE devices. see http://stackoverflow.com/questions/18699251/finding-out-android-bluetooth-le-gatt-profiles
    private final Object characteristicsMonitor = new Object();

    private BluetoothAdapter mBtAdapter;
    private Context mContext;

    public MiBandSupport() {
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_IMMEDIATE_ALERT);
        addSupportedService(MiBandService.UUID_SERVICE_MIBAND_SERVICE);
        addSupportedService(MiBandService.UUID_SERVICE_HEART_RATE);
        addSupportedService(MiBandService.UUID_CHARACTERISTIC_NOTIFICATION);
        addSupportedService(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS);
        addSupportedService(MiBandService.UUID_CHARACTERISTIC_DATE_TIME);
        addSupportedService(MiBandService.UUID_CHARACTERISTIC_PAIR);
    }

    public void setContext(MiBandDevice device, BluetoothAdapter btAdapter, Context context) {
        mDevice = device;
        mBtAdapter = btAdapter;
        mContext = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean connect() {
        if (mQueue == null) {
            mQueue = new BluetoothQueue(getBluetoothAdapter(), getDevice(), this, getContext());
        }
        return mQueue.connect();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean connectFirstTime() {
        for (int i = 0; i < 5; i++) {
            if (connect()) {
                return true;
            }
        }
        return false;
    }

    public void dispose() {
        Log.d(MiBandSupport.TAG, "Dispose");
        close();
    }

    private void close() {
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        gattServicesDiscovered(gatt.getServices());

        if (getDevice().getState().compareTo(MiBandDevice.State.INITIALIZING) >= 0) {
            Log.d(MiBandSupport.TAG, "Services discovered, but device state is already " + getDevice().getState() + " for device: " + getDevice() + ", so ignoring");
            return;
        }
        initializeDevice(createTransactionBuilder("Initializing device")).queue(getQueue());
    }

    public TransactionBuilder createTransactionBuilder(String taskName) {
        return new TransactionBuilder(taskName);
    }

    protected Set<UUID> getSupportedServices() {
        return mSupportedServices;
    }

    private void gattServicesDiscovered(List<BluetoothGattService> discoveredGattServices) {
        if (discoveredGattServices == null) {
            Log.d(MiBandSupport.TAG, "No gatt services discovered: null!");
            return;
        }

        //TODO Take a look. Probably there is some error with supported services
        Set<UUID> supportedServices = getSupportedServices();
        Map<UUID, BluetoothGattCharacteristic> newCharacteristics = new HashMap<>();
        for (BluetoothGattService service : discoveredGattServices) {
            //if (supportedServices.contains(service.getUuid())) {
                Log.d(MiBandSupport.TAG, "discovered supported service: " + BleNamesResolver.resolveServiceName(service.getUuid().toString()) + ": " + service.getUuid());
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.d(MiBandSupport.TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                HashMap<UUID, BluetoothGattCharacteristic> intmAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    intmAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                    Log.d(MiBandSupport.TAG, "    characteristic: " + BleNamesResolver.resolveCharacteristicName(characteristic.getUuid().toString()) + ": " + characteristic.getUuid());
                }
                newCharacteristics.putAll(intmAvailableCharacteristics);

                synchronized (characteristicsMonitor) {
                    mAvailableCharacteristics = newCharacteristics;
                }
           // } else {
           //     Log.d(MiBandSupport.TAG, "discovered unsupported service: " + BleNamesResolver.resolveServiceName(service.getUuid().toString()) + ": " + service.getUuid());
           // }
        }
    }

    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        enableNotifications(builder, true);

        builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.AUTHENTICATING, getContext()));
        // write key to device
        byte[] sendKey = org.apache.commons.lang3.ArrayUtils.addAll(new byte[]{MiBandService.AUTH_SEND_KEY, MiBandService.AUTH_BYTE}, getSecretKey());
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUTH), sendKey);

        return builder;
    }

    private byte[] getSecretKey() {
        byte[] authKeyBytes = new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45};

        return authKeyBytes;
    }

    private MiBandSupport enableNotifications(TransactionBuilder builder, boolean enable) {
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_NOTIFICATION), enable);
        return this;
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.INITIALIZED, getContext()));
    }

    public MiBandSupport setHighLatency(TransactionBuilder builder) {
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS), getHighLatency());
        return this;
    }

    private byte[] getHighLatency() {
        int minConnectionInterval = 460;
        int maxConnectionInterval = 500;
        int latency = 0;
        int timeout = 500;
        int advertisementInterval = 0;

        return getLatency(minConnectionInterval, maxConnectionInterval, latency, timeout, advertisementInterval);
    }

    private MiBandSupport pair(TransactionBuilder transaction) {
        Log.d(MiBandSupport.TAG, "Attempting to pair MI device...");
        BluetoothGattCharacteristic characteristic = getCharacteristic(MiBandService.UUID_CHARACTERISTIC_PAIR);
        if (characteristic != null) {
            transaction.write(characteristic, new byte[]{2});
        } else {
            Log.d(MiBandSupport.TAG, "Unable to pair MI device -- characteristic not available");
        }

        return this;
    }


    private MiBandSupport readDate(TransactionBuilder builder) {
        builder.read(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_DATE_TIME));
        return this;
    }

    public MiBandSupport setLowLatency(TransactionBuilder builder) {
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_LE_PARAMS), getLowLatency());
        return this;
    }

    private byte[] getLowLatency() {
        int minConnectionInterval = 39;
        int maxConnectionInterval = 49;
        int latency = 0;
        int timeout = 500;
        int advertisementInterval = 0;

        return getLatency(minConnectionInterval, maxConnectionInterval, latency, timeout, advertisementInterval);
    }

    private byte[] getLatency(int minConnectionInterval, int maxConnectionInterval, int latency, int timeout, int advertisementInterval) {
        byte result[] = new byte[12];
        result[0] = (byte) (minConnectionInterval & 0xff);
        result[1] = (byte) (0xff & minConnectionInterval >> 8);
        result[2] = (byte) (maxConnectionInterval & 0xff);
        result[3] = (byte) (0xff & maxConnectionInterval >> 8);
        result[4] = (byte) (latency & 0xff);
        result[5] = (byte) (0xff & latency >> 8);
        result[6] = (byte) (timeout & 0xff);
        result[7] = (byte) (0xff & timeout >> 8);
        result[8] = 0;
        result[9] = 0;
        result[10] = (byte) (advertisementInterval & 0xff);
        result[11] = (byte) (0xff & advertisementInterval >> 8);

        return result;
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        synchronized (characteristicsMonitor) {
            if (mAvailableCharacteristics == null) {
                return null;
            }
            BluetoothGattCharacteristic retCharacteristic = mAvailableCharacteristics.get(uuid);
            return retCharacteristic;
        }
    }

    public BluetoothQueue getQueue() {
        return mQueue;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBtAdapter;
    }

    protected void addSupportedService(UUID aSupportedService) {
        mSupportedServices.add(aSupportedService);
    }

    public MiBandDevice getDevice(){
        return mDevice;
    }

    public Context getContext(){
        return mContext;
    }

}
