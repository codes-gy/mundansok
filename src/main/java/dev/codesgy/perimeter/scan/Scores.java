package dev.codesgy.perimeter.scan;

import dev.codesgy.perimeter.check.CheckFinding;
import java.util.List;

/** 100점에서 위험도별 감점(높음 20 · 보통 10 · 낮음 3)을 뺀다. 최저 0 */
public final class Scores {

    private Scores() {}

    public static int of(List<CheckFinding> findings) {
        int penalty = findings.stream().mapToInt(f -> f.severity().penalty()).sum();
        return Math.max(0, 100 - penalty);
    }
}
