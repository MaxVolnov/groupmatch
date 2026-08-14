package com.groupmatch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Диагностика привязки к сети при старте.
 *
 * Повод: на Timeweb App Platform контейнер стартовал штатно («Tomcat started on
 * port 8080»), но health-check платформы, идущий на http://localhost:8080
 * изнутри контейнера, до приложения не доходил — контейнер убивали через 60
 * секунд, снаружи curl возвращал пустой ответ. Разбирать это было нечем:
 * консоль платформы недоступна, shell-утилит в alpine-образе почти нет, а
 * строка «Tomcat started» сообщает, что сокет открыт, но не говорит ни на каком
 * интерфейсе, ни достижим ли он по localhost.
 *
 * Поэтому приложение рассказывает о себе само: какой адрес задан в конфиге, что
 * реально видно в /proc как слушающий сокет, во что резолвится localhost и
 * устанавливается ли соединение — по тем же адресам, по которым ходит
 * платформа. Шесть строк на старте против ещё одного дня вслепую.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListenAddressLogger implements ApplicationListener<WebServerInitializedEvent> {

    /** Столько ждём собственный сокет: он локальный, дольше ждать нечего. */
    private static final int PROBE_TIMEOUT_MS = 500;

    private static final String[] PROBE_HOSTS = {"localhost", "127.0.0.1", "::1"};

    /** Состояние LISTEN в /proc/net/tcp — четвёртая колонка. */
    private static final String TCP_STATE_LISTEN = "0A";

    private final Environment environment;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String configured = environment.getProperty("server.address");

        log.info("Bind: server.address={}, порт={}",
                configured == null || configured.isBlank()
                        ? "не задан (wildcard: и IPv4, и IPv6)"
                        : configured,
                port);

        log.info("Bind: localhost резолвится в {}", resolve());
        log.info("Bind: слушающие сокеты на порту {}: {}", port, listeningSockets(port));

        for (String host : PROBE_HOSTS) {
            log.info("Bind: соединение с {}:{} — {}", host, port, probe(host, port));
        }
    }

    /**
     * Порядок адресов важен: если localhost резолвится сначала в ::1, а сокет
     * открыт только по IPv4, клиент без фолбэка получит отказ — при живом
     * приложении.
     */
    private String resolve() {
        try {
            return Arrays.stream(InetAddress.getAllByName("localhost"))
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return "не удалось определить (" + e.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Читаем /proc напрямую: это единственный способ увидеть, открыт сокет как
     * IPv4-only или как двойной стек. Linux-специфично и best-effort — вне
     * Linux просто ничего не покажем.
     */
    private String listeningSockets(int port) {
        List<String> found = new ArrayList<>();
        for (String file : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            try {
                Path path = Path.of(file);
                if (!Files.exists(path)) continue;
                for (String line : Files.readAllLines(path)) {
                    String[] columns = line.trim().split("\\s+");
                    if (columns.length < 4 || !TCP_STATE_LISTEN.equals(columns[3])) continue;
                    String[] address = columns[1].split(":");
                    if (address.length != 2 || Integer.parseInt(address[1], 16) != port) continue;
                    found.add(file.endsWith("tcp6") ? "IPv6[" + address[0] + "]" : "IPv4[" + address[0] + "]");
                }
            } catch (Exception e) {
                found.add(file + ": не прочитан (" + e.getClass().getSimpleName() + ")");
            }
        }
        return found.isEmpty() ? "не найдены (/proc недоступен?)" : String.join(", ", found);
    }

    /**
     * Соединение до себя же. Именно TCP-connect, без HTTP: нас интересует, есть
     * ли вообще слушающий сокет на этом адресе, а не что он отвечает.
     */
    private String probe(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return "OK";
        } catch (IOException | RuntimeException e) {
            // Ошибку глотаем намеренно: это диагностика, ронять из-за неё старт
            // приложения нельзя. Недостижимый ::1 на хосте без IPv6 — норма.
            return "НЕТ (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
    }
}
