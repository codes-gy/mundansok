package dev.codesgy.mundansok.support;

import dev.codesgy.mundansok.net.TlsInfo;
import dev.codesgy.mundansok.net.TlsInspector;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 스프링 컨텍스트 테스트에서 실제 네트워크 대신 쓰는 가짜 DNS · HTTP · TLS. @Import로 붙인다 */
@TestConfiguration
public class FakeNetwork {

    @Bean
    @Primary
    public FakeDnsResolver fakeDns() {
        return new FakeDnsResolver();
    }

    @Bean
    @Primary
    public FakeHttpProbe fakeProbe() {
        return new FakeHttpProbe();
    }

    @Bean
    @Primary
    public TlsInspector fakeTls() {
        return host -> new TlsInfo("TLSv1.3", Instant.now().plus(Duration.ofDays(90)), true, null);
    }
}
