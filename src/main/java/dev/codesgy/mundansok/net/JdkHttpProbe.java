package dev.codesgy.mundansok.net;

import dev.codesgy.mundansok.config.ScanProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 점검용 HTTP/1.1 GET. 리다이렉트는 따라가지 않는다.
 *
 * <p>java.net.http.HttpClient는 send 할 때 호스트 이름을 다시 풀기 때문에, {@link AddressGuard}로 검사한 IP와 실제로 연결하는 IP가
 * 달라질 수 있다(DNS 리바인딩). 그래서 소켓을 직접 열어 <b>검사한 IP로만</b> 연결하고, 이름은 Host 헤더와 TLS SNI · 인증서 검증에만 쓴다.
 */
@Component
public class JdkHttpProbe implements HttpProbe {

    static final int BODY_LIMIT = 2048;
    private static final int LINE_LIMIT = 8 * 1024;
    private static final int HEADER_LIMIT = 64 * 1024;
    private static final int MAX_HEADERS = 200;

    private final ScanProperties properties;
    private final AddressGuard guard;
    private final SocketConnector connector;
    private final SSLSocketFactory tlsFactory;

    @Autowired
    public JdkHttpProbe(ScanProperties properties, AddressGuard guard) {
        this(properties, guard, SocketConnector.DIRECT, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    JdkHttpProbe(ScanProperties properties, AddressGuard guard, SocketConnector connector, SSLSocketFactory tlsFactory) {
        this.properties = properties;
        this.guard = guard;
        this.connector = connector;
        this.tlsFactory = tlsFactory;
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("http · https 주소만 점검합니다: " + url);
        }
        String host = uri.getHost();
        if (host == null) throw new IllegalArgumentException("호스트가 없는 주소입니다: " + url);
        boolean tls = scheme.equals("https");
        int defaultPort = tls ? 443 : 80;
        int port = uri.getPort() == -1 ? defaultPort : uri.getPort();

        // 이름은 여기서 한 번만 푼다. 아래 연결은 이 IP로만 한다
        InetAddress address = guard.resolvePublic(host);
        int timeoutMillis = (int) properties.httpTimeout().toMillis();
        long deadline = System.nanoTime() + properties.httpTimeout().toNanos();

        try (Socket raw = connector.connect(new InetSocketAddress(address, port), timeoutMillis)) {
            raw.setSoTimeout(timeoutMillis);
            Socket socket = tls ? startTls(raw, host, port) : raw;
            try {
                String hostHeader = port == defaultPort ? host : host + ":" + port;
                writeRequest(socket.getOutputStream(), target(uri), hostHeader, headers);
                return readResponse(new DeadlineInputStream(socket, deadline));
            } finally {
                if (socket != raw) socket.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 검사한 IP로 연결한 소켓 위에 TLS를 얹는다. SNI와 인증서 호스트 이름 검증은 도메인 이름으로 한다 */
    private SSLSocket startTls(Socket raw, String host, int port) throws IOException {
        SSLSocket socket = (SSLSocket) tlsFactory.createSocket(raw, host, port, true);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        parameters.setServerNames(List.of(new SNIHostName(host)));
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        return socket;
    }

    private static String target(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private void writeRequest(OutputStream out, String target, String host, Map<String, String> headers) throws IOException {
        StringBuilder request = new StringBuilder()
                .append("GET ").append(requireSafe(target)).append(" HTTP/1.1\r\n")
                .append("Host: ").append(requireSafe(host)).append("\r\n")
                .append("User-Agent: ").append(requireSafe(properties.userAgent())).append("\r\n")
                .append("Accept: */*\r\n")
                .append("Connection: close\r\n");
        headers.forEach((name, value) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("host") || lower.equals("connection") || lower.equals("user-agent")) return;
            request.append(requireSafe(name)).append(": ").append(requireSafe(value)).append("\r\n");
        });
        request.append("\r\n");
        out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    /** 헤더 주입(CRLF) 방지 */
    private static String requireSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == 0) throw new IllegalArgumentException("요청에 줄바꿈 문자를 넣을 수 없습니다");
        }
        return value;
    }

    static HttpResult readResponse(InputStream in) throws IOException {
        int status;
        Map<String, List<String>> headers;
        // 1xx(100 Continue · 103 Early Hints)는 건너뛰고 최종 응답을 읽는다
        do {
            String statusLine = readLine(in);
            if (statusLine == null) throw new IOException("응답 없이 연결이 끊겼습니다");
            status = parseStatus(statusLine);
            headers = readHeaders(in);
        } while (status >= 100 && status < 200);

        String body = "";
        if (status != 204 && status != 304) {
            body = new String(readBody(in, headers), StandardCharsets.UTF_8);
        }
        return new HttpResult(status, headers, body);
    }

    private static int parseStatus(String line) throws IOException {
        String[] parts = line.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) throw new IOException("HTTP 응답이 아닙니다");
        int status;
        try {
            status = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("상태 코드가 올바르지 않습니다: " + parts[1]);
        }
        if (status < 100 || status > 999) throw new IOException("상태 코드가 올바르지 않습니다: " + parts[1]);
        return status;
    }

