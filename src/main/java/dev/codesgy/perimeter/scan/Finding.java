package dev.codesgy.perimeter.scan;

import dev.codesgy.perimeter.check.CheckFinding;
import dev.codesgy.perimeter.check.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scanId;

    @Column(nullable = false)
    private String checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String detail;

    @Column(nullable = false)
    private String recommendation;

    protected Finding() {}

    Finding(Long scanId, CheckFinding finding) {
        this.scanId = scanId;
        this.checkId = finding.checkId();
        this.severity = finding.severity();
        this.title = cut(finding.title(), 200);
        this.detail = cut(finding.detail(), 500);
        this.recommendation = cut(finding.recommendation(), 500);
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public Long getId() {
        return id;
    }

    public String getCheckId() {
        return checkId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
