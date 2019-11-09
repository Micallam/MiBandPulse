package com.example.miband.Bluetooth.Gatt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.example.miband.Bluetooth.Actions.NotifyAction;
import com.example.miband.Bluetooth.Actions.SetDeviceStateAction;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.Device.MiBandSupport;
import com.example.miband.Bluetooth.TransactionBuilder;
import com.example.miband.Utils.BleNamesResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GattCallback extends BluetoothGattCallback {

    MiBandDevice mDevice;
    MiBandSupport mSupport;
    public static String TAG = "MiBand: GattCallback";

    private Map<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics;
    private final Set<UUID> mSupportedServices = new HashSet<>(4);
    private final Object characteristicsMonitor = new Object();

    public MiBandDevice getDevice(){
        return mDevice;
    }

    public GattCallback(MiBandDevice device){
        Log.d(GattCallback.TAG, "Created GattCallback");
        mDevice = device;
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(GattCallback.TAG, "On services discovered");
        gattServicesDiscovered(gatt.getServices());

        if (getDevice().getState().compareTo(MiBandDevice.State.INITIALIZING) >= 0) {
            Log.d(GattCallback.TAG, "Services discovered, but device state is already " + getDevice().getState() + " for device: " + getDevice() + ", so ignoring");
            return;
        }
        initializeDevice(createTransactionBuilder("Initializing device")).executeInThread(getSupport());
    }

    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.add(new NotifyAction(getCharacteristic(UUID.fromString("00000009-0000-3512-2118-0009af100700")), true));
        builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.AUTHENTICATING, getContext()));

        byte[] sendKey = org.apache.commons.lang3.ArrayUtils.addAll(new byte[]{0x01, 0}, getSecretKey());
        builder.write(getCharacteristic(UUID.fromString("00000009-0000-3512-2118-0009af100700")), sendKey);

        builder.setGattCallback(this);
        return builder;
    }

    private byte[] getSecretKey() {
        byte[] authKeyBytes = new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45};

        return authKeyBytes;
    }

    private Context getContext(){
        return mSupport.getContext();
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        synchronized (characteristicsMonitor) {
            if (mAvailableCharacteristics == null) {
                return null;
            }
            return mAvailableCharacteristics.get(uuid);
        }
    }

    public TransactionBuilder createTransactionBuilder(String taskName) {
        return new TransactionBuilder(taskName);
    }

    public void setSupport(MiBandSupport support){
        mSupport = support;
    }

    public MiBandSupport getSupport(){
        return mSupport;
    }

    private void gattServicesDiscovered(List<BluetoothGattService> discoveredGattServices) {
        if (discoveredGattServices == null) {
            Log.d(GattCallback.TAG, "No gatt services discovered: null!");
            return;
        }
        Set<UUID> supportedServices = getSupportedServices();
        Map<UUID, BluetoothGattCharacteristic> newCharacteristics = new HashMap<>();
        for (BluetoothGattService service : discoveredGattServices) {
           // if (supportedServices.contains(service.getUuid())) {
                Log.d(GattCallback.TAG, "discovered supported service: " + BleNamesResolver.resolveServiceName(service.getUuid().toString()) + ": " + service.getUuid());
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.d(GattCallback.TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                HashMap<UUID, BluetoothGattCharacteristic> intmAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    intmAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                    Log.d(GattCallback.TAG, "    characteristic: " + BleNamesResolver.resolveCharacteristicName(characteristic.getUuid().toString()) + ": " + characteristic.getUuid());
                }
                newCharacteristics.putAll(intmAvailableCharacteristics);

                synchronized (characteristicsMonitor) {
                    mAvailableCharacteristics = newCharacteristics;
                }
           // } else {
           //     Log.d(GattCallback.TAG, "discovered unsupported service: " + BleNamesResolver.resolveServiceName(service.getUuid().toString()) + ": " + service.getUuid());
           // }
        }
    }

    protected Set<UUID> getSupportedServices() {
        return mSupportedServices;
    }
}
