package dev.codesgy.mundansok.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * 점검 대상 도메인 이름. 이름당 한 행이며, 소유 인증은 등록한 사람별로 {@link DomainClaim}에 따로 둔다.
 * 쿨다운(같은 도메인 재점검 간격)은 누가 요청했든 이름 기준이라 여기에 둔다.
 */
@Entity
public class Domain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** 마지막으로 점검 요청을 받은 시각. 조건부 UPDATE로만 바꾼다 ({@link DomainRepository#acquireScanSlot}) */
    private Instant lastScanRequestedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Domain() {}

    public Domain(String name, Instant createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    /** 소유 인증 TXT 레코드를 둘 이름 */
    public String verificationRecordName() {
        return "_mundansok-verify." + name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getLastScanRequestedAt() {
        return lastScanRequestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
