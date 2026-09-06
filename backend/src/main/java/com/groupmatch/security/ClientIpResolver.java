package com.groupmatch.security;

import com.groupmatch.util.CidrMatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Определяет реальный IP клиента.
 *
 * X-Forwarded-For подделывается одной строкой в curl, поэтому заголовок
 * учитывается только если TCP-пир (RemoteAddr) — доверенный прокси из
 * {@code app.trusted-proxies}. Иначе используется RemoteAddr.
 *
 * Из цепочки берётся самый правый недоверенный хоп: всё левее него написал
 * клиент, всё правее — наши прокси.
 *
 * Пустой список доверенных прокси = «мы не за прокси», XFF игнорируется всегда.
 *
 * <h2>Проксирование Cloudflare</h2>
 *
 * Флаг {@code app.trust-cloudflare-proxy} добавляет к списку официальные
 * диапазоны Cloudflare ({@link CloudflareRanges}). Выключен по умолчанию, и
 * пока он выключен, поведение ровно прежнее — ни одной новой ветки на пути
 * запроса.
 *
 * Включать его нужно ДО того, как в панели Cloudflare зажгут «оранжевое
 * облако», и вот почему. Cloudflare дописывает адрес клиента в
 * {@code X-Forwarded-For} и передаёт запрос дальше; фронтовый прокси площадки
 * дописывает туда же адрес узла Cloudflare, с которого пришёл запрос. Цепочка
 * получается вида {@code <клиент>, <edge Cloudflare>}. Пока диапазоны
 * Cloudflare не считаются доверенными, самым правым недоверенным хопом
 * оказывается edge-узел — и все, кого он обслуживает, попадают в одну корзину
 * лимитера. Стоит добавить диапазоны, и разбор цепочки проскакивает edge и
 * доходит до настоящего клиента.
 *
 * ⚠️ Флаг НЕ делает доверенным кого попало: он добавляет к списку конкретные
 * диапазоны, а всё, что вне их и вне приватных сетей, доверенным не становится.
 * Прямой запрос к origin в обход Cloudflare с подделанным заголовком по-прежнему
 * ничего не даёт — прокси площадки допишет в цепочку настоящий адрес
 * отправителя, и он окажется самым правым недоверенным.
 */
@Component
@Slf4j
public class ClientIpResolver {

    private static final String XFF_HEADER = "X-Forwarded-For";

    /** Ограничение на длину цепочки: защита от гигантского заголовка. */
    private static final int MAX_HOPS = 16;

    private final List<CidrMatcher> trustedProxies;
    private final String trustedProxiesRaw;
    private final boolean trustCloudflare;

    public ClientIpResolver(@Value("${app.trusted-proxies:}") String trustedProxies,
                            @Value("${app.trust-cloudflare-proxy:false}") boolean trustCloudflare) {
        this.trustedProxiesRaw = trustedProxies == null ? "" : trustedProxies.trim();
        this.trustCloudflare = trustCloudflare;

        List<CidrMatcher> ranges = new ArrayList<>(CidrMatcher.parseList(trustedProxies));
        if (trustCloudflare) {
            CloudflareRanges.all().forEach(cidr -> ranges.add(CidrMatcher.parse(cidr)));
        }
        this.trustedProxies = List.copyOf(ranges);

        logConfiguration();
    }

    /**
     * Режим печатается при старте целиком и явно.
     *
     * От этих двух строк зависит, как считаются лимиты на вход и регистрацию, а
     * узнают об этом обычно при разборе инцидента — когда гадать «а включён ли
     * там флаг» дороже всего. Список Cloudflare печатается счётчиком, а не
     * целиком: двадцать две строки в логе старта никто не читает, а факт
     * «режим включён» из счётчика виден так же.
     */
    private void logConfiguration() {
        if (trustedProxies.isEmpty()) {
            log.info("ClientIpResolver: список доверенных прокси пуст — X-Forwarded-For игнорируется");
            return;
        }
        if (trustCloudflare) {
            log.info("ClientIpResolver: доверенные прокси = {} + диапазоны Cloudflare ({})",
                    trustedProxiesRaw, CloudflareRanges.summary());
            log.info("ClientIpResolver: проксирование Cloudflare — ДОВЕРЯЕМ "
                    + "(app.trust-cloudflare-proxy=true). Флаг обязан совпадать с состоянием "
                    + "оранжевого облака в панели Cloudflare: включён без облака — лишнее "
                    + "доверие, облако без флага — общая корзина лимитера на всех.");
        } else {
            log.info("ClientIpResolver: доверенные прокси = {}", trustedProxiesRaw);
            log.info("ClientIpResolver: проксирование Cloudflare — НЕ доверяем "
                    + "(app.trust-cloudflare-proxy=false, режим по умолчанию)");
        }
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader(XFF_HEADER);
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }

        String[] hops = forwarded.split(",", MAX_HOPS + 1);
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            byte[] parsed = CidrMatcher.parseIp(hop);
            if (parsed == null) {
                // Мусор в цепочке — дальше ей верить нельзя.
                log.debug("Malformed X-Forwarded-For entry from proxy {}: {}", remoteAddr, hop);
                return remoteAddr;
            }
            if (!CidrMatcher.anyMatch(trustedProxies, parsed)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    public boolean isTrusted(String ip) {
        return CidrMatcher.anyMatch(trustedProxies, ip);
    }

    /** Включён ли режим доверия Cloudflare. Нужен диагностике и тестам. */
    public boolean isCloudflareTrusted() {
        return trustCloudflare;
    }
}
