package dev.codesgy.mundansok.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** 호스트 이름 → IP. 테스트에서 DNS 리바인딩(조회할 때마다 다른 답)을 흉내 내려고 분리했다 */
@FunctionalInterface
public interface HostResolver {

    HostResolver SYSTEM = InetAddress::getAllByName;

    InetAddress[] resolve(String host) throws UnknownHostException;
}
