package com.example.miband.Device;

import android.annotation.SuppressLint;
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
import com.example.miband.Bluetooth.TransactionBuilder;
import com.example.miband.Utils.AndroidUtils;
import com.example.miband.Utils.BleNamesResolver;
import com.example.miband.Utils.CalendarUtils;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class MiBandSupport extends BluetoothGattCallback {
    public static String TAG = "MiBand: MiBandSupport";

    private BluetoothQueue mQueue;
    private MiBandDevice mDevice;

    private Map<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics;

    private final Object characteristicsMonitor = new Object();

    private BluetoothAdapter mBtAdapter;
    private Context mContext;

    void setContext(MiBandDevice device, BluetoothAdapter btAdapter, Context context) {
        mDevice = device;
        mBtAdapter = btAdapter;
        mContext = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public TransactionBuilder performInitialized() throws IOException {
        if (!mDevice.isConnected()) {
            if (!connect()) {
                throw new IOException("1: Unable to connect to device: " + getDevice());
            }
        }

        return createTransactionBuilder();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    boolean connect() {
        if (mQueue == null) {
            mQueue = new BluetoothQueue(getBluetoothAdapter(), getDevice(), this, getContext());
        }
        return mQueue.connect();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void connectFirstTime() {
        for (int i = 0; i < 5; i++) {
            if (connect()) {
                return;
            }
        }
    }

    void dispose() {
        Log.d(MiBandSupport.TAG, "Dispose");
    }

    private byte[] requestAuthNumber() {
        return new byte[]{MiBandService.AUTH_REQUEST_RANDOM_AUTH_NUMBER, MiBandService.AUTH_BYTE};
    }

    private void performImmediately(TransactionBuilder builder) throws IOException {
        if (!mDevice.isConnected()) {
            throw new IOException("Not connected to device: " + getDevice());
        }
        getQueue().insert(builder.getTransaction());
    }


    private byte[] getTimeBytes(Calendar calendar) {
        byte[] bytes;
        bytes = CalendarUtils.calendarToRawBytes(calendar);
        byte[] tail = new byte[] { 0, CalendarUtils.mapTimeZone(calendar.getTimeZone()) };

        return CalendarUtils.join(bytes, tail);
    }


    private void setCurrentTimeWithService(TransactionBuilder builder) {
        GregorianCalendar now = new GregorianCalendar();
        byte[] bytes = getTimeBytes(now);
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_CURRENT_TIME), bytes);
    }

    private void enableFurtherNotifications(TransactionBuilder builder) {
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_3_CONFIGURATION), true);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_6_BATTERY_INFO), true);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_DEVICEEVENT), true);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUDIO), true);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUDIODATA), true);

    }

    public void onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        UUID characteristicUUID = characteristic.getUuid();
        if (MiBandService.UUID_CHARACTERISTIC_AUTH.equals(characteristicUUID)) {
            try {
                byte[] value = characteristic.getValue();

                if (value[0] == MiBandService.AUTH_RESPONSE &&
                        value[1] == MiBandService.AUTH_SEND_KEY &&
                        value[2] == MiBandService.AUTH_SUCCESS) {
                    TransactionBuilder builder = createTransactionBuilder();
                    builder.write(characteristic, requestAuthNumber());
                    performImmediately(builder);
                } else if (value[0] == MiBandService.AUTH_RESPONSE &&
                        (value[1] & 0x0f) == MiBandService.AUTH_REQUEST_RANDOM_AUTH_NUMBER &&
                        value[2] == MiBandService.AUTH_SUCCESS) {
                    byte[] eValue = handleAESAuth(value, getSecretKey());
                    byte[] responseValue = org.apache.commons.lang3.ArrayUtils.addAll(
                            new byte[]{(byte) (MiBandService.AUTH_SEND_ENCRYPTED_AUTH_NUMBER | MiBandService.CRYPT_FLAGS), MiBandService.AUTH_BYTE}, eValue);

                    TransactionBuilder builder = createTransactionBuilder();
                    builder.write(characteristic, responseValue);
                    setCurrentTimeWithService(builder);
                    performImmediately(builder);
                } else if (value[0] == MiBandService.AUTH_RESPONSE &&
                        (value[1] & 0x0f) == MiBandService.AUTH_SEND_ENCRYPTED_AUTH_NUMBER &&
                        value[2] == MiBandService.AUTH_SUCCESS) {
                    TransactionBuilder builder = createTransactionBuilder();
                    builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.INITIALIZING, getContext()));
                    enableFurtherNotifications(builder);
                    setInitialized(builder);
                    performImmediately(builder);
                }
            } catch (Exception e) {
                AndroidUtils.toast(getContext(), "Error authenticating device", Toast.LENGTH_LONG);
            }
        }
        else {
            Log.d(MiBandSupport.TAG, "Unhandled characteristic changed: " + characteristicUUID);
        }
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        gattServicesDiscovered(gatt.getServices());

        if (getDevice().getState().compareTo(MiBandDevice.State.INITIALIZING) >= 0) {
            Log.d(MiBandSupport.TAG, "Services discovered, but device state is already " + getDevice().getState() + " for device: " + getDevice() + ", so ignoring");
            return;
        }
        initializeDevice(createTransactionBuilder()).queue(getQueue());
    }

    private TransactionBuilder createTransactionBuilder() {
        return new TransactionBuilder();
    }

    private void gattServicesDiscovered(List<BluetoothGattService> discoveredGattServices) {
        if (discoveredGattServices == null) {
            Log.d(MiBandSupport.TAG, "No gatt services discovered: null!");
            return;
        }

        Map<UUID, BluetoothGattCharacteristic> newCharacteristics = new HashMap<>();
        for (BluetoothGattService service : discoveredGattServices) {
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
        }
    }


    private TransactionBuilder initializeDevice(TransactionBuilder builder) {
        enableNotifications(builder);

        builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.AUTHENTICATING, getContext()));

        // authentication key to write to device
        byte[] sendKey =
                org.apache.commons.lang3.ArrayUtils.addAll(
                        new byte[]{MiBandService.AUTH_SEND_KEY, MiBandService.AUTH_BYTE},
                        getSecretKey());

        //UUID_CHARACTERISTIC_AUTH: 00000009-0000-3512-2118-0009af100700
        builder.write(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUTH), sendKey);

        return builder;
    }

    private byte[] getSecretKey() {
        return new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45};
    }

    private void enableNotifications(TransactionBuilder builder) {
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_NOTIFICATION), true);
        builder.notify(getCharacteristic(MiBandService.UUID_SERVICE_CURRENT_TIME), true);
        // Notify CHARACTERISTIC9 to receive random auth code
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUTH), true);
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.INITIALIZED, getContext()));
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        synchronized (characteristicsMonitor) {
            if (mAvailableCharacteristics == null) {
                return null;
            }
            return mAvailableCharacteristics.get(uuid);
        }
    }

    public BluetoothQueue getQueue() {
        return mQueue;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        return mBtAdapter;
    }

    public MiBandDevice getDevice(){
        return mDevice;
    }

    public Context getContext(){
        return mContext;
    }

    private byte[] handleAESAuth(byte[] value, byte[] secretKey) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException {
        byte[] mValue = Arrays.copyOfRange(value, 3, 19);
        @SuppressLint("GetInstance") Cipher ecipher = Cipher.getInstance("AES/ECB/NoPadding");
        SecretKeySpec newKey = new SecretKeySpec(secretKey, "AES");
        ecipher.init(Cipher.ENCRYPT_MODE, newKey);
        return ecipher.doFinal(mValue);
    }
}
