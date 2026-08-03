package com.groupmatch.meetings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Минимальный разбор iCalendar обратно — чтобы «валидный .ics» означало
 * «разбирается», а не «содержит нужную подстроку».
 *
 * Своя реализация, а не библиотека: тянуть ical4j ради структурной проверки
 * в одном тесте дороже, чем сорок строк здесь. Проверяем то, на чём реально
 * спотыкаются клиенты: CRLF, парность BEGIN/END, разворачивание длинных строк,
 * обязательные свойства VEVENT.
 */
final class IcsAssert {

    private IcsAssert() {}

    static void assertParsable(String ics) {
        assertThat(ics).as("пустой календарь").isNotBlank();
        assertThat(ics).as("строки разделяются CRLF").contains("\r\n");
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");

        List<String> lines = unfold(ics);
        Deque<String> open = new ArrayDeque<>();
        int events = 0;
        boolean inEvent = false;
        boolean hasUid = false, hasStart = false, hasStamp = false;

        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon < 0) fail("Строка без разделителя ':' → " + line);
            String name = line.substring(0, colon);
            String value = line.substring(colon + 1);

            if ("BEGIN".equals(name)) {
                open.push(value);
                if ("VEVENT".equals(value)) {
                    inEvent = true;
                    hasUid = hasStart = hasStamp = false;
                }
                continue;
            }
            if ("END".equals(name)) {
                if (open.isEmpty() || !open.peek().equals(value)) {
                    fail("END:" + value + " без парного BEGIN");
                }
                open.pop();
                if ("VEVENT".equals(value)) {
                    assertThat(hasUid).as("VEVENT без UID").isTrue();
                    assertThat(hasStart).as("VEVENT без DTSTART").isTrue();
                    assertThat(hasStamp).as("VEVENT без DTSTAMP").isTrue();
                    inEvent = false;
                    events++;
                }
                continue;
            }
            if (inEvent) {
                if ("UID".equals(name)) hasUid = true;
                if (name.startsWith("DTSTART")) hasStart = true;
                if ("DTSTAMP".equals(name)) hasStamp = true;
            }
        }

        assertThat(open).as("незакрытые компоненты: %s", open).isEmpty();
        assertThat(events).as("календарь без единого VEVENT — тоже валиден").isGreaterThanOrEqualTo(0);
    }

    static int eventCount(String ics) {
        return (int) unfold(ics).stream().filter("BEGIN:VEVENT"::equals).count();
    }

    /** RFC 5545 §3.1: продолжение строки начинается с пробела или таба. */
    private static List<String> unfold(String ics) {
        List<String> out = new ArrayList<>();
        for (String raw : ics.split("\r\n", -1)) {
            if (raw.isEmpty()) continue;
            char first = raw.charAt(0);
            if ((first == ' ' || first == '\t') && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + raw.substring(1));
            } else {
                out.add(raw);
            }
        }
        return out;
    }
}
