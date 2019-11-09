package com.example.miband.Device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.miband.Bluetooth.Actions.BtLEAction;
import com.example.miband.Bluetooth.Gatt.GattCallback;
import com.example.miband.Bluetooth.Transaction;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MiBandSupport {
    public static String TAG = "MiBand: MiBandSupport";

    MiBandDevice mDevice;
    boolean mAutoReconnect;

    private final Object mGattMonitor = new Object();
    private final BluetoothAdapter mBluetoothAdapter;
    private final Context mContext;
    private BluetoothGatt mBluetoothGatt;
    private final InternalGattCallback internalGattCallback;
    private GattCallback mGattCallback;

    private BluetoothGattCharacteristic mWaitCharacteristic;

    private volatile boolean mDisposed;
    private volatile boolean mCrashed;
    private volatile boolean mAbortTransaction;

    private CountDownLatch mConnectionLatch;
    private CountDownLatch mWaitForActionResultLatch;

    Transaction mTransaction;



    private Thread dispatchThread = new Thread("Gadgetbridge GATT Dispatcher") {

        @Override
        public void run() {
            Log.d(MiBandSupport.TAG, "Queue Dispatch Thread started.");

            while (!mDisposed && !mCrashed) {
                try {
                    Transaction qTransaction = mTransaction;

                    if (!mDevice.isConnected()) {
                        Log.d(MiBandSupport.TAG, "not connected, waiting for connection...");
                        // TODO: request connection and initialization from the outside and wait until finished
                        internalGattCallback.reset();

                        // wait until the connection succeeds before running the actions
                        // Note that no automatic connection is performed. This has to be triggered
                        // on the outside typically by the DeviceSupport. The reason is that
                        // devices have different kinds of initializations and this class has no
                        // idea about them.
                        mConnectionLatch = new CountDownLatch(1);
                        mConnectionLatch.await();
                        mConnectionLatch = null;
                    }

                    if(qTransaction instanceof Transaction) {
                        Transaction transaction = (Transaction)qTransaction;
                        internalGattCallback.setTransactionGattCallback(transaction.getGattCallback());
                        mAbortTransaction = false;
                        // Run all actions of the transaction until one doesn't succeed
                        for (BtLEAction action : transaction.getActions()) {
                            if (mAbortTransaction) { // got disconnected
                                Log.d(MiBandSupport.TAG, "Aborting running transaction");
                                break;
                            }
                            mWaitCharacteristic = action.getCharacteristic();

                            if (mWaitCharacteristic != null){

                                mWaitForActionResultLatch = new CountDownLatch(1);

                                Log.d(MiBandSupport.TAG, "About to run action: " + action);

                                if (action.run(mBluetoothGatt)) {
                                    // check again, maybe due to some condition, action did not need to write, so we can't wait
                                    boolean waitForResult = action.expectsResult();
                                    if (waitForResult) {
                                        mWaitForActionResultLatch.await();
                                        mWaitForActionResultLatch = null;
                                        if (mAbortTransaction) {
                                            break;
                                        }
                                    }
                                } else {
                                    Log.d(MiBandSupport.TAG, "Action returned false: " + action);
                                    break; // abort the transaction
                                }
                            }
                        }
                    }
                } catch (InterruptedException ignored) {
                    mConnectionLatch = null;
                    Log.d(MiBandSupport.TAG, "Thread interrupted");
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "Queue Dispatch Thread died: " + ex.getMessage(), ex);
                    mCrashed = true;
                    mConnectionLatch = null;
                } finally {
                    mWaitForActionResultLatch = null;
                    mWaitCharacteristic = null;
                }
            }
            Log.d(MiBandSupport.TAG, "Queue Dispatch Thread terminated.");
        }
    };

    public void runDispatchThread(Transaction transaction){
        mTransaction = transaction;

        dispatchThread.start();
    //TODO: Back in here
     /*   try {
            dispatchThread.join();
            dispose();
        }
        catch (InterruptedException ignored) {
            Log.d(MiBandSupport.TAG, "Disposed");
        }*/
    }

    public MiBandSupport(MiBandDevice device, Context context, BluetoothGattCallback bluetoothGattCallback, GattCallback gattCallback){
        mDevice = device;
        mContext = context;
        mAutoReconnect = true;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        internalGattCallback = new InternalGattCallback(bluetoothGattCallback);

        mGattCallback = gattCallback;
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
        synchronized (mGattMonitor) {
            Log.d(MiBandSupport.TAG, "disconnect()");
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                mBluetoothGatt = null;
                Log.d(MiBandSupport.TAG, "Disconnecting BtLEQueue from GATT device");
                gatt.disconnect();
                gatt.close();
                setDeviceConnectionState(MiBandDevice.State.NOT_CONNECTED);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean connect(){
        if (mDevice.isConnected()) {
            Log.d(MiBandSupport.TAG, "Ingoring connect() because already connected.");
            return false;
        }
        synchronized (mGattMonitor) {
            if (mBluetoothGatt != null) {
                // Tribal knowledge says you're better off not reusing existing BluetoothGatt connections,
                // so create a new one.
                Log.d(MiBandSupport.TAG, "connect() requested -- disconnecting previous connection: " + mDevice.getName());
                disconnect();
            }
        }
        Log.d(MiBandSupport.TAG, "Attempting to connect to " + mDevice.getName());
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
        Log.d(MiBandSupport.TAG, "new device connection state: " + newState);

        mDevice.setState(newState);
        mDevice.sendDeviceUpdateIntent(mContext);
    }

    public void dispose(){
        mDisposed = true;
        disconnect();
        dispatchThread.interrupt();
        dispatchThread = null;
    }

    public MiBandDevice getDevice(){
        return mDevice;
    }

    public void setAutoReconnect(boolean autoReconnect){
        mAutoReconnect = autoReconnect;
    }

    private boolean checkCorrectGattInstance(BluetoothGatt gatt, String where) {
        if (gatt != mBluetoothGatt && mBluetoothGatt != null) {
            Log.d(MiBandSupport.TAG, "Ignoring event from wrong BluetoothGatt instance: " + where + "; " + gatt);
            return false;
        }
        return true;
    }

    private void handleDisconnected(int status) {
        Log.d(MiBandSupport.TAG, "handleDisconnected: " + status);
        internalGattCallback.reset();
        //mAbortTransaction = true;
        //mAbortServerTransaction = true;

        boolean wasInitialized = mDevice.isInitialized();

        setDeviceConnectionState(MiBandDevice.State.NOT_CONNECTED);

        if (mBluetoothGatt != null) {
            //if (!wasInitialized || !maybeReconnect()) {
            if (!maybeReconnect()) {
                disconnect(); // ensure that we start over cleanly next time
            }
        }
    }


    private boolean maybeReconnect() {
        if (mAutoReconnect && mBluetoothGatt != null) {
            Log.d(MiBandSupport.TAG, "Enabling automatic ble reconnect...");
            boolean result = mBluetoothGatt.connect();
            if (result) {
                mCrashed = false;
                setDeviceConnectionState(MiBandDevice.State.WAITING_FOR_RECONNECT);
            }
            return result;
        }
        return false;
    }

    public Context getContext(){
        return mContext;
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
            if (mGattCallback != null) {
                return mGattCallback;
            }
            return mExternalGattCallback;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(MiBandSupport.TAG, "connection state change, newState: " + newState + getStatusString(status));

            synchronized (mGattMonitor) {
                if (mBluetoothGatt == null) {
                    mBluetoothGatt = gatt;
                }
            }

            if (!checkCorrectGattInstance(gatt, "connection state event")) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(MiBandSupport.TAG, "connection state event with error status " + status);
            }

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(MiBandSupport.TAG, "Connected to GATT server.");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTED);
                    // Attempts to discover services after successful connection.
                    List<BluetoothGattService> cachedServices = gatt.getServices();
                    if (cachedServices != null && cachedServices.size() > 0) {
                        Log.d(MiBandSupport.TAG, "Using cached services, skipping discovery");
                        onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS);
                    } else {
                        Log.d(MiBandSupport.TAG, "Attempting to start service discovery");
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
                    Log.d(MiBandSupport.TAG, "Disconnected from GATT server.");
                    handleDisconnected(status);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    Log.d(MiBandSupport.TAG, "Connecting to GATT server...");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTING);
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(MiBandSupport.TAG, "On services discovered...");
            if (!checkCorrectGattInstance(gatt, "services discovered: " + getStatusString(status))) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (getCallbackToUse() != null) {
                    // only propagate the successful event
                    getCallbackToUse().onServicesDiscovered(gatt, status);
                }
            } else {
                Log.d(MiBandSupport.TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(MiBandSupport.TAG, "characteristic write: " + characteristic.getUuid() + getStatusString(status));
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
            Log.d(MiBandSupport.TAG, "characteristic read: " + characteristic.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "characteristic read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicRead(gatt, characteristic, status);
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "onCharacteristicRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(MiBandSupport.TAG, "descriptor read: " + descriptor.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "descriptor read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorRead(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "onDescriptorRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(MiBandSupport.TAG, "descriptor write: " + descriptor.getUuid() + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "descriptor write")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorWrite(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "onDescriptorWrite: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(MiBandSupport.TAG, "characteristic changed: " + characteristic.getUuid());

            if (!checkCorrectGattInstance(gatt, "characteristic changed")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicChanged(gatt, characteristic);
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "onCharaceristicChanged: " + ex.getMessage(), ex);
                }
            } else {
                Log.d(MiBandSupport.TAG, "No gattcallback registered, ignoring characteristic change");
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(MiBandSupport.TAG, "remote rssi: " + rssi + getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "remote rssi")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onReadRemoteRssi(gatt, rssi, status);
                } catch (Throwable ex) {
                    Log.d(MiBandSupport.TAG, "onReadRemoteRssi: " + ex.getMessage(), ex);
                }
            }
        }

        private void checkWaitingCharacteristic(BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (characteristic != null) {
                    Log.d(MiBandSupport.TAG, "failed btle action, aborting transaction: " + characteristic.getUuid() + getStatusString(status));
                }
                mAbortTransaction = true;
            }
            if (characteristic != null && mWaitCharacteristic != null && characteristic.getUuid().equals(mWaitCharacteristic.getUuid())) {
                if (mWaitForActionResultLatch != null) {
                    mWaitForActionResultLatch.countDown();
                }
            } else {
                if (mWaitCharacteristic != null) {
                    Log.d(MiBandSupport.TAG, "checkWaitingCharacteristic: mismatched characteristic received: " + ((characteristic != null && characteristic.getUuid() != null) ? characteristic.getUuid().toString() : "(null)"));
                }
            }
        }

        private String getStatusString(int status) {
            return status == BluetoothGatt.GATT_SUCCESS ? " (success)" : " (failed: " + status + ")";
        }

        public void reset() {
            Log.d(MiBandSupport.TAG, "internal gatt callback set to null");

            mTransactionGattCallback = null;
        }
    }

}