    private static Map<String, List<String>> readHeaders(InputStream in) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>();
        int total = 0;
        int count = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            total += line.length();
            if (total > HEADER_LIMIT || ++count > MAX_HEADERS) throw new IOException("응답 헤더가 너무 큽니다");
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        headers.replaceAll((name, values) -> List.copyOf(values));
        return headers;
    }

    private static byte[] readBody(InputStream in, Map<String, List<String>> headers) throws IOException {
        List<String> encoding = headers.getOrDefault("transfer-encoding", List.of());
        if (encoding.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("chunked"))) {
            return readChunked(in);
        }
        List<String> length = headers.getOrDefault("content-length", List.of());
        if (!length.isEmpty()) {
            try {
                long declared = Long.parseLong(length.getFirst().trim());
                return in.readNBytes((int) Math.min(Math.max(declared, 0), BODY_LIMIT));
            } catch (NumberFormatException ignored) {
                // 잘못된 길이면 연결이 끝날 때까지(한도까지) 읽는다
            }
        }
        return in.readNBytes(BODY_LIMIT);
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (body.size() < BODY_LIMIT) {
            String sizeLine = readLine(in);
            if (sizeLine == null) break;
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
            long size;
            try {
                size = Long.parseLong(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("chunked 본문 형식이 올바르지 않습니다");
            }
            if (size <= 0) break;
            byte[] chunk = in.readNBytes((int) Math.min(size, BODY_LIMIT - body.size()));
            body.write(chunk);
            if (chunk.length < size) break; // 한도에 닿았거나 연결이 끊김
            readLine(in); // 조각 뒤 CRLF
        }
        return body.toByteArray();
    }

    /** CRLF(또는 LF)로 끝나는 한 줄. 연결이 바로 끝나면 null */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (line.size() >= LINE_LIMIT) throw new IOException("응답 줄이 너무 깁니다");
            line.write(c);
        }
        if (c == -1 && line.size() == 0) return null;
        String text = line.toString(StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }

    /** 읽기마다 남은 시간만큼만 기다린다 — 한 바이트씩 흘리는 서버가 점검 스레드를 붙잡지 못하게 요청 전체에 시간 제한을 건다 */
    private static final class DeadlineInputStream extends InputStream {

        private final Socket socket;
        private final InputStream in;
        private final long deadline;
        private final byte[] buffer = new byte[4096];
        private int position;
        private int limit;

        DeadlineInputStream(Socket socket, long deadline) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.deadline = deadline;
        }

        @Override
        public int read() throws IOException {
            if (position == limit && !fill()) return -1;
            return buffer[position++] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (position == limit && !fill()) return -1;
            int n = Math.min(len, limit - position);
            System.arraycopy(buffer, position, b, off, n);
            position += n;
            return n;
        }

        private boolean fill() throws IOException {
            long remaining = (deadline - System.nanoTime()) / 1_000_000;
            if (remaining <= 0) throw new SocketTimeoutException("점검 요청 시간이 지났습니다");
            socket.setSoTimeout((int) Math.max(1, remaining));
            int n = in.read(buffer, 0, buffer.length);
            if (n <= 0) return false;
            position = 0;
            limit = n;
            return true;
        }
    }
}
