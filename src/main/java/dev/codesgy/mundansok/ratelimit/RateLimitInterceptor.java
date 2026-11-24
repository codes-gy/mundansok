package dev.codesgy.mundansok.ratelimit;

import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.ratelimit.RateLimitProperties.Limit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimited}가 붙은 요청을 IP별 토큰 버킷으로 제한한다. 넘치면 429 RATE_LIMITED + Retry-After.
 *
 * <p>IP는 {@link HttpServletRequest#getRemoteAddr()}다. 프록시(Next.js 등) 뒤에 둘 때는 server.forward-headers-strategy로
 * 믿을 수 있는 프록시가 붙인 X-Forwarded-For만 반영되게 한다 (application.yaml 참고). 클라이언트가 보낸 헤더를 그대로 믿지 않는다.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled() || !(handler instanceof HandlerMethod method)) return true;
        RateLimited annotation = method.getMethodAnnotation(RateLimited.class);
        if (annotation == null) return true;
        Limit limit = properties.limits().get(annotation.value());
        if (limit == null) {
            throw new IllegalStateException("mundansok.rate-limit.limits." + annotation.value() + " 설정이 없습니다");
        }

        Optional<Duration> wait = limiter.tryAcquire(annotation.value() + "|" + request.getRemoteAddr(), limit);
        if (wait.isEmpty()) return true;
        long seconds = Math.max(1, (wait.get().toMillis() + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많아요. " + seconds + "초 뒤에 다시 시도해 주세요");
    }
}
