package com.groupmatch.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Срок подписки считался как {@code periodMonths * 30} дней. Тридцатидневных
 * месяцев в году только половина, и ошибка всегда была против того, кто
 * заплатил: год давал 360 дней вместо 365.
 *
 * Проверяем именно те даты, на которых наивная арифметика ломается: конец
 * месяца и високосный год. Числа в утверждениях записаны руками — если считать
 * их тем же способом, что и код, тест подтвердит любую ошибку.
 */
public class PlanPeriodTest {

    private static Instant utc(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static LocalDate dateOf(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate();
    }

    /** 31 января: 31 февраля не бывает, календарь прижимает к концу месяца. */
    @Test
    void januaryThirtyFirstPlusOneMonthLandsOnEndOfFebruary() {
        assertThat(dateOf(PlanPeriod.advance(utc(2026, 1, 31), 1)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    /** 31 августа: в сентябре 30 дней. */
    @Test
    void augustThirtyFirstPlusOneMonthLandsOnSeptemberThirtieth() {
        assertThat(dateOf(PlanPeriod.advance(utc(2026, 8, 31), 1)))
                .isEqualTo(LocalDate.of(2026, 9, 30));
    }

    /** 29 февраля високосного года плюс год: 2029-й не високосный. */
    @Test
    void leapDayPlusTwelveMonthsLandsOnFebruaryTwentyEighth() {
        assertThat(dateOf(PlanPeriod.advance(utc(2028, 2, 29), 12)))
                .isEqualTo(LocalDate.of(2029, 2, 28));
    }

    /**
     * Главное расхождение со старой формулой: 12 месяцев — это год, а не
     * 360 дней. Проверяем на обычном годе без пограничных чисел.
     */
    @Test
    void twelveMonthsIsAFullYearNotThreeHundredSixtyDays() {
        Instant from = utc(2026, 3, 15);
        assertThat(dateOf(PlanPeriod.advance(from, 12))).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(dateOf(from.plusSeconds(360L * 24 * 3600)))
                .as("прежняя формула отставала на пять дней")
                .isEqualTo(LocalDate.of(2027, 3, 10));
    }

    /** Время суток — часть якоря: списание не должно уползать к полуночи. */
    @Test
    void timeOfDayIsPreserved() {
        Instant from = LocalDateTime.of(2026, 5, 10, 13, 45, 30).toInstant(ZoneOffset.UTC);
        assertThat(PlanPeriod.advance(from, 1))
                .isEqualTo(LocalDateTime.of(2026, 6, 10, 13, 45, 30).toInstant(ZoneOffset.UTC));
    }

    @Test
    void rejectsNonPositivePeriods() {
        assertThatThrownBy(() -> PlanPeriod.advance(utc(2026, 1, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanPeriod.advance(utc(2026, 1, 1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
