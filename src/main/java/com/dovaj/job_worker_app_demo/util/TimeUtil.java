package com.dovaj.job_worker_app_demo.util;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * packageName    : com.sks.wpm.core.utils
 * fileName       : TimeUtil
 * author         : samuel
 * date           : 25. 4. 7.
 * description    : 시간 연산 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 4. 7.        samuel       최초 생성
 */
@Slf4j
public class TimeUtil {

    public static final DateTimeFormatter DATA_DATE_FORMATTER_NO_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMATTER_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMATTER_2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static final DateTimeFormatter DATETIME_FORMATTER_3 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER_1 = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter TIME_FORMATTER_2 = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Map<DayOfWeek, String> KOREAN_DAY_OF_WEEK = Map.of(
            DayOfWeek.MONDAY, "월요일",
            DayOfWeek.TUESDAY, "화요일",
            DayOfWeek.WEDNESDAY, "수요일",
            DayOfWeek.THURSDAY, "목요일",
            DayOfWeek.FRIDAY, "금요일",
            DayOfWeek.SATURDAY, "토요일",
            DayOfWeek.SUNDAY, "일요일"
    );

    public static String convertLocalDateToString(LocalDate localDate) {
        try {
            if (localDate == null) {
                return null;
            }

            return localDate.format(DATETIME_FORMATTER_3);
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate convertStringToLocalDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DATETIME_FORMATTER_3);
        } catch (Exception e) {
            return null;
        }
    }

    public static String convertLocalDateTimeToString(LocalDateTime localDateTime) {
        try {
            if (localDateTime == null) {
                return null;
            }

            return localDateTime.format(DATETIME_FORMATTER_1);
        } catch (Exception e1) {
            try {
                return localDateTime.format(DATETIME_FORMATTER_2);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static LocalDateTime convertStringToLocalDateTime(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, DATETIME_FORMATTER_1);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(dateStr, DATETIME_FORMATTER_2);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static String convertLocalTimeToString(LocalTime localTime) {
        try {
            return localTime.format(TIME_FORMATTER_1);
        } catch (Exception e1) {
            try {
                return localTime.format(TIME_FORMATTER_2);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static String convertLocalTimeToString(LocalTime localTime, DateTimeFormatter formatter) {
        try {
            return localTime.format(formatter);
        } catch (Exception e1) {
            return null;
        }
    }

    public static LocalTime convertStringToLocalTime(String dateStr) {
        try {
            return LocalTime.parse(dateStr, TIME_FORMATTER_1);
        } catch (Exception e1) {
            try {
                return LocalTime.parse(dateStr, TIME_FORMATTER_2);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static String getRandomStringByCurrentDate() {
        return UUID.randomUUID() + ":" + getCurrentDateWithNoSpace();
    }

    public static String getCurrentDateWithNoSpace() {
        return TimeUtil.convertLocalDateTimeToStringWithNoSpace(TimeUtil.nowUTCLocalDateTime());
    }

    public static String convertLocalDateTimeToStringWithNoSpace(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }

        return localDateTime.format(DATA_DATE_FORMATTER_NO_SPACE);
    }

    public static LocalDateTime nowUTCLocalDateTime() {
        return nowUTCOffsetDateTime().toLocalDateTime();
    }

    public static OffsetDateTime nowUTCOffsetDateTime() {
        OffsetDateTime now = OffsetDateTime.now();
        return now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public static String getDayOfWeekKoreanStringFromLocalDate(LocalDate date) {
        return KOREAN_DAY_OF_WEEK.get(date.getDayOfWeek());
    }

    public static LocalDate getLocalDateFromStringAndFormatter(String localDateTime, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(localDateTime, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDateTime getLocalDateTimeFromStringAndFormatter(String localDateTime, DateTimeFormatter formatter) {
        try {
            return LocalDateTime.parse(localDateTime, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isOverlapped(LocalTime existStart, LocalTime existEnd, LocalTime newStart, LocalTime newEnd) {
        int existStartMin = existStart.toSecondOfDay();
        int existEndMin = existEnd.toSecondOfDay();
        int newStartMin = newStart.toSecondOfDay();
        int newEndMin = newEnd.toSecondOfDay();

        // 자정 넘김 처리 (종료 시간이 시작보다 앞서면 +24시간)
        if (existEndMin <= existStartMin) existEndMin += 86400;
        if (newEndMin <= newStartMin) newEndMin += 86400;

        // 모든 시간대를 0~172800(48시간)으로 확장
        for (int base = 0; base <= 86400; base += 86400) {
            int adjustedNewStart = newStartMin + base;
            int adjustedNewEnd = newEndMin + base;

            // 겹치는지 확인
            boolean isOverlap = (existStartMin < adjustedNewEnd) && (existEndMin > adjustedNewStart);
            if (isOverlap) {
                return true;
            }
        }

        return false;
    }

    public static TimeUnit convertStringToTimeUnit(String timeUnitString) {
        if (timeUnitString == null) {
            return null;
        }
        return switch (timeUnitString) {
            case "ms" -> TimeUnit.MILLISECONDS;
            case "s" -> TimeUnit.SECONDS;
            case "m" -> TimeUnit.MINUTES;
            case "h" -> TimeUnit.HOURS;
            case "d" -> TimeUnit.DAYS;
            default -> null;
        };
    }

}
