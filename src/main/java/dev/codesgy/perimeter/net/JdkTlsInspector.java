package dev.codesgy.perimeter.net;

import dev.codesgy.perimeter.config.ScanProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.time.Instant;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;

@Component
public class JdkTlsInspector implements TlsInspector {

    private final ScanProperties properties;
    private final AddressGuard guard;

    public JdkTlsInspector(ScanProperties properties, AddressGuard guard) {
        this.properties = properties;
        this.guard = guard;
    }

    @Override
    public TlsInfo inspect(String host) {
        guard.requirePublic(host);
        try {
            // 1) 정상 검증(신뢰 체인 + 호스트 이름)으로 연결해 본다
            return handshake(host, SSLContext.getDefault(), true);
        } catch (SSLHandshakeException e) {
            // 2) 검증에 실패하면 검증 없이 다시 연결해 만료일 · 버전만 읽고, 실패 사유를 함께 남긴다
            try {
                TlsInfo raw = handshake(host, trustAllContext(), false);
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

    private TlsInfo handshake(String host, SSLContext context, boolean verifyHostname) throws IOException {
        int timeout = (int) properties.httpTimeout().toMillis();
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(host, 443), timeout);
            raw.setSoTimeout(timeout);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(raw, host, 443, false)) {
                if (verifyHostname) {
                    SSLParameters parameters = socket.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    socket.setSSLParameters(parameters);
                }
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
