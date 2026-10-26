package dev.codesgy.mundansok.check;

/**
 * 점검 하나가 찾은 문제.
 *
 * @param detail         무엇을 봤는지(근거). 노출된 비밀 값 자체는 넣지 않는다
 * @param recommendation 고치는 방법
 */
public record CheckFinding(String checkId, Severity severity, String title, String detail, String recommendation) {}
