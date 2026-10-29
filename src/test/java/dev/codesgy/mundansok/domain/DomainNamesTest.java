package dev.codesgy.mundansok.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codesgy.mundansok.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainNamesTest {

    @Test
    void 주소에서_스킴_경로_포트_대소문자를_정리한다() {
        assertThat(DomainNames.normalize("  HTTPS://Shop.Example.com:8443/login?next=/ ")).isEqualTo("shop.example.com");
        assertThat(DomainNames.normalize("example.com.")).isEqualTo("example.com");
    }

    @Test
    void 한글_도메인은_퓨니코드로_바꾼다() {
        assertThat(DomainNames.normalize("한글.kr")).isEqualTo("xn--bj0bj06e.kr");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "intranet", "10.0.0.1", "[::1]", "printer.local", "api.internal", "exa mple.com", "-bad.com", "a.123"})
    void 점검할_수_없는_값은_거절한다(String input) {
        assertThatThrownBy(() -> DomainNames.normalize(input))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_DOMAIN");
    }
}
