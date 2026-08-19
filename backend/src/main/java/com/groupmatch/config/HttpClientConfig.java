package com.groupmatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Общий {@link HttpClient} для исходящих вызовов.
 *
 * До этого {@code YooKassaService} создавал клиент прямо в методе, на каждый
 * запрос. Два следствия. Первое — каждый вызов заново поднимал пул соединений
 * и селектор, то есть платил за то, что должно создаваться однажды. Второе и
 * важное: у клиента по умолчанию **нет** таймаута на соединение, поэтому
 * недоступный платёжный шлюз держал бы поток обработки столько, сколько
 * операционная система готова ждать TCP-хендшейк.
 *
 * Таймаут на ответ задаётся на самом запросе — клиент такого умолчания не
 * имеет вовсе; см. {@code REQUEST_TIMEOUT} в {@code YooKassaService}.
 */
@Configuration
public class HttpClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
