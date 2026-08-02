package com.groupmatch.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Сопоставление IP-адреса с CIDR-диапазоном без обращения к DNS.
 *
 * Литералы парсятся вручную, а не через {@link java.net.InetAddress#getByName},
 * потому что вход недоверенный (заголовок X-Forwarded-For): getByName на
 * произвольной строке уходит в резолвер и превращает заголовок в SSRF/DoS-вектор.
 *
 * Поддерживаются IPv4 и IPv6, включая IPv4-mapped-адреса (::ffff:1.2.3.4) —
 * они нормализуются к 4 байтам, чтобы совпадать с IPv4-диапазонами.
 */
public final class CidrMatcher {

    private final byte[] network;
    private final int prefixBits;

    private CidrMatcher(byte[] network, int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    /** {@code "10.0.0.0/8"}, {@code "2a02:5180::/32"} или одиночный адрес (маска по умолчанию — полная). */
    public static CidrMatcher parse(String cidr) {
        if (cidr == null) throw new IllegalArgumentException("CIDR is null");
        String s = cidr.trim();
        int slash = s.indexOf('/');
        String addrPart = slash < 0 ? s : s.substring(0, slash);

        byte[] network = parseIp(addrPart);
        if (network == null) throw new IllegalArgumentException("Invalid IP in CIDR: " + cidr);

        int maxBits = network.length * 8;
        int prefix = maxBits;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(s.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid prefix in CIDR: " + cidr);
            }
            if (prefix < 0 || prefix > maxBits) {
                throw new IllegalArgumentException("Prefix out of range in CIDR: " + cidr);
            }
        }
        return new CidrMatcher(network, prefix);
    }

    /** Разбирает список вида {@code "10.0.0.0/8, ::1/128"}; пустые элементы игнорируются. */
    public static List<CidrMatcher> parseList(String csv) {
        List<CidrMatcher> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(parse(trimmed));
        }
        return out;
    }

    public static boolean anyMatch(List<CidrMatcher> ranges, String ip) {
        return anyMatch(ranges, parseIp(ip));
    }

    public static boolean anyMatch(List<CidrMatcher> ranges, byte[] address) {
        if (address == null) return false;
        for (CidrMatcher range : ranges) {
            if (range.matches(address)) return true;
        }
        return false;
    }

    public boolean matches(String ip) {
        return matches(parseIp(ip));
    }

    public boolean matches(byte[] address) {
        if (address == null || address.length != network.length) return false;
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != network[i]) return false;
        }
        if (remainingBits > 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            if (((address[fullBytes] ^ network[fullBytes]) & mask) != 0) return false;
        }
        return true;
    }

    // ─── Разбор литералов ────────────────────────────────────────────────────

    /** @return 4 или 16 байт, либо {@code null}, если строка не является IP-литералом. */
    public static byte[] parseIp(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);

        int zone = s.indexOf('%'); // scope id: fe80::1%eth0
        if (zone >= 0) s = s.substring(0, zone);

        byte[] parsed = s.indexOf(':') >= 0 ? parseIpv6(s) : parseIpv4(s);
        return parsed == null ? null : unmapIpv4(parsed);
    }

    private static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) return null;
            int value = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') return null;
                value = value * 10 + (c - '0');
            }
            if (value > 255) return null;
            out[i] = (byte) value;
        }
        return out;
    }

    private static byte[] parseIpv6(String input) {
        String s = input;

        // Встроенный IPv4-хвост (::ffff:1.2.3.4) → две шестнадцатеричные группы.
        int lastColon = s.lastIndexOf(':');
        if (lastColon < 0) return null;
        String tailToken = s.substring(lastColon + 1);
        if (tailToken.indexOf('.') >= 0) {
            byte[] v4 = parseIpv4(tailToken);
            if (v4 == null) return null;
            s = s.substring(0, lastColon + 1) + String.format("%02x%02x:%02x%02x",
                    v4[0] & 0xFF, v4[1] & 0xFF, v4[2] & 0xFF, v4[3] & 0xFF);
        }

        int shorthand = s.indexOf("::");
        if (shorthand != s.lastIndexOf("::")) return null; // "::" может быть только одно

        String head = shorthand < 0 ? s : s.substring(0, shorthand);
        String tail = shorthand < 0 ? "" : s.substring(shorthand + 2);

        int[] headGroups = parseHextets(head);
        int[] tailGroups = parseHextets(tail);
        if (headGroups == null || tailGroups == null) return null;

        int total = headGroups.length + tailGroups.length;
        if (shorthand < 0 ? total != 8 : total > 7) return null;

        byte[] out = new byte[16];
        int i = 0;
        for (int g : headGroups) {
            out[i++] = (byte) (g >> 8);
            out[i++] = (byte) g;
        }
        i = 16 - tailGroups.length * 2;
        for (int g : tailGroups) {
            out[i++] = (byte) (g >> 8);
            out[i++] = (byte) g;
        }
        return out;
    }

    private static int[] parseHextets(String s) {
        if (s.isEmpty()) return new int[0];
        String[] parts = s.split(":", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 4) return null;
            int value = 0;
            for (int j = 0; j < p.length(); j++) {
                int digit = Character.digit(p.charAt(j), 16);
                if (digit < 0) return null;
                value = value * 16 + digit;
            }
            out[i] = value;
        }
        return out;
    }

    /** ::ffff:a.b.c.d → a.b.c.d, чтобы IPv4-диапазоны совпадали с IPv4-mapped-адресами. */
    private static byte[] unmapIpv4(byte[] address) {
        if (address.length != 16) return address;
        for (int i = 0; i < 10; i++) {
            if (address[i] != 0) return address;
        }
        if ((address[10] & 0xFF) != 0xFF || (address[11] & 0xFF) != 0xFF) return address;
        return new byte[]{address[12], address[13], address[14], address[15]};
    }
}
