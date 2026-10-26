package dev.codesgy.mundansok.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** 인증서 만료 · 점검 간격 계산을 테스트에서 고정할 수 있게 시계를 빈으로 둔다 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
