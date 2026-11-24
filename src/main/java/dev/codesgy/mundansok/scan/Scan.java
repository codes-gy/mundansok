package dev.codesgy.mundansok.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API에 내보내는 식별자. 순번 id는 추측할 수 있어 밖으로 내보내지 않는다 (V2 이전 기록은 null — 조회할 수 없음) */
    @Column(unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private Long domainId;

    /** 점검을 요청한 등록. 결과는 이 등록의 소유자 토큰으로만 볼 수 있다 (V2 이전 기록은 null) */
    @Column(updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    private Integer score;

    private String error;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;

    protected Scan() {}

    public Scan(Long domainId, Long claimId, Instant createdAt) {
        this.publicId = UUID.randomUUID();
        this.domainId = domainId;
        this.claimId = claimId;
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

    public UUID getPublicId() {
        return publicId;
    }

    public Long getClaimId() {
        return claimId;
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
