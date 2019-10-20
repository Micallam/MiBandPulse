package com.example.miband;

import android.net.Uri;

import java.util.UUID;

public interface EventHandler {

    void onInstallApp(Uri uri);

    void onAppInfoReq();

    void onAppStart(UUID uuid, boolean start);

    void onAppDelete(UUID uuid);

    void onAppConfiguration(UUID appUuid, String config, Integer id);

    void onAppReorder(UUID uuids[]);

    void onFetchRecordedData(int dataTypes);

    void onReset(int flags);

    void onHeartRateTest();

    void onEnableRealtimeHeartRateMeasurement(boolean enable);

    void onFindDevice(boolean start);

    void onEnableHeartRateSleepSupport(boolean enable);

    void onSetHeartRateMeasurementInterval(int seconds);

    void onSendConfiguration(String config);

    void onReadConfiguration(String config);

    void onTestNewFunction();

    void onSetFmFrequency(float frequency);
}