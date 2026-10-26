package dev.codesgy.perimeter.common;

import org.springframework.http.HttpStatus;

/** 사용자에게 그대로 보여 줄 수 있는 요청 오류. code는 화면에서 분기할 때 쓴다 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
