package com.example.miband;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DiscoveryActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    int BONDING_STYLE_NONE = 0;
    /**
     * Bond after discovery.
     * This is not recommended, as there are mobile devices on which bonding does not work.
     * Prefer to use #BONDING_STYLE_ASK instead.
     */
    int BONDING_STYLE_BOND = 1;
    /**
     * Let the user decide whether to bond or not after discovery.
     * Prefer this over #BONDING_STYLE_BOND
     */
    int BONDING_STYLE_ASK = 2;


    private static final long SCAN_DURATION = 60000; // 60s

    private ScanCallback newLeScanCallback = null;

    // Disabled for testing, it seems worse for a few people
    private final boolean disableNewBLEScanning = true;

    private final Handler handler = new Handler();

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    if (isScanning != Scanning.SCANNING_BTLE && isScanning != Scanning.SCANNING_NEW_BTLE) {
                        discoveryStarted(Scanning.SCANNING_BT);
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // continue with LE scan, if available
                            if (isScanning == Scanning.SCANNING_BT) {
                                checkAndRequestLocationPermission();
                                if (MainActivity.isRunningLollipopOrLater() && !disableNewBLEScanning) {
                                    startDiscovery(Scanning.SCANNING_NEW_BTLE);
                                } else {
                                    startDiscovery(Scanning.SCANNING_BTLE);
                                }
                            } else {
                                discoveryFinished();
                            }
                        }
                    });
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                    bluetoothStateChanged(newState);
                    break;
                case BluetoothDevice.ACTION_FOUND: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, MiBandDevice.RSSI_UNKNOWN);
                    handleDeviceFound(device, rssi);
                    break;
                }
                case BluetoothDevice.ACTION_UUID: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, MiBandDevice.RSSI_UNKNOWN);
                    Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    ParcelUuid[] uuids2 = AndroidUtils.toParcelUuids(uuids);
                    handleDeviceFound(device, rssi, uuids2);
                    break;
                }
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !device.getAddress().isEmpty()) {
                        int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            handleDeviceBonded();
                        }
                    }
                }
            }
        }
    };

    private void connectAndFinish(MiBandDevice device) {
        AndroidUtils.toast(DiscoveryActivity.this, "Trying to connect to: " + device.getName(), Toast.LENGTH_SHORT);
        MainActivity.getMiBandService().connect(device, true);
        finish();
    }

    private void createBond(final MiBandDevice miBandDevice, int bondingStyle) {
        if (bondingStyle == BONDING_STYLE_NONE) {
            return;
        }
        if (bondingStyle == BONDING_STYLE_ASK) {
            new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle("Pair with " + miBandDevice.getName())
                    .setMessage("Select Pair to pair your devices. If this fails, try again without pairing.")
                    .setPositiveButton("Pair", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            doCreatePair(miBandDevice);
                        }
                    })
                    .setNegativeButton("Don't pair", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            connectAndFinish(miBandDevice);
                        }
                    })
                    .show();
        } else {
            doCreatePair(miBandDevice);
        }
    }

    private void doCreatePair(MiBandDevice miBandDevice) {
        AndroidUtils.toast(DiscoveryActivity.this, "Attempting to pair with " + miBandDevice.getName(), Toast.LENGTH_SHORT);
        if (miBandDevice.getDevice().createBond()) {
            bondingDevice = miBandDevice;
        } else {
            AndroidUtils.toast(DiscoveryActivity.this,"Bonding with " + miBandDevice.getName() + " failed immediately", Toast.LENGTH_SHORT);
        }
    }

    private void handleDeviceBonded() {
        AndroidUtils.toast(DiscoveryActivity.this,"Bound to" + bondingDevice.getName(), Toast.LENGTH_SHORT);
        connectAndFinish(bondingDevice);
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            logMessageContent(scanRecord);
            handleDeviceFound(device, (short) rssi);
        }
    };


    // why use a method to get callback?
    // because this callback need API >= 21
    // we cant add @TARGETAPI("Lollipop") at class header
    // so use a method with SDK check to return this callback
    private ScanCallback getScanCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            newLeScanCallback = new ScanCallback() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    try {
                        ScanRecord scanRecord = result.getScanRecord();
                        ParcelUuid[] uuids = null;
                        if (scanRecord != null) {
                            //logMessageContent(scanRecord.getBytes());
                            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
                            if (serviceUuids != null) {
                                uuids = serviceUuids.toArray(new ParcelUuid[0]);
                            }
                        }
                        Log.d(MainActivity.TAG, result.getDevice().getName() + ": " +
                                ((scanRecord != null) ? scanRecord.getBytes().length : -1));
                        handleDeviceFound(result.getDevice(), (short) result.getRssi(), uuids);
                    } catch (NullPointerException e) {
                        Log.d(MainActivity.TAG, "Error handling scan result");
                    }
                }
            };
        }
        return newLeScanCallback;
    }

    public void logMessageContent(byte[] value) {
        if (value != null) {
            Log.d(MainActivity.TAG, "DATA: " + AndroidUtils.hexdump(value, 0, value.length));
        }
    }

    private final Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopDiscovery();
        }
    };

    private ProgressBar progressView;
    private BluetoothAdapter adapter;
    private final ArrayList<MiBandDevice> deviceCandidates = new ArrayList<>();
    private DeviceCandidateAdapter cadidateListAdapter;
    private Button startButton;
    private Scanning isScanning = Scanning.SCANNING_OFF;
    private MiBandDevice bondingDevice;

    private enum Scanning {
        SCANNING_BT,
        SCANNING_BTLE,
        SCANNING_NEW_BTLE,
        SCANNING_OFF
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_discovery);
        startButton = findViewById(R.id.discovery_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButtonClick(startButton);
            }
        });

        progressView = findViewById(R.id.discovery_progressbar);
        progressView.setProgress(0);
        progressView.setIndeterminate(true);
        progressView.setVisibility(View.GONE);
        ListView deviceCandidatesView = findViewById(R.id.discovery_deviceCandidatesView);

        cadidateListAdapter = new DeviceCandidateAdapter(this, deviceCandidates);
        deviceCandidatesView.setAdapter(cadidateListAdapter);
        deviceCandidatesView.setOnItemClickListener(this);
        deviceCandidatesView.setOnItemLongClickListener(this);

        IntentFilter bluetoothIntents = new IntentFilter();
        bluetoothIntents.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothIntents.addAction(BluetoothDevice.ACTION_UUID);
        bluetoothIntents.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(bluetoothReceiver, bluetoothIntents);

        startDiscovery();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("deviceCandidates", deviceCandidates);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Parcelable> restoredCandidates = savedInstanceState.getParcelableArrayList("deviceCandidates");
        if (restoredCandidates != null) {
            deviceCandidates.clear();
            for (Parcelable p : restoredCandidates) {
                deviceCandidates.add((MiBandDevice) p);
            }
        }
    }

    public void onStartButtonClick(View button) {
        Log.d(MainActivity.TAG, "Start Button clicked");
        if (isScanning()) {
            stopDiscovery();
        } else {
            startDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            Log.d(MainActivity.TAG, "Tried to unregister Bluetooth Receiver that wasn't registered.");
        }
        super.onDestroy();
    }

    private void handleDeviceFound(BluetoothDevice device, short rssi) {
        ParcelUuid[] uuids = device.getUuids();
        if (uuids == null) {
            if (device.fetchUuidsWithSdp()) {
                return;
            }
        }

        handleDeviceFound(device, rssi, uuids);
    }


    private void handleDeviceFound(BluetoothDevice device, short rssi, ParcelUuid[] uuids) {
        Log.d(MainActivity.TAG, "found device: " + device.getName() + ", " + device.getAddress());

        MiBandDevice candidate = new MiBandDevice(device, device.getAddress());

        Log.d(MainActivity.TAG,"Recognized  device: " + candidate.getName());
        int index = deviceCandidates.indexOf(candidate);
        if (index >= 0) {
            deviceCandidates.set(index, candidate); // replace
        } else {
            deviceCandidates.add(candidate);
        }
        cadidateListAdapter.notifyDataSetChanged();
    }

    /**
     * Pre: bluetooth is available, enabled and scanning is off.
     * Post: BT is discovering
     */
    private void startDiscovery() {
        if (isScanning()) {
            Log.d(MainActivity.TAG, "Not starting discovery, because already scanning.");
            return;
        }
        startDiscovery(Scanning.SCANNING_BT);
    }

    private void startDiscovery(Scanning what) {
        Log.d(MainActivity.TAG, "Starting discovery: " + what);
        discoveryStarted(what); // just to make sure
        if (ensureBluetoothReady()) {
            if (what == Scanning.SCANNING_BT) {
                startBTDiscovery();
            } else if (what == Scanning.SCANNING_BTLE) {
                if (AndroidUtils.supportsBluetoothLE()) {
                    startBTLEDiscovery();
                } else {
                    discoveryFinished();
                }
            } else if (what == Scanning.SCANNING_NEW_BTLE) {
                if (AndroidUtils.supportsBluetoothLE()) {
                    startNEWBTLEDiscovery();
                } else {
                    discoveryFinished();
                }
            }
        } else {
            discoveryFinished();
            AndroidUtils.toast(DiscoveryActivity.this, "Enable bluetooth to discovery devices", Toast.LENGTH_SHORT);
        }
    }

    private boolean isScanning() {
        return isScanning != Scanning.SCANNING_OFF;
    }

    private void stopDiscovery() {
        Log.d(MainActivity.TAG, "Stopping discovery");
        if (isScanning()) {
            Scanning wasScanning = isScanning;
            // unfortunately, we don't always get a call back when stopping the scan, so
            // we do it manually; BEFORE stopping the scan!
            discoveryFinished();

            if (wasScanning == Scanning.SCANNING_BT) {
                stopBTDiscovery();
            } else if (wasScanning == Scanning.SCANNING_BTLE) {
                stopBTLEDiscovery();
            } else if (wasScanning == Scanning.SCANNING_NEW_BTLE) {
                stopNewBTLEDiscovery();
            }
            handler.removeMessages(0, stopRunnable);
        }
    }

    private void stopBTLEDiscovery() {
        if (adapter != null)
            adapter.stopLeScan(leScanCallback);
    }

    private void stopBTDiscovery() {
        if (adapter != null)
            adapter.cancelDiscovery();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopNewBTLEDiscovery() {
        if (adapter == null)
            return;

        BluetoothLeScanner bluetoothLeScanner = adapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.d(MainActivity.TAG, "could not get BluetoothLeScanner()!");
            return;
        }
        if (newLeScanCallback == null) {
            Log.d(MainActivity.TAG, "newLeScanCallback == null!");
            return;
        }
        bluetoothLeScanner.stopScan(newLeScanCallback);
    }

    private void bluetoothStateChanged(int newState) {
        discoveryFinished();
        if (newState == BluetoothAdapter.STATE_ON) {
            this.adapter = BluetoothAdapter.getDefaultAdapter();
            startButton.setEnabled(true);
        } else {
            this.adapter = null;
            startButton.setEnabled(false);
        }
    }

    private void discoveryFinished() {
        isScanning = Scanning.SCANNING_OFF;
        progressView.setVisibility(View.GONE);
        startButton.setText("Start scanning");
    }

    private void discoveryStarted(Scanning what) {
        isScanning = what;
        progressView.setVisibility(View.VISIBLE);
        startButton.setText("Stop scanning");
    }

    private boolean ensureBluetoothReady() {
        boolean available = checkBluetoothAvailable();
        startButton.setEnabled(available);
        if (available) {
            adapter.cancelDiscovery();
            // must not return the result of cancelDiscovery()
            // appears to return false when currently not scanning
            return true;
        }
        return false;
    }

    private boolean checkBluetoothAvailable() {
        BluetoothManager bluetoothService = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothService == null) {
            Log.d(MainActivity.TAG, "No bluetooth available");
            this.adapter = null;
            return false;
        }
        BluetoothAdapter adapter = bluetoothService.getAdapter();
        if (adapter == null) {
            Log.d(MainActivity.TAG, "No bluetooth available");
            this.adapter = null;
            return false;
        }
        if (!adapter.isEnabled()) {
            Log.d(MainActivity.TAG, "Bluetooth not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            this.adapter = null;
            return false;
        }
        this.adapter = adapter;
        return true;
    }

    // New BTLE Discovery use startScan (List<ScanFilter> filters,
    //                                  ScanSettings settings,
    //                                  ScanCallback callback)
    // It's added on API21
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startNEWBTLEDiscovery() {
        // Only use new API when user uses Lollipop+ device
        Log.d(MainActivity.TAG, "Start New BTLE Discovery");
        handler.removeMessages(0, stopRunnable);
        handler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_DURATION);
        adapter.getBluetoothLeScanner().startScan(getScanFilters(), getScanSettings(), getScanCallback());
    }

    private List<ScanFilter> getScanFilters() {
        List<ScanFilter> allFilters = new ArrayList<>();
        allFilters.addAll(MiBandDevice.createBLEScanFilters());

        return allFilters;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private ScanSettings getScanSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_STICKY)
                    .build();
        } else {
            return new ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
        }
    }

    private void startBTLEDiscovery() {
        Log.d(MainActivity.TAG, "Starting BTLE Discovery");
        handler.removeMessages(0, stopRunnable);
        handler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_DURATION);
        adapter.startLeScan(leScanCallback);
    }

    private void startBTDiscovery() {
        Log.d(MainActivity.TAG, "Starting BT Discovery");
        handler.removeMessages(0, stopRunnable);
        handler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_DURATION);
        adapter.startDiscovery();
    }

    private void checkAndRequestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
    }

    private Message getPostMessage(Runnable runnable) {
        Message m = Message.obtain(handler, runnable);
        m.obj = runnable;
        return m;
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        MiBandDevice deviceCandidate = deviceCandidates.get(position);
        if (deviceCandidate == null) {
            Log.d(MainActivity.TAG, "Device candidate clicked, but item not found");
            return true;
        }
        /*
        TODO Device settings activity
        Intent startIntent;
        startIntent = new Intent(this, DeviceSettingsActivity.class);
        startIntent.putExtra(MiBandDevice.EXTRA_DEVICE, deviceCandidate);
        startActivity(startIntent);
         */
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MiBandDevice deviceCandidate = deviceCandidates.get(position);
        if (deviceCandidate == null) {
            Log.d(MainActivity.TAG, "Device candidate clicked, but item not found");
            return;
        }

        stopDiscovery();

        Log.d(MainActivity.TAG, "Using device candidate " + deviceCandidate);

        Intent intent = new Intent(this, PairingActivity.class);
        intent.putExtra(MiBandDevice.EXTRA_DEVICE_CANDIDATE, deviceCandidate);
        startActivity(intent);

        /*
        if (pairingActivity != null) {
            Intent intent = new Intent(this, pairingActivity);
            intent.putExtra(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE, deviceCandidate);
            startActivity(intent);
        } else {
            GBDevice device = DeviceHelper.getInstance().toSupportedDevice(deviceCandidate);
            int bondingStyle = coordinator.getBondingStyle(device);
            if (bondingStyle == DeviceCoordinator.BONDING_STYLE_NONE) {
                Log.d(MainActivity.TAG, "No bonding needed, according to coordinator, so connecting right away");
                connectAndFinish(deviceCandidate);
                return;
            }

            try {
                BluetoothDevice btDevice = adapter.getRemoteDevice(deviceCandidate.getMacAddress());
                switch (btDevice.getBondState()) {
                    case BluetoothDevice.BOND_NONE: {
                        createBond(deviceCandidate, bondingStyle);
                        break;
                    }
                    case BluetoothDevice.BOND_BONDING:
                        // async, wait for bonding event to finish this activity
                        bondingDevice = deviceCandidate;
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        handleDeviceBonded();
                        break;
                }
            } catch (Exception e) {
                Log.d(MainActivity.TAG, "Error pairing device: " + deviceCandidate.getAddress());
            }
        }*/
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBTDiscovery();
        stopBTLEDiscovery();
        if (MainActivity.isRunningLollipopOrLater() && !disableNewBLEScanning) {
            stopNewBTLEDiscovery();
        }
    }
}
