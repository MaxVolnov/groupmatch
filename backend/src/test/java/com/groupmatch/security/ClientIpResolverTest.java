package com.groupmatch.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientIpResolverTest {

    private static final String PROXIES = "127.0.0.1/32,10.0.0.0/8";

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) req.addHeader("X-Forwarded-For", xff);
        return req;
    }

    // ── Подделка заголовка ───────────────────────────────────────────────────

    /** Главный кейс P0: клиент напрямую шлёт X-Forwarded-For и не должен ничего добиться. */
    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxiesConfigured() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(request("127.0.0.1", "1.2.3.4")))
                .isEqualTo("127.0.0.1");
    }

    // ── Нормальная работа за прокси ──────────────────────────────────────────

    @Test
    void usesForwardedHeaderFromTrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Клиент дописал слева фальшивый хоп, прокси добавил справа настоящий —
     * берём самый правый недоверенный.
     */
    @Test
    void takesRightmostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void skipsTrailingTrustedProxiesInChain() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 10.0.0.7, 10.0.0.8")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToRemoteAddrWhenChainIsAllTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "10.0.0.7, 10.0.0.8")))
                .isEqualTo("10.0.0.5");
    }

    @Test
    void fallsBackToRemoteAddrWhenHeaderAbsentOrBlank() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "   "))).isEqualTo("10.0.0.5");
    }

    /** Мусор в цепочке — дальше ей верить нельзя, откатываемся на пира. */
    @Test
    void fallsBackToRemoteAddrOnMalformedChain() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, evil.example.com")))
                .isEqualTo("10.0.0.5");
    }

    @Test
    void reportsTrustedPeers() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES);
        assertThat(resolver.isTrusted("10.1.2.3")).isTrue();
        assertThat(resolver.isTrusted("203.0.113.9")).isFalse();
    }

    // ── Конфигурация прода: Cloudflare перед Railway ─────────────────────────

    /**
     * Значение, которое лежит в TRUSTED_PROXIES на Railway: loopback и
     * приватные диапазоны (TCP-пиром приложение видит внутренний прокси
     * Railway) плюс IPv4-диапазоны Cloudflare, который проксирует
     * api.groupmatch.app.
     *
     * Копия, а не источник истины: в yml список намеренно не зашит, чтобы
     * обновлять его переменной окружения без релиза. Тесты ниже проверяют
     * логику резолвинга на этом списке, а не актуальность самого списка —
     * за ней следить по cloudflare.com/ips-v4.
     */
    private static final String PROD_PROXIES = String.join(",",
            "127.0.0.1/32", "::1/128",
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "fd00::/8",
            // Cloudflare IPv4
            "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
            "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
            "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
            "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
            // Cloudflare IPv6 — нужны, потому что к origin с AAAA-записью
            // Cloudflare ходит по IPv6, и хопом в цепочке будет IPv6-адрес
            "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
            "2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32");

    /** По одному представителю каждого IPv4-диапазона Cloudflare. */
    private static final String[] CLOUDFLARE_SAMPLES = {
            "173.245.48.1", "103.21.244.1", "103.22.200.1", "103.31.4.1",
            "141.101.64.1", "108.162.192.1", "190.93.240.1", "188.114.96.1",
            "197.234.240.1", "198.41.128.1", "162.158.0.1", "104.16.0.1",
            "104.24.0.1", "172.64.0.1", "131.0.72.1"};

    /** По одному представителю каждого IPv6-диапазона Cloudflare. */
    private static final String[] CLOUDFLARE_SAMPLES_V6 = {
            "2400:cb00::1", "2606:4700::1", "2803:f800::1", "2405:b500::1",
            "2405:8100::1", "2a06:98c0::1", "2c0f:f248::1"};

    @Test
    void prodListParsesAndCoversEveryCloudflareRange() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        for (String ip : CLOUDFLARE_SAMPLES) {
            assertThat(resolver.isTrusted(ip)).as("диапазон Cloudflare с адресом %s", ip).isTrue();
        }
        // Приватные диапазоны никуда не делись — ими приходит пир Railway.
        assertThat(resolver.isTrusted("10.0.0.5")).isTrue();
        assertThat(resolver.isTrusted("100.64.1.1")).isTrue();
    }

    @Test
    void prodListCoversEveryCloudflareIpv6Range() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        for (String ip : CLOUDFLARE_SAMPLES_V6) {
            assertThat(resolver.isTrusted(ip)).as("IPv6-диапазон Cloudflare с адресом %s", ip).isTrue();
        }
        // Тот же /29, но у дальней границы: значимы 5 бит, диапазон c0…c7.
        assertThat(resolver.isTrusted("2a06:98c7:ffff:ffff:ffff:ffff:ffff:ffff")).isTrue();
    }

    /** Обычный пользователь не должен случайно попасть в доверенные. */
    @Test
    void prodListDoesNotTrustArbitraryAddresses() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        for (String ip : new String[]{"203.0.113.9", "8.8.8.8", "173.245.64.1",
                                      "104.28.0.1", "172.32.0.1", "131.0.76.1"}) {
            assertThat(resolver.isTrusted(ip)).as("%s не должен считаться прокси", ip).isFalse();
        }
    }

    /** Соседние по номеру IPv6-адреса — уже не Cloudflare. */
    @Test
    void prodListDoesNotTrustArbitraryIpv6Addresses() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        for (String ip : new String[]{
                "2001:db8::1",             // документационный диапазон
                "2400:cb01::1",            // соседний к 2400:cb00::/32
                "2606:4701::1",            // соседний к 2606:4700::/32
                "2a06:98c8::1",            // сразу за границей /29
                "2a06:98bf:ffff::1",       // сразу перед границей /29
                "2c0f:f249::1"}) {         // соседний к 2c0f:f248::/32
            assertThat(resolver.isTrusted(ip)).as("%s не должен считаться прокси", ip).isFalse();
        }
    }

    /**
     * Целевой сценарий после переключения: клиент → Cloudflare → Railway → мы.
     * Railway дописывает в цепочку адрес своего пира (Cloudflare), поэтому
     * самый правый хоп — это Cloudflare, а пользователь левее.
     */
    @Test
    void resolvesRealClientBehindCloudflare() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 162.158.0.1")))
                .as("нужен адрес пользователя, а не эджа Cloudflare")
                .isEqualTo("203.0.113.9");
    }

    /**
     * Смешанная цепочка: Cloudflare пошёл к origin по IPv6 (у Railway есть
     * AAAA), поэтому хоп — IPv6, а пир от Railway и сам клиент — IPv4.
     * Ровно тот случай, ради которого IPv6-диапазоны и добавлены: без них
     * вернулся бы адрес эджа.
     */
    @Test
    void resolvesIpv4ClientBehindIpv6CloudflareHop() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 2606:4700::1")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 2a06:98c0::1")))
                .isEqualTo("203.0.113.9");
    }

    /** Обратный случай: клиент по IPv6, эдж Cloudflare — по IPv4. */
    @Test
    void resolvesIpv6ClientBehindIpv4CloudflareHop() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "2001:db8::42, 162.158.0.1")))
                .isEqualTo("2001:db8::42");
    }

    /** Всё по IPv6, включая пира. */
    @Test
    void resolvesFullyIpv6Chain() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("fd00::5", "2001:db8::42, 2400:cb00::1")))
                .isEqualTo("2001:db8::42");
    }

    /** Длинная смешанная цепочка: несколько хопов Cloudflare обеих версий. */
    @Test
    void skipsMixedTrailingCloudflareHops() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(
                request("10.0.0.5", "203.0.113.9, 2606:4700::1, 104.16.0.1, 2a06:98c0::9")))
                .isEqualTo("203.0.113.9");
    }

    /** Подделка с IPv6-хопом Cloudflare тоже не помогает. */
    @Test
    void forgedIpv6CloudflareHopDoesNotHelp() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 2606:4700::1, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /** Двое пользователей за одним эджем Cloudflare не должны слиться в один IP. */
    @Test
    void separatesUsersSharingOneCloudflareEdge() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        String first = resolver.resolve(request("10.0.0.5", "203.0.113.9, 104.16.0.1"));
        String second = resolver.resolve(request("10.0.0.5", "198.51.100.7, 104.16.0.1"));
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo("203.0.113.9");
        assertThat(second).isEqualTo("198.51.100.7");
    }

    // ── Регрессия к P0: подделка XFF по-прежнему не проходит ────────────────

    /**
     * Атакующий стучится прямо в Railway, минуя Cloudflare, и подставляет
     * чужой XFF. Railway допишет его настоящий адрес справа — по нему и
     * считаем, лимит не обходится.
     */
    @Test
    void forgedForwardedForStillCannotBypassLimit() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "9.9.9.9, 8.8.8.8, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Самый неприятный вариант подделки: атакующий дописывает справа адрес
     * Cloudflare, изображая проход через прокси. Пока его собственный адрес
     * (который Railway допишет ещё правее) не из доверенных, подмена не
     * работает — берётся именно он.
     */
    @Test
    void forgedCloudflareHopDoesNotHelp() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 162.158.0.1, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /** Клиент ходит напрямую (недоверенный пир) — XFF игнорируется целиком. */
    @Test
    void directRequestIgnoresForwardedForEvenWithProdList() {
        ClientIpResolver resolver = new ClientIpResolver(PROD_PROXIES);
        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4, 162.158.0.1")))
                .isEqualTo("203.0.113.9");
    }
}
