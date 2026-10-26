package dev.codesgy.mundansok.check;

/** 위험도와 점수에서 빼는 값 */
public enum Severity {
    HIGH(20),
    MEDIUM(10),
    LOW(3),
    INFO(0);

    private final int penalty;

    Severity(int penalty) {
        this.penalty = penalty;
    }

    public int penalty() {
        return penalty;
    }
}
