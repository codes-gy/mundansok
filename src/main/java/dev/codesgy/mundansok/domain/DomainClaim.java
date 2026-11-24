package dev.codesgy.mundansok.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * "이 사람이 이 도메인의 주인이라고 주장한다"는 등록 한 건.
 *
 * <p>등록할 때마다 새로 만들고, 등록한 사람에게만 소유자 토큰(평문은 한 번만 응답)을 준다. 인증 · 점검 · 결과 조회는 이 토큰이 있어야 한다.
 * 같은 도메인을 다른 사람이 등록해도 기존 등록의 토큰이나 인증 상태를 넘겨받지 못한다.
 */
@Entity
@Table(name = "domain_claim")
public class DomainClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API에 내보내는 식별자. 순번(id)은 추측할 수 있어 밖으로 내보내지 않는다 */
    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id", nullable = false, updatable = false)
    private Domain domain;

    /** 소유자 토큰의 SHA-256. 평문은 저장하지 않는다 */
    @Column(nullable = false)
    private String ownerSecretHash;

    /** TXT 레코드에 넣을 값. 등록마다 다르다 */
    @Column(nullable = false)
    private String verificationToken;

    /** 마지막으로 TXT 레코드를 확인한 시각. 점검 요청 때마다 다시 확인한다 */
    private Instant verifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected DomainClaim() {}

    DomainClaim(UUID publicId, Domain domain, String ownerSecretHash, String verificationToken, Instant createdAt) {
        this.publicId = publicId;
        this.domain = domain;
        this.ownerSecretHash = ownerSecretHash;
        this.verificationToken = verificationToken;
        this.createdAt = createdAt;
    }

    public String verificationRecordName() {
        return domain.verificationRecordName();
    }

    public String verificationRecordValue() {
        return "mundansok-verify=" + verificationToken;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    void markVerified(Instant at) {
        this.verifiedAt = at;
    }

    boolean ownedBy(String ownerToken) {
        return OwnerTokens.matches(ownerToken, ownerSecretHash);
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Domain getDomain() {
        return domain;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
