package com.keep.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static com.keep.util.DateUtil.ZONE_UTC8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilTest {

    @Test
    void checkinDate_atResetHour_returnsSameDay() {
        // 2026-03-25 05:00 (UTC+8) -> 打卡日 2026-03-25
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 25, 5, 0, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 25), DateUtil.getCheckinDate(dt, 5));
    }

    @Test
    void checkinDate_beforeResetHour_returnsPreviousDay() {
        // 2026-03-25 04:59 (UTC+8) -> 打卡日 2026-03-24
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 25, 4, 59, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 24), DateUtil.getCheckinDate(dt, 5));
    }

    @Test
    void checkinDate_lateNight_returnsSameDay() {
        // 2026-03-25 23:59 (UTC+8) -> 打卡日 2026-03-25
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 25, 23, 59, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 25), DateUtil.getCheckinDate(dt, 5));
    }

    @Test
    void checkinDate_customResetHour8_before_returnsPreviousDay() {
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 25, 7, 59, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 24), DateUtil.getCheckinDate(dt, 8));
    }

    @Test
    void checkinDate_customResetHour8_atReset_returnsSameDay() {
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 25, 8, 0, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 25), DateUtil.getCheckinDate(dt, 8));
    }

    @Test
    void checkinDate_crossMonthBoundary() {
        // 2026-04-01 04:59 (UTC+8) -> 打卡日 2026-03-31
        ZonedDateTime dt = ZonedDateTime.of(2026, 4, 1, 4, 59, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 3, 31), DateUtil.getCheckinDate(dt, 5));
    }

    @Test
    void checkinDate_crossYearBoundary() {
        // 2027-01-01 04:59 (UTC+8) -> 打卡日 2026-12-31
        ZonedDateTime dt = ZonedDateTime.of(2027, 1, 1, 4, 59, 0, 0, ZONE_UTC8);
        assertEquals(LocalDate.of(2026, 12, 31), DateUtil.getCheckinDate(dt, 5));
    }
}
