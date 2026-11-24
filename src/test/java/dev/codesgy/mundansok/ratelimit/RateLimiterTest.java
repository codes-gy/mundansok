package dev.codesgy.mundansok.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.ratelimit.RateLimitProperties.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Limit THREE_PER_MINUTE = new Limit(3, Duration.ofMinutes(1));

    /** 테스트에서 시간을 움직이는 시계 */
    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-25T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void 한도까지는_통과하고_넘치면_기다릴_시간을_준다() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("scan|1.2.3.4", THREE_PER_MINUTE)).isEmpty();
        }
        assertThat(limiter.tryAcquire("scan|1.2.3.4", THREE_PER_MINUTE)).hasValueSatisfying(wait ->
                assertThat(wait).isEqualTo(Duration.ofSeconds(20)));
    }

    @Test
    void 시간이_지나면_다시_찬다() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", THREE_PER_MINUTE);
        assertThat(limiter.tryAcquire("k", THREE_PER_MINUTE)).isPresent();

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire("k", THREE_PER_MINUTE)).isEmpty();
        assertThat(limiter.tryAcquire("k", THREE_PER_MINUTE)).isPresent();
    }

    @Test
    void 키마다_따로_센다() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("register|1.1.1.1", THREE_PER_MINUTE);
        assertThat(limiter.tryAcquire("register|1.1.1.1", THREE_PER_MINUTE)).isPresent();
        assertThat(limiter.tryAcquire("register|2.2.2.2", THREE_PER_MINUTE)).isEmpty();
        assertThat(limiter.tryAcquire("verify|1.1.1.1", THREE_PER_MINUTE)).isEmpty();
    }
}
