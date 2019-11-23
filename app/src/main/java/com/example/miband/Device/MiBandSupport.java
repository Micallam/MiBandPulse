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
import com.example.miband.Bluetooth.Gatt.GattCharacteristic;
import com.example.miband.Bluetooth.Gatt.GattService;
import com.example.miband.Bluetooth.TransactionBuilder;
import com.example.miband.Utils.AndroidUtils;
import com.example.miband.Utils.BleNamesResolver;
import com.example.miband.Utils.CalendarUtils;

import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

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
    public TransactionBuilder performInitialized(String taskName) throws IOException {
        if (!mDevice.isConnected()) {
            if (!connect()) {
                throw new IOException("1: Unable to connect to device: " + getDevice());
            }
        }
        if (!mDevice.isInitialized()) {
            //TODO handle uninitialized state

            /*TransactionBuilder builder = createTransactionBuilder("Initialize device");
            builder.add(new CheckInitializedAction(gbDevice));
            initializeDevice(builder).queue(getQueue());*/
        }
        return createTransactionBuilder(taskName);
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

    private byte[] requestAuthNumber() {
        return new byte[]{MiBandService.AUTH_REQUEST_RANDOM_AUTH_NUMBER, MiBandService.AUTH_BYTE};
    }

    public void performImmediately(TransactionBuilder builder) throws IOException {
        if (!mDevice.isConnected()) {
            throw new IOException("Not connected to device: " + getDevice());
        }
        getQueue().insert(builder.getTransaction());
    }


    public byte[] getTimeBytes(Calendar calendar, TimeUnit precision) {
        byte[] bytes;
        if (precision == TimeUnit.MINUTES) {
            bytes = CalendarUtils.shortCalendarToRawBytes(calendar);
        } else if (precision == TimeUnit.SECONDS) {
            bytes = CalendarUtils.calendarToRawBytes(calendar);
        } else {
            throw new IllegalArgumentException("Unsupported precision, only MINUTES and SECONDS are supported till now");
        }
        byte[] tail = new byte[] { 0, CalendarUtils.mapTimeZone(calendar.getTimeZone(), 1) };
        // 0 = adjust reason bitflags? or DST offset?? , timezone
//        byte[] tail = new byte[] { 0x2 }; // reason
        byte[] all = CalendarUtils.join(bytes, tail);
        return all;
    }


    public MiBandSupport setCurrentTimeWithService(TransactionBuilder builder) {
        GregorianCalendar now = new GregorianCalendar();
        byte[] bytes = getTimeBytes(now, TimeUnit.SECONDS);
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME), bytes);
        return this;
    }

    public MiBandSupport enableFurtherNotifications(TransactionBuilder builder, boolean enable) {
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_3_CONFIGURATION), enable);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_6_BATTERY_INFO), enable);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_DEVICEEVENT), enable);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUDIO), enable);
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUDIODATA), enable);

        return this;
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
                    TransactionBuilder builder = createTransactionBuilder("Sending the secret key to the device");
                    builder.write(characteristic, requestAuthNumber());
                    performImmediately(builder);
                } else if (value[0] == MiBandService.AUTH_RESPONSE &&
                        (value[1] & 0x0f) == MiBandService.AUTH_REQUEST_RANDOM_AUTH_NUMBER &&
                        value[2] == MiBandService.AUTH_SUCCESS) {
                    byte[] eValue = handleAESAuth(value, getSecretKey());
                    byte[] responseValue = org.apache.commons.lang3.ArrayUtils.addAll(
                            new byte[]{(byte) (MiBandService.AUTH_SEND_ENCRYPTED_AUTH_NUMBER | MiBandService.CRYPT_FLAGS), MiBandService.AUTH_BYTE}, eValue);

                    TransactionBuilder builder = createTransactionBuilder("Sending the encrypted random key to the device");
                    builder.write(characteristic, responseValue);
                    setCurrentTimeWithService(builder);
                    performImmediately(builder);
                } else if (value[0] == MiBandService.AUTH_RESPONSE &&
                        (value[1] & 0x0f) == MiBandService.AUTH_SEND_ENCRYPTED_AUTH_NUMBER &&
                        value[2] == MiBandService.AUTH_SUCCESS) {
                    TransactionBuilder builder = createTransactionBuilder("Authenticated, now initialize phase 2");
                    builder.add(new SetDeviceStateAction(getDevice(), MiBandDevice.State.INITIALIZING, getContext()));
                    enableFurtherNotifications(builder, true);
                    //phase2Initialize(builder);
                    //phase3Initialize(builder);
                    setInitialized(builder);
                    performImmediately(builder);
                }
            } catch (Exception e) {
                AndroidUtils.toast(getContext(), "Error authenticating device", Toast.LENGTH_LONG);
            }
            return;
        }
        else if (GattCharacteristic.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT.equals(characteristicUUID)){
            Log.d(MiBandSupport.TAG, "Heart rate characteristic captured");
            handleHeartrate(characteristic.getValue());
        }
        else {
            Log.d(MiBandSupport.TAG, "Unhandled characteristic changed: " + characteristicUUID);
        }
    }

    private void handleHeartrate(byte[] value) {
        if (value.length == 2 && value[0] == 0) {
            int hrValue = (value[1] & 0xff);

            Log.d(MiBandSupport.TAG, "heart rate: " + hrValue);
        }
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
        builder.notify(getCharacteristic(GattService.UUID_SERVICE_CURRENT_TIME), enable);
        // Notify CHARACTERISTIC9 to receive random auth code
        builder.notify(getCharacteristic(MiBandService.UUID_CHARACTERISTIC_AUTH), enable);
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

    private byte[] handleAESAuth(byte[] value, byte[] secretKey) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException {
        byte[] mValue = Arrays.copyOfRange(value, 3, 19);
        @SuppressLint("GetInstance") Cipher ecipher = Cipher.getInstance("AES/ECB/NoPadding");
        SecretKeySpec newKey = new SecretKeySpec(secretKey, "AES");
        ecipher.init(Cipher.ENCRYPT_MODE, newKey);
        return ecipher.doFinal(mValue);
    }
}
