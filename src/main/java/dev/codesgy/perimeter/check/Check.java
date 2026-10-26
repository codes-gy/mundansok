package dev.codesgy.perimeter.check;

import java.util.List;

/** 외부 노출 점검 하나. 문제가 없으면 빈 목록을 돌려준다 */
public interface Check {

    String id();

    List<CheckFinding> run(String host);
}
