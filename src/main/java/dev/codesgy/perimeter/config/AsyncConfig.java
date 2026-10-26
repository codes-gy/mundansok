package dev.codesgy.perimeter.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /** 점검은 외부 응답을 기다리는 일이 대부분이라 요청 스레드와 분리해 작은 풀에서 돌린다 */
    @Bean
    public Executor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
