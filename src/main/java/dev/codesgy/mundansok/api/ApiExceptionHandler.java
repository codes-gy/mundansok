package dev.codesgy.mundansok.api;

import dev.codesgy.mundansok.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 오류는 RFC 9457 Problem Details로 내려 주고, 화면이 분기할 수 있게 code를 붙인다 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handle(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handle(MethodArgumentNotValidException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력값을 확인해 주세요");
    }

    /** UUID가 아닌 id 등 경로 값 형식 오류 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handle(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 주소의 id 형식이 올바르지 않습니다");
    }

    /** 같은 도메인을 동시에 처음 등록하는 경우 등. 다시 보내면 된다 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handle(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", "같은 요청이 동시에 처리되고 있어요. 잠시 후 다시 시도해 주세요");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        return problem;
    }
}
