package com.example.miband.DataStructures;


import java.util.Date;

public class HeartRate {
    private int value;
    private Date time;

    public HeartRate(int value, Date time){
        this.value = value;
        this.time = time;
    }

    public Date getTime() {
        return time;
    }

    public int getValue() {
        return value;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
