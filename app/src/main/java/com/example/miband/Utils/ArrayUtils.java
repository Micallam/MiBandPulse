package com.example.miband.Utils;

public class ArrayUtils {
    public static boolean equals(byte[] first, byte[] second, int startIndex) {
        if (first == null) {
            throw new IllegalArgumentException("first must not be null");
        }
        if (second == null) {
            throw new IllegalArgumentException("second must not be null");
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0");
        }

        if (second.length + startIndex > first.length) {
            return false;
        }
        for (int i = 0; i < second.length; i++) {
            if (first[startIndex + i] != second[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] join(byte[] start, byte[] end) {
        if (start == null || start.length == 0) {
            return end;
        }
        if (end == null || end.length == 0) {
            return start;
        }

        byte[] result = new byte[start.length + end.length];
        System.arraycopy(start, 0, result, 0, start.length);
        System.arraycopy(end, 0, result, start.length, end.length);
        return result;
    }
}
