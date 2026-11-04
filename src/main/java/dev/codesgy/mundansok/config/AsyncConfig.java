package dev.codesgy.mundansok.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 점검은 외부 응답을 기다리는 일이 대부분이라 요청 스레드와 분리해 작은 풀에서 돌린다.
     * ThreadPoolTaskExecutor 타입으로 노출해야 Spring Boot가 executor.* 지표(name=scanExecutor)를 자동으로 붙인다.
     * 대기열까지 가득 차면 execute가 TaskRejectedException을 던진다 → ScanService가 503으로 돌려보낸다.
     */
    @Bean
    public ThreadPoolTaskExecutor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
