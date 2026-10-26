package dev.codesgy.mundansok.scan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.check.CheckFinding;
import dev.codesgy.mundansok.check.Severity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoresTest {

    private static CheckFinding of(Severity severity) {
        return new CheckFinding("test", severity, "t", "d", "r");
    }

    @Test
    void 위험도별로_감점한다() {
        assertThat(Scores.of(List.of())).isEqualTo(100);
        assertThat(Scores.of(List.of(of(Severity.HIGH), of(Severity.MEDIUM), of(Severity.LOW), of(Severity.INFO)))).isEqualTo(67);
    }

    @Test
    void 영점_아래로_내려가지_않는다() {
        assertThat(Scores.of(Collections.nCopies(10, of(Severity.HIGH)))).isZero();
    }
}
