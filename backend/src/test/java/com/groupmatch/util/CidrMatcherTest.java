package com.groupmatch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CidrMatcherTest {

    @Test
    void matchesIpv4Range() {
        CidrMatcher m = CidrMatcher.parse("10.0.0.0/8");
        assertThat(m.matches("10.0.0.1")).isTrue();
        assertThat(m.matches("10.255.255.255")).isTrue();
        assertThat(m.matches("11.0.0.1")).isFalse();
        assertThat(m.matches("9.255.255.255")).isFalse();
    }

    @Test
    void respectsNonByteAlignedPrefix() {
        CidrMatcher m = CidrMatcher.parse("185.71.76.0/27"); // .0 – .31
        assertThat(m.matches("185.71.76.0")).isTrue();
        assertThat(m.matches("185.71.76.31")).isTrue();
        assertThat(m.matches("185.71.76.32")).isFalse();
    }

    @Test
    void singleAddressIsExactMatch() {
        CidrMatcher m = CidrMatcher.parse("77.75.156.11");
        assertThat(m.matches("77.75.156.11")).isTrue();
        assertThat(m.matches("77.75.156.12")).isFalse();
    }

    @Test
    void matchesIpv6Range() {
        CidrMatcher m = CidrMatcher.parse("2a02:5180::/32");
        assertThat(m.matches("2a02:5180::1")).isTrue();
        assertThat(m.matches("2a02:5180:ffff:ffff::abcd")).isTrue();
        assertThat(m.matches("2a02:5181::1")).isFalse();
        assertThat(m.matches("::1")).isFalse();
    }

    @Test
    void ipv4AndIpv6NeverCrossMatch() {
        assertThat(CidrMatcher.parse("0.0.0.0/0").matches("2a02:5180::1")).isFalse();
        assertThat(CidrMatcher.parse("::/0").matches("10.0.0.1")).isFalse();
    }

    @Test
    void ipv4MappedAddressIsNormalisedToIpv4() {
        assertThat(CidrMatcher.parse("10.0.0.0/8").matches("::ffff:10.1.2.3")).isTrue();
        assertThat(CidrMatcher.parse("10.0.0.0/8").matches("::ffff:11.1.2.3")).isFalse();
    }

    @Test
    void stripsBracketsAndZoneId() {
        assertThat(CidrMatcher.parse("fe80::/10").matches("[fe80::1%eth0]")).isTrue();
    }

    /** Ключевое требование: разбор литерала не должен уходить в DNS. */
    @Test
    void hostnamesAreRejectedNotResolved() {
        assertThat(CidrMatcher.parseIp("localhost")).isNull();
        assertThat(CidrMatcher.parseIp("evil.example.com")).isNull();
        assertThat(CidrMatcher.parseIp("")).isNull();
        assertThat(CidrMatcher.parseIp(null)).isNull();
    }

    @Test
    void rejectsMalformedLiterals() {
        assertThat(CidrMatcher.parseIp("10.0.0")).isNull();
        assertThat(CidrMatcher.parseIp("10.0.0.256")).isNull();
        assertThat(CidrMatcher.parseIp("10.0.0.01.5")).isNull();
        assertThat(CidrMatcher.parseIp("1::2::3")).isNull();
        assertThat(CidrMatcher.parseIp("12345::1")).isNull();
        assertThat(CidrMatcher.parseIp("1:2:3:4:5:6:7")).isNull();
    }

    @Test
    void rejectsMalformedCidr() {
        assertThatThrownBy(() -> CidrMatcher.parse("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CidrMatcher.parse("nope/8"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseListIgnoresBlanksAndAnyMatchWorks() {
        List<CidrMatcher> list = CidrMatcher.parseList(" 10.0.0.0/8 , , ::1/128 ");
        assertThat(list).hasSize(2);
        assertThat(CidrMatcher.anyMatch(list, "10.1.1.1")).isTrue();
        assertThat(CidrMatcher.anyMatch(list, "::1")).isTrue();
        assertThat(CidrMatcher.anyMatch(list, "8.8.8.8")).isFalse();
        assertThat(CidrMatcher.parseList("")).isEmpty();
        assertThat(CidrMatcher.parseList(null)).isEmpty();
    }
}
