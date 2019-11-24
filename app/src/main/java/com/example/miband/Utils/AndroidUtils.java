package com.example.miband.Utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.miband.MainActivity;

public class AndroidUtils {

    public static void toast(final Context context, final String message, final int displayTime) {
        Looper mainLooper = Looper.getMainLooper();
        if (Thread.currentThread() == mainLooper.getThread()) {
            Toast.makeText(context, message, displayTime).show();
        } else {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, displayTime).show();
                }
            };

            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(runnable);
            } else {
                new Handler(mainLooper).post(runnable);
            }
        }
    }

    public static String hexdump(byte[] buffer, int offset, int length) {
        if (length == -1) {
            length = buffer.length - offset;
        }
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int v = buffer[i + offset] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    public static boolean supportsBluetoothLE() {
        return MainActivity.getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public static void safeUnregisterBroadcastReceiver(LocalBroadcastManager manager, BroadcastReceiver receiver) {
        try {
            manager.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static void safeUnregisterBroadcastReceiver(Context context, BroadcastReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
