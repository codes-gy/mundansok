package dev.codesgy.mundansok.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 이미 검증한 IP로 TCP 연결을 맺는다. 이름이 아니라 {@link InetSocketAddress}(풀린 주소)만 받으므로 여기서 DNS를 다시 조회하지 않는다.
 * 테스트에서는 어디로 연결하려 했는지 기록하는 가짜로 바꾼다.
 */
@FunctionalInterface
public interface SocketConnector {

    SocketConnector DIRECT = (address, timeoutMillis) -> {
        Socket socket = new Socket();
        try {
            socket.connect(address, timeoutMillis);
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    };

    Socket connect(InetSocketAddress address, int timeoutMillis) throws IOException;
}
