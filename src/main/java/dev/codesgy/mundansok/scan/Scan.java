package dev.codesgy.mundansok.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long domainId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    private Integer score;

    private String error;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;

    protected Scan() {}

    public Scan(Long domainId, Instant createdAt) {
        this.domainId = domainId;
        this.status = ScanStatus.QUEUED;
        this.createdAt = createdAt;
    }

    void start() {
        this.status = ScanStatus.RUNNING;
    }

    void finish(int score, Instant at) {
        this.status = ScanStatus.DONE;
        this.score = score;
        this.finishedAt = at;
    }

    void fail(String error, Instant at) {
        this.status = ScanStatus.FAILED;
        this.error = error.length() > 500 ? error.substring(0, 500) : error;
        this.finishedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getDomainId() {
        return domainId;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
