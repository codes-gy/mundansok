package dev.codesgy.mundansok.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 메서드에 붙이면 요청한 IP별로 횟수를 제한한다. 값은 mundansok.rate-limit.limits 의 키 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String value();
}
