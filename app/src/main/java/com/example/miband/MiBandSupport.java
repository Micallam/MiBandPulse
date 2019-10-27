package com.example.miband;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

public class MiBandSupport {
    MiBandDevice mDevice;
    boolean mAutoReconnect;

    private final Object mGattMonitor = new Object();
    private final BluetoothAdapter mBluetoothAdapter;
    private final Context mContext;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattServer mBluetoothGattServer;
    private final InternalGattCallback internalGattCallback;


    public MiBandSupport(MiBandDevice device, Context context, BluetoothGattCallback bluetoothGattCallback){
        mDevice = device;
        mContext = context;
        mAutoReconnect = true;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        internalGattCallback = new InternalGattCallback(bluetoothGattCallback);
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

    public void disconnect(){

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean connect(){
        if (mDevice.isConnected()) {
            Log.d(MainActivity.TAG, "Ingoring connect() because already connected.");
            return false;
        }
        synchronized (mGattMonitor) {
            if (mBluetoothGatt != null) {
                // Tribal knowledge says you're better off not reusing existing BluetoothGatt connections,
                // so create a new one.
                Log.d(MainActivity.TAG, "connect() requested -- disconnecting previous connection: " + mDevice.getName());
                disconnect();
            }
        }
        Log.d(MainActivity.TAG, "Attempting to connect to " + mDevice.getName());
        mBluetoothAdapter.cancelDiscovery();
        BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mDevice.getAddress());

        synchronized (mGattMonitor) {
            // connectGatt with true doesn't really work ;( too often connection problems

            mBluetoothGatt = remoteDevice.connectGatt(mContext, false, internalGattCallback, BluetoothDevice.TRANSPORT_LE);

        }
        boolean result = mBluetoothGatt != null;
        if (result) {
            setDeviceConnectionState(MiBandDevice.State.CONNECTING);
        }
        return result;

    }

    public void setDeviceConnectionState(MiBandDevice.State newState){
        Log.d(MainActivity.TAG, "new device connection state: " + newState);

        mDevice.setState(newState);
        mDevice.sendDeviceUpdateIntent(mContext);
    }

    public boolean dispose(){
        return true;
    }

    public MiBandDevice getDevice(){
        return mDevice;
    }

    public void setAutoReconnect(boolean autoReconnect){
        mAutoReconnect = autoReconnect;
    }

    private boolean checkCorrectGattInstance(BluetoothGatt gatt, String where) {
        if (gatt != mBluetoothGatt && mBluetoothGatt != null) {
            Log.d(MainActivity.TAG, "Ignoring event from wrong BluetoothGatt instance: " + where + "; " + gatt);
            return false;
        }
        return true;
    }

    private void handleDisconnected(int status) {
        Log.d(MainActivity.TAG, "handleDisconnected: " + status);
        internalGattCallback.reset();
        //mAbortTransaction = true;
        //mAbortServerTransaction = true;

        boolean wasInitialized = mDevice.isInitialized();

        setDeviceConnectionState(MiBandDevice.State.NOT_CONNECTED);

        if (mBluetoothGatt != null) {
            if (!wasInitialized || !maybeReconnect()) {
                disconnect(); // ensure that we start over cleanly next time
            }
        }
    }


    private boolean maybeReconnect() {
        if (mAutoReconnect && mBluetoothGatt != null) {
            Log.d(MainActivity.TAG, "Enabling automatic ble reconnect...");
            boolean result = mBluetoothGatt.connect();
            if (result) {
                setDeviceConnectionState(MiBandDevice.State.WAITING_FOR_RECONNECT);
            }
            return result;
        }
        return false;
    }

    private final class InternalGattCallback extends BluetoothGattCallback {
        private
        @Nullable
        BluetoothGattCallback mTransactionGattCallback;
        private final BluetoothGattCallback mExternalGattCallback;

        public InternalGattCallback(BluetoothGattCallback externalGattCallback) {
            mExternalGattCallback = externalGattCallback;
        }

        public void setTransactionGattCallback(@Nullable BluetoothGattCallback callback) {
            mTransactionGattCallback = callback;
        }

        private BluetoothGattCallback getCallbackToUse() {
            if (mTransactionGattCallback != null) {
                return mTransactionGattCallback;
            }
            return mExternalGattCallback;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(MainActivity.TAG, "connection state change, newState: " + newState + getStatusString(status));

            synchronized (mGattMonitor) {
                if (mBluetoothGatt == null) {
                    mBluetoothGatt = gatt;
                }
            }

            if (!checkCorrectGattInstance(gatt, "connection state event")) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(MainActivity.TAG, "connection state event with error status " + status);
            }

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(MainActivity.TAG, "Connected to GATT server.");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTED);
                    // Attempts to discover services after successful connection.
                    List<BluetoothGattService> cachedServices = gatt.getServices();
                    if (cachedServices != null && cachedServices.size() > 0) {
                        Log.d(MainActivity.TAG, "Using cached services, skipping discovery");
                        onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS);
                    } else {
                        Log.d(MainActivity.TAG, "Attempting to start service discovery");
                        // discover services in the main thread (appears to fix Samsung connection problems)
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (mBluetoothGatt != null) {
                                    mBluetoothGatt.discoverServices();
                                }
                            }
                        });
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(MainActivity.TAG, "Disconnected from GATT server.");
                    handleDisconnected(status);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    Log.d(MainActivity.TAG, "Connecting to GATT server...");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTING);
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (!checkCorrectGattInstance(gatt, "services discovered: " + getStatusString(status))) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (getCallbackToUse() != null) {
                    // only propagate the successful event
                    getCallbackToUse().onServicesDiscovered(gatt, status);
                }
            } else {
                Log.d(MainActivity.TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(MainActivity.TAG, "characteristic write: " + characteristic.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "characteristic write")) {
                return;
            }
            if (getCallbackToUse() != null) {
                getCallbackToUse().onCharacteristicWrite(gatt, characteristic, status);
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(MainActivity.TAG, "characteristic read: " + characteristic.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "characteristic read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicRead(gatt, characteristic, status);
                } catch (Throwable ex) {
                    Log.d(MainActivity.TAG, "onCharacteristicRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(MainActivity.TAG, "descriptor read: " + descriptor.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "descriptor read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorRead(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(MainActivity.TAG, "onDescriptorRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(MainActivity.TAG, "descriptor write: " + descriptor.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "descriptor write")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorWrite(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(MainActivity.TAG, "onDescriptorWrite: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(MainActivity.TAG, "characteristic changed: " + characteristic.getUuid());

            if (!checkCorrectGattInstance(gatt, "characteristic changed")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicChanged(gatt, characteristic);
                } catch (Throwable ex) {
                    Log.d(MainActivity.TAG, "onCharaceristicChanged: " + ex.getMessage(), ex);
                }
            } else {
                Log.d(MainActivity.TAG, "No gattcallback registered, ignoring characteristic change");
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(MainActivity.TAG, "remote rssi: " + rssi + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "remote rssi")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onReadRemoteRssi(gatt, rssi, status);
                } catch (Throwable ex) {
                    Log.d(MainActivity.TAG, "onReadRemoteRssi: " + ex.getMessage(), ex);
                }
            }
        }

        private void checkWaitingCharacteristic(BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (characteristic != null) {
                    Log.d(MainActivity.TAG, "failed btle action, aborting transaction: " + characteristic.getUuid() + getStatusString(status));
                }
              //  mAbortTransaction = true;
            }
         /*   if (characteristic != null && BtLEQueue.this.mWaitCharacteristic != null && characteristic.getUuid().equals(BtLEQueue.this.mWaitCharacteristic.getUuid())) {
                if (mWaitForActionResultLatch != null) {
                    mWaitForActionResultLatch.countDown();
                }
            } else {
                if (BtLEQueue.this.mWaitCharacteristic != null) {
                    Log.d(MainActivity.TAG, "checkWaitingCharacteristic: mismatched characteristic received: " + ((characteristic != null && characteristic.getUuid() != null) ? characteristic.getUuid().toString() : "(null)"));
                }
            }*/
        }

        private String getStatusString(int status) {
            return status == BluetoothGatt.GATT_SUCCESS ? " (success)" : " (failed: " + status + ")";
        }

        public void reset() {
            Log.d(MainActivity.TAG, "internal gatt callback set to null");

            mTransactionGattCallback = null;
        }
    }

}
