package com.groupmatch.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Единственное в продукте определение того, что такое «месяц» для плана.
 *
 * До этого их было два. `TrialService` считал календарными месяцами и объяснял
 * в комментарии почему: «три месяца» в баннере должно означать то же, что и в
 * календаре пользователя. `YooKassaService` считал подписку как
 * {@code periodMonths * 30} дней. Тридцатидневных месяцев в году только
 * половина, и ошибка всегда была в одну сторону — против того, кто заплатил:
 * годовая подписка давала 360 дней вместо 365.
 *
 * Календарная арифметика Java сама решает случаи, где дня просто нет:
 * 31 января плюс месяц — это 28 (или 29) февраля, а не 3 марта.
 */
public final class PlanPeriod {

    private PlanPeriod() {}

    /**
     * Момент окончания периода в {@code months} календарных месяцев от
     * {@code from}.
     *
     * Считаем в UTC, а не в зоне пользователя: план — сущность серверная, и
     * переезд человека в другой пояс не должен двигать дату списания.
     */
    public static Instant advance(Instant from, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("months must be positive, got " + months);
        }
        return LocalDateTime.ofInstant(from, ZoneOffset.UTC)
                .plusMonths(months)
                .toInstant(ZoneOffset.UTC);
    }
}
