package dev.codesgy.mundansok.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * 점검 대상이 내부망을 가리키면 요청하지 않는다(SSRF 방지).
 *
 * <p>호스트가 풀리는 IP 중 하나라도 공인 주소가 아니면 막는다. 검사한 IP를 돌려주므로 호출하는 쪽은 그 IP로 직접 연결해야 한다 —
 * 연결할 때 이름을 다시 풀면 그사이 DNS가 내부 주소로 바뀌는 DNS 리바인딩(TOCTOU)에 뚫린다.
 */
@Component
public class AddressGuard {

    private final HostResolver resolver;

    public AddressGuard() {
        this(HostResolver.SYSTEM);
    }

    public AddressGuard(HostResolver resolver) {
        this.resolver = resolver;
    }

    /** 호스트를 한 번만 풀어 모든 주소가 공인 주소인지 확인하고, 연결에 쓸 주소를 돌려준다 */
    public InetAddress resolvePublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new BlockedAddressException(host + " 의 주소를 찾을 수 없습니다");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BlockedAddressException(host + " 의 주소를 찾을 수 없습니다");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new BlockedAddressException(host + " 이(가) 내부 주소(" + address.getHostAddress() + ")로 연결돼 점검하지 않습니다");
            }
        }
        return addresses[0];
    }

    static boolean isPublic(InetAddress address) {
        if (address instanceof Inet6Address v6) {
            return isPublicV6(v6);
        }
        return isPublicV4(address);
    }

    private static boolean isPublicV4(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        int third = b[2] & 0xff;
        if (first == 0 || first == 127 || first >= 224) return false; // 0/8 · 루프백 · 멀티캐스트 · 예약(240/4) · 브로드캐스트
        if (first == 10) return false; // 10/8
        if (first == 172 && second >= 16 && second <= 31) return false; // 172.16/12
        if (first == 192 && second == 168) return false; // 192.168/16
        if (first == 169 && second == 254) return false; // 169.254/16 링크 로컬 (클라우드 메타데이터)
        if (first == 100 && second >= 64 && second <= 127) return false; // 100.64.0.0/10 CGNAT
        if (first == 198 && (second == 18 || second == 19)) return false; // 198.18.0.0/15 벤치마크
        if (first == 192 && second == 0 && third == 0) return false; // 192.0.0.0/24 IETF 프로토콜 할당
        if (first == 192 && second == 88 && third == 99) return false; // 192.88.99.0/24 6to4 릴레이
        return true;
    }

    private static boolean isPublicV6(Inet6Address address) {
        byte[] b = address.getAddress();
        // IPv4-mapped(::ffff:a.b.c.d) · IPv4-compatible(::a.b.c.d)는 안에 든 IPv4로 판단한다
        if (allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            return isPublicV4(embeddedV4(b, 12));
        }
        if (allZero(b, 0, 12)) {
            // ::, ::1 도 여기로 온다 (0.0.0.0 · 0.0.0.1 → 비공인)
            return isPublicV4(embeddedV4(b, 12));
        }
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        int w0 = word(b, 0);
        int w1 = word(b, 2);
        if ((b[0] & 0xfe) == 0xfc) return false; // fc00::/7 고유 로컬 주소
        if (w0 == 0x0064 && w1 == 0xff9b) return false; // 64:ff9b::/96 NAT64 (+ 64:ff9b:1::/48 로컬 NAT64)
        if (w0 == 0x2002) return false; // 2002::/16 6to4 — 안에 임의의 IPv4를 담을 수 있다
        if (w0 == 0x2001 && w1 == 0x0000) return false; // 2001::/32 Teredo
        if (w0 == 0x2001 && w1 == 0x0db8) return false; // 2001:db8::/32 문서용
        if (w0 == 0x0100 && allZero(b, 2, 8)) return false; // 100::/64 폐기용
        return true;
    }

    private static InetAddress embeddedV4(byte[] b, int offset) {
        try {
            return Inet4Address.getByAddress(Arrays.copyOfRange(b, offset, offset + 4));
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e); // 4바이트면 일어나지 않는다
        }
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) return false;
        }
        return true;
    }

    private static int word(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 8) | (b[offset + 1] & 0xff);
    }

    public static class BlockedAddressException extends RuntimeException {
        public BlockedAddressException(String message) {
            super(message);
        }
    }
}
