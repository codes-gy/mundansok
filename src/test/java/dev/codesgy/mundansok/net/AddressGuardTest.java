package dev.codesgy.mundansok.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codesgy.mundansok.net.AddressGuard.BlockedAddressException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.10", "169.254.169.254", "100.64.0.1", "0.0.0.0",
            "198.18.0.1", "198.19.255.254", "192.0.0.8", "224.0.0.1", "255.255.255.255",
            "::1", "::", "fd00::1", "fe80::1", "fec0::1",
            "64:ff9b::7f00:1", "64:ff9b::808:808", "64:ff9b:1::a00:1", // NAT64 — 안의 IPv4와 관계없이 막는다
            "2002:7f00:1::1", "2002:0808:0808::1", // 6to4
            "2001:0:4136:e378:8000:63bf:3fff:fdd2", // Teredo
            "::127.0.0.1", "::10.0.0.1", // IPv4-compatible
            "2001:db8::1", "100::1"})
    void 내부_주소는_막는다(String ip) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(ip))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "203.0.113.10", "198.20.0.1", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void 공인_주소는_통과한다(String ip) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(ip))).isTrue();
    }

    @Test
    void IPv4_mapped_IPv6는_안의_IPv4로_판단한다() throws Exception {
        // InetAddress.getByName("::ffff:…")은 Inet4Address로 바꿔 버리므로 바이트로 직접 만든다
        assertThat(AddressGuard.isPublic(mapped(127, 0, 0, 1))).isFalse();
        assertThat(AddressGuard.isPublic(mapped(169, 254, 169, 254))).isFalse();
        assertThat(AddressGuard.isPublic(mapped(10, 0, 0, 1))).isFalse();
        assertThat(AddressGuard.isPublic(mapped(8, 8, 8, 8))).isTrue();
    }

    @Test
    void 풀린_주소_중_하나라도_내부면_막는다() {
        AddressGuard guard = new AddressGuard(host -> new InetAddress[] {ip("8.8.8.8"), ip("10.0.0.1")});
        assertThatThrownBy(() -> guard.resolvePublic("mixed.example.com"))
                .isInstanceOf(BlockedAddressException.class)
                .hasMessageContaining("10.0.0.1");
    }

    @Test
    void 검사한_주소를_돌려준다() {
        AddressGuard guard = new AddressGuard(host -> new InetAddress[] {ip("8.8.8.8"), ip("1.1.1.1")});
        assertThat(guard.resolvePublic("ok.example.com")).isEqualTo(ip("8.8.8.8"));
    }

    @Test
    void 이름을_못_풀면_막는다() {
        AddressGuard guard = new AddressGuard(host -> {
            throw new UnknownHostException(host);
        });
        assertThatThrownBy(() -> guard.resolvePublic("nx.example.com")).isInstanceOf(BlockedAddressException.class);
        AddressGuard empty = new AddressGuard(host -> new InetAddress[0]);
        assertThatThrownBy(() -> empty.resolvePublic("empty.example.com")).isInstanceOf(BlockedAddressException.class);
    }

    static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static InetAddress mapped(int a, int b, int c, int d) throws UnknownHostException {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        bytes[12] = (byte) a;
        bytes[13] = (byte) b;
        bytes[14] = (byte) c;
        bytes[15] = (byte) d;
        return Inet6Address.getByAddress(null, bytes, -1);
    }
}
