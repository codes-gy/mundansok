package dev.codesgy.mundansok.net;

import dev.codesgy.mundansok.config.ScanProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 443 포트 TLS 핸드셰이크로 인증서를 읽는다.
 * 이름은 {@link AddressGuard}에서 한 번만 풀고, 두 번의 핸드셰이크 모두 그때 검사한 IP로만 연결한다(DNS 리바인딩 방지).
 * 도메인 이름은 SNI와 인증서 호스트 이름 검증에만 쓴다.
 */
@Component
public class JdkTlsInspector implements TlsInspector {

    private final ScanProperties properties;
    private final AddressGuard guard;
    private final SocketConnector connector;

    @Autowired
    public JdkTlsInspector(ScanProperties properties, AddressGuard guard) {
        this(properties, guard, SocketConnector.DIRECT);
    }

    JdkTlsInspector(ScanProperties properties, AddressGuard guard, SocketConnector connector) {
        this.properties = properties;
        this.guard = guard;
        this.connector = connector;
    }

    @Override
    public TlsInfo inspect(String host) {
        InetAddress address = guard.resolvePublic(host);
        try {
            // 1) 정상 검증(신뢰 체인 + 호스트 이름)으로 연결해 본다
            return handshake(address, host, SSLContext.getDefault(), true);
        } catch (SSLHandshakeException e) {
            // 2) 검증에 실패하면 검증 없이 다시 연결해 만료일 · 버전만 읽고, 실패 사유를 함께 남긴다
            try {
                TlsInfo raw = handshake(address, host, trustAllContext(), false);
                return new TlsInfo(raw.protocol(), raw.notAfter(), false, e.getMessage());
            } catch (IOException retry) {
                throw new UncheckedIOException(retry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("TLS 점검 준비 실패", e);
        }
    }

    private TlsInfo handshake(InetAddress address, String host, SSLContext context, boolean verifyHostname) throws IOException {
        int timeout = (int) properties.httpTimeout().toMillis();
        // 이름이 아닌 검사한 IP로 연결한다 — new InetSocketAddress(host, 443)는 이름을 다시 풀어 리바인딩에 뚫린다
        try (Socket raw = connector.connect(new InetSocketAddress(address, 443), timeout)) {
            raw.setSoTimeout(timeout);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(raw, host, 443, false)) {
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setServerNames(List.of(new SNIHostName(host)));
                if (verifyHostname) {
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                }
                socket.setSSLParameters(parameters);
                socket.startHandshake();
                X509Certificate leaf = (X509Certificate) socket.getSession().getPeerCertificates()[0];
                Instant notAfter = leaf.getNotAfter().toInstant();
                return new TlsInfo(socket.getSession().getProtocol(), notAfter, verifyHostname, null);
            }
        }
    }

    private static SSLContext trustAllContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            context.init(null, new TrustManager[] {trustAll}, null);
            return context;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
