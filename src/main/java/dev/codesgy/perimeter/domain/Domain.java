package dev.codesgy.perimeter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** 점검 대상 도메인. 소유 인증(verifiedAt)이 있어야 점검할 수 있다 */
@Entity
public class Domain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String verificationToken;

    private Instant verifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Domain() {}

    public Domain(String name, String verificationToken, Instant createdAt) {
        this.name = name;
        this.verificationToken = verificationToken;
        this.createdAt = createdAt;
    }

    /** 소유 인증 TXT 레코드를 둘 이름 */
    public String verificationRecordName() {
        return "_perimeter-verify." + name;
    }

    /** 소유 인증 TXT 레코드에 넣을 값 */
    public String verificationRecordValue() {
        return "perimeter-verify=" + verificationToken;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    void markVerified(Instant at) {
        this.verifiedAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
