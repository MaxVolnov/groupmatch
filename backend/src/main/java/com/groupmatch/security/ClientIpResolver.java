package com.groupmatch.security;

import com.groupmatch.util.CidrMatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 */
@Component
@Slf4j
public class ClientIpResolver {

    private static final String XFF_HEADER = "X-Forwarded-For";

    /** Ограничение на длину цепочки: защита от гигантского заголовка. */
    private static final int MAX_HOPS = 16;

    private final List<CidrMatcher> trustedProxies;
    private final String trustedProxiesRaw;

    public ClientIpResolver(@Value("${app.trusted-proxies:}") String trustedProxies) {
        this.trustedProxiesRaw = trustedProxies == null ? "" : trustedProxies.trim();
        this.trustedProxies = CidrMatcher.parseList(trustedProxies);
        if (this.trustedProxies.isEmpty()) {
            log.info("ClientIpResolver: список доверенных прокси пуст — X-Forwarded-For игнорируется");
        } else {
            log.info("ClientIpResolver: доверенные прокси = {}", this.trustedProxiesRaw);
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
}
