package com.example.miband.Utils;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class CalendarUtils {
    public static byte[] shortCalendarToRawBytes(Calendar timestamp) {
        // MiBand2:
        // year,year,month,dayofmonth,hour,minute

        byte[] year = fromUint16(timestamp.get(Calendar.YEAR));
        return new byte[] {
                year[0],
                year[1],
                fromUint8(timestamp.get(Calendar.MONTH) + 1),
                fromUint8(timestamp.get(Calendar.DATE)),
                fromUint8(timestamp.get(Calendar.HOUR_OF_DAY)),
                fromUint8(timestamp.get(Calendar.MINUTE))
        };
    }

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
                0, // fractions256 (not set)
                // 0 (DST offset?) Mi2
                // k (tz) Mi2
        };
    }

    public static Calendar fromTimeBytes(byte[] bytes) {
        GregorianCalendar timestamp = rawBytesToCalendar(bytes);
        return timestamp;
    }

    public static GregorianCalendar rawBytesToCalendar(byte[] value) {
        if (value.length >= 7) {
            int year = toUint16(value[0], value[1]);
            GregorianCalendar timestamp = new GregorianCalendar(
                    year,
                    (value[2] & 0xff) - 1,
                    value[3] & 0xff,
                    value[4] & 0xff,
                    value[5] & 0xff,
                    value[6] & 0xff
            );

            if (value.length > 7) {
                TimeZone timeZone = TimeZone.getDefault();
                timeZone.setRawOffset(value[7] * 15 * 60 * 1000);
                timestamp.setTimeZone(timeZone);
            }
            return timestamp;
        }

        return new GregorianCalendar();
    }

    public static byte mapTimeZone(TimeZone timeZone, int timezoneFlags) {
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
        switch (calValue) {
            case Calendar.SUNDAY:
                return 7;
            default:
                return (byte) (calValue - 1);
        }
    }

    public static byte fromUint8(int value) {
        return (byte) (value & 0xff);
    }

    public static byte[] fromUint16(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
        };
    }

    public static int toUint16(byte... bytes) {
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8);
    }
}
