package com.groupmatch.dto.availability;

import com.groupmatch.exception.BadRequestException;

/** Область действия удаления слота: он один или вся его серия. */
public enum DeleteScope {
    SINGLE,
    SERIES;

    /**
     * Разбор значения query-параметра.
     *
     * Отдельный метод, а не автоматическая конвертация Spring: она сверяет
     * строку с именем константы посимвольно, и документированное
     * {@code ?scope=single} не совпало бы с {@code SINGLE}. Ошибка выглядела
     * бы как 400 без внятной причины на совершенно правильном запросе.
     */
    public static DeleteScope parse(String raw) {
        if (raw == null || raw.isBlank()) return SINGLE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown scope: " + raw + " (expected single or series)");
        }
    }
}
