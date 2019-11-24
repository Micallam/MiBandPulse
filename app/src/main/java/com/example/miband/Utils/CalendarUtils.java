package com.example.miband.Utils;

import java.util.Calendar;
import java.util.TimeZone;

public class CalendarUtils {

    public static byte[] calendarToRawBytes(Calendar timestamp) {
        // MiBand3:
        // year,year,month,dayofmonth,hour,minute,second,dayofweek,0,0,tz

        byte[] year = fromUint16(timestamp.get(Calendar.YEAR));
        return new byte[] {
                year[0],
                year[1],
                fromUint8(timestamp.get(Calendar.MONTH) + 1),
                fromUint8(timestamp.get(Calendar.DATE)),
                fromUint8(timestamp.get(Calendar.HOUR_OF_DAY)),
                fromUint8(timestamp.get(Calendar.MINUTE)),
                fromUint8(timestamp.get(Calendar.SECOND)),
                dayOfWeekToRawBytes(timestamp),
                0,
        };
    }

    public static byte mapTimeZone(TimeZone timeZone) {
        int offsetMillis = timeZone.getRawOffset();
        int utcOffsetInHours =  (offsetMillis / (1000 * 60 * 60));
        return (byte) (utcOffsetInHours * 4);
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

    private static byte dayOfWeekToRawBytes(Calendar cal) {
        int calValue = cal.get(Calendar.DAY_OF_WEEK);
        if (calValue == Calendar.SUNDAY) {
            return 7;
        }
        return (byte) (calValue - 1);
    }

    private static byte fromUint8(int value) {
        return (byte) (value & 0xff);
    }

    private static byte[] fromUint16(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
        };
    }
}
