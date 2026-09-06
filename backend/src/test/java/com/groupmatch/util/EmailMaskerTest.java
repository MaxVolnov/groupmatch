package com.groupmatch.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Маскировка адреса в логах и защита от того, чтобы адрес туда вернулся.
 *
 * Второе важнее первого. Сама функция проста, а вот «кто-то добавил ещё одну
 * строку лога с сырым request.email()» — ровно то, как эта утечка появилась в
 * первый раз, и заметить такое на ревью нечем.
 */
public class EmailMaskerTest {

    // ─── Поведение ────────────────────────────────────────────────────────────

    @Test
    void keepsFirstCharacterAndDomain() {
        assertThat(EmailMasker.mask("ivan@example.com")).isEqualTo("i***@example.com");
        assertThat(EmailMasker.mask("jvorik@gmail.com")).isEqualTo("j***@gmail.com");
    }

    /** Односимвольная локальная часть — первый символ и есть весь адрес. */
    @Test
    void hidesSingleCharacterLocalPartEntirely() {
        assertThat(EmailMasker.mask("a@example.com")).isEqualTo("***@example.com");
    }

    @Test
    void masksEverythingWhenThereIsNoAddressToSpeakOf() {
        assertThat(EmailMasker.mask(null)).isEqualTo("***");
        assertThat(EmailMasker.mask("")).isEqualTo("***");
        assertThat(EmailMasker.mask("   ")).isEqualTo("***");
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("***");
        assertThat(EmailMasker.mask("@example.com")).isEqualTo("***");
        assertThat(EmailMasker.mask("ivan@")).isEqualTo("***");
    }

    /** Адрес с «собакой» в локальной части — домен берётся по последней. */
    @Test
    void splitsOnTheLastAtSign() {
        assertThat(EmailMasker.mask("weird@name@example.com")).isEqualTo("w***@example.com");
    }

    @Test
    void maskedFormNeverContainsTheOriginalLocalPart() {
        for (String email : List.of("ivan@example.com", "jvorik@gmail.com", "a.b.c+tag@mail.ru")) {
            String local = email.substring(0, email.lastIndexOf('@'));
            assertThat(EmailMasker.mask(email))
                    .as("маска для %s", email)
                    .doesNotContain(local);
        }
    }

    // ─── Защита от возврата утечки ────────────────────────────────────────────

    /**
     * Ищем в исходниках вызовы логгера, куда адрес передаётся напрямую.
     *
     * Проверка грубая — по тексту, — и намеренно: точный разбор Java здесь
     * стоил бы дороже, чем ловит. Ложное срабатывание чинится тем, что значение
     * оборачивают в EmailMasker.mask, то есть ровно тем, чего мы и добиваемся.
     */
    @Test
    void noLogStatementPassesARawEmail() {
        Pattern rawEmailArgument = Pattern.compile(
                "log\\.(info|warn|error|debug|trace)\\([^;]*?,\\s*(request\\.email\\(\\)|user\\.getEmail\\(\\)|adminEmail|\\bto\\b)\\s*[,)]",
                Pattern.DOTALL);

        List<String> offenders = sources().stream()
                .filter(path -> {
                    Matcher m = rawEmailArgument.matcher(read(path));
                    return m.find();
                })
                .map(Path::toString)
                .toList();

        assertThat(offenders)
                .as("адрес уходит в лог без EmailMasker.mask")
                .isEmpty();
    }

    private static List<Path> sources() {
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось обойти исходники", e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать " + path, e);
        }
    }
}
