package dev.codesgy.mundansok.net;

import static dev.codesgy.mundansok.net.AddressGuardTest.ip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codesgy.mundansok.config.ScanProperties;
import dev.codesgy.mundansok.net.AddressGuard.BlockedAddressException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** TLS 점검도 검사한 IP로만 연결하는지 (실제 연결은 하지 않고 연결하려던 주소만 본다) */
class JdkTlsInspectorTest {

    private static final ScanProperties PROPERTIES =
            new ScanProperties(Duration.ofMinutes(10), Duration.ofSeconds(1), "MundansokTest/1.0", Duration.ofMinutes(15));

    private final List<InetSocketAddress> connectedTo = new CopyOnWriteArrayList<>();

    private final SocketConnector refusing = (address, timeout) -> {
        connectedTo.add(address);
        throw new IOException("Connection refused");
    };

    @Test
    void 리바인딩_DNS여도_검사한_IP로만_연결한다() {
        AtomicInteger calls = new AtomicInteger();
        HostResolver rebinding = host -> calls.getAndIncrement() == 0
                ? new InetAddress[] {ip("203.0.113.10")}
                : new InetAddress[] {ip("10.0.0.5")};
        JdkTlsInspector inspector = new JdkTlsInspector(PROPERTIES, new AddressGuard(rebinding), refusing);

        assertThatThrownBy(() -> inspector.inspect("rebind.example.com")).isInstanceOf(UncheckedIOException.class);

        assertThat(calls).hasValue(1);
        assertThat(connectedTo).isNotEmpty().allSatisfy(address -> {
            assertThat(address.isUnresolved()).isFalse();
            assertThat(address.getAddress()).isEqualTo(ip("203.0.113.10"));
            assertThat(address.getPort()).isEqualTo(443);
        });
    }

    @Test
    void 내부_IP면_연결하지_않는다() {
        JdkTlsInspector inspector =
                new JdkTlsInspector(PROPERTIES, new AddressGuard(host -> new InetAddress[] {ip("64:ff9b::a00:1")}), refusing);
        assertThatThrownBy(() -> inspector.inspect("nat64.example.com")).isInstanceOf(BlockedAddressException.class);
        assertThat(connectedTo).isEmpty();
    }
}
