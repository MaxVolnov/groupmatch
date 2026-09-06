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
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxiesConfigured() {
        ClientIpResolver resolver = new ClientIpResolver("", false);
        assertThat(resolver.resolve(request("127.0.0.1", "1.2.3.4")))
                .isEqualTo("127.0.0.1");
    }

    // ── Нормальная работа за прокси ──────────────────────────────────────────

    @Test
    void usesForwardedHeaderFromTrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Клиент дописал слева фальшивый хоп, прокси добавил справа настоящий —
     * берём самый правый недоверенный.
     */
    @Test
    void takesRightmostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void skipsTrailingTrustedProxiesInChain() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 10.0.0.7, 10.0.0.8")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToRemoteAddrWhenChainIsAllTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", "10.0.0.7, 10.0.0.8")))
                .isEqualTo("10.0.0.5");
    }

    @Test
    void fallsBackToRemoteAddrWhenHeaderAbsentOrBlank() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "   "))).isEqualTo("10.0.0.5");
    }

    /** Мусор в цепочке — дальше ей верить нельзя, откатываемся на пира. */
    @Test
    void fallsBackToRemoteAddrOnMalformedChain() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, evil.example.com")))
                .isEqualTo("10.0.0.5");
    }

    @Test
    void reportsTrustedPeers() {
        ClientIpResolver resolver = new ClientIpResolver(PROXIES, false);
        assertThat(resolver.isTrusted("10.1.2.3")).isTrue();
        assertThat(resolver.isTrusted("203.0.113.9")).isFalse();
    }

    // ── Конфигурация прода: Cloudflare перед приложением ─────────────────────

    /**
     * Настоящее значение TRUSTED_PROXIES в проде: loopback и приватные
     * диапазоны. TCP-пиром приложение видит внутренний прокси площадки, он
     * оттуда и приходит.
     *
     * Диапазонов Cloudflare здесь нет и быть не должно — их добавляет флаг
     * app.trust-cloudflare-proxy, и добавляет из одного места
     * ({@link CloudflareRanges}). Раньше в этом файле лежала вторая копия
     * списка «как в проде»; копия успела протухнуть — из прода диапазоны убрали
     * при переезде на Timeweb, а здесь они остались, и комментарий над ними
     * утверждал, что это и есть боевое значение.
     */
    private static final String PRIVATE_PROXIES = String.join(",",
            "127.0.0.1/32", "::1/128",
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "fd00::/8");

    /** Резолвер в режиме «оранжевое облако включено». */
    private static ClientIpResolver cloudflareOn() {
        return new ClientIpResolver(PRIVATE_PROXIES, true);
    }

    /** Резолвер в сегодняшнем режиме: проксирование выключено, флаг тоже. */
    private static ClientIpResolver cloudflareOff() {
        return new ClientIpResolver(PRIVATE_PROXIES, false);
    }

    /**
     * По одному представителю каждого диапазона — сам адрес сети.
     *
     * Выводится из {@link CloudflareRanges}, а не переписывается руками: третья
     * копия списка разошлась бы с первыми двумя ровно так же, как разошлась
     * предыдущая.
     */
    private static java.util.List<String> cloudflareSamples() {
        return CloudflareRanges.all().stream().map(cidr -> cidr.split("/")[0]).toList();
    }

    /**
     * ⑥ Каждый диапазон из списка разбирается и доверяется — и IPv4, и IPv6.
     *
     * IPv6 здесь наравне с IPv4 не для симметрии: у origin есть AAAA-запись, и
     * Cloudflare ходит к нему по IPv6, так что хопом в цепочке будет
     * IPv6-адрес. Без этих диапазонов вернулся бы адрес эджа.
     */
    @Test
    void everyCloudflareRangeIsTrustedWhenFlagIsOn() {
        ClientIpResolver resolver = cloudflareOn();
        for (String ip : cloudflareSamples()) {
            assertThat(resolver.isTrusted(ip)).as("диапазон Cloudflare с адресом %s", ip).isTrue();
        }
        // Приватные диапазоны никуда не делись — ими приходит пир площадки.
        assertThat(resolver.isTrusted("10.0.0.5")).isTrue();
        assertThat(resolver.isTrusted("100.64.1.1")).isTrue();
    }

    /** ① Тот же список при выключенном флаге не доверяется ни одним адресом. */
    @Test
    void noCloudflareRangeIsTrustedWhenFlagIsOff() {
        ClientIpResolver resolver = cloudflareOff();
        for (String ip : cloudflareSamples()) {
            assertThat(resolver.isTrusted(ip))
                    .as("при выключенном флаге %s не должен считаться прокси", ip)
                    .isFalse();
        }
        // А приватные — по-прежнему да: сегодняшнее поведение не меняется.
        assertThat(resolver.isTrusted("10.0.0.5")).isTrue();
    }

    /** Обычный пользователь не должен случайно попасть в доверенные. */
    @Test
    void prodListDoesNotTrustArbitraryAddresses() {
        ClientIpResolver resolver = cloudflareOn();
        for (String ip : new String[]{"203.0.113.9", "8.8.8.8", "173.245.64.1",
                                      "104.28.0.1", "172.32.0.1", "131.0.76.1"}) {
            assertThat(resolver.isTrusted(ip)).as("%s не должен считаться прокси", ip).isFalse();
        }
    }

    /** Соседние по номеру IPv6-адреса — уже не Cloudflare. */
    @Test
    void prodListDoesNotTrustArbitraryIpv6Addresses() {
        ClientIpResolver resolver = cloudflareOn();
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
     * Целевой сценарий после переключения: клиент → Cloudflare → прокси
     * площадки → мы. Прокси дописывает в цепочку адрес своего пира
     * (Cloudflare), поэтому самый правый хоп — это Cloudflare, а пользователь
     * левее.
     */
    @Test
    void resolvesRealClientBehindCloudflare() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 162.158.0.1")))
                .as("нужен адрес пользователя, а не эджа Cloudflare")
                .isEqualTo("203.0.113.9");
    }

    /**
     * Смешанная цепочка: Cloudflare пошёл к origin по IPv6 (AAAA у origin
     * есть), поэтому хоп — IPv6, а пир от площадки и сам клиент — IPv4.
     * Ровно тот случай, ради которого IPv6-диапазоны и добавлены: без них
     * вернулся бы адрес эджа.
     */
    @Test
    void resolvesIpv4ClientBehindIpv6CloudflareHop() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 2606:4700::1")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 2a06:98c0::1")))
                .isEqualTo("203.0.113.9");
    }

    /** Обратный случай: клиент по IPv6, эдж Cloudflare — по IPv4. */
    @Test
    void resolvesIpv6ClientBehindIpv4CloudflareHop() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "2001:db8::42, 162.158.0.1")))
                .isEqualTo("2001:db8::42");
    }

    /** Всё по IPv6, включая пира. */
    @Test
    void resolvesFullyIpv6Chain() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("fd00::5", "2001:db8::42, 2400:cb00::1")))
                .isEqualTo("2001:db8::42");
    }

    /** Длинная смешанная цепочка: несколько хопов Cloudflare обеих версий. */
    @Test
    void skipsMixedTrailingCloudflareHops() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(
                request("10.0.0.5", "203.0.113.9, 2606:4700::1, 104.16.0.1, 2a06:98c0::9")))
                .isEqualTo("203.0.113.9");
    }

    /** Подделка с IPv6-хопом Cloudflare тоже не помогает. */
    @Test
    void forgedIpv6CloudflareHopDoesNotHelp() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 2606:4700::1, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /** Двое пользователей за одним эджем Cloudflare не должны слиться в один IP. */
    @Test
    void separatesUsersSharingOneCloudflareEdge() {
        ClientIpResolver resolver = cloudflareOn();
        String first = resolver.resolve(request("10.0.0.5", "203.0.113.9, 104.16.0.1"));
        String second = resolver.resolve(request("10.0.0.5", "198.51.100.7, 104.16.0.1"));
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo("203.0.113.9");
        assertThat(second).isEqualTo("198.51.100.7");
    }

    // ── Регрессия к P0: подделка XFF по-прежнему не проходит ────────────────

    /**
     * Атакующий стучится прямо в origin, минуя Cloudflare, и подставляет
     * чужой XFF. Прокси площадки допишет его настоящий адрес справа — по нему и
     * считаем, лимит не обходится.
     */
    @Test
    void forgedForwardedForStillCannotBypassLimit() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "9.9.9.9, 8.8.8.8, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Самый неприятный вариант подделки: атакующий дописывает справа адрес
     * Cloudflare, изображая проход через прокси. Пока его собственный адрес
     * (который прокси площадки допишет ещё правее) не из доверенных, подмена не
     * работает — берётся именно он.
     */
    @Test
    void forgedCloudflareHopDoesNotHelp() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, 162.158.0.1, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /** Клиент ходит напрямую (недоверенный пир) — XFF игнорируется целиком. */
    @Test
    void directRequestIgnoresForwardedForEvenWithProdList() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4, 162.158.0.1")))
                .isEqualTo("203.0.113.9");
    }

    // ── Переключатель: что меняется, а что нет ───────────────────────────────

    /**
     * ① Флаг выключен, запрос пришёл прямо с адреса Cloudflare, заголовок
     * подделан. Берётся адрес пира, XFF не смотрим вовсе.
     *
     * Это сегодняшнее состояние прода, и правка не должна его трогать.
     */
    @Test
    void flagOffIgnoresForgedHeaderFromCloudflareAddress() {
        ClientIpResolver resolver = cloudflareOff();
        assertThat(resolver.resolve(request("162.158.0.1", "1.2.3.4")))
                .as("адрес Cloudflare без флага — обычный недоверенный пир")
                .isEqualTo("162.158.0.1");
    }

    /**
     * ② Флаг включён, пир — адрес Cloudflare, в XFF настоящий клиент.
     *
     * Такая топология возможна, если запрос от Cloudflare приходит в приложение
     * напрямую, без прокси площадки перед ним. Сегодня это не так (пиром
     * приходит внутренний прокси, см. тесты выше), но правка обязана работать
     * в обоих случаях: топология фронта — не то, что мы контролируем.
     */
    @Test
    void flagOnResolvesRealClientWhenPeerIsCloudflareItself() {
        ClientIpResolver resolver = cloudflareOn();
        assertThat(resolver.resolve(request("162.158.0.1", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * ③ Главный тест. Флаг включён, но запрос пришёл с адреса, которого нет ни
     * в диапазонах Cloudflare, ни в приватных сетях, и XFF подделан.
     *
     * Включение флага не должно открывать доверие кому попало: список
     * расширяется конкретными диапазонами, а не превращается в «доверять всем».
     */
    @Test
    void flagOnStillIgnoresForgedHeaderFromStranger() {
        ClientIpResolver resolver = cloudflareOn();
        for (String stranger : new String[]{"203.0.113.9", "8.8.8.8", "2001:db8::42"}) {
            // Проверяется и причина, а не только результат. Без этой строки тест
            // проходил бы и при «доверяем всем»: тогда доверенной оказывается вся
            // цепочка, резолвер сваливается на адрес пира — и ответ совпадает с
            // ожидаемым по неверной причине. Обнаружено саботажем.
            assertThat(resolver.isTrusted(stranger))
                    .as("%s не должен становиться доверенным от включения флага", stranger)
                    .isFalse();

            assertThat(resolver.resolve(request(stranger, "1.2.3.4, 9.9.9.9")))
                    .as("подделка от %s не должна проходить", stranger)
                    .isEqualTo(stranger);
        }
    }

    /**
     * ④ CF-Connecting-IP не читается — ни при включённом флаге, ни при
     * выключенном, ни от доверенного пира, ни от постороннего.
     *
     * Решение осознанное. Заголовок сам по себе надёжнее разбора цепочки, но
     * доверять ему можно только зная, что запрос действительно прошёл через
     * Cloudflare. Пир этого не доказывает: перед приложением стоит прокси
     * площадки, он доверенный всегда, и запрос к origin в обход Cloudflare
     * приходит с тем же пиром. Прочитать заголовок «когда пир доверенный»
     * означало бы принимать чужой адрес от любого, кто знает прямой адрес
     * origin, — дыра ровно того класса, от которой защищались.
     *
     * Разбор XFF такой ошибки не допускает: прокси площадки дописывает в
     * цепочку настоящий адрес отправителя, и он оказывается самым правым
     * недоверенным.
     */
    @Test
    void cfConnectingIpIsNeverUsed() {
        for (ClientIpResolver resolver : new ClientIpResolver[]{cloudflareOn(), cloudflareOff()}) {
            MockHttpServletRequest forgedByStranger = request("203.0.113.9", null);
            forgedByStranger.addHeader("CF-Connecting-IP", "1.2.3.4");
            assertThat(resolver.resolve(forgedByStranger))
                    .as("заголовок от постороннего")
                    .isEqualTo("203.0.113.9");

            MockHttpServletRequest forgedThroughPlatformProxy = request("10.0.0.5", null);
            forgedThroughPlatformProxy.addHeader("CF-Connecting-IP", "1.2.3.4");
            assertThat(resolver.resolve(forgedThroughPlatformProxy))
                    .as("заголовок, пришедший через доверенный прокси площадки")
                    .isEqualTo("10.0.0.5");
        }
    }

    /**
     * ⑤ Границы диапазонов: последний адрес внутри и первый снаружи.
     *
     * Ошибка на единицу в маске не видна ни на глаз, ни на «одном адресе из
     * середины» — а стоит она либо лишнего доверия соседней сети, либо общей
     * корзины лимитера для края диапазона Cloudflare.
     */
    @Test
    void rangeBoundariesAreExact() {
        ClientIpResolver resolver = cloudflareOn();

        // 173.245.48.0/20 → 173.245.48.0 … 173.245.63.255
        assertThat(resolver.isTrusted("173.245.48.0")).as("первый внутри").isTrue();
        assertThat(resolver.isTrusted("173.245.63.255")).as("последний внутри").isTrue();
        assertThat(resolver.isTrusted("173.245.47.255")).as("на единицу до").isFalse();
        assertThat(resolver.isTrusted("173.245.64.0")).as("на единицу после").isFalse();

        // 131.0.72.0/22 → 131.0.72.0 … 131.0.75.255
        assertThat(resolver.isTrusted("131.0.75.255")).as("последний внутри").isTrue();
        assertThat(resolver.isTrusted("131.0.76.0")).as("на единицу после").isFalse();

        // 104.16.0.0/13 и 104.24.0.0/14 — соседние, но между ними нет разрыва
        assertThat(resolver.isTrusted("104.23.255.255")).isTrue();
        assertThat(resolver.isTrusted("104.24.0.0")).isTrue();
        assertThat(resolver.isTrusted("104.27.255.255")).as("конец /14").isTrue();
        assertThat(resolver.isTrusted("104.28.0.0")).as("сразу за /14").isFalse();

        // 172.64.0.0/13 не должен задевать приватный 172.16.0.0/12
        assertThat(resolver.isTrusted("172.71.255.255")).as("конец диапазона Cloudflare").isTrue();
        assertThat(resolver.isTrusted("172.72.0.0")).as("сразу за ним").isFalse();

        // IPv6: 2a06:98c0::/29 → значимы 5 бит старшего байта, c0…c7
        assertThat(resolver.isTrusted("2a06:98c0::")).as("первый внутри").isTrue();
        assertThat(resolver.isTrusted("2a06:98c7:ffff:ffff:ffff:ffff:ffff:ffff"))
                .as("последний внутри").isTrue();
        assertThat(resolver.isTrusted("2a06:98bf:ffff:ffff:ffff:ffff:ffff:ffff"))
                .as("на единицу до").isFalse();
        assertThat(resolver.isTrusted("2a06:98c8::")).as("на единицу после").isFalse();
    }

    /** Режим виден снаружи — это читает диагностика и лог старта. */
    @Test
    void modeIsExposed() {
        assertThat(cloudflareOn().isCloudflareTrusted()).isTrue();
        assertThat(cloudflareOff().isCloudflareTrusted()).isFalse();
    }

    /**
     * Пустой TRUSTED_PROXIES с включённым флагом означает «доверяем только
     * Cloudflare»: список не пуст, в нём её диапазоны.
     *
     * Так и задумано — флаг говорит «Cloudflare перед нами», и base-список к
     * этому отношения не имеет. Но конфигурация всё равно неполная: пир от
     * прокси площадки (приватный адрес) в такой сборке доверенным не будет, и
     * лимиты посчитаются по одному адресу на всех. Тест фиксирует обе половины,
     * чтобы поведение было записано, а не выведено при разборе инцидента.
     */
    @Test
    void flagWithoutBaseListTrustsCloudflareOnly() {
        ClientIpResolver resolver = new ClientIpResolver("", true);

        assertThat(resolver.resolve(request("162.158.0.1", "203.0.113.9")))
                .as("пир из диапазона Cloudflare — цепочке верим")
                .isEqualTo("203.0.113.9");

        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9")))
                .as("а прокси площадки в такой сборке не доверенный")
                .isEqualTo("10.0.0.5");
    }
}
