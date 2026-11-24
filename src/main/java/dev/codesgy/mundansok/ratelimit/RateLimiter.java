package dev.codesgy.mundansok.ratelimit;

import dev.codesgy.mundansok.ratelimit.RateLimitProperties.Limit;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 메모리 토큰 버킷. 키(동작 + IP)마다 버킷 하나.
 *
 * <p><b>한계</b>: 서버 한 대 기준이다. 여러 대로 띄우면 대수만큼 한도가 늘어나고, 재시작하면 초기화된다.
 * 여러 대로 운영할 때는 Redis 등 공유 저장소를 쓰는 구현(Bucket4j + Redis 등)이나 앞단 프록시 · WAF의 제한으로 바꿔야 한다.
 */
@Component
public class RateLimiter {

    /** 이보다 많아지면 가득 찬(오래 안 쓴) 버킷을 정리한다 — 여러 IP로 메모리를 채우는 것을 막는다 */
    static final int MAX_BUCKETS = 100_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 한 번 쓸 수 있으면 빈 값, 아니면 다음에 쓸 수 있을 때까지 기다릴 시간 */
    public Optional<Duration> tryAcquire(String key, Limit limit) {
        long now = clock.millis();
        if (buckets.size() >= MAX_BUCKETS) evictFull(now);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit, now));
        return bucket.tryAcquire(now);
    }

    int size() {
        return buckets.size();
    }

    private void evictFull(long now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(now));
    }

    private static final class Bucket {

        private final Limit limit;
        private double tokens;
        private long updatedAt;

        Bucket(Limit limit, long now) {
            this.limit = limit;
            this.tokens = limit.capacity();
            this.updatedAt = now;
        }

        synchronized Optional<Duration> tryAcquire(long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return Optional.empty();
            }
            double millisPerToken = (double) limit.period().toMillis() / limit.capacity();
            long wait = (long) Math.ceil((1 - tokens) * millisPerToken);
            return Optional.of(Duration.ofMillis(Math.max(1, wait)));
        }

        /** 다시 가득 찬 버킷은 지워도 처음 만든 것과 같다 */
        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= limit.capacity();
        }

        private void refill(long now) {
            long elapsed = Math.max(0, now - updatedAt);
            double refill = elapsed * (double) limit.capacity() / limit.period().toMillis();
            tokens = Math.min(limit.capacity(), tokens + refill);
            updatedAt = now;
        }
    }
}
