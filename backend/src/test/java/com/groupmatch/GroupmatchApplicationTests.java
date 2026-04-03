package com.groupmatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke-тест: проверяет что Spring-контекст поднимается без ошибок.
 * Требует запущенных Postgres + Redis (через docker-compose или CI services).
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupmatchApplicationTests {

    @Test
    void contextLoads() {
        // Пустой тест — если контекст не поднялся, Spring выбросит исключение
    }
}
