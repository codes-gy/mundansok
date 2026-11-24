package dev.codesgy.mundansok.net;

import static dev.codesgy.mundansok.net.AddressGuardTest.ip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codesgy.mundansok.config.ScanProperties;
import dev.codesgy.mundansok.net.AddressGuard.BlockedAddressException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DNS 리바인딩: 검사할 때는 공인 IP, 연결할 때는 내부 IP를 주는 DNS여도 검사한 IP로만 연결하는지 확인한다.
 * 실제 외부로 나가지 않도록 연결은 로컬 테스트 서버로 돌리고, "어느 주소로 연결하려 했는지"만 기록한다.
 */
class JdkHttpProbeTest {

    private static final ScanProperties PROPERTIES =
            new ScanProperties(Duration.ofMinutes(10), Duration.ofSeconds(3), "MundansokTest/1.0", Duration.ofMinutes(15));

    private ServerSocket server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<InetSocketAddress> connectedTo = new CopyOnWriteArrayList<>();
    private volatile String cannedResponse = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Test: a\r\nX-Test: b\r\n\r\nok";

    /** 요청받은 주소를 기록하고, 실제로는 로컬 테스트 서버로 연결한다 */
    private final SocketConnector recordingConnector = (address, timeout) -> {
        connectedTo.add(address);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), timeout);
        return socket;
    };

    @BeforeEach
    void startServer() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    requests.add(readRequestHead(client.getInputStream()));
                    client.getOutputStream().write(cannedResponse.getBytes(StandardCharsets.ISO_8859_1));
                    client.getOutputStream().flush();
                } catch (IOException e) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    /** 첫 조회는 공인 IP, 그다음부터는 내부 IP를 돌려주는 리바인딩 DNS */
    private static HostResolver rebinding(AtomicInteger calls) {
        return host -> calls.getAndIncrement() == 0
                ? new InetAddress[] {ip("203.0.113.10")}
                : new InetAddress[] {ip("127.0.0.1")};
    }

    private JdkHttpProbe probe(HostResolver resolver) {
        return new JdkHttpProbe(PROPERTIES, new AddressGuard(resolver), recordingConnector,
                (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    @Test
    void 검사한_IP로만_연결하고_이름을_다시_풀지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        JdkHttpProbe probe = probe(rebinding(calls));

        HttpResult result = probe.get("http://rebind.example.com/path?q=1", Map.of("Origin", "https://evil.example"));

        assertThat(calls).hasValue(1); // 이름은 한 번만 푼다
        assertThat(connectedTo).hasSize(1);
        InetSocketAddress target = connectedTo.getFirst();
        assertThat(target.isUnresolved()).isFalse();
        assertThat(target.getAddress()).isEqualTo(ip("203.0.113.10")); // 두 번째 답(127.0.0.1)이 아니라 검사한 IP
        assertThat(target.getPort()).isEqualTo(80);

        String request = requests.getFirst();
        assertThat(request).startsWith("GET /path?q=1 HTTP/1.1\r\n");
        assertThat(request).contains("\r\nHost: rebind.example.com\r\n", "\r\nOrigin: https://evil.example\r\n",
                "\r\nUser-Agent: MundansokTest/1.0\r\n");
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.bodyPrefix()).isEqualTo("ok");
        assertThat(result.values("X-Test")).containsExactly("a", "b");
    }

    @Test
    void 요청마다_다시_검사하므로_내부_IP로_바뀌면_연결하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        JdkHttpProbe probe = probe(rebinding(calls));

        probe.get("http://rebind.example.com/", Map.of());
        assertThatThrownBy(() -> probe.get("http://rebind.example.com/.env", Map.of()))
                .isInstanceOf(BlockedAddressException.class)
                .hasMessageContaining("127.0.0.1");
        assertThat(connectedTo).hasSize(1);
    }

    @Test
    void 처음부터_내부_IP면_연결하지_않는다() {
        JdkHttpProbe probe = probe(host -> new InetAddress[] {ip("169.254.169.254")});
        assertThatThrownBy(() -> probe.get("http://metadata.example.com/", Map.of()))
                .isInstanceOf(BlockedAddressException.class);
        assertThat(connectedTo).isEmpty();
    }

    @Test
    void 포트가_있으면_Host에_붙인다() {
        probe(host -> new InetAddress[] {ip("203.0.113.10")}).get("http://port.example.com:8080/", Map.of());
        assertThat(connectedTo.getFirst().getPort()).isEqualTo(8080);
        assertThat(requests.getFirst()).contains("\r\nHost: port.example.com:8080\r\n");
    }

    @Test
    void 헤더_주입은_거절한다() {
        JdkHttpProbe probe = probe(host -> new InetAddress[] {ip("203.0.113.10")});
        assertThatThrownBy(() -> probe.get("http://inject.example.com/", Map.of("Origin", "x\r\nX-Evil: 1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 연결이_안_되면_UncheckedIOException() {
        JdkHttpProbe probe = new JdkHttpProbe(PROPERTIES, new AddressGuard(host -> new InetAddress[] {ip("203.0.113.10")}),
                (address, timeout) -> {
                    throw new IOException("Connection refused");
                }, (SSLSocketFactory) SSLSocketFactory.getDefault());
        assertThatThrownBy(() -> probe.get("http://down.example.com/", Map.of())).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void chunked_본문을_풀고_1xx는_건너뛴다() throws IOException {
        String raw = "HTTP/1.1 103 Early Hints\r\nLink: </a.css>\r\n\r\n"
                + "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n"
                + "5;ext=1\r\nSECRE\r\n7\r\nT_KEY=1\r\n0\r\n\r\n";
        HttpResult result = JdkHttpProbe.readResponse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.bodyPrefix()).isEqualTo("SECRET_KEY=1");
        assertThat(result.header("content-type")).contains("text/plain");
    }

    @Test
    void 본문은_앞부분만_읽는다() throws IOException {
        String raw = "HTTP/1.1 200 OK\r\n\r\n" + "a".repeat(10_000);
        HttpResult result = JdkHttpProbe.readResponse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
        assertThat(result.bodyPrefix()).hasSize(JdkHttpProbe.BODY_LIMIT);
    }

    private static String readRequestHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        List<Integer> last = new ArrayList<>();
        int c;
        while ((c = in.read()) != -1) {
            head.write(c);
            last.add(c);
            if (last.size() > 4) last.removeFirst();
            if (last.equals(List.of((int) '\r', (int) '\n', (int) '\r', (int) '\n'))) break;
        }
        return head.toString(StandardCharsets.ISO_8859_1);
    }
}
