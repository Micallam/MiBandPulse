package com.example.miband;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.miband.Activities.DiscoveryActivity;
import com.example.miband.Device.MiBandSupport;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MiBand: MainActivity";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @SuppressLint("StaticFieldLeak")
    private static MainActivity context;
    @SuppressLint("StaticFieldLeak")
    private static MiBandSupport miBandSupport;

    BluetoothAdapter bluetoothAdapter;

    Button onOffBtn;
    Button searchBtn;

    public MainActivity() {
        context = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        onOffBtn = findViewById(R.id.onOffBtn);
        searchBtn = findViewById(R.id.searchBtn);

        onOffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableDisableBT();
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.getContext(), DiscoveryActivity.class));
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant access");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "coarse location permission granted");
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Functionality limited");
                builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                    }
                });
                builder.show();
            }
        }
    }

    public void enableDisableBT() {
        if (bluetoothAdapter == null) {
            Log.d(TAG, "Does not have BT capabilities.");
        }
        else {
            if (!bluetoothAdapter.isEnabled()) {
                Log.d(TAG, "enabling BT.");
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(enableBTIntent);
            } else {
                Log.d(TAG, "disabling BT.");
                bluetoothAdapter.disable();
            }
        }
    }

    public static Context getContext() {
        return context;
    }

    public static void setMiBandSupport(MiBandSupport miBandSupport) {
        MainActivity.miBandSupport = miBandSupport;
    }

    public static MiBandSupport getMiBandSupport(){
        return MainActivity.miBandSupport;
    }
}
