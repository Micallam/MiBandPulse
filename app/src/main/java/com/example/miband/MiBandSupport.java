package com.example.miband;

public class MiBandSupport {
    MiBandDevice mDevice;

    public MiBandSupport(MiBandDevice device){
        mDevice = device;
    }

    public boolean connectFirstTime() {
        for (int i = 0; i < 5; i++) {
            if (connect()) {
                return true;
            }
        }
        return false;
    }

    public boolean connect(){
        // TODO implement based on BtLEQueue
        return true;
    }
}
