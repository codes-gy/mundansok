package dev.codesgy.perimeter.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.10", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1"})
    void 내부_주소는_막는다(String ip) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(ip))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "203.0.113.10", "2606:4700:4700::1111"})
    void 공인_주소는_통과한다(String ip) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(ip))).isTrue();
    }
}
