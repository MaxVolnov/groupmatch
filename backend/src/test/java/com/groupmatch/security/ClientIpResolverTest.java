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
}
