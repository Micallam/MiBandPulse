package com.example.miband.Device;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import java.util.UUID;

public class MiBandService extends DeviceService{
    @SuppressLint("StaticFieldLeak")
    protected static Context mContext;
    private final Class<? extends Service> mService;

    public static final String BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb"; //this is common for all BTLE devices.

    public static final UUID UUID_SERVICE_CURRENT_TIME = UUID.fromString((String.format(BASE_UUID, "1805")));

    public static final UUID UUID_CHARACTERISTIC_NOTIFICATION = UUID.fromString(String.format(BASE_UUID, "FF03"));

    public static final UUID UUID_CHARACTERISTIC_CURRENT_TIME = UUID.fromString((String.format(BASE_UUID, "2A2B")));
    public static final UUID UUID_CHARACTERISTIC_HEART_RATE_CONTROL_POINT = UUID.fromString((String.format(BASE_UUID, "2A39")));
    public static final UUID UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT = UUID.fromString((String.format(BASE_UUID, "2A37")));

    public static final UUID UUID_CHARACTERISTIC_AUTH = UUID.fromString("00000009-0000-3512-2118-0009af100700");
    public static final UUID UUID_CHARACTERISTIC_DEVICEEVENT = UUID.fromString("00000010-0000-3512-2118-0009af100700");
    public static final UUID UUID_CHARACTERISTIC_AUDIO = UUID.fromString("00000012-0000-3512-2118-0009af100700");
    public static final UUID UUID_CHARACTERISTIC_AUDIODATA = UUID.fromString("00000013-0000-3512-2118-0009af100700");

    public static final byte AUTH_BYTE = 0x00;
    public static final byte CRYPT_FLAGS = 0x00;
    public static final byte AUTH_SEND_KEY = 0x01;
    public static final byte AUTH_RESPONSE = 0x10;
    public static final byte AUTH_SUCCESS = 0x01;
    public static final byte AUTH_REQUEST_RANDOM_AUTH_NUMBER = 0x02;
    public static final byte AUTH_SEND_ENCRYPTED_AUTH_NUMBER = 0x03;

    public static final UUID UUID_CHARACTERISTIC_3_CONFIGURATION = UUID.fromString("00000003-0000-3512-2118-0009af100700");
    public static final UUID UUID_CHARACTERISTIC_6_BATTERY_INFO = UUID.fromString("00000006-0000-3512-2118-0009af100700");

    public static final byte COMMAND_SET__HR_CONTINUOUS = 0x1;
    public static final byte COMMAND_SET_HR_MANUAL = 0x2;

    public MiBandService(Context context) {
        mContext = context;
        mService = DeviceService.class;
    }

    public void connect(@Nullable MiBandDevice device, boolean firstTime) {
        Intent intent = createIntent().setAction(ACTION_CONNECT)
                .putExtra(MiBandDevice.EXTRA_DEVICE, device)
                .putExtra(EXTRA_CONNECT_FIRST_TIME, firstTime);
        invokeService(intent);
    }

    protected Intent createIntent() {
        return new Intent(mContext, mService);
    }

    protected void invokeService(Intent intent) {
        mContext.startService(intent);
    }

    public void disconnect() {
        Intent intent = createIntent().setAction(ACTION_DISCONNECT);
        invokeService(intent);
    }
}
