package dev.codesgy.mundansok.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * 점검 대상이 내부망을 가리키면 요청하지 않는다(SSRF 방지).
 * 호스트가 풀리는 IP 중 하나라도 사설 · 루프백 · 링크 로컬 · CGNAT · IPv6 ULA면 막는다.
 */
@Component
public class AddressGuard {

    public void requirePublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BlockedAddressException(host + " 의 주소를 찾을 수 없습니다");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new BlockedAddressException(host + " 이(가) 내부 주소(" + address.getHostAddress() + ")로 연결돼 점검하지 않습니다");
            }
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc; // fc00::/7 고유 로컬 주소
        }
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        if (first == 100 && second >= 64 && second <= 127) return false; // 100.64.0.0/10 CGNAT
        if (first == 0 || first >= 240) return false; // 예약 대역
        return true;
    }

    public static class BlockedAddressException extends RuntimeException {
        public BlockedAddressException(String message) {
            super(message);
        }
    }
}
