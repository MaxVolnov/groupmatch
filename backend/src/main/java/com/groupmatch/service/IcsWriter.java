package com.groupmatch.service;

import com.groupmatch.domain.Meeting;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сборка iCalendar (RFC 5545).
 *
 * Вынесено из {@code MeetingService}, чтобы разовый экспорт одной встречи и
 * фид-подписка на всю группу собирались одним кодом и не разъезжались.
 *
 * Разовый файл и фид отличаются только шапкой: у фида есть имя календаря и
 * подсказка клиенту, как часто перечитывать. Тело VEVENT одинаковое.
 */
@Component
public class IcsWriter {

    private static final DateTimeFormatter ICS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** Разовый файл: одна встреча, без метаданных подписки. */
    public String singleEvent(Meeting meeting) {
        StringBuilder sb = new StringBuilder();
        openCalendar(sb);
        appendEvent(sb, meeting);
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /**
     * Фид для подписки: календарь целиком, перечитывается клиентом.
     *
     * @param calendarName имя, которое календарь покажет пользователю
     * @param refreshMinutes как часто клиенту стоит перечитывать фид
     */
    public String feed(String calendarName, List<Meeting> meetings, int refreshMinutes) {
        StringBuilder sb = new StringBuilder();
        openCalendar(sb);
        // METHOD:PUBLISH + имя + интервал обновления — то, по чему Google и
        // Apple Calendar понимают, что это подписка, а не разовый импорт.
        line(sb, "METHOD", "PUBLISH");
        line(sb, "X-WR-CALNAME", escape(calendarName));
        String duration = "PT" + refreshMinutes + "M";
        sb.append("REFRESH-INTERVAL;VALUE=DURATION:").append(duration).append("\r\n");
        line(sb, "X-PUBLISHED-TTL", duration);
        meetings.forEach(m -> appendEvent(sb, m));
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    // ─── Внутреннее ──────────────────────────────────────────────────────────

    private void openCalendar(StringBuilder sb) {
        sb.append("BEGIN:VCALENDAR\r\n");
        line(sb, "VERSION", "2.0");
        line(sb, "PRODID", "-//GroupMatch//GroupMatch//EN");
        line(sb, "CALSCALE", "GREGORIAN");
    }

    private void appendEvent(StringBuilder sb, Meeting m) {
        sb.append("BEGIN:VEVENT\r\n");
        line(sb, "UID", m.getId() + "@groupmatch.app");
        line(sb, "SUMMARY", escape(m.getTitle()));
        if (m.getDescription() != null && !m.getDescription().isBlank()) {
            line(sb, "DESCRIPTION", escape(m.getDescription()));
        }
        line(sb, "DTSTART", ICS_FMT.format(m.getStartsAt()));
        line(sb, "DTEND", ICS_FMT.format(m.getEndsAt()));
        line(sb, "DTSTAMP", ICS_FMT.format(Instant.now()));
        sb.append("END:VEVENT\r\n");
    }

    private void line(StringBuilder sb, String name, String value) {
        fold(sb, name + ":" + value);
    }

    /**
     * RFC 5545 §3.1: строка длиннее 75 октетов разбивается, продолжение
     * начинается с пробела. Без этого длинное название встречи ломает разбор
     * у строгих клиентов.
     */
    private void fold(StringBuilder sb, String content) {
        final int limit = 75;
        if (content.length() <= limit) {
            sb.append(content).append("\r\n");
            return;
        }
        sb.append(content, 0, limit).append("\r\n");
        int pos = limit;
        while (pos < content.length()) {
            int end = Math.min(pos + limit - 1, content.length());
            sb.append(' ').append(content, pos, end).append("\r\n");
            pos = end;
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                    .replace(";", "\\;")
                    .replace(",", "\\,")
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n");
    }
}
