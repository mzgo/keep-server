package com.keep.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 打卡日边界计算工具 (服务端)
 * 一个"打卡日"= RESET_HOUR:00:00 至次日 (RESET_HOUR-1):59:59 (UTC+8)
 */
public final class DateUtil {

    public static final ZoneId ZONE_UTC8 = ZoneId.of("Asia/Shanghai");
    public static final int DEFAULT_RESET_HOUR = 5;

    private DateUtil() {}

    /**
     * 获取指定时刻对应的"打卡日"日期
     */
    public static LocalDate getCheckinDate(ZonedDateTime dateTime, int resetHour) {
        ZonedDateTime utc8 = dateTime.withZoneSameInstant(ZONE_UTC8);
        if (utc8.getHour() < resetHour) {
            return utc8.toLocalDate().minusDays(1);
        }
        return utc8.toLocalDate();
    }

    /**
     * 获取当前打卡日
     */
    public static LocalDate getCurrentCheckinDate(int resetHour) {
        return getCheckinDate(ZonedDateTime.now(ZONE_UTC8), resetHour);
    }

    /**
     * 获取当前UTC+8时间
     */
    public static LocalDateTime nowUtc8() {
        return LocalDateTime.now(ZONE_UTC8);
    }
}
