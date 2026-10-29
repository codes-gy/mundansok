package dev.codesgy.mundansok.net;

import java.util.List;

/** DNS 레코드 조회. 테스트에서는 가짜 구현으로 바꾼다. 조회 자체가 실패하면 {@link IllegalStateException} */
public interface DnsResolver {

    /** 이름의 TXT 레코드 값들. 레코드가 없으면 빈 목록 */
    List<String> txt(String name);

    /** 이름의 CAA 레코드("0 issue \"letsencrypt.org\"" 형식). 레코드가 없으면 빈 목록 */
    List<String> caa(String name);
}
